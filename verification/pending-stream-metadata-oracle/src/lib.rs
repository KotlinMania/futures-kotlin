#[cfg(test)]
mod tests {
    use futures_util::{
        future::FusedFuture,
        stream::{self, FusedStream},
        Stream, StreamExt,
    };
    use std::{
        future::Future,
        panic::{catch_unwind, AssertUnwindSafe},
        pin::Pin,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
        task::{Context, Poll, Wake, Waker},
    };
    struct Counter(AtomicUsize);
    impl Wake for Counter {
        fn wake(self: Arc<Self>) {
            self.0.fetch_add(1, Ordering::SeqCst);
        }
        fn wake_by_ref(self: &Arc<Self>) {
            self.0.fetch_add(1, Ordering::SeqCst);
        }
    }
    fn context() -> (Arc<Counter>, Waker) {
        let count = Arc::new(Counter(AtomicUsize::new(0)));
        let w = Waker::from(count.clone());
        (count, w)
    }
    #[test]
    fn inactive_before_and_after_poll() {
        let mut s = stream::pending::<Option<i32>>();
        let (_, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(s.is_terminated());
        for _ in 0..8 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
            assert!(s.is_terminated());
        }
    }
    #[test]
    fn exact_zero_hint_before_and_after_poll() {
        let mut s = stream::pending::<i32>();
        let (_, w) = context();
        let mut cx = Context::from_waker(&w);
        assert_eq!(s.size_hint(), (0, Some(0)));
        for _ in 0..8 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
            assert_eq!(s.size_hint(), (0, Some(0)));
        }
    }
    #[test]
    fn pending_poll_does_not_wake_or_yield_end() {
        let mut s = stream::pending::<Option<i32>>();
        let (count, w) = context();
        let mut cx = Context::from_waker(&w);
        for _ in 0..8 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        }
        assert_eq!(count.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn positive_take_keeps_inactive_metadata() {
        let mut s = stream::pending::<i32>().take(5);
        let (_, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(s.is_terminated());
        assert_eq!(s.size_hint(), (0, Some(0)));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        assert!(s.is_terminated());
        assert_eq!(s.size_hint(), (0, Some(0)));
    }
    #[test]
    fn fuse_does_not_synthesize_an_end() {
        let mut s = stream::pending::<i32>().fuse();
        let (_, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(!s.is_terminated());
        assert_eq!(s.size_hint(), (0, Some(0)));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        assert!(!s.is_terminated());
        assert_eq!(s.size_hint(), (0, Some(0)));
    }
    #[test]
    fn select_next_some_detects_inactive_stream() {
        let mut s = stream::pending::<i32>();
        let (_, w) = context();
        let mut cx = Context::from_waker(&w);
        let mut f = s.select_next_some();
        assert!(f.is_terminated());
        let err = catch_unwind(AssertUnwindSafe(|| {
            let _ = Pin::new(&mut f).poll(&mut cx);
        }))
        .unwrap_err();
        let text = if let Some(s) = err.downcast_ref::<&str>() {
            s.to_string()
        } else {
            err.downcast_ref::<String>().unwrap().clone()
        };
        assert_eq!(text, "SelectNextSome polled after terminated");
    }
}
