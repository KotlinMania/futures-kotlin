#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,TryStreamExt,stream::FusedStream};
    use std::{cell::{Cell,RefCell},collections::VecDeque,future::Future,pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    type Output=Result<Option<i32>,Option<&'static str>>;
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    #[derive(Clone)] struct Gate {released:Rc<Cell<bool>>,waker:Rc<RefCell<Option<Waker>>>}
    impl Gate {fn release(&self) {self.released.set(true);if let Some(w)=self.waker.borrow().as_ref() {w.wake_by_ref();}}}
    struct Step<T:Copy> {id:usize,value:T,gate:Gate,trace:Rc<RefCell<Vec<usize>>>,completed:bool}
    fn step<T:Copy>(id:usize,value:T,released:bool,trace:&Rc<RefCell<Vec<usize>>>)->(Step<T>,Gate) {
        let gate=Gate{released:Rc::new(Cell::new(released)),waker:Rc::new(RefCell::new(None))};
        (Step{id,value,gate:gate.clone(),trace:trace.clone(),completed:false},gate)
    }
    impl<T:Copy+Unpin> Future for Step<T> {
        type Output=T;
        fn poll(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<T> {
            assert!(!self.completed,"fixture polled after completion");self.trace.borrow_mut().push(self.id);
            if !self.gate.released.get() {*self.gate.waker.borrow_mut()=Some(cx.waker().clone());return Poll::Pending;}
            self.completed=true;Poll::Ready(self.value)
        }
    }
    struct Input<T> {events:VecDeque<Poll<Option<T>>>,ended:bool,polls:Rc<Cell<usize>>}
    fn input<T>(events:Vec<Poll<Option<T>>>)->(Input<T>,Rc<Cell<usize>>) {let polls=Rc::new(Cell::new(0));(Input{events:events.into(),ended:false,polls:polls.clone()},polls)}
    fn value<T>(v:T)->Poll<Option<T>> {Poll::Ready(Some(v))}
    impl<T:Unpin> Stream for Input<T> {
        type Item=T;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<T>> {
            assert!(!self.ended,"input polled after end");self.polls.set(self.polls.get()+1);let e=self.events.pop_front().expect("script exhausted");
            if matches!(e,Poll::Ready(None)) {self.ended=true;}if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn size_hint(&self)->(usize,Option<usize>) {assert!(!self.ended,"ended hint must come from Fuse");let n=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(n,Some(n))}
    }
    #[test] fn idle_futures_are_not_repolled_without_wake() {
        let t=Rc::new(RefCell::new(vec![]));let(a,_)=step(0,1,false,&t);let(b,_)=step(1,2,false,&t);
        let(s,_)=input(vec![value(a),value(b)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffer_unordered(2);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);}assert_eq!(*t.borrow(),vec![0,1]);
    }
    #[test] fn only_woken_future_is_polled_and_null_remains_present() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step(0,Some(7),false,&t);let(b,gb)=step(1,None,false,&t);
        let(s,_)=input(vec![value(a),value(b),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffer_unordered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);gb.release();assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(None)));assert_eq!(*t.borrow(),vec![0,1,1]);ga.release();assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(7))));
    }
    #[test] fn zero_capacity_does_not_poll_input() {
        let(s,count)=input::<Step<i32>>(vec![Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffer_unordered(0);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);}assert_eq!(count.get(),0);
    }
    #[test] fn ended_input_uses_fused_zero_hints() {
        let(s,_)=input::<Step<i32>>(vec![Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.buffer_unordered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert_eq!(s.size_hint(),(0,Some(0)));assert!(s.is_terminated());
    }
    #[test] fn fallible_queue_only_polls_woken_future_and_yields_its_error() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step::<Output>(0,Ok(None),false,&t);let(b,gb)=step::<Output>(1,Err(None),false,&t);
        let(s,_)=input(vec![value(Ok::<_,Option<&str>>(a)),value(Ok(b)),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffer_unordered(2);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);}assert_eq!(*t.borrow(),vec![0,1]);gb.release();
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(None))));assert_eq!(*t.borrow(),vec![0,1,1]);ga.release();assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(None))));
    }
    #[test] fn outer_error_precedes_future_poll_then_resumes() {
        let t=Rc::new(RefCell::new(vec![]));let(a,ga)=step::<Output>(0,Ok(Some(1)),false,&t);let(b,_)=step::<Output>(1,Err(Some("future")),true,&t);
        let(s,_)=input(vec![value(Ok(a)),value(Err(None)),value(Ok(b)),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffer_unordered(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(None))));assert!(t.borrow().is_empty());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(Some("future")))));ga.release();assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(Some(1)))));
    }
    #[test] fn zero_capacity_does_not_poll_fallible_error() {
        let(s,count)=input::<Result<Step<Output>,Option<&str>>>(vec![value(Err(Some("unpolled"))),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffer_unordered(0);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);}assert_eq!(count.get(),0);
    }
    #[test] fn fallible_size_hint_is_default() {
        let(s,_)=input::<Result<Step<Output>,Option<&str>>>(vec![value(Err(None)),Poll::Ready(None)]);let w=waker();let mut cx=Context::from_waker(&w);let mut s=s.try_buffer_unordered(2);
        assert_eq!(s.size_hint(),(0,None));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(None))));assert_eq!(s.size_hint(),(0,None));
    }
}
