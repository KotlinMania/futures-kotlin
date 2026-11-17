#[cfg(test)]
mod tests {
    use futures_util::{Sink, SinkExt, Stream};
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
    type Error = Option<&'static str>;
    type Event = Poll<Option<Result<Option<i32>, Error>>>;
    #[derive(Default)]
    struct Record {
        trace: Vec<String>,
        attempts: Vec<Option<i32>>,
    }
    #[derive(Default)]
    struct Output {
        record: Rc<RefCell<Record>>,
        ready: VecDeque<Poll<Result<(), Error>>>,
        sends: VecDeque<Result<(), Error>>,
        flushes: VecDeque<Poll<Result<(), Error>>>,
        closes: VecDeque<Poll<Result<(), Error>>>,
    }
    fn event(
        events: &mut VecDeque<Poll<Result<(), Error>>>,
        cx: &mut Context<'_>,
    ) -> Poll<Result<(), Error>> {
        let result = events.pop_front().unwrap_or(Poll::Ready(Ok(())));
        if result.is_pending() {
            cx.waker().wake_by_ref();
        }
        result
    }
    impl Sink<Option<i32>> for Output {
        type Error = Error;
        fn poll_ready(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            self.record.borrow_mut().trace.push("ready".into());
            event(&mut self.ready, cx)
        }
        fn start_send(mut self: Pin<&mut Self>, item: Option<i32>) -> Result<(), Error> {
            let mut r = self.record.borrow_mut();
            r.trace.push(format!(
                "send:{}",
                item.map(|v| v.to_string()).unwrap_or("null".into())
            ));
            r.attempts.push(item);
            drop(r);
            self.sends.pop_front().unwrap_or(Ok(()))
        }
        fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            self.record.borrow_mut().trace.push("flush".into());
            event(&mut self.flushes, cx)
        }
        fn poll_close(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            self.record.borrow_mut().trace.push("close".into());
            event(&mut self.closes, cx)
        }
    }
    struct Input {
        record: Rc<RefCell<Record>>,
        events: VecDeque<Event>,
    }
    impl Stream for Input {
        type Item = Result<Option<i32>, Error>;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Event {
            self.record.borrow_mut().trace.push("next".into());
            let result = self.events.pop_front().unwrap_or(Poll::Ready(None));
            if result.is_pending() {
                cx.waker().wake_by_ref();
            }
            result
        }
    }
    struct Count(AtomicUsize);
    impl Wake for Count {
        fn wake(self: Arc<Self>) {
            self.0.fetch_add(1, Ordering::SeqCst);
        }
    }
    fn task() -> (Arc<Count>, Waker) {
        let count = Arc::new(Count(AtomicUsize::new(0)));
        let waker = Waker::from(count.clone());
        (count, waker)
    }
    fn value(v: Option<i32>) -> Event {
        Poll::Ready(Some(Ok(v)))
    }
    fn setup() -> (Output, Rc<RefCell<Record>>) {
        let out = Output::default();
        let r = out.record.clone();
        (out, r)
    }
    fn input(r: Rc<RefCell<Record>>, events: Vec<Event>, n: i32) -> Input {
        r.borrow_mut().trace.push(format!("transform:{n}"));
        Input {
            record: r,
            events: events.into(),
        }
    }
    fn trace(r: &Rc<RefCell<Record>>, expected: &[&str]) {
        assert_eq!(r.borrow().trace, expected);
    }
    #[test]
    fn idle_readiness_does_not_poll_inner() {
        let (mut out, r) = setup();
        out.ready.push_back(Poll::Pending);
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| input(rr.clone(), vec![], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &[]);
    }
    #[test]
    fn nonnull_drain_has_no_extra_readiness_poll() {
        let (out, r) = setup();
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| input(rr.clone(), vec![value(Some(7))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["transform:1", "next", "ready", "send:7", "next"]);
    }
    #[test]
    fn nullable_item_survives_repeated_pending() {
        let (mut out, r) = setup();
        out.ready.extend([Poll::Pending, Poll::Pending]);
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| input(rr.clone(), vec![value(None)], n));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "ready",
                "ready",
                "ready",
                "send:null",
                "next",
            ],
        );
        assert_eq!(r.borrow().attempts, vec![None]);
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn fresh_readiness_error_drops_item() {
        let (mut out, r) = setup();
        out.ready.push_back(Poll::Ready(Err(Some("ready"))));
        let rr = r.clone();
        let mut s =
            out.with_flat_map(move |n| input(rr.clone(), vec![value(Some(7)), value(Some(8))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(
            Pin::new(&mut s).poll_ready(&mut cx),
            Poll::Ready(Err(Some("ready")))
        );
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "ready",
                "next",
                "ready",
                "send:8",
                "next",
            ],
        );
        assert_eq!(r.borrow().attempts, vec![Some(8)]);
    }
    #[test]
    fn buffered_readiness_error_retains_item() {
        let (mut out, r) = setup();
        out.ready.extend([Poll::Pending, Poll::Ready(Err(None))]);
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| input(rr.clone(), vec![value(None)], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Err(None)));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "ready",
                "ready",
                "ready",
                "send:null",
                "next",
            ],
        );
    }
    #[test]
    fn fresh_send_error_consumes_item() {
        let (mut out, r) = setup();
        out.sends.push_back(Err(Some("send")));
        let rr = r.clone();
        let mut s =
            out.with_flat_map(move |n| input(rr.clone(), vec![value(Some(7)), value(Some(8))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(
            Pin::new(&mut s).poll_ready(&mut cx),
            Poll::Ready(Err(Some("send")))
        );
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "ready",
                "send:7",
                "next",
                "ready",
                "send:8",
                "next",
                "flush",
            ],
        );
    }
    #[test]
    fn buffered_send_error_consumes_item() {
        let (mut out, r) = setup();
        out.ready.push_back(Poll::Pending);
        out.sends.push_back(Err(Some("send")));
        let rr = r.clone();
        let mut s =
            out.with_flat_map(move |n| input(rr.clone(), vec![value(None), value(Some(8))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Pending);
        assert_eq!(
            Pin::new(&mut s).poll_ready(&mut cx),
            Poll::Ready(Err(Some("send")))
        );
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "ready",
                "ready",
                "send:null",
                "next",
                "ready",
                "send:8",
                "next",
                "flush",
            ],
        );
    }
    #[test]
    fn stream_error_does_not_poll_sink() {
        let (out, r) = setup();
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| {
            input(
                rr.clone(),
                vec![Poll::Ready(Some(Err(None))), value(Some(8))],
                n,
            )
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Err(None)));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "next",
                "ready",
                "send:8",
                "next",
                "flush",
            ],
        );
    }
    #[test]
    fn stream_pending_delays_flush() {
        let (out, r) = setup();
        let rr = r.clone();
        let mut s =
            out.with_flat_map(move |n| input(rr.clone(), vec![Poll::Pending, value(None)], n));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Pending);
        trace(&r, &["transform:1", "next"]);
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "next",
                "ready",
                "send:null",
                "next",
                "flush",
            ],
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn buffered_item_delays_close() {
        let (mut out, r) = setup();
        out.ready.push_back(Poll::Pending);
        out.closes.push_back(Poll::Pending);
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| input(rr.clone(), vec![value(None)], n));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "transform:1",
                "next",
                "ready",
                "ready",
                "send:null",
                "next",
                "close",
                "close",
            ],
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn overlapping_start_send_panics_before_transform() {
        let (out, r) = setup();
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| input(rr.clone(), vec![value(Some(n))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(2);
        }));
        assert!(p.is_err());
        trace(&r, &["transform:1"]);
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &["transform:1", "next", "ready", "send:1", "next", "flush"],
        );
    }
    #[test]
    fn transform_panic_leaves_no_active_stream() {
        let (out, r) = setup();
        let rr = r.clone();
        let mut s = out.with_flat_map(move |n| {
            if n == 1 {
                rr.borrow_mut().trace.push("panic:1".into());
                panic!("transform panic");
            }
            input(rr.clone(), vec![value(Some(n))], n)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(1);
        }));
        assert!(p.is_err());
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["panic:1"]);
        assert_eq!(Pin::new(&mut s).start_send(2), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "panic:1",
                "transform:2",
                "next",
                "ready",
                "send:2",
                "next",
                "flush",
            ],
        );
    }
}
