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
    type Event = Poll<Result<Option<i32>, Error>>;
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
    struct TransformFuture {
        record: Rc<RefCell<Record>>,
        events: VecDeque<Event>,
    }
    impl Future for TransformFuture {
        type Output = Result<Option<i32>, Error>;
        fn poll(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Event {
            self.record.borrow_mut().trace.push("future".into());
            let result = self.events.pop_front().expect("unexpected future repoll");
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
        let c = Arc::new(Count(AtomicUsize::new(0)));
        let w = Waker::from(c.clone());
        (c, w)
    }
    fn setup() -> (Output, Rc<RefCell<Record>>) {
        let o = Output::default();
        let r = o.record.clone();
        (o, r)
    }
    fn transform(r: Rc<RefCell<Record>>, events: Vec<Event>, n: i32) -> TransformFuture {
        r.borrow_mut().trace.push(format!("transform:{n}"));
        TransformFuture {
            record: r,
            events: events.into(),
        }
    }
    fn trace(r: &Rc<RefCell<Record>>, e: &[&str]) {
        assert_eq!(r.borrow().trace, e);
    }
    #[test]
    fn idle_readiness_control() {
        let (mut o, r) = setup();
        o.ready.push_back(Poll::Pending);
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![], n));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Pending);
        trace(&r, &["ready"]);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn ready_sends_before_next_reservation() {
        let (o, r) = setup();
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "transform:1", "future", "send:7", "ready"]);
    }
    #[test]
    fn flush_does_not_reserve_again() {
        let (mut o, r) = setup();
        o.ready.extend([Poll::Ready(Ok(())), Poll::Pending]);
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "transform:1", "future", "send:7", "flush"]);
        assert_eq!(r.borrow().attempts, vec![Some(7)]);
    }
    #[test]
    fn null_output_is_sent() {
        let (o, r) = setup();
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(None))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &["ready", "transform:1", "future", "send:null", "flush"],
        );
        assert_eq!(r.borrow().attempts, vec![None]);
    }
    #[test]
    fn pending_future_is_retained() {
        let (o, r) = setup();
        let rr = r.clone();
        let mut s = o.with(move |n| {
            transform(
                rr.clone(),
                vec![Poll::Pending, Poll::Pending, Poll::Ready(Ok(Some(7)))],
                n,
            )
        });
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(
            &r,
            &[
                "ready",
                "transform:1",
                "future",
                "future",
                "future",
                "send:7",
                "flush",
            ],
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn overlapping_send_panics_before_transform() {
        let (o, r) = setup();
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(n)))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(2);
        }))
        .is_err());
        trace(&r, &["ready", "transform:1"]);
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "transform:1", "future", "send:1", "flush"]);
    }
    #[test]
    fn send_error_consumes_future() {
        let (mut o, r) = setup();
        o.sends.push_back(Err(Some("send")));
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(
            Pin::new(&mut s).poll_flush(&mut cx),
            Poll::Ready(Err(Some("send")))
        );
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Ok(())));
        trace(&r, &["ready", "transform:1", "future", "send:7", "flush"]);
    }
    #[test]
    fn transform_error_retains_active_future() {
        let (o, r) = setup();
        let rr = r.clone();
        let mut s =
            o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Err(Some("transform")))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(
            Pin::new(&mut s).poll_flush(&mut cx),
            Poll::Ready(Err(Some("transform")))
        );
        assert!(std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).start_send(2);
        }))
        .is_err());
        trace(&r, &["ready", "transform:1", "future"]);
        assert!(r.borrow().attempts.is_empty());
    }
    #[test]
    fn null_transform_error_delays_close() {
        let (o, r) = setup();
        let rr = r.clone();
        let mut s =
            o.with(move |n| transform(rr.clone(), vec![Poll::Pending, Poll::Ready(Err(None))], n));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Ready(Err(None)));
        trace(&r, &["ready", "transform:1", "future", "future"]);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn next_readiness_error_occurs_after_send() {
        let (mut o, r) = setup();
        o.ready
            .extend([Poll::Ready(Ok(())), Poll::Ready(Err(Some("ready")))]);
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(
            Pin::new(&mut s).poll_ready(&mut cx),
            Poll::Ready(Err(Some("ready")))
        );
        trace(&r, &["ready", "transform:1", "future", "send:7", "ready"]);
        assert_eq!(r.borrow().attempts, vec![Some(7)]);
    }
    #[test]
    fn flush_error_follows_successful_send() {
        let (mut o, r) = setup();
        o.flushes.push_back(Poll::Ready(Err(None)));
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], n));
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_flush(&mut cx), Poll::Ready(Err(None)));
        trace(&r, &["ready", "transform:1", "future", "send:7", "flush"]);
    }
    #[test]
    fn close_pending_does_not_repoll_future() {
        let (mut o, r) = setup();
        o.closes
            .extend([Poll::Pending, Poll::Ready(Err(Some("close")))]);
        let rr = r.clone();
        let mut s = o.with(move |n| transform(rr.clone(), vec![Poll::Ready(Ok(Some(7)))], n));
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_ready(&mut cx), Poll::Ready(Ok(())));
        assert_eq!(Pin::new(&mut s).start_send(1), Ok(()));
        assert_eq!(Pin::new(&mut s).poll_close(&mut cx), Poll::Pending);
        assert_eq!(
            Pin::new(&mut s).poll_close(&mut cx),
            Poll::Ready(Err(Some("close")))
        );
        trace(
            &r,
            &["ready", "transform:1", "future", "send:7", "close", "close"],
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
}
