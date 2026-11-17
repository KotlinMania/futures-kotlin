#[cfg(test)]
mod tests {
    use futures_util::{Sink, SinkExt};
    use std::{
        cell::RefCell,
        collections::VecDeque,
        pin::Pin,
        rc::Rc,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
        task::{Context, Poll, Wake, Waker},
    };
    type Outcome = Poll<Result<(), Option<&'static str>>>;
    #[derive(Clone, Copy)]
    enum Mode {
        Normal,
        Null,
        Panic,
    }
    struct SourceError {
        value: Option<&'static str>,
        trace: Rc<RefCell<Vec<String>>>,
        mode: Mode,
    }
    #[derive(Debug, PartialEq, Eq)]
    struct Converted(Option<&'static str>);
    impl From<SourceError> for Converted {
        fn from(error: SourceError) -> Self {
            match error.mode {
                Mode::Normal => {
                    error
                        .trace
                        .borrow_mut()
                        .push(format!("map:{}", error.value.unwrap()));
                    Converted(Some("mapped"))
                }
                Mode::Null => {
                    assert_eq!(error.value, None);
                    error.trace.borrow_mut().push("map:null".into());
                    Converted(None)
                }
                Mode::Panic => {
                    error.trace.borrow_mut().push("map:panic".into());
                    panic!("mapper panic");
                }
            }
        }
    }

    #[derive(Clone, Copy)]
    enum Op {
        Ready,
        Send,
        Flush,
        Close,
    }
    impl Op {
        fn name(self) -> &'static str {
            match self {
                Self::Ready => "ready",
                Self::Send => "send",
                Self::Flush => "flush",
                Self::Close => "close",
            }
        }
    }
    const OPS: [Op; 4] = [Op::Ready, Op::Send, Op::Flush, Op::Close];
    struct Output {
        trace: Rc<RefCell<Vec<String>>>,
        events: VecDeque<Outcome>,
        mode: Mode,
    }
    impl Output {
        fn event(&mut self, op: Op, cx: Option<&mut Context<'_>>) -> Poll<Result<(), SourceError>> {
            self.trace.borrow_mut().push(op.name().into());
            let result = self.events.pop_front().expect("unexpected sink operation");
            if result.is_pending() {
                cx.expect("start_send must not be Pending")
                    .waker()
                    .wake_by_ref();
            }
            result.map(|r| {
                r.map_err(|value| SourceError {
                    value,
                    trace: self.trace.clone(),
                    mode: self.mode,
                })
            })
        }
    }
    impl Sink<i32> for Output {
        type Error = SourceError;
        fn poll_ready(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            self.event(Op::Ready, Some(cx))
        }
        fn start_send(mut self: Pin<&mut Self>, _: i32) -> Result<(), Self::Error> {
            match self.event(Op::Send, None) {
                Poll::Ready(v) => v,
                Poll::Pending => panic!("unexpected Pending send"),
            }
        }
        fn poll_flush(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            self.event(Op::Flush, Some(cx))
        }
        fn poll_close(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            self.event(Op::Close, Some(cx))
        }
    }
    struct Count(AtomicUsize);
    impl Wake for Count {
        fn wake(self: Arc<Self>) {
            self.0.fetch_add(1, Ordering::SeqCst);
        }
    }
    fn task() -> (Arc<Count>, Waker) {
        let c = Arc::new(Count(AtomicUsize::new(0)));
        let w = Waker::from(c.clone());
        (c, w)
    }
    fn output(events: Vec<Outcome>) -> (Output, Rc<RefCell<Vec<String>>>) {
        let r = Rc::new(RefCell::new(vec![]));
        (
            Output {
                trace: r.clone(),
                events: events.into(),
                mode: Mode::Normal,
            },
            r,
        )
    }
    fn call<S: Sink<i32> + Unpin>(
        s: &mut S,
        op: Op,
        cx: &mut Context<'_>,
    ) -> Poll<Result<(), S::Error>> {
        match op {
            Op::Ready => Pin::new(s).poll_ready(cx),
            Op::Send => Poll::Ready(Pin::new(s).start_send(1)),
            Op::Flush => Pin::new(s).poll_flush(cx),
            Op::Close => Pin::new(s).poll_close(cx),
        }
    }
    fn panic_text(p: Box<dyn std::any::Any + Send>) -> String {
        if let Some(s) = p.downcast_ref::<&str>() {
            s.to_string()
        } else {
            p.downcast_ref::<String>().unwrap().clone()
        }
    }
    fn first_error(first: Op) {
        for second in OPS {
            let (o, r) = output(vec![
                Poll::Ready(Err(Some("first"))),
                Poll::Ready(Err(Some("second"))),
            ]);
            let mut o = o;
            o.mode = Mode::Normal;
            let mut s = o.sink_err_into::<Converted>();
            let (_, w) = task();
            let mut cx = Context::from_waker(&w);
            assert_eq!(
                call(&mut s, first, &mut cx),
                Poll::Ready(Err(Converted(Some("mapped"))))
            );
            let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                let _ = call(&mut s, second, &mut cx);
            }))
            .unwrap_err();
            assert_eq!(panic_text(p), "polled MapErr after completion");
            assert_eq!(*r.borrow(), vec![first.name(), "map:first", second.name()]);
        }
    }
    #[test]
    fn ready_consumes_mapper() {
        first_error(Op::Ready);
    }
    #[test]
    fn send_consumes_mapper() {
        first_error(Op::Send);
    }
    #[test]
    fn flush_consumes_mapper() {
        first_error(Op::Flush);
    }
    #[test]
    fn close_consumes_mapper() {
        first_error(Op::Close);
    }
    #[test]
    fn null_error_and_null_result_are_errors() {
        let (o, r) = output(vec![
            Poll::Ready(Err(None)),
            Poll::Ready(Err(Some("second"))),
        ]);
        let mut o = o;
        o.mode = Mode::Null;
        let mut s = o.sink_err_into::<Converted>();
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            call(&mut s, Op::Ready, &mut cx),
            Poll::Ready(Err(Converted(None)))
        );
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = call(&mut s, Op::Close, &mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "polled MapErr after completion");
        assert_eq!(*r.borrow(), vec!["ready", "map:null", "close"]);
    }
    #[test]
    fn mapper_panic_consumes_mapper() {
        let (o, r) = output(vec![
            Poll::Ready(Err(Some("first"))),
            Poll::Ready(Err(Some("second"))),
        ]);
        let mut o = o;
        o.mode = Mode::Panic;
        let mut s = o.sink_err_into::<Converted>();
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = call(&mut s, Op::Flush, &mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "mapper panic");
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = call(&mut s, Op::Send, &mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "polled MapErr after completion");
        assert_eq!(*r.borrow(), vec!["flush", "map:panic", "send"]);
    }
    #[test]
    fn pending_and_success_preserve_mapper() {
        let (o, r) = output(vec![
            Poll::Pending,
            Poll::Ready(Ok(())),
            Poll::Ready(Ok(())),
            Poll::Ready(Ok(())),
            Poll::Ready(Err(Some("first"))),
        ]);
        let mut o = o;
        o.mode = Mode::Normal;
        let mut s = o.sink_err_into::<Converted>();
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(call(&mut s, Op::Ready, &mut cx), Poll::Pending);
        for op in [Op::Flush, Op::Close, Op::Send] {
            assert_eq!(call(&mut s, op, &mut cx), Poll::Ready(Ok(())));
        }
        assert_eq!(
            call(&mut s, Op::Ready, &mut cx),
            Poll::Ready(Err(Converted(Some("mapped"))))
        );
        assert_eq!(
            *r.borrow(),
            vec!["ready", "flush", "close", "send", "ready", "map:first"]
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn success_after_consumption_still_delegates() {
        let (o, r) = output(vec![
            Poll::Ready(Err(Some("first"))),
            Poll::Ready(Ok(())),
            Poll::Pending,
            Poll::Ready(Err(Some("second"))),
        ]);
        let mut o = o;
        o.mode = Mode::Normal;
        let mut s = o.sink_err_into::<Converted>();
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            call(&mut s, Op::Ready, &mut cx),
            Poll::Ready(Err(Converted(Some("mapped"))))
        );
        assert_eq!(call(&mut s, Op::Flush, &mut cx), Poll::Ready(Ok(())));
        assert_eq!(call(&mut s, Op::Close, &mut cx), Poll::Pending);
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = call(&mut s, Op::Send, &mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "polled MapErr after completion");
        assert_eq!(
            *r.borrow(),
            vec!["ready", "map:first", "flush", "close", "send"]
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn getter_reaches_original_sink() {
        let (o, r) = output(vec![Poll::Ready(Ok(()))]);
        let s = o.sink_err_into::<Converted>();
        assert!(Rc::ptr_eq(&s.get_ref().trace, &r));
        assert_eq!(s.get_ref().events.len(), 1);
        assert!(r.borrow().is_empty());
    }
}
