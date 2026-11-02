#[cfg(test)]
mod tests {
    use futures_util::{Stream,TryStreamExt,stream,future::{self,FusedFuture}};
    use std::{cell::{Cell,RefCell},future::Future,panic::{catch_unwind,AssertUnwindSafe},pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    type Acc=Option<i32>;type Error=Option<&'static str>;type Out=Result<Acc,Error>;
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    #[test] fn empty_stream_keeps_present_null_until_completion() {
        let mut f=stream::iter(Vec::<Result<i32,Error>>::new()).try_fold(None,|_,_|future::ready(Ok::<Acc,Error>(Some(99))));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(None)));assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
    #[test] fn null_future_success_reaches_next_operation_then_nullable_error() {
        let seen=RefCell::new(vec![]);let mut f=stream::iter(vec![Ok::<i32,Error>(1),Ok(2)]).try_fold(Some(0),|a,x|{seen.borrow_mut().push(a);future::ready(if x==1 {Ok(None)}else{Err(None)})});let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err(None)));assert_eq!(*seen.borrow(),vec![Some(0),None]);assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
    struct Input(Rc<Cell<usize>>);
    impl Stream for Input {type Item=Result<i32,Error>;fn poll_next(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Self::Item>> {let n=self.0.get()+1;self.0.set(n);match n {1=>{cx.waker().wake_by_ref();Poll::Pending},2=>Poll::Ready(Some(Ok(1))),_=>Poll::Ready(None)}}}
    #[derive(Clone)] struct Control {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>}
    struct Gate(Control);
    impl Future for Gate {type Output=Out;fn poll(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Out> {self.0.polls.set(self.0.polls.get()+1);if self.0.released.get() {Poll::Ready(Ok(Some(7)))}else{*self.0.waker.borrow_mut()=Some(cx.waker().clone());Poll::Pending}}}
    #[test] fn null_accum_survives_input_and_operation_pending() {
        let polls=Rc::new(Cell::new(0));let gate=Control{released:Rc::new(Cell::new(false)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0))};let calls=Cell::new(0);let mut f=Input(polls.clone()).try_fold(None,|a,_|{assert_eq!(a,None);calls.set(calls.get()+1);Gate(gate.clone())});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),1);assert_eq!(calls.get(),0);assert!(!f.is_terminated());assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(calls.get(),1);assert_eq!(gate.polls.get(),1);assert_eq!(polls.get(),2);
        gate.released.set(true);gate.waker.borrow().as_ref().unwrap().wake_by_ref();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(Some(7))));assert_eq!(calls.get(),1);assert_eq!(gate.polls.get(),2);assert!(f.is_terminated());
    }
    #[test] fn nullable_input_error_terminates_without_operation() {
        let calls=Cell::new(0);let mut f=stream::iter(vec![Err::<i32,Error>(None)]).try_fold(Some(3),|_,_|{calls.set(calls.get()+1);future::ready(Ok::<Acc,Error>(Some(99)))});let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err(None)));assert_eq!(calls.get(),0);assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
    #[test] fn creation_panic_consumes_accum_and_terminates() {
        let calls=Cell::new(0);let mut f=stream::iter(vec![Ok::<i32,Error>(1),Ok(2)]).try_fold(Some(3),|_,_|->future::Ready<Out>{calls.set(calls.get()+1);panic!("operation")});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert_eq!(calls.get(),1);
    }
    struct Explode;
    impl Future for Explode {type Output=Out;fn poll(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<Out> {panic!("operation future")}}
    #[test] fn future_panic_keeps_pending_future_state() {
        let mut f=stream::iter(vec![Ok::<i32,Error>(1)]).try_fold(Some(3),|_,_|Explode);let w=waker();let mut cx=Context::from_waker(&w);assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(!f.is_terminated());
    }
}
