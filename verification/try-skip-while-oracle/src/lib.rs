#[cfg(test)]
mod tests {
    use futures_util::{future, stream::FusedStream, Stream, TryStreamExt};
    use std::{cell::{Cell, RefCell}, collections::VecDeque, future::Future,
        panic::{catch_unwind, AssertUnwindSafe}, pin::Pin, rc::Rc, sync::Arc,
        task::{Context, Poll, Wake, Waker}};
    type Item = Result<Option<i32>, &'static str>;
    struct Noop;
    impl Wake for Noop { fn wake(self: Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    struct Input { events: VecDeque<Poll<Option<Item>>>, polls: Rc<Cell<usize>> }
    fn input(events: Vec<Poll<Option<Item>>>) -> (Input, Rc<Cell<usize>>) {
        let polls = Rc::new(Cell::new(0));
        (Input { events: events.into(), polls: polls.clone() }, polls)
    }
    impl Stream for Input {
        type Item = Item;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Item>> {
            self.polls.set(self.polls.get() + 1);
            self.events.pop_front().unwrap_or(Poll::Ready(None))
        }
        fn size_hint(&self) -> (usize, Option<usize>) {
            let count = self.events.iter().filter(|e| matches!(e, Poll::Ready(Some(_)))).count();
            (count, Some(count))
        }
    }
    impl FusedStream for Input { fn is_terminated(&self) -> bool { self.events.is_empty() } }
    fn value(item: Item) -> Poll<Option<Item>> { Poll::Ready(Some(item)) }
    #[derive(Clone)]
    struct Control { released: Rc<Cell<bool>>, waker: Rc<RefCell<Option<Waker>>> }
    impl Control { fn release(&self) { self.released.set(true); if let Some(w) = self.waker.borrow().as_ref() { w.wake_by_ref(); } } }
    struct Gate { control: Control, result: Result<bool, &'static str> }
    impl Future for Gate {
        type Output = Result<bool, &'static str>;
        fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output> {
            if self.control.released.get() { Poll::Ready(self.result) }
            else { *self.control.waker.borrow_mut() = Some(cx.waker().clone()); Poll::Pending }
        }
    }
    #[test]
    fn pending_null_hint_and_false_yields_before_direct_delegation() {
        let (s, polls) = input(vec![value(Ok(None)), value(Ok(Some(7)))]);
        let control = Control { released: Rc::new(Cell::new(false)), waker: Rc::new(RefCell::new(None)) };
        let calls = Cell::new(0);
        let mut f = s.try_skip_while(|_| { calls.set(calls.get()+1); Gate { control: control.clone(), result: Ok(false) } });
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll_next(&mut cx).is_pending());
        assert_eq!(f.size_hint(), (0, Some(2))); assert_eq!(polls.get(), 1);
        control.release(); assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Ok(None)));
        assert_eq!(f.size_hint(), (0, Some(1))); assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Ok(Some(7))));
        assert_eq!(calls.get(), 1); assert!(f.is_terminated());
    }
    #[test]
    fn predicate_error_retains_pending_null_through_input_end() {
        let (s, _) = input(vec![value(Ok(None))]);
        let mut f = s.try_skip_while(|_| future::ready(Err::<bool, _>("predicate")));
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Err("predicate")));
        assert!(f.get_ref().is_terminated()); assert!(!f.is_terminated()); assert_eq!(f.size_hint(), (0, Some(1)));
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), Poll::Ready(None));
        assert!(!f.is_terminated()); assert_eq!(f.size_hint(), (0, Some(1)));
    }
    #[test]
    fn creation_panic_does_not_cache_consumed_item() {
        let (s, polls) = input(vec![value(Ok(Some(1))), value(Ok(Some(2)))]);
        let mut f = s.try_skip_while(|_| -> future::Ready<Result<bool, &'static str>> { panic!("predicate") });
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(|| Pin::new(&mut f).poll_next(&mut cx))).is_err());
        assert_eq!(polls.get(), 1); assert_eq!(f.size_hint(), (0, Some(1)));
    }
    struct Explode;
    impl Future for Explode { type Output = Result<bool, &'static str>; fn poll(self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Self::Output> { panic!("future") } }
    #[test]
    fn polling_panic_retains_pending_null() {
        let (s, _) = input(vec![value(Ok(None))]); let mut f = s.try_skip_while(|_| Explode);
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(|| Pin::new(&mut f).poll_next(&mut cx))).is_err());
        assert!(!f.is_terminated()); assert_eq!(f.size_hint(), (0, Some(1)));
    }
    #[test]
    fn input_error_does_not_discard_item_retained_after_predicate_error() {
        let (s, _) = input(vec![value(Ok(None)), value(Err("input")), Poll::Ready(None)]); let calls = Cell::new(0);
        let mut f = s.try_skip_while(|_| { calls.set(calls.get()+1); future::ready(Err::<bool, _>("predicate")) });
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Err("predicate"))); assert_eq!(f.size_hint(), (0, Some(2)));
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Err("input"))); assert_eq!(f.size_hint(), (0, Some(1)));
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), Poll::Ready(None)); assert_eq!(f.size_hint(), (0, Some(1)));
        assert!(!f.is_terminated()); assert_eq!(calls.get(), 1);
    }
    #[test]
    fn true_skips_null_then_false_yields_without_reading_ahead() {
        let (s, polls) = input(vec![value(Ok(None)), value(Ok(Some(7))), value(Ok(Some(8)))]); let calls = RefCell::new(vec![]);
        let mut f = s.try_skip_while(|x| { calls.borrow_mut().push(*x); future::ready(Ok::<_, &'static str>(x.is_none())) });
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Ok(Some(7)))); assert_eq!(polls.get(), 2);
        assert_eq!(*calls.borrow(), vec![None, Some(7)]); assert_eq!(f.size_hint(), (0, Some(1)));
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx), value(Ok(Some(8)))); assert_eq!(*calls.borrow(), vec![None, Some(7)]);
    }
}
