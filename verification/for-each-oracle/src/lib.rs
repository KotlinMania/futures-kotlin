#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream::{self,FusedStream},future::{self,FusedFuture}};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,panic::{catch_unwind,AssertUnwindSafe},pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Input {events:VecDeque<Poll<Option<Option<i32>>>>,polls:Rc<Cell<usize>>}
    fn input(events:Vec<Poll<Option<Option<i32>>>>)->(Input,Rc<Cell<usize>>) {let polls=Rc::new(Cell::new(0));(Input{events:events.into(),polls:polls.clone()},polls)}
    impl Stream for Input {
        type Item=Option<i32>;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Self::Item>> {self.polls.set(self.polls.get()+1);let e=self.events.pop_front().unwrap_or(Poll::Ready(None));if e.is_pending() {cx.waker().wake_by_ref();}e}
    }
    impl FusedStream for Input {fn is_terminated(&self)->bool {self.events.is_empty()}}
    #[derive(Clone)] struct Control {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>}
    fn control()->Control {Control{released:Rc::new(Cell::new(false)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0))}}
    impl Control {fn release(&self) {self.released.set(true);if let Some(w)=self.waker.borrow().as_ref() {w.wake_by_ref();}}}
    struct Gate(Control);
    impl Future for Gate {type Output=();fn poll(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<()> {self.0.polls.set(self.0.polls.get()+1);if self.0.released.get() {Poll::Ready(())}else{*self.0.waker.borrow_mut()=Some(cx.waker().clone());Poll::Pending}}}
    #[test] fn pending_action_blocks_input_and_is_not_recreated() {
        let(s,polls)=input(vec![Poll::Ready(Some(Some(1))),Poll::Ready(Some(Some(2))),Poll::Ready(None)]);let gate=control();let calls=RefCell::new(vec![]);
        let mut f=s.for_each(|x|{calls.borrow_mut().push(x);if x==Some(1) {future::Either::Left(Gate(gate.clone()))}else{future::Either::Right(future::ready(()))}});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*calls.borrow(),vec![Some(1)]);assert_eq!(polls.get(),1);assert_eq!(gate.polls.get(),1);assert!(!f.is_terminated());gate.release();assert!(Pin::new(&mut f).poll(&mut cx).is_ready());assert_eq!(*calls.borrow(),vec![Some(1),Some(2)]);assert_eq!(gate.polls.get(),2);assert_eq!(polls.get(),3);assert!(f.is_terminated());
    }
    struct Explode;
    impl Future for Explode {type Output=();fn poll(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<()> {panic!("action future")}}
    #[test] fn returned_future_panic_is_not_discarded() {
        let mut f=stream::iter(vec![1]).for_each(|_|Explode);let w=waker();let mut cx=Context::from_waker(&w);assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
    #[test] fn fused_empty_input_is_terminated_before_poll() {
        let(s,polls)=input(vec![]);let f=s.for_each(|_|future::ready(()));assert!(f.is_terminated());assert_eq!(polls.get(),0);
    }
    #[test] fn outer_and_inner_pending_are_distinct() {
        let(s,polls)=input(vec![Poll::Pending,Poll::Ready(Some(Some(3))),Poll::Ready(None)]);let gate=control();let calls=Cell::new(0);let mut f=s.for_each(|_|{calls.set(calls.get()+1);Gate(gate.clone())});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(calls.get(),0);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(calls.get(),1);assert_eq!(polls.get(),2);gate.release();assert!(Pin::new(&mut f).poll(&mut cx).is_ready());assert_eq!(calls.get(),1);
    }
    #[test] fn nullable_items_reach_callbacks_in_order() {
        let calls=RefCell::new(vec![]);let mut f=stream::iter(vec![None,Some(7)]).for_each(|x|{calls.borrow_mut().push(x);future::ready(())});let w=waker();let mut cx=Context::from_waker(&w);assert!(Pin::new(&mut f).poll(&mut cx).is_ready());assert_eq!(*calls.borrow(),vec![None,Some(7)]);
    }
    #[test] fn pending_then_end_creates_no_action() {
        let(s,_)=input(vec![Poll::Pending,Poll::Ready(None)]);let calls=Cell::new(0);let mut f=s.for_each(|_|{calls.set(calls.get()+1);future::ready(())});let w=waker();let mut cx=Context::from_waker(&w);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert!(Pin::new(&mut f).poll(&mut cx).is_ready());assert_eq!(calls.get(),0);
    }
}
