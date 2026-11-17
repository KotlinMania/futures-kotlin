#[cfg(test)]
mod tests {
    use futures_util::future::select_ok;
    use std::{cell::RefCell,rc::Rc,pin::Pin,future::Future,sync::Arc,task::{Context,Poll,Wake,Waker}};

    struct Noop;
    impl Wake for Noop { fn wake(self: Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    struct Step { id: usize, pending: usize, result: Option<Result<Option<i32>,Option<&'static str>>>, trace: Rc<RefCell<Vec<usize>>> }
    impl Future for Step {
        type Output = Result<Option<i32>,Option<&'static str>>;
        fn poll(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output> {
            self.trace.borrow_mut().push(self.id);
            if self.pending>0 { self.pending-=1;cx.waker().wake_by_ref();return Poll::Pending; }
            Poll::Ready(self.result.take().expect("fixture polled after completion"))
        }
    }
    fn step(id:usize,pending:usize,result:Result<Option<i32>,Option<&'static str>>,trace:&Rc<RefCell<Vec<usize>>>) -> Step {
        Step {id,pending,result:Some(result),trace:trace.clone()}
    }

    #[test]
    fn error_restarts_scan_before_later_success() {
        let trace = Rc::new(RefCell::new(Vec::new()));let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=select_ok(vec![step(0,1,Ok(Some(7)),&trace),step(1,0,Err(Some("discard")),&trace),step(2,0,Ok(Some(99)),&trace)]);
        let Poll::Ready(Ok((value,rest)))=Pin::new(&mut f).poll(&mut cx) else {panic!("not success")};
        assert_eq!(value,Some(7));assert_eq!(*trace.borrow(),vec![0,1,0]);
        assert_eq!(rest.iter().map(|f|f.id).collect::<Vec<_>>(),vec![2]);
    }

    #[test]
    fn earlier_pending_future_is_repolled_after_error() {
        let trace = Rc::new(RefCell::new(Vec::new()));let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=select_ok(vec![step(0,2,Ok(Some(7)),&trace),step(1,0,Err(Some("discard")),&trace),step(2,1,Ok(Some(99)),&trace)]);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*trace.borrow(),vec![0,1,0,2]);
        let Poll::Ready(Ok((value,rest)))=Pin::new(&mut f).poll(&mut cx) else {panic!("not success")};
        assert_eq!(value,Some(7));assert_eq!(rest.iter().map(|f|f.id).collect::<Vec<_>>(),vec![2]);
    }

    #[test]
    fn concrete_completed_combinator_does_not_repoll_returned_futures() {
        let trace = Rc::new(RefCell::new(Vec::new()));let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=select_ok(vec![step(0,0,Ok(Some(7)),&trace),step(1,1,Ok(Some(99)),&trace)]);
        assert!(Pin::new(&mut f).poll(&mut cx).is_ready());
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*trace.borrow(),vec![0]);
    }

    #[test]
    fn nullable_result_payloads_remain_tagged() {
        let trace = Rc::new(RefCell::new(Vec::new()));let w=waker();let mut cx=Context::from_waker(&w);
        let mut success=select_ok(vec![step(0,0,Err(None),&trace),step(1,0,Ok(None),&trace)]);
        let Poll::Ready(Ok((value,rest)))=Pin::new(&mut success).poll(&mut cx) else {panic!("not success")};
        assert_eq!(value,None);assert!(rest.is_empty());
        let mut failure=select_ok(vec![step(0,0,Err(Some("first")),&trace),step(1,0,Err(None),&trace)]);
        let Poll::Ready(Err(value))=Pin::new(&mut failure).poll(&mut cx) else {panic!("not error")};assert_eq!(value,None);
    }

    #[test]
    fn entirely_pending_scan_polls_each_once() {
        let trace = Rc::new(RefCell::new(Vec::new()));let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=select_ok(vec![step(0,1,Ok(Some(7)),&trace),step(1,1,Ok(Some(99)),&trace)]);
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*trace.borrow(),vec![0,1]);
    }
}
