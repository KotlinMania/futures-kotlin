#[cfg(test)]
mod tests {
    use futures_util::{
        future::FusedFuture,
        stream::{self, FusedStream},
        Stream, StreamExt,
    };
    use std::{
        cell::Cell,
        collections::VecDeque,
        future::Future,
        pin::Pin,
        rc::Rc,
        sync::Arc,
        task::{Context, Poll, Wake, Waker},
    };
    enum Step {
        Value(Option<i32>),
        Pending,
        InactivePending,
        End,
    }
    struct Input {
        events: VecDeque<Step>,
        flag: Rc<Cell<bool>>,
        polls: Rc<Cell<usize>>,
    }
    impl Stream for Input {
        type Item = Option<i32>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            match self.events.pop_front().unwrap_or(Step::Pending) {
                Step::Value(v) => Poll::Ready(Some(v)),
                Step::Pending => Poll::Pending,
                Step::InactivePending => {
                    self.flag.set(true);
                    Poll::Pending
                }
                Step::End => {
                    self.flag.set(true);
                    Poll::Ready(None)
                }
            }
        }
    }
    impl FusedStream for Input {
        fn is_terminated(&self) -> bool {
            self.flag.get()
        }
    }
    fn input(events: Vec<Step>) -> (Input, Rc<Cell<bool>>, Rc<Cell<usize>>) {
        let flag = Rc::new(Cell::new(false));
        let polls = Rc::new(Cell::new(0));
        (
            Input {
                events: events.into(),
                flag: flag.clone(),
                polls: polls.clone(),
            },
            flag,
            polls,
        )
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    fn waker() -> Waker {
        Waker::from(Arc::new(Noop))
    }
    #[test]
    fn empty_source_is_terminated_before_count_poll() {
        let mut f = stream::empty::<i32>().count();
        assert!(f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(0));
        assert!(f.is_terminated());
    }
    #[test]
    fn inactive_pending_source_stays_pending() {
        let mut f = stream::pending::<i32>().count();
        assert!(f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(f.is_terminated());
    }
    #[test]
    fn external_termination_change_needs_no_poll() {
        let (s, flag, polls) = input(vec![]);
        let f = s.count();
        assert!(!f.is_terminated());
        assert_eq!(polls.get(), 0);
        flag.set(true);
        assert!(f.is_terminated());
        assert_eq!(polls.get(), 0);
    }
    #[test]
    fn source_can_become_inactive_while_count_is_pending() {
        let (s, _, polls) = input(vec![Step::Value(Some(1)), Step::InactivePending]);
        let mut f = s.count();
        assert!(!f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(f.is_terminated());
        assert_eq!(polls.get(), 2);
    }
    #[test]
    fn pending_preserves_nullable_item_count() {
        let (s, _, polls) = input(vec![
            Step::Value(Some(1)),
            Step::Pending,
            Step::Value(None),
            Step::End,
        ]);
        let mut f = s.count();
        assert!(!f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(!f.is_terminated());
        assert_eq!(polls.get(), 2);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(2));
        assert!(f.is_terminated());
        assert_eq!(polls.get(), 4);
    }
}
