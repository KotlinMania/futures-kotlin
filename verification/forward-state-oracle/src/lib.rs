#[cfg(test)]
mod tests {
    use futures_util::{future::FusedFuture, Sink, Stream, StreamExt};
    use std::{cell::{Cell, RefCell}, collections::VecDeque, future::Future, pin::Pin,
        rc::Rc, sync::Arc, task::{Context, Poll, Wake, Waker}};
    type Item = Result<Option<i32>, &'static str>;
    struct Noop;
    impl Wake for Noop { fn wake(self: Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    struct Input { events: VecDeque<Poll<Option<Item>>>, polls: Rc<Cell<usize>> }
    fn input(events: Vec<Poll<Option<Item>>>) -> (Input, Rc<Cell<usize>>) {
        let polls=Rc::new(Cell::new(0)); (Input { events: events.into(), polls: polls.clone() }, polls)
    }
    impl Stream for Input {
        type Item = Item;
        fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Item>> {
            self.polls.set(self.polls.get()+1);
            let event=self.events.pop_front().unwrap_or(Poll::Ready(None)); if event.is_pending() {cx.waker().wake_by_ref();} event
        }
    }
    fn ok(item: Option<i32>) -> Poll<Option<Item>> { Poll::Ready(Some(Ok(item))) }
    #[derive(Default)]
    struct Record { attempts: Vec<Option<i32>>, ready: usize, flush: usize, close: usize }
    #[derive(Default)]
    struct Output { record: Rc<RefCell<Record>>, ready: VecDeque<Poll<Result<(), &'static str>>>, sends: VecDeque<Result<(), &'static str>>, flushes: VecDeque<Poll<Result<(), &'static str>>>, closes: VecDeque<Poll<Result<(), &'static str>>> }
    fn output() -> (Output, Rc<RefCell<Record>>) { let s=Output::default(); let r=s.record.clone();(s,r) }
    fn poll(events: &mut VecDeque<Poll<Result<(), &'static str>>>, cx: &mut Context<'_>) -> Poll<Result<(), &'static str>> {
        let event=events.pop_front().unwrap_or(Poll::Ready(Ok(())));if event.is_pending(){cx.waker().wake_by_ref();}event
    }
    impl Sink<Option<i32>> for Output {
        type Error = &'static str;
        fn poll_ready(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {self.record.borrow_mut().ready+=1;poll(&mut self.ready,cx)}
        fn start_send(mut self: Pin<&mut Self>, item: Option<i32>) -> Result<(), Self::Error> {self.record.borrow_mut().attempts.push(item);self.sends.pop_front().unwrap_or(Ok(()))}
        fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {self.record.borrow_mut().flush+=1;poll(&mut self.flushes,cx)}
        fn poll_close(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {self.record.borrow_mut().close+=1;poll(&mut self.closes,cx)}
    }
    #[test]
    fn null_item_is_sent_before_next_item_and_close() {
        let(s,_)=input(vec![ok(None),ok(Some(7)),Poll::Ready(None)]);let(out,r)=output();let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(())));assert_eq!(r.borrow().attempts,vec![None,Some(7)]);assert_eq!(r.borrow().ready,2);assert_eq!(r.borrow().close,1);assert!(f.is_terminated());
    }
    #[test]
    fn pending_readiness_retains_null_without_reading_ahead() {
        let(s,polls)=input(vec![ok(None),ok(Some(7)),Poll::Ready(None)]);let(mut out,r)=output();out.ready.push_back(Poll::Pending);let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),1);assert!(r.borrow().attempts.is_empty());assert_eq!(r.borrow().close,0);assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(())));assert_eq!(r.borrow().attempts,vec![None,Some(7)]);assert_eq!(polls.get(),3);assert!(f.is_terminated());
    }
    #[test]
    fn close_error_does_not_mark_terminated() {
        let(s,polls)=input(vec![Poll::Ready(None)]);let(mut out,r)=output();out.closes.push_back(Poll::Ready(Err("close")));let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("close")));assert!(!f.is_terminated());assert_eq!(polls.get(),1);assert_eq!(r.borrow().close,1);
    }
    #[test]
    fn close_pending_then_success_uses_fused_input() {
        let(s,polls)=input(vec![Poll::Ready(None)]);let(mut out,r)=output();out.closes.extend([Poll::Pending,Poll::Ready(Ok(()))]);let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(())));assert!(f.is_terminated());assert_eq!(polls.get(),1);assert_eq!(r.borrow().close,2);
    }
    #[test]
    fn readiness_error_returns_before_send_close_without_termination() {
        let(s,polls)=input(vec![ok(Some(1)),Poll::Ready(None)]);let(mut out,r)=output();out.ready.push_back(Poll::Ready(Err("ready")));let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("ready")));assert!(r.borrow().attempts.is_empty());assert_eq!(polls.get(),1);assert_eq!(r.borrow().close,0);assert!(!f.is_terminated());
    }
    #[test]
    fn send_error_returns_before_further_input_close_without_termination() {
        let(s,polls)=input(vec![ok(Some(1)),Poll::Ready(None)]);let(mut out,r)=output();out.sends.push_back(Err("send"));let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("send")));assert_eq!(r.borrow().attempts,vec![Some(1)]);assert_eq!(polls.get(),1);assert_eq!(r.borrow().close,0);assert!(!f.is_terminated());
    }
    #[test]
    fn pending_input_flush_error_does_not_close_or_terminate() {
        let(s,polls)=input(vec![Poll::Pending,Poll::Ready(None)]);let(mut out,r)=output();out.flushes.push_back(Poll::Ready(Err("flush")));let mut f=s.forward(out);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("flush")));assert_eq!(r.borrow().flush,1);assert_eq!(r.borrow().close,0);assert_eq!(polls.get(),1);assert!(!f.is_terminated());
    }
}
