#[cfg(test)]
mod tests {
    use futures_util::{Stream,TryStreamExt,stream::FusedStream};
    use std::{collections::VecDeque,pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    type Item=Result<Option<i32>,Option<&'static str>>;
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Script {events:VecDeque<Poll<Option<Item>>>,ended:bool,polls:usize}
    fn script(events:Vec<Poll<Option<Item>>>)->Script {Script{events:events.into(),ended:false,polls:0}}
    fn ok(v:Option<i32>)->Poll<Option<Item>> {Poll::Ready(Some(Ok(v)))}
    fn err(e:Option<&'static str>)->Poll<Option<Item>> {Poll::Ready(Some(Err(e)))}
    impl Stream for Script {
        type Item=Item;
        fn poll_next(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Item>> {
            assert!(!self.ended,"fixture polled after end");self.polls+=1;
            let e=self.events.pop_front().expect("script exhausted");
            if e==Poll::Ready(None) {self.ended=true;}
            if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn size_hint(&self)->(usize,Option<usize>) {
            let count=self.events.iter().filter(|e|matches!(e,Poll::Ready(Some(_)))).count();(count,Some(count))
        }
    }
    impl FusedStream for Script {fn is_terminated(&self)->bool {self.ended}}
    #[test]
    fn try_chunks_fuse_after_partial_chunk() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![ok(None),Poll::Ready(None)]).try_chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(vec![None]))));assert!(s.is_terminated());
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));}
        assert_eq!(s.size_hint(),(0,Some(0)));assert_eq!(s.get_ref().polls,2);
    }
    #[test]
    fn try_ready_chunks_fuse_after_partial_chunk() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![ok(None),Poll::Ready(None)]).try_ready_chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(vec![None]))));assert!(s.is_terminated());
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));}
        assert_eq!(s.size_hint(),(0,Some(0)));assert_eq!(s.get_ref().polls,2);
    }
    #[test]
    fn try_chunks_upper_includes_every_error() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![err(Some("a")),err(None),err(Some("c")),Poll::Ready(None)]).try_chunks(4);
        assert_eq!(s.size_hint(),(0,Some(3)));
        for _ in 0..3 {assert!(matches!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(_)))));}
    }
    #[test]
    fn try_ready_chunks_upper_includes_every_error() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![err(Some("a")),err(None),err(Some("c")),Poll::Ready(None)]).try_ready_chunks(4);
        assert_eq!(s.size_hint(),(0,Some(3)));
        for _ in 0..3 {assert!(matches!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Err(_)))));}
    }
    #[test]
    fn try_chunks_keep_buffer_across_pending_and_nullable_error() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![ok(None),Poll::Pending,err(None),ok(Some(2)),Poll::Ready(None)]).try_chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Pending);assert_eq!(s.size_hint(),(1,Some(3)));
        let Poll::Ready(Some(Err(e)))=Pin::new(&mut s).poll_next(&mut cx) else {panic!("not error")};assert_eq!(e.0,vec![None]);assert_eq!(e.1,None);assert!(!s.is_terminated());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(vec![Some(2)]))));assert!(s.is_terminated());
    }
    #[test]
    fn try_ready_chunks_return_buffer_with_error_and_resume() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![ok(None),err(None),ok(Some(2)),Poll::Ready(None)]).try_ready_chunks(4);
        let Poll::Ready(Some(Err(e)))=Pin::new(&mut s).poll_next(&mut cx) else {panic!("not error")};assert_eq!(e.0,vec![None]);assert_eq!(e.1,None);assert!(!s.is_terminated());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(vec![Some(2)]))));
    }
    #[test]
    fn try_ready_chunks_flush_pending_before_next_error() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut s=script(vec![ok(None),Poll::Pending,err(Some("later")),Poll::Ready(None)]).try_ready_chunks(4);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Ok(vec![None]))));
        let Poll::Ready(Some(Err(e)))=Pin::new(&mut s).poll_next(&mut cx) else {panic!("not error")};assert!(e.0.is_empty());assert_eq!(e.1,Some("later"));
    }
}
