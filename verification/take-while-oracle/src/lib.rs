#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream::FusedStream,future};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,panic::{catch_unwind,AssertUnwindSafe},pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Input {events:VecDeque<Poll<Option<Option<i32>>>>,polls:Rc<Cell<usize>>}
    fn input(events:Vec<Poll<Option<Option<i32>>>>)->(Input,Rc<Cell<usize>>) {let polls=Rc::new(Cell::new(0));(Input{events:events.into(),polls:polls.clone()},polls)}
    impl Stream for Input {type Item=Option<i32>;fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Self::Item>> {self.polls.set(self.polls.get()+1);let e=self.events.pop_front().unwrap_or(Poll::Ready(None));if e.is_pending() {cx.waker().wake_by_ref();}e}fn size_hint(&self)->(usize,Option<usize>){let n=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(n,Some(n))}}
    impl FusedStream for Input {fn is_terminated(&self)->bool {self.events.is_empty()}}
    #[derive(Clone)] struct Control {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>,value:bool}
    fn control(value:bool)->Control {Control{released:Rc::new(Cell::new(false)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0)),value}}
    impl Control {fn release(&self){self.released.set(true);if let Some(w)=self.waker.borrow().as_ref(){w.wake_by_ref();}}}
    struct Gate(Control);
    impl Future for Gate {type Output=bool;fn poll(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<bool>{self.0.polls.set(self.0.polls.get()+1);if self.0.released.get(){Poll::Ready(self.0.value)}else{*self.0.waker.borrow_mut()=Some(cx.waker().clone());Poll::Pending}}}
    #[test] fn pending_predicate_retains_null_and_upper_hint() {
        let(s,polls)=input(vec![Poll::Ready(Some(None)),Poll::Ready(Some(Some(7))),Poll::Ready(None)]);let gate=control(true);let calls=RefCell::new(vec![]);let mut f=s.take_while(|x|{calls.borrow_mut().push(*x);if x.is_none(){future::Either::Left(Gate(gate.clone()))}else{future::Either::Right(future::ready(true))}});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll_next(&mut cx).is_pending());assert_eq!(*calls.borrow(),vec![None]);assert_eq!(polls.get(),1);assert_eq!(f.size_hint(),(0,Some(2)));gate.release();assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(Some(None)));assert_eq!(polls.get(),1);assert_eq!(f.size_hint(),(0,Some(1)));assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(Some(Some(7))));assert_eq!(*calls.borrow(),vec![None,Some(7)]);assert_eq!(gate.polls.get(),2);
    }
    #[test] fn false_predicate_stops_without_reading_next_item() {
        let(s,polls)=input(vec![Poll::Ready(Some(None)),Poll::Ready(Some(Some(7))),Poll::Ready(None)]);let gate=control(false);let calls=RefCell::new(vec![]);let mut f=s.take_while(|x|{calls.borrow_mut().push(*x);Gate(gate.clone())});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll_next(&mut cx).is_pending());gate.release();assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(None));assert!(f.is_terminated());assert_eq!(f.size_hint(),(0,Some(0)));assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(None));assert_eq!(*calls.borrow(),vec![None]);assert_eq!(polls.get(),1);
    }
    #[test] fn pending_predicate_blocks_termination_on_exhausted_input() {
        let(s,_)=input(vec![Poll::Ready(Some(Some(1)))]);let gate=control(true);let mut f=s.take_while(|_|Gate(gate.clone()));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll_next(&mut cx).is_pending());assert!(f.get_ref().is_terminated());assert!(!f.is_terminated());assert_eq!(f.size_hint(),(0,Some(1)));gate.release();assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(Some(Some(1))));assert!(f.is_terminated());assert_eq!(f.size_hint(),(0,Some(0)));
    }
    #[test] fn creation_panic_does_not_cache_consumed_item() {
        let(s,polls)=input(vec![Poll::Ready(Some(Some(1))),Poll::Ready(Some(Some(2)))]);let mut f=s.take_while(|_|->future::Ready<bool>{panic!("predicate")});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll_next(&mut cx))).is_err());assert_eq!(polls.get(),1);assert_eq!(f.size_hint(),(0,Some(1)));assert!(!f.is_terminated());
    }
    struct Explode;
    impl Future for Explode {type Output=bool;fn poll(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<bool>{panic!("predicate future")}}
    #[test] fn future_panic_retains_pending_item_and_future() {
        let(s,_)=input(vec![Poll::Ready(Some(None))]);let mut f=s.take_while(|_|Explode);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll_next(&mut cx))).is_err());assert_eq!(f.size_hint(),(0,Some(1)));assert!(!f.is_terminated());
    }
    #[test] fn input_pending_then_end_creates_no_predicate() {
        let(s,_)=input(vec![Poll::Pending,Poll::Ready(None)]);let mut f=s.take_while(|_|->future::Ready<bool>{panic!("not called")});let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll_next(&mut cx).is_pending());assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(None));assert!(f.is_terminated());
    }
    #[test] fn input_end_does_not_latch_done_for_non_fused_stream() {
        let polls=Cell::new(0);let s=futures_util::stream::poll_fn(|_|{polls.set(polls.get()+1);if polls.get()==1{Poll::Ready(None)}else{Poll::Ready(Some(1))}});let mut f=s.take_while(|_|future::ready(true));let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(None));assert_eq!(Pin::new(&mut f).poll_next(&mut cx),Poll::Ready(Some(1)));assert_eq!(polls.get(),2);
    }

}
