#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream::FusedStream};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    #[derive(Clone)] struct Gate {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>,polls:Rc<Cell<usize>>}
    impl Gate {fn release(&self) {self.released.set(true);if let Some(w)=self.waker.borrow().as_ref() {w.wake_by_ref();}}}
    struct Step {id:usize,value:Option<i32>,gate:Gate,trace:Rc<RefCell<Vec<usize>>>,completed:bool}
    fn step(id:usize,value:Option<i32>,released:bool,trace:&Rc<RefCell<Vec<usize>>>)->(Step,Gate) {
        let gate=Gate{released:Rc::new(Cell::new(released)),waker:Rc::new(RefCell::new(None)),polls:Rc::new(Cell::new(0))};
        (Step{id,value,gate:gate.clone(),trace:trace.clone(),completed:false},gate)
    }
    impl Future for Step {
        type Output=Option<i32>;
        fn poll(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Self::Output> {
            assert!(!self.completed,"fixture polled after completion");self.trace.borrow_mut().push(self.id);self.gate.polls.set(self.gate.polls.get()+1);
            if !self.gate.released.get() {*self.gate.waker.borrow_mut()=Some(cx.waker().clone());return Poll::Pending;}
            self.completed=true;Poll::Ready(self.value)
        }
    }
    struct Input {events:VecDeque<Poll<Option<Step>>>,ended:bool,polls:Rc<Cell<usize>>}
    fn input(events:Vec<Poll<Option<Step>>>)->(Input,Rc<Cell<usize>>) {
        let polls=Rc::new(Cell::new(0));(Input{events:events.into(),ended:false,polls:polls.clone()},polls)
    }
    impl Stream for Input {
        type Item=Step;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Step>> {
            assert!(!self.ended,"input polled after end");self.polls.set(self.polls.get()+1);
            let e=self.events.pop_front().expect("script exhausted");
            if matches!(e,Poll::Ready(None)) {self.ended=true;}if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn size_hint(&self)->(usize,Option<usize>) {
            assert!(!self.ended,"ended input hint must be supplied by Fuse");let count=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(count,Some(count))
        }
    }
    #[test] fn later_progress_and_ordered_outputs() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step(0,Some(1),false,&t);let(b,_)=step(1,Some(2),true,&t);let(c,_)=step(2,Some(3),true,&t);
        let(s,_)=input(vec![Poll::Ready(Some(a)),Poll::Ready(Some(b)),Poll::Ready(Some(c)),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffered(3);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(*t.borrow(),vec![0,1,2]);ga.release();
        for v in 1..=3 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(v))));}
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert_eq!(*t.borrow(),vec![0,1,2,0]);
    }
    #[test] fn buffered_outputs_count_towards_capacity() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step(0,Some(1),false,&t);let(b,gb)=step(1,Some(2),true,&t);let(c,gc)=step(2,Some(3),true,&t);
        let(s,count)=input(vec![Poll::Ready(Some(a)),Poll::Ready(Some(b)),Poll::Ready(Some(c)),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(gb.polls.get(),1);assert_eq!(count.get(),2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(count.get(),2);assert_eq!(gc.polls.get(),0);ga.release();
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(1))));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(2))));
        assert_eq!(count.get(),3);assert_eq!(gc.polls.get(),0);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(3))));
    }
    #[test] fn nullable_output_is_not_empty_queue() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step(0,None,false,&t);let(b,gb)=step(1,Some(7),true,&t);
        let(s,_)=input(vec![Poll::Ready(Some(a)),Poll::Ready(Some(b)),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(gb.polls.get(),1);ga.release();
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(None)));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(7))));assert_eq!(gb.polls.get(),1);
    }
    #[test] fn zero_capacity_never_polls_input() {
        let(s,count)=input(vec![Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffered(0);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);}assert_eq!(count.get(),0);
    }
    #[test] fn outer_pending_does_not_terminate_queue() {
        let t=Rc::new(RefCell::new(vec![]));let(a,_)=step(0,Some(5),true,&t);let(s,_)=input(vec![Poll::Pending,Poll::Ready(Some(a)),Poll::Ready(None)]);
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(5))));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert!(s.is_terminated());
    }
    #[test] fn ended_input_uses_fused_zero_hints() {
        let(s,_)=input(vec![Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert_eq!(s.size_hint(),(0,Some(0)));assert!(s.is_terminated());
    }
}
