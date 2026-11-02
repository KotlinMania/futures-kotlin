#[cfg(test)]
mod tests {
    use futures_util::future::select_all;
    use std::{cell::RefCell, future::Future, pin::Pin, rc::Rc, sync::Arc, task::{Context, Poll, Wake, Waker}};
    struct Noop;
    impl Wake for Noop { fn wake(self: Arc<Self>) {} }
    struct Step { id: usize, value: Option<i32>, ready: bool, trace: Rc<RefCell<Vec<usize>>> }
    impl Future for Step {
        type Output = Option<i32>;
        fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output> {
            self.trace.borrow_mut().push(self.id);
            if self.ready { Poll::Ready(self.value) } else { cx.waker().wake_by_ref(); Poll::Pending }
        }
    }
    fn step(id:usize, ready:bool, trace:&Rc<RefCell<Vec<usize>>>) -> Step {
        Step { id, value:Some(id as i32), ready, trace:trace.clone() }
    }
    fn context_waker() -> Waker { Waker::from(Arc::new(Noop)) }
    #[test]
    fn first_winner_uses_swap_removal() {
        let trace=Rc::new(RefCell::new(vec![]));let w=context_waker();let mut cx=Context::from_waker(&w);
        let mut f=select_all((0..4).map(|id|step(id,true,&trace)));
        let Poll::Ready((value,index,rest))=Pin::new(&mut f).poll(&mut cx) else {panic!("pending")};
        assert_eq!(value,Some(0));assert_eq!(index,0);assert_eq!(*trace.borrow(),vec![0]);
        assert_eq!(rest.iter().map(|s|s.id).collect::<Vec<_>>(),vec![3,1,2]);
    }
    #[test]
    fn middle_winner_preserves_poll_order_and_swaps_last() {
        let trace=Rc::new(RefCell::new(vec![]));let w=context_waker();let mut cx=Context::from_waker(&w);
        let mut f=select_all((0..4).map(|id|step(id,id==1,&trace)));
        let Poll::Ready((value,index,rest))=Pin::new(&mut f).poll(&mut cx) else {panic!("pending")};
        assert_eq!(value,Some(1));assert_eq!(index,1);assert_eq!(*trace.borrow(),vec![0,1]);
        assert_eq!(rest.iter().map(|s|s.id).collect::<Vec<_>>(),vec![0,3,2]);
    }
    #[test]
    fn success_transfers_remaining_futures() {
        let trace=Rc::new(RefCell::new(vec![]));let w=context_waker();let mut cx=Context::from_waker(&w);
        let mut f=select_all(vec![step(0,false,&trace),Step{value:Some(7),..step(1,true,&trace)}]);
        let Poll::Ready((value,index,rest))=Pin::new(&mut f).poll(&mut cx) else {panic!("pending")};
        assert_eq!(value,Some(7));assert_eq!(index,1);assert_eq!(rest.len(),1);assert_eq!(rest[0].id,0);
        assert!(f.into_inner().is_empty());
    }
    #[test]
    fn nullable_ready_value_remains_ready() {
        let trace=Rc::new(RefCell::new(vec![]));let w=context_waker();let mut cx=Context::from_waker(&w);
        let mut f=select_all(vec![Step{value:None,..step(0,true,&trace)}]);
        let Poll::Ready((value,index,rest))=Pin::new(&mut f).poll(&mut cx) else {panic!("pending")};
        assert_eq!(value,None);assert_eq!(index,0);assert!(rest.is_empty());
    }
    #[test]
    fn pending_scan_polls_each_once_and_keeps_all() {
        let trace=Rc::new(RefCell::new(vec![]));let w=context_waker();let mut cx=Context::from_waker(&w);
        let mut f=select_all((0..3).map(|id|step(id,false,&trace)));
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(*trace.borrow(),vec![0,1,2]);
        assert_eq!(f.into_inner().iter().map(|s|s.id).collect::<Vec<_>>(),vec![0,1,2]);
    }
}
