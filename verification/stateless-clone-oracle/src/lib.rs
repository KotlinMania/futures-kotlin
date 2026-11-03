#[cfg(test)]
mod tests {
    use futures_util::{
        future::{self, FusedFuture},
        stream::{self, FusedStream},
        Stream,
    };
    use std::{
        future::Future,
        pin::Pin,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
        task::{Context, Poll, Wake, Waker},
    };
    // Deliberately no Clone/Copy/Debug/PartialEq implementation on the payload.
    struct NoClone;
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
        let c = Arc::new(Counter(AtomicUsize::new(0)));
        (c.clone(), Waker::from(c))
    }
    fn require_clone<T: Clone>(_: &T) {}
    #[test]
    fn empty_stream_clone_has_no_payload_bound() {
        let mut original = stream::empty::<Option<NoClone>>();
        require_clone(&original);
        let mut cloned: stream::Empty<Option<NoClone>> = original.clone();
        let (c, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(original.is_terminated());
        assert!(cloned.is_terminated());
        for _ in 0..8 {
            assert!(matches!(
                Pin::new(&mut original).poll_next(&mut cx),
                Poll::Ready(None)
            ));
            assert!(matches!(
                Pin::new(&mut cloned).poll_next(&mut cx),
                Poll::Ready(None)
            ));
            assert_eq!(original.size_hint(), (0, Some(0)));
            assert_eq!(cloned.size_hint(), (0, Some(0)));
            assert!(original.is_terminated());
            assert!(cloned.is_terminated());
        }
        let mut after = original.clone();
        assert!(matches!(
            Pin::new(&mut after).poll_next(&mut cx),
            Poll::Ready(None)
        ));
        assert!(after.is_terminated());
        assert_eq!(after.size_hint(), (0, Some(0)));
        assert_eq!(c.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn pending_stream_clone_has_no_payload_bound() {
        let mut original = stream::pending::<Option<NoClone>>();
        require_clone(&original);
        let mut cloned: stream::Pending<Option<NoClone>> = original.clone();
        let (c, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(original.is_terminated());
        assert!(cloned.is_terminated());
        for _ in 0..8 {
            assert!(matches!(
                Pin::new(&mut original).poll_next(&mut cx),
                Poll::Pending
            ));
            assert!(matches!(
                Pin::new(&mut cloned).poll_next(&mut cx),
                Poll::Pending
            ));
            assert_eq!(original.size_hint(), (0, Some(0)));
            assert_eq!(cloned.size_hint(), (0, Some(0)));
            assert!(original.is_terminated());
            assert!(cloned.is_terminated());
        }
        let mut after = original.clone();
        assert!(matches!(
            Pin::new(&mut after).poll_next(&mut cx),
            Poll::Pending
        ));
        assert!(after.is_terminated());
        assert_eq!(after.size_hint(), (0, Some(0)));
        assert_eq!(c.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn pending_future_clone_has_no_output_bound() {
        let mut original = future::pending::<Option<NoClone>>();
        require_clone(&original);
        let mut cloned: future::Pending<Option<NoClone>> = original.clone();
        let (c, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(original.is_terminated());
        assert!(cloned.is_terminated());
        for _ in 0..8 {
            assert!(matches!(
                Pin::new(&mut original).poll(&mut cx),
                Poll::Pending
            ));
            assert!(matches!(Pin::new(&mut cloned).poll(&mut cx), Poll::Pending));
            assert!(original.is_terminated());
            assert!(cloned.is_terminated());
        }
        let mut after = original.clone();
        assert!(matches!(Pin::new(&mut after).poll(&mut cx), Poll::Pending));
        assert!(after.is_terminated());
        assert_eq!(c.0.load(Ordering::SeqCst), 0);
    }
}
