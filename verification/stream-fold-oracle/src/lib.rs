#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream,future::{self,FusedFuture}};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,panic::{catch_unwind,AssertUnwindSafe},pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Input {events:VecDeque<Poll<Option<i32>>>,polls:Rc<Cell<usize>>}
    fn input(events:Vec<Poll<Option<i32>>>)->(Input,Rc<Cell<usize>>) {let polls=Rc::new(Cell::new(0));(Input{events:events.into(),polls:polls.clone()},polls)}
    impl Stream for Input {type Item=i32;fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<i32>> {self.polls.set(self.polls.get()+1);let e=self.events.pop_front().expect("script exhausted");if e.is_pending() {cx.waker().wake_by_ref();}e}}
    #[derive(Clone)] struct Control {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>}
    fn control()->Control {Control{released:Rc::new(Cell::new(false)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0))}}
    impl Control {fn release(&self) {self.released.set(true);if let Some(w)=self.waker.borrow().as_ref() {w.wake_by_ref();}}}
    struct Gate(Control);
    impl Future for Gate {type Output=i32;fn poll(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<i32> {self.0.polls.set(self.0.polls.get()+1);if self.0.released.get() {Poll::Ready(10)}else{*self.0.waker.borrow_mut()=Some(cx.waker().clone());Poll::Pending}}}
    #[test] fn pending_future_blocks_input_and_supplies_next_accum() {
        let(s,polls)=input(vec![Poll::Ready(Some(1)),Poll::Ready(Some(2)),Poll::Ready(None)]);let gate=control();let calls=RefCell::new(vec![]);let mut f=s.fold(0,|a,x|{calls.borrow_mut().push((a,x));if x==1 {future::Either::Left(Gate(gate.clone()))}else{future::Either::Right(future::ready(a+x))}});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*calls.borrow(),vec![(0,1)]);assert_eq!(polls.get(),1);assert!(!f.is_terminated());gate.release();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(12));assert_eq!(*calls.borrow(),vec![(0,1),(10,2)]);assert_eq!(gate.polls.get(),2);assert!(f.is_terminated());
    }
    #[test] fn nullable_accumulator_and_future_output_are_present() {
        let seen=RefCell::new(vec![]);let mut f=stream::iter(vec![1,2]).fold(None,|a:Option<i32>,x|{seen.borrow_mut().push(a);future::ready(if x==1 {None}else{Some(7)})});let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(7)));assert_eq!(*seen.borrow(),vec![None,None]);assert!(f.is_terminated());
    }
    #[test] fn creation_panic_leaves_accumulator_taken_and_terminated() {
        let calls=Cell::new(0);let mut f=stream::iter(vec![1,2]).fold(3,|_,_|->future::Ready<i32>{calls.set(calls.get()+1);panic!("operation")});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert_eq!(calls.get(),1);
    }
    struct Explode;
    impl Future for Explode {type Output=i32;fn poll(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<i32> {panic!("operation future")}}
    #[test] fn future_panic_keeps_pending_state() {
        let mut f=stream::iter(vec![1]).fold(3,|_,_|Explode);let w=waker();let mut cx=Context::from_waker(&w);assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(!f.is_terminated());
    }
    #[test] fn outer_pending_preserves_accumulator() {
        let(s,_)=input(vec![Poll::Pending,Poll::Ready(Some(2)),Poll::Ready(None)]);let calls=Cell::new(0);let mut f=s.fold(5,|a,x|{calls.set(calls.get()+1);future::ready(a+x)});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(calls.get(),0);assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(7));assert_eq!(calls.get(),1);
    }
    #[test] fn empty_stream_returns_initial_null_then_panics() {
        let mut f=stream::iter(Vec::<i32>::new()).fold(None::<i32>,|_,_|future::ready(Some(99)));let w=waker();let mut cx=Context::from_waker(&w);assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(None));assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
}
