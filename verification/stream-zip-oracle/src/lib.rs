#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream::FusedStream};
    use std::{cell::RefCell,collections::VecDeque,pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Script {id:usize,events:VecDeque<Poll<Option<Option<i32>>>>,trace:Rc<RefCell<Vec<usize>>>,ended:bool}
    fn script(id:usize,events:Vec<Poll<Option<Option<i32>>>>,trace:&Rc<RefCell<Vec<usize>>>)->Script {Script{id,events:events.into(),trace:trace.clone(),ended:false}}
    fn value(v:Option<i32>)->Poll<Option<Option<i32>>> {Poll::Ready(Some(v))}
    impl Stream for Script {
        type Item=Option<i32>;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Self::Item>> {
            assert!(!self.ended,"fixture polled after end");self.trace.borrow_mut().push(self.id);
            let e=self.events.pop_front().expect("script exhausted");
            if e==Poll::Ready(None) {self.ended=true;}
            if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn size_hint(&self)->(usize,Option<usize>) {
            let count=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(count,Some(count))
        }
    }
    impl FusedStream for Script {fn is_terminated(&self)->bool {self.ended || self.events.front()==Some(&Poll::Ready(None))}}
    #[test]
    fn left_end_still_polls_and_queues_right() {
        let t=Rc::new(RefCell::new(vec![]));let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(0,vec![Poll::Ready(None)],&t).zip(script(1,vec![value(Some(7)),Poll::Ready(None)],&t));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert_eq!(*t.borrow(),vec![0,1]);
        assert!(!s.is_terminated());assert_eq!(s.size_hint(),(0,Some(0)));assert_eq!(s.get_ref().0.id,0);assert_eq!(s.into_inner().1.id,1);
    }
    #[test]
    fn right_end_keeps_polling_left_and_fuses_right() {
        let t=Rc::new(RefCell::new(vec![]));let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(0,vec![Poll::Pending,Poll::Pending],&t).zip(script(1,vec![Poll::Ready(None)],&t));
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));}
        assert_eq!(*t.borrow(),vec![0,1,0]);assert!(!s.is_terminated());
    }
    #[test]
    fn both_ended_inputs_are_observed_in_same_poll() {
        let t=Rc::new(RefCell::new(vec![]));let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(0,vec![Poll::Ready(None)],&t).zip(script(1,vec![Poll::Ready(None)],&t));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert_eq!(*t.borrow(),vec![0,1]);assert!(s.is_terminated());
    }
    #[test]
    fn termination_tracks_fuse_observation() {
        let t=Rc::new(RefCell::new(vec![]));
        let s=script(0,vec![Poll::Ready(None)],&t).zip(script(1,vec![Poll::Ready(None)],&t));
        assert!(!s.is_terminated());assert!(t.borrow().is_empty());
    }
    #[test]
    fn buffered_nullable_value_survives_pending() {
        let t=Rc::new(RefCell::new(vec![]));let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(0,vec![value(None),Poll::Ready(None)],&t).zip(script(1,vec![Poll::Pending,value(Some(7)),Poll::Ready(None)],&t));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(s.size_hint(),(1,Some(1)));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some((None,Some(7)))));assert_eq!(*t.borrow(),vec![0,1,1]);
    }
    #[test]
    fn entirely_pending_visits_both() {
        let t=Rc::new(RefCell::new(vec![]));let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(0,vec![Poll::Pending],&t).zip(script(1,vec![Poll::Pending],&t));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(*t.borrow(),vec![0,1]);assert!(!s.is_terminated());
    }
}
