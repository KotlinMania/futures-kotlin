#[cfg(test)]
mod tests {
    use futures_util::future::{self, FusedFuture};
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
        let c = Arc::new(Counter(AtomicUsize::new(0)));
        (c.clone(), Waker::from(c))
    }
    #[test]
    fn ready_value_does_not_wake() {
        let mut f = future::ready(42);
        let (count, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(42));
        assert!(f.is_terminated());
        assert_eq!(count.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn nullable_value_remains_a_ready_value() {
        let mut f = future::ready(None::<i32>);
        let (count, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(None));
        assert!(f.is_terminated());
        assert_eq!(count.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn success_and_error_payloads_do_not_wake() {
        let mut ok = future::ok::<Option<i32>, Option<&str>>(None);
        let mut err = future::err::<Option<i32>, Option<&str>>(None);
        let (count, w) = context();
        let mut cx = Context::from_waker(&w);
        assert!(!ok.is_terminated());
        assert!(!err.is_terminated());
        assert_eq!(Pin::new(&mut ok).poll(&mut cx), Poll::Ready(Ok(None)));
        assert_eq!(Pin::new(&mut err).poll(&mut cx), Poll::Ready(Err(None)));
        assert!(ok.is_terminated());
        assert!(err.is_terminated());
        assert_eq!(count.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn completed_ready_has_exact_guard() {
        let mut f = future::ready(7);
        let (count, w) = context();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(7));
        let err = catch_unwind(AssertUnwindSafe(|| {
            let _ = Pin::new(&mut f).poll(&mut cx);
        }))
        .unwrap_err();
        let text = if let Some(s) = err.downcast_ref::<&str>() {
            s.to_string()
        } else {
            err.downcast_ref::<String>().unwrap().clone()
        };
        assert_eq!(text, "Ready polled after completion");
        assert!(f.is_terminated());
        assert_eq!(count.0.load(Ordering::SeqCst), 0);
    }
}
