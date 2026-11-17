#[cfg(test)]
mod tests {
    use futures_util::{Sink, SinkExt, Stream};
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
    fn repeated_pending_readiness_retains_null_before_next_item() {
        let(mut s,polls)=input(vec![ok(None),ok(Some(7)),Poll::Ready(None)]);let(mut out,r)=output();out.ready.extend([Poll::Pending,Poll::Pending,Poll::Ready(Ok(()))]);let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),1);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),1);assert!(r.borrow().attempts.is_empty());
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(())));assert_eq!(r.borrow().attempts,vec![None,Some(7)]);assert_eq!(polls.get(),3);assert_eq!(r.borrow().flush,1);assert_eq!(r.borrow().close,0);
    }
    #[test]
    fn readiness_error_on_buffered_null_precedes_further_input() {
        let(mut s,polls)=input(vec![ok(None),ok(Some(7)),Poll::Ready(None)]);let(mut out,r)=output();out.ready.extend([Poll::Pending,Poll::Ready(Err("ready"))]);let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("ready")));assert_eq!(polls.get(),1);assert!(r.borrow().attempts.is_empty());assert_eq!(r.borrow().flush,0);assert_eq!(r.borrow().close,0);
    }
    #[test]
    fn send_error_on_buffered_null_precedes_further_input() {
        let(mut s,polls)=input(vec![ok(None),ok(Some(7)),Poll::Ready(None)]);let(mut out,r)=output();out.ready.push_back(Poll::Pending);out.sends.push_back(Err("send"));let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("send")));assert_eq!(r.borrow().attempts,vec![None]);assert_eq!(polls.get(),1);assert_eq!(r.borrow().flush,0);assert_eq!(r.borrow().close,0);
    }
    #[test]
    fn eof_flush_pending_then_success_does_not_repoll_input_or_close() {
        let(mut s,polls)=input(vec![Poll::Ready(None),ok(Some(99))]);let(mut out,r)=output();out.flushes.extend([Poll::Pending,Poll::Ready(Ok(()))]);let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(())));assert_eq!(polls.get(),1);assert!(r.borrow().attempts.is_empty());assert_eq!(r.borrow().flush,2);assert_eq!(r.borrow().close,0);
    }
    #[test]
    fn pending_input_flushes_without_completing_then_ready_null_is_sent() {
        let(mut s,polls)=input(vec![Poll::Pending,ok(None),Poll::Ready(None)]);let(mut out,r)=output();let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(polls.get(),1);assert_eq!(r.borrow().flush,1);assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(())));assert_eq!(r.borrow().attempts,vec![None]);assert_eq!(polls.get(),3);assert_eq!(r.borrow().flush,2);assert_eq!(r.borrow().close,0);
    }
    #[test]
    fn input_error_after_send_returns_without_flush_or_close() {
        let(mut s,polls)=input(vec![ok(None),Poll::Ready(Some(Err("input")))]);let(mut out,r)=output();let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("input")));assert_eq!(r.borrow().attempts,vec![None]);assert_eq!(polls.get(),2);assert_eq!(r.borrow().flush,0);assert_eq!(r.borrow().close,0);
    }
    #[test]
    fn eof_flush_error_returns_without_closing_sink() {
        let(mut s,polls)=input(vec![Poll::Ready(None)]);let(mut out,r)=output();out.flushes.push_back(Poll::Ready(Err("flush")));let mut f=out.send_all(&mut s);let w=waker();let mut cx=Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Err("flush")));assert_eq!(polls.get(),1);assert_eq!(r.borrow().flush,1);assert_eq!(r.borrow().close,0);
    }
}
