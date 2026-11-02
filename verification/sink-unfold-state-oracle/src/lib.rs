#[cfg(test)]
mod tests {
    use futures_util::{sink, Sink};
    use std::{
        cell::RefCell,
        collections::VecDeque,
        future::Future,
        pin::Pin,
        rc::Rc,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
        task::{Context, Poll, Wake, Waker},
    };
    type Error = Option<&'static str>;
    type Event = Poll<Result<Option<i32>, Error>>;
    struct Script {
        trace: Rc<RefCell<Vec<String>>>,
        events: VecDeque<Event>,
    }
    impl Future for Script {
        type Output = Result<Option<i32>, Error>;
        fn poll(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Event {
            self.trace.borrow_mut().push("future".into());
            let result = self
                .events
                .pop_front()
                .expect("unexpected completed future repoll");
            if result.is_pending() {
                cx.waker().wake_by_ref();
            }
            result
        }
    }
    fn text(v: Option<i32>) -> String {
        v.map(|n| n.to_string()).unwrap_or("null".into())
    }
    fn script(
        r: Rc<RefCell<Vec<String>>>,
        events: Vec<Event>,
        state: Option<i32>,
        item: i32,
    ) -> Script {
        r.borrow_mut().push(format!("call:{}:{item}", text(state)));
        Script {
            trace: r,
            events: events.into(),
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
    fn record() -> Rc<RefCell<Vec<String>>> {
        Rc::new(RefCell::new(vec![]))
    }
    fn trace(r: &Rc<RefCell<Vec<String>>>, e: &[&str]) {
        assert_eq!(*r.borrow(), e);
    }
    #[test]
    fn nullable_initial_state() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(None, move |state, item| {
            script(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], state, item)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["call:null:1", "future"]);
    }
    #[test]
    fn nullable_returned_state() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(7), move |state, item| {
            script(rr.clone(), vec![Poll::Ready(Ok(None))], state, item)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(2), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["call:7:1", "future", "call:null:2", "future"]);
    }
    #[test]
    fn callback_panic_consumes_state() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(7), move |state, item| {
            let _ = script(rr.clone(), vec![Poll::Ready(Ok(state))], state, item);
            panic!("callback panic");
            #[allow(unreachable_code)]
            Script {
                trace: rr.clone(),
                events: VecDeque::new(),
            }
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(1);
        }))
        .is_err());
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(2);
        }))
        .is_err());
        trace(&r, &["call:7:1"]);
    }
    #[test]
    fn pending_future_retained() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(7), move |state, item| {
            script(
                rr.clone(),
                vec![Poll::Pending, Poll::Pending, Poll::Ready(Ok(Some(8)))],
                state,
                item,
            )
        });
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["call:7:1", "future", "future", "future"]);
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn nullable_error_empties_state() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(7), move |state, item| {
            script(rr.clone(), vec![Poll::Ready(Err(None))], state, item)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Err(None)));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Ok(())));
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(2);
        }))
        .is_err());
        trace(&r, &["call:7:1", "future"]);
    }
    #[test]
    fn nonnull_accumulation_control() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(0), move |state, item| {
            script(
                rr.clone(),
                vec![Poll::Ready(Ok(Some(state.unwrap() + item)))],
                state,
                item,
            )
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(2), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(3), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["call:0:2", "future", "call:2:3", "future"]);
    }
    #[test]
    fn overlapping_send_preserves_future() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(7), move |state, item| {
            script(rr.clone(), vec![Poll::Ready(Ok(Some(8)))], state, item)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(2);
        }))
        .is_err());
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["call:7:1", "future"]);
    }
    #[test]
    fn close_pending_drains_once() {
        let r = record();
        let rr = r.clone();
        let mut s = sink::unfold(Some(7), move |state, item| {
            script(
                rr.clone(),
                vec![Poll::Pending, Poll::Ready(Ok(Some(8)))],
                state,
                item,
            )
        });
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["call:7:1", "future", "future"]);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
}
