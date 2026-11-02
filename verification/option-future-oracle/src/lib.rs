#[cfg(test)]
mod tests {
    use futures_util::future::{always_ready,ready,FusedFuture,OptionFuture};
    use std::{cell::Cell,future::Future,pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop { fn wake(self:Arc<Self>) {} }
    fn waker() -> Waker { Waker::from(Arc::new(Noop)) }
    #[test]
    fn repeatedly_polls_always_ready_producer() {
        let calls=Cell::new(0);let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=OptionFuture::from(Some(always_ready(||{calls.set(calls.get()+1);calls.get()})));
        for value in 1..=3 { assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(value))); }
        assert_eq!(calls.get(),3);
    }
    #[test]
    fn always_ready_does_not_become_terminated() {
        let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=OptionFuture::from(Some(always_ready(||7)));
        assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(7)));
        assert!(!f.is_terminated());
    }
    #[test]
    fn one_shot_termination_is_forwarded() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut f=OptionFuture::from(Some(ready(9)));
        assert!(!f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(9)));
        assert!(f.is_terminated());
    }
    struct Step { calls:usize, expected_waker:Waker }
    impl Future for Step {
        type Output=i32;
        fn poll(mut self:Pin<&mut Self>,cx:&mut Context<'_>) -> Poll<i32> {
            assert!(cx.waker().will_wake(&self.expected_waker));self.calls+=1;
            if self.calls==1 {cx.waker().wake_by_ref();Poll::Pending} else {Poll::Ready(12)}
        }
    }
    #[test]
    fn pending_poll_forwards_context() {
        let w=waker();let mut cx=Context::from_waker(&w);
        let mut f=OptionFuture::from(Some(Step{calls:0,expected_waker:w.clone()}));
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Pending);
        assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(12)));
    }
    #[test]
    fn absent_inner_remains_ready_and_terminated() {
        let w=waker();let mut cx=Context::from_waker(&w);let mut f:OptionFuture<futures_util::future::Ready<i32>>=None.into();
        for _ in 0..3 {assert!(f.is_terminated());assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(None));}
    }
}
