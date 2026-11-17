#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,stream::FusedStream};
    use std::{collections::VecDeque,pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop { fn wake(self:Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    struct Script { events:VecDeque<Poll<Option<Option<i32>>>>,ended:bool,polls:usize }
    fn script(events:Vec<Poll<Option<Option<i32>>>>) -> Script {Script{events:events.into(),ended:false,polls:0}}
    fn value(v:Option<i32>) -> Poll<Option<Option<i32>>> { Poll::Ready(Some(v)) }
    impl Stream for Script {
        type Item=Option<i32>;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>) -> Poll<Option<Self::Item>> {
            assert!(!self.ended,"fixture polled after end");self.polls+=1;
            let event=self.events.pop_front().expect("script exhausted");
            if event==Poll::Ready(None) {self.ended=true;}
            if event.is_pending() {cx.waker().wake_by_ref();}
            event
        }
        fn size_hint(&self) -> (usize,Option<usize>) {
            let count=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(count,Some(count))
        }
    }
    impl FusedStream for Script {fn is_terminated(&self) -> bool {self.ended}}
    #[test]
    fn chunks_fuse_after_final_partial_chunk() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![value(None),Poll::Ready(None)]).chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![None])));assert!(s.is_terminated());
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));}
        assert_eq!(s.size_hint(),(0,Some(0)));assert_eq!(s.get_ref().polls,2);assert_eq!(s.into_inner().polls,2);
    }
    #[test]
    fn ready_chunks_fuse_after_final_partial_chunk() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![value(None),Poll::Ready(None)]).ready_chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![None])));assert!(s.is_terminated());
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));}
        assert_eq!(s.size_hint(),(0,Some(0)));assert_eq!(s.get_ref().polls,2);assert_eq!(s.into_inner().polls,2);
    }
    #[test]
    fn chunks_keep_conservative_upper_bound() {
        let mut events=(0..5).map(|i|value(Some(i))).collect::<Vec<_>>();events.push(Poll::Ready(None));
        let s=script(events).chunks(4);assert_eq!(s.size_hint(),(1,Some(5)));
    }
    #[test]
    fn chunks_retain_null_buffer_across_pending() {
        let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(vec![value(None),Poll::Pending,value(Some(2)),value(Some(3)),Poll::Ready(None)]).chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(s.size_hint(),(1,Some(3)));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![None,Some(2),Some(3)])));assert!(s.is_terminated());
    }
    #[test]
    fn ready_chunks_flush_on_pending_without_losing_null() {
        let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(vec![value(None),Poll::Pending,value(Some(2)),Poll::Ready(None)]).ready_chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![None])));assert_eq!(s.size_hint(),(0,Some(1)));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![Some(2)])));
    }
    #[test]
    fn full_chunks_preserve_order_and_nulls() {
        let w=waker();let mut cx=Context::from_waker(&w);
        let mut s=script(vec![value(Some(1)),value(None),value(Some(3)),Poll::Ready(None)]).chunks(2);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![Some(1),None])));
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(vec![Some(3)])));
    }
}
