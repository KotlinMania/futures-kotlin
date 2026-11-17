#[cfg(test)]
mod tests {
    use futures_util::{future::{self, FusedFuture}, Stream, StreamExt};
    use std::{cell::{Cell, RefCell}, collections::VecDeque, future::Future, pin::Pin,
        panic::{catch_unwind, AssertUnwindSafe}, rc::Rc, sync::Arc, task::{Context, Poll, Wake, Waker}};
    struct Noop;
    impl Wake for Noop { fn wake(self: Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    struct Input { events: VecDeque<Poll<Option<i32>>>, polls: Rc<Cell<usize>>, log: Option<Rc<RefCell<Vec<String>>>> }
    fn input(events: Vec<Poll<Option<i32>>>) -> (Input, Rc<Cell<usize>>) {
        let polls = Rc::new(Cell::new(0)); (Input { events: events.into(), polls: polls.clone(), log: None }, polls)
    }
    impl Stream for Input {
        type Item = i32;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get()+1);
            if let Some(log) = &self.log { log.borrow_mut().push(format!("input{}", self.polls.get())); }
            self.events.pop_front().unwrap_or(Poll::Ready(None))
        }
    }
    fn ok(item: i32) -> Poll<Option<i32>> { Poll::Ready(Some(item)) }
    #[derive(Clone)]
    struct Control { released: Rc<Cell<bool>>, polls: Rc<Cell<usize>>, waker: Rc<RefCell<Option<Waker>>> }
    fn control() -> Control { Control { released: Rc::new(Cell::new(false)), polls: Rc::new(Cell::new(0)), waker: Rc::new(RefCell::new(None)) } }
    impl Control { fn release(&self) { self.released.set(true); if let Some(w) = self.waker.borrow().as_ref() { w.wake_by_ref(); } } }
    struct Gate(Control);
    impl Future for Gate {
        type Output = ();
        fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output> {
            self.0.polls.set(self.0.polls.get()+1);
            if self.0.released.get() { Poll::Ready(()) }
            else { *self.0.waker.borrow_mut() = Some(cx.waker().clone()); Poll::Pending }
        }
    }
    #[test]
    fn zero_limit_is_unlimited() {
        let (s, polls) = input(vec![ok(1), ok(2), Poll::Ready(None)]); let first = control(); let second = control(); let calls = RefCell::new(vec![]);
        let mut f = s.for_each_concurrent(Some(0), |x| { calls.borrow_mut().push(x); Gate(if x==1 { first.clone() } else { second.clone() }) });
        let w = waker(); let mut cx = Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending()); assert_eq!(*calls.borrow(), vec![1,2]); assert_eq!(polls.get(),3);
        assert_eq!(first.polls.get(),1); assert_eq!(second.polls.get(),1); first.release(); second.release();
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(())); assert!(f.is_terminated());
    }
    #[test]
    fn sleeping_actions_are_not_repolled_without_their_wake() {
        let (s,polls)=input(vec![ok(1),ok(2),Poll::Ready(None)]); let first=control(); let second=control();
        let mut f=s.for_each_concurrent(Some(2),|x|Gate(if x==1{first.clone()}else{second.clone()})); let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),2);assert_eq!(first.polls.get(),1);assert_eq!(second.polls.get(),1);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(first.polls.get(),1);assert_eq!(second.polls.get(),1);
        second.release();assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),3);assert_eq!(first.polls.get(),1);assert_eq!(second.polls.get(),2);assert!(!f.is_terminated());
        first.release();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(()));assert!(f.is_terminated());
    }
    #[test]
    fn one_slot_backpressures_input_until_action_completes() {
        let(s,polls)=input(vec![ok(1),ok(2),Poll::Ready(None)]);let first=control();let calls=RefCell::new(vec![]);
        let mut f=s.for_each_concurrent(Some(1),|x|{calls.borrow_mut().push(x);if x==1{future::Either::Left(Gate(first.clone()))}else{future::Either::Right(future::ready(()))}});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*calls.borrow(),vec![1]);assert_eq!(polls.get(),1);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),1);assert_eq!(first.polls.get(),1);
        first.release();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(()));assert_eq!(*calls.borrow(),vec![1,2]);assert_eq!(polls.get(),3);
    }
    #[test]
    fn ready_actions_interleave_one_input_poll_with_one_queue_result() {
        let(mut s,_)=input(vec![ok(1),ok(2),Poll::Ready(None)]);let log=Rc::new(RefCell::new(vec![]));s.log=Some(log.clone());
        let mut f=s.for_each_concurrent(None,|x|{let log=log.clone();future::poll_fn(move |_|{log.borrow_mut().push(format!("action{x}"));Poll::Ready(())})});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(()));assert_eq!(*log.borrow(),vec!["input1","action1","input2","action2","input3"]);assert!(f.is_terminated());
    }
    #[test]
    fn callback_panic_happens_after_earlier_action_has_been_polled() {
        let(s,polls)=input(vec![ok(1),ok(2),ok(3)]);let first=control();let calls=RefCell::new(vec![]);
        let mut f=s.for_each_concurrent(Some(2),|x|{calls.borrow_mut().push(x);if x==2{panic!("callback")}Gate(first.clone())});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert_eq!(*calls.borrow(),vec![1,2]);assert_eq!(polls.get(),2);assert_eq!(first.polls.get(),1);assert!(!f.is_terminated());
    }
    #[test]
    fn pending_input_does_not_cause_extra_action_polls_or_input_reads() {
        let(s,polls)=input(vec![ok(1),Poll::Pending,Poll::Pending,Poll::Ready(None)]);let first=control();let mut f=s.for_each_concurrent(None,|_|Gate(first.clone()));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),2);assert_eq!(first.polls.get(),1);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),3);assert_eq!(first.polls.get(),1);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),4);assert_eq!(first.polls.get(),1);
        first.release();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(()));assert_eq!(polls.get(),4);assert!(f.is_terminated());
    }

}
