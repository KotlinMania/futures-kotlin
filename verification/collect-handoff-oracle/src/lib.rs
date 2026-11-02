#[cfg(test)]
mod tests {
    use futures_util::{stream, Stream, StreamExt, TryStreamExt, future::FusedFuture};
    use std::{cell::Cell, collections::VecDeque, future::Future, pin::Pin, rc::Rc,
        sync::Arc, task::{Context, Poll, Wake, Waker}};
    struct Noop;
    impl Wake for Noop { fn wake(self: Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    struct Input<T> { events: VecDeque<Poll<Option<T>>>, polls: Rc<Cell<usize>> }
    fn input<T>(events: Vec<Poll<Option<T>>>) -> (Input<T>, Rc<Cell<usize>>) {
        let polls = Rc::new(Cell::new(0));
        (Input { events: events.into(), polls: polls.clone() }, polls)
    }
    impl<T: Unpin> Stream for Input<T> {
        type Item = T;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<T>> {
            self.polls.set(self.polls.get() + 1);
            let event = self.events.pop_front().unwrap_or(Poll::Ready(None));
            if event.is_pending() { cx.waker().wake_by_ref(); }
            event
        }
    }
    #[test]
    fn collect_transfers_buffer_and_repeated_end_returns_empty() {
        let (s, polls) = input(vec![Poll::Ready(Some(None)), Poll::Ready(Some(Some(1))), Poll::Ready(None)]);
        let mut f = s.collect::<Vec<_>>(); let w = waker(); let mut cx = Context::from_waker(&w);
        let first = match Pin::new(&mut f).poll(&mut cx) { Poll::Ready(v) => v, _ => panic!("pending") };
        assert_eq!(first, vec![None, Some(1)]);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(vec![]));
        assert_eq!(first, vec![None, Some(1)]); assert_eq!(polls.get(), 4);
    }
    #[test]
    fn collect_delegates_termination_to_already_terminated_input() {
        let mut f = stream::empty::<i32>().collect::<Vec<_>>(); let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(f.is_terminated()); assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(vec![])); assert!(f.is_terminated());
    }
    #[test]
    fn non_fused_input_resuming_after_end_starts_new_collection() {
        let (s, polls) = input(vec![Poll::Ready(Some(1)), Poll::Ready(None), Poll::Pending, Poll::Ready(Some(2)), Poll::Ready(None)]);
        let mut f = s.collect::<Vec<_>>(); let w = waker(); let mut cx = Context::from_waker(&w);
        let first = match Pin::new(&mut f).poll(&mut cx) { Poll::Ready(v) => v, _ => panic!("pending") };
        assert_eq!(first, vec![1]); assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(vec![2])); assert_eq!(first, vec![1]); assert_eq!(polls.get(), 5);
    }
    #[test]
    fn try_collect_transfers_success_and_repeated_end_returns_empty() {
        let (s, polls) = input::<Result<Option<i32>, &'static str>>(vec![Poll::Ready(Some(Ok(None))), Poll::Ready(Some(Ok(Some(1)))), Poll::Ready(None)]);
        let mut f = s.try_collect::<Vec<_>>(); let w = waker(); let mut cx = Context::from_waker(&w);
        let first = match Pin::new(&mut f).poll(&mut cx) { Poll::Ready(Ok(v)) => v, _ => panic!("not success") };
        assert_eq!(first, vec![None, Some(1)]); assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(vec![])));
        assert_eq!(first, vec![None, Some(1)]); assert_eq!(polls.get(), 4);
    }
    #[test]
    fn try_collect_retains_prefix_across_pending_error_then_transfers_success() {
        let (s, polls) = input(vec![Poll::Ready(Some(Ok(None))), Poll::Pending, Poll::Ready(Some(Ok(Some(1)))), Poll::Ready(Some(Err("input"))), Poll::Ready(Some(Ok(Some(2)))), Poll::Ready(None)]);
        let mut f = s.try_collect::<Vec<_>>(); let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending()); assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Err("input")));
        let first = match Pin::new(&mut f).poll(&mut cx) { Poll::Ready(Ok(v)) => v, _ => panic!("not success") };
        assert_eq!(first, vec![None, Some(1), Some(2)]); assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(vec![])));
        assert_eq!(first, vec![None, Some(1), Some(2)]); assert_eq!(polls.get(), 7);
    }
    #[test]
    fn try_collect_keeps_delegating_termination_to_input() {
        let mut f = stream::empty::<Result<Option<i32>, &'static str>>().try_collect::<Vec<_>>();
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(f.is_terminated()); assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(vec![]))); assert!(f.is_terminated());
    }
}
