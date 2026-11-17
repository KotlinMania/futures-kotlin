#[cfg(test)]
mod tests {
    use futures_util::{Stream,TryStreamExt};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    type Output=Result<Option<i32>,Option<&'static str>>;
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    #[derive(Clone)] struct Gate {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>}
    impl Gate {fn release(&self) {self.released.set(true);if let Some(w)=self.waker.borrow().as_ref() {w.wake_by_ref();}}}
    struct Step {id:usize,value:Output,gate:Gate,trace:Rc<RefCell<Vec<usize>>>,completed:bool}
    fn step(id:usize,value:Output,released:bool,trace:&Rc<RefCell<Vec<usize>>>)->(Step,Gate) {
        let gate=Gate{released:Rc::new(Cell::new(released)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0))};
        (Step{id,value,gate:gate.clone(),trace:trace.clone(),completed:false},gate)
    }
    impl Future for Step {
        type Output=Output;
        fn poll(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Output> {
            assert!(!self.completed,"fixture polled after completion");self.trace.borrow_mut().push(self.id);self.gate.polls.set(self.gate.polls.get()+1);
            if !self.gate.released.get() {*self.gate.waker.borrow_mut()=Some(cx.waker().clone());return Poll::Pending;}
            self.completed=true;Poll::Ready(self.value)
        }
    }
    type Event=Poll<Option<Result<Step,Option<&'static str>>>>;
    struct Input {events:VecDeque<Event>,ended:bool,polls:Rc<Cell<usize>>}
    fn input(events:Vec<Event>)->(Input,Rc<Cell<usize>>) {let polls=Rc::new(Cell::new(0));(Input{events:events.into(),ended:false,polls:polls.clone()},polls)}
    fn future(s:Step)->Event {Poll::Ready(Some(Ok(s)))}
    fn error(e:Option<&'static str>)->Event {Poll::Ready(Some(Err(e)))}
    impl Stream for Input {
        type Item=Result<Step,Option<&'static str>>;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Event {
            assert!(!self.ended,"input polled after end");self.polls.set(self.polls.get()+1);let e=self.events.pop_front().expect("script exhausted");
            if matches!(e,Poll::Ready(None)) {self.ended=true;}if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn size_hint(&self)->(usize,Option<usize>) {let n=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(n,Some(n))}
    }
    #[test] fn later_error_is_driven_but_waits_for_nullable_first_success() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step(0,Ok(None),false,&t);let(b,gb)=step(1,Err(None),true,&t);
        let(s,_)=input(vec![future(a),future(b),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(*t.borrow(),vec![0,1]);ga.release();
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(None))));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(None))));
        assert_eq!(gb.polls.get(),1);assert_eq!(*t.borrow(),vec![0,1,0]);
    }
    #[test] fn outer_error_precedes_queued_poll_then_resumes() {
        let t=Rc::new(RefCell::new(vec![]));let(a,_)=step(0,Ok(Some(1)),true,&t);let(b,_)=step(1,Ok(Some(2)),true,&t);
        let(s,_)=input(vec![future(a),error(Some("outer")),future(b),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(Some("outer")))));assert!(t.borrow().is_empty());
        for v in 1..=2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(Some(v)))));}
    }
    #[test] fn cached_error_counts_toward_capacity_and_refill_error_wins() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step(0,Ok(Some(1)),false,&t);let(b,gb)=step(1,Err(None),true,&t);
        let(s,count)=input(vec![future(a),future(b),error(Some("outer")),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(gb.polls.get(),1);assert_eq!(count.get(),2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(count.get(),2);ga.release();
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(Some(1)))));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(Some("outer")))));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(None))));assert_eq!(gb.polls.get(),1);
    }
    #[test] fn zero_capacity_does_not_pull_outer_error() {
        let(s,count)=input(vec![error(Some("unpolled")),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffered(0);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);}assert_eq!(count.get(),0);
    }
    #[test] fn default_size_hint_even_with_known_input() {
        let t=Rc::new(RefCell::new(vec![]));let(a,_)=step(0,Ok(Some(7)),true,&t);
        let(s,_)=input(vec![future(a),error(None),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffered(2);
        assert_eq!(s.size_hint(),(0,None));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(None))));assert_eq!(s.size_hint(),(0,None));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(Some(7)))));
    }
    #[test] fn outer_pending_does_not_terminate_empty_queue() {
        let t=Rc::new(RefCell::new(vec![]));let(a,_)=step(0,Ok(Some(5)),true,&t);
        let(s,_)=input(vec![Poll::Pending,future(a),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(Some(5)))));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));
    }
}
