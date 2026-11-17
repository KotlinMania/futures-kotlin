#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream,future::{self,FusedFuture}};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,panic::{catch_unwind,AssertUnwindSafe},pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Input {events:VecDeque<Poll<Option<Option<i32>>>>,polls:Rc<Cell<usize>>}
    fn input(events:Vec<Poll<Option<Option<i32>>>>)->(Input,Rc<Cell<usize>>) {let polls=Rc::new(Cell::new(0));(Input{events:events.into(),polls:polls.clone()},polls)}
    impl Stream for Input {type Item=Option<i32>;fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Self::Item>> {self.polls.set(self.polls.get()+1);let e=self.events.pop_front().expect("script exhausted");if e.is_pending() {cx.waker().wake_by_ref();}e}}
    #[derive(Clone)] struct Control {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>,value:bool}
    fn control(value:bool)->Control {Control{released:Rc::new(Cell::new(false)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0)),value}}
    impl Control {fn release(&self) {self.released.set(true);if let Some(w)=self.waker.borrow().as_ref() {w.wake_by_ref();}}}
    struct Gate(Control);
    impl Future for Gate {type Output=bool;fn poll(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<bool> {self.0.polls.set(self.0.polls.get()+1);if self.0.released.get() {Poll::Ready(self.0.value)}else{*self.0.waker.borrow_mut()=Some(cx.waker().clone());Poll::Pending}}}

    #[test] fn all_waits_then_short_circuits() {
        let(s,polls)=input(vec![Poll::Ready(Some(None)),Poll::Ready(Some(Some(7))),Poll::Ready(None)]);let gate=control(false);let calls=RefCell::new(vec![]);let mut f=s.all(|x|{calls.borrow_mut().push(x);Gate(gate.clone())});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*calls.borrow(),vec![None]);assert_eq!(polls.get(),1);assert!(!f.is_terminated());gate.release();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(false));assert_eq!(polls.get(),1);assert_eq!(gate.polls.get(),2);assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
    #[test] fn all_visits_nullable_items_until_eof() {
        let calls=RefCell::new(vec![]);let mut f=stream::iter(vec![None,Some(7)]).all(|x|{calls.borrow_mut().push(x);future::ready(true)});let w=waker();let mut cx=Context::from_waker(&w);assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(true));assert_eq!(*calls.borrow(),vec![None,Some(7)]);assert!(f.is_terminated());
    }
    #[test] fn all_input_pending_then_empty_identity() {
        let(s,_)=input(vec![Poll::Pending,Poll::Ready(None)]);let mut f=s.all(|_|->future::Ready<bool>{panic!("not called")});let w=waker();let mut cx=Context::from_waker(&w);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(true));assert!(f.is_terminated());
    }

    #[test] fn any_waits_then_short_circuits() {
        let(s,polls)=input(vec![Poll::Ready(Some(None)),Poll::Ready(Some(Some(7))),Poll::Ready(None)]);let gate=control(true);let calls=RefCell::new(vec![]);let mut f=s.any(|x|{calls.borrow_mut().push(x);Gate(gate.clone())});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*calls.borrow(),vec![None]);assert_eq!(polls.get(),1);assert!(!f.is_terminated());gate.release();assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(true));assert_eq!(polls.get(),1);assert_eq!(gate.polls.get(),2);assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());
    }
    #[test] fn any_visits_nullable_items_until_eof() {
        let calls=RefCell::new(vec![]);let mut f=stream::iter(vec![None,Some(7)]).any(|x|{calls.borrow_mut().push(x);future::ready(false)});let w=waker();let mut cx=Context::from_waker(&w);assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(false));assert_eq!(*calls.borrow(),vec![None,Some(7)]);assert!(f.is_terminated());
    }
    #[test] fn any_input_pending_then_empty_identity() {
        let(s,_)=input(vec![Poll::Pending,Poll::Ready(None)]);let mut f=s.any(|_|->future::Ready<bool>{panic!("not called")});let w=waker();let mut cx=Context::from_waker(&w);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(false));assert!(f.is_terminated());
    }

    #[test] fn predicate_creation_panic_does_not_complete_all() {
        let mut f=stream::iter(vec![1]).all(|_|->future::Ready<bool>{panic!("predicate")});let w=waker();let mut cx=Context::from_waker(&w);assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(!f.is_terminated());
    }
    struct Explode;
    impl Future for Explode {type Output=bool;fn poll(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<bool> {panic!("predicate future")}}
    #[test] fn returned_future_panic_is_not_discarded_by_any() {
        let mut f=stream::iter(vec![1]).any(|_|Explode);let w=waker();let mut cx=Context::from_waker(&w);assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(!f.is_terminated());
    }
}
