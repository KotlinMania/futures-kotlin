#[cfg(test)]
mod tests {
    use futures_util::{future::FusedFuture, stream::FusedStream, Stream, StreamExt};
    use std::{
        cell::Cell,
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
    struct Input {
        events: VecDeque<Poll<Option<Option<i32>>>>,
        done: Rc<Cell<bool>>,
        polls: Rc<Cell<usize>>,
    }
    impl Stream for Input {
        type Item = Option<i32>;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            let p = self.events.pop_front().expect("unexpected stream poll");
            if p.is_pending() {
                cx.waker().wake_by_ref();
            }
            if p == Poll::Ready(None) {
                self.done.set(true);
            }
            p
        }
    }
    impl FusedStream for Input {
        fn is_terminated(&self) -> bool {
            self.done.get()
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
    fn input(
        done: bool,
        events: Vec<Poll<Option<Option<i32>>>>,
    ) -> (Input, Rc<Cell<bool>>, Rc<Cell<usize>>) {
        let d = Rc::new(Cell::new(done));
        let p = Rc::new(Cell::new(0));
        (
            Input {
                events: events.into(),
                done: d.clone(),
                polls: p.clone(),
            },
            d,
            p,
        )
    }
    fn panic_text(p: Box<dyn std::any::Any + Send>) -> String {
        if let Some(s) = p.downcast_ref::<&str>() {
            s.to_string()
        } else {
            p.downcast_ref::<String>().unwrap().clone()
        }
    }
    #[test]
    fn terminated_rejected_before_poll() {
        let (mut s, _, polls) = input(true, vec![]);
        let mut f = s.select_next_some();
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(f.is_terminated());
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut f).poll(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "SelectNextSome polled after terminated");
        assert_eq!(polls.get(), 0);
        assert_eq!(c.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn pending_delegates_wakeup() {
        let (mut s, _, polls) = input(false, vec![Poll::Pending]);
        let mut f = s.select_next_some();
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(!f.is_terminated());
        assert_eq!(polls.get(), 1);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn nullable_item_then_end() {
        let (mut s, _, polls) = input(false, vec![Poll::Ready(Some(None)), Poll::Ready(None)]);
        let mut f = s.select_next_some();
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(None));
        assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(f.is_terminated());
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut f).poll(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "SelectNextSome polled after terminated");
        assert_eq!(polls.get(), 2);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn nonnull_item_then_end() {
        let (mut s, _, polls) = input(false, vec![Poll::Ready(Some(Some(7))), Poll::Ready(None)]);
        let mut f = s.select_next_some();
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Some(7)));
        assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(f.is_terminated());
        assert_eq!(polls.get(), 2);
        assert_eq!(c.0.load(Ordering::SeqCst), 1);
    }
}
