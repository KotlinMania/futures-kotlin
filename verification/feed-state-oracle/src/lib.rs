#[cfg(test)]
mod tests {
    use futures_util::{Sink, SinkExt};
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
    #[derive(Default)]
    struct Record {
        trace: Vec<String>,
        attempts: Vec<Option<i32>>,
        flush: usize,
        close: usize,
    }
    #[derive(Default)]
    struct Output {
        record: Rc<RefCell<Record>>,
        ready: VecDeque<Poll<Result<(), Error>>>,
        sends: VecDeque<Result<(), Error>>,
        flushes: VecDeque<Poll<Result<(), Error>>>,
        ready_panic: bool,
    }
    fn output() -> (Output, Rc<RefCell<Record>>) {
        let out = Output::default();
        let r = out.record.clone();
        (out, r)
    }
    fn event(
        events: &mut VecDeque<Poll<Result<(), Error>>>,
        cx: &mut Context<'_>,
    ) -> Poll<Result<(), Error>> {
        let p = events.pop_front().unwrap_or(Poll::Ready(Ok(())));
        if p.is_pending() {
            cx.waker().wake_by_ref();
        }
        p
    }
    impl Sink<Option<i32>> for Output {
        type Error = Error;
        fn poll_ready(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            self.record.borrow_mut().trace.push("ready".into());
            if self.ready_panic {
                self.ready_panic = false;
                panic!("ready panic");
            }
            event(&mut self.ready, cx)
        }
        fn start_send(mut self: Pin<&mut Self>, item: Option<i32>) -> Result<(), Error> {
            let mut r = self.record.borrow_mut();
            r.trace.push(format!(
                "send:{}",
                item.map(|x| x.to_string()).unwrap_or("null".into())
            ));
            r.attempts.push(item);
            drop(r);
            self.sends.pop_front().unwrap_or(Ok(()))
        }
        fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            let mut r = self.record.borrow_mut();
            r.trace.push("flush".into());
            r.flush += 1;
            drop(r);
            event(&mut self.flushes, cx)
        }
        fn poll_close(self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Result<(), Error>> {
            self.record.borrow_mut().close += 1;
            Poll::Ready(Ok(()))
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
    fn trace(r: &Rc<RefCell<Record>>, expected: &[&str]) {
        assert_eq!(r.borrow().trace, expected);
        assert_eq!(r.borrow().close, 0);
    }
    #[test]
    fn nullable_ready() {
        let (mut out, r) = output();
        let mut f = out.feed(None);
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "send:null"]);
        assert_eq!(r.borrow().attempts, vec![None]);
        assert_eq!(r.borrow().flush, 0);
    }
    #[test]
    fn nullable_pending_twice() {
        let (mut out, r) = output();
        out.ready
            .extend([Poll::Pending, Poll::Pending, Poll::Ready(Ok(()))]);
        let mut f = out.feed(None);
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
        assert!(r.borrow().attempts.is_empty());
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "ready", "ready", "send:null"]);
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
        assert_eq!(r.borrow().flush, 0);
    }
    #[test]
    fn readiness_error_retains_item() {
        let (mut out, r) = output();
        out.ready
            .extend([Poll::Ready(Err(Some("ready"))), Poll::Ready(Ok(()))]);
        let mut f = out.feed(None);
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut f).poll(&mut cx),
            Poll::Ready(Err(Some("ready")))
        );
        assert!(r.borrow().attempts.is_empty());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "ready", "send:null"]);
    }
    #[test]
    fn send_error_consumes_item() {
        let (mut out, r) = output();
        out.sends.push_back(Err(Some("send")));
        let mut f = out.feed(None);
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut f).poll(&mut cx),
            Poll::Ready(Err(Some("send")))
        );
        assert_eq!(r.borrow().attempts, vec![None]);
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(
            || Pin::new(&mut f).poll(&mut cx)
        ))
        .is_err());
        trace(&r, &["ready", "send:null", "ready"]);
        assert_eq!(r.borrow().flush, 0);
    }
    #[test]
    fn nullable_error_none_is_not_success() {
        let (mut out, r) = output();
        out.ready.push_back(Poll::Ready(Err(None)));
        let mut f = out.feed(None);
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Err(None)));
        trace(&r, &["ready"]);
        assert!(r.borrow().attempts.is_empty());
    }
    #[test]
    fn nonnull_control() {
        let (mut out, r) = output();
        let mut f = out.feed(Some(7));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "send:7"]);
        assert_eq!(r.borrow().flush, 0);
    }
    #[test]
    fn completed_feed_defers_to_readiness() {
        let (mut out, r) = output();
        out.ready.extend([
            Poll::Ready(Ok(())),
            Poll::Pending,
            Poll::Ready(Err(None)),
            Poll::Ready(Ok(())),
        ]);
        let mut f = out.feed(Some(7));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Err(None)));
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(
            || Pin::new(&mut f).poll(&mut cx)
        ))
        .is_err());
        trace(&r, &["ready", "send:7", "ready", "ready", "ready"]);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn send_error_repoll_defers_to_readiness() {
        let (mut out, r) = output();
        out.ready
            .extend([Poll::Ready(Ok(())), Poll::Pending, Poll::Ready(Ok(()))]);
        out.sends.push_back(Err(Some("send")));
        let mut f = out.feed(Some(7));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut f).poll(&mut cx),
            Poll::Ready(Err(Some("send")))
        );
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(
            || Pin::new(&mut f).poll(&mut cx)
        ))
        .is_err());
        trace(&r, &["ready", "send:7", "ready", "ready"]);
    }
    #[test]
    fn send_null_waits_for_flush() {
        let (mut out, r) = output();
        out.flushes.extend([Poll::Pending, Poll::Ready(Ok(()))]);
        let mut f = out.send(None);
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
        trace(&r, &["ready", "send:null", "flush"]);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "send:null", "flush", "flush"]);
        assert_eq!(r.borrow().flush, 2);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn readiness_panic_retains_item() {
        let (mut out, r) = output();
        out.ready_panic = true;
        let mut f = out.feed(None);
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(
            || Pin::new(&mut f).poll(&mut cx)
        ))
        .is_err());
        assert!(r.borrow().attempts.is_empty());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "ready", "send:null"]);
    }
}
