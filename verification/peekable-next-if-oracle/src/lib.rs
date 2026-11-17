#[cfg(test)]
mod tests {
    use futures_util::{Stream,StreamExt,future::FusedFuture,stream::{self,FusedStream}};
    use std::{cell::Cell,future::Future,panic::{catch_unwind,AssertUnwindSafe},pin::Pin,rc::Rc,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    #[test] fn accepted_nullable_calls_and_consumes() {
        let mut s=stream::iter(vec![None,Some(7)]).peekable();let calls=Cell::new(0);let w=waker();let mut cx=Context::from_waker(&w);
        {let mut p=Pin::new(&mut s);let mut f=p.as_mut().next_if(|x|{calls.set(calls.get()+1);assert_eq!(*x,None);true});assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(None)));assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());}
        assert_eq!(calls.get(),1);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(7))));
    }
    #[test] fn rejected_nullable_calls_and_restores() {
        let mut s=stream::iter(vec![None,Some(7)]).peekable();let calls=Cell::new(0);let w=waker();let mut cx=Context::from_waker(&w);
        {let mut p=Pin::new(&mut s);let mut f=p.as_mut().next_if(|x|{calls.set(calls.get()+1);assert_eq!(*x,None);false});assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(None));}
        assert_eq!(calls.get(),1);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(None)));assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(7))));
    }
    #[test] fn next_if_eq_consumes_null() {
        let mut s=stream::iter(vec![None,Some(7)]).peekable();let w=waker();let mut cx=Context::from_waker(&w);
        {let mut p=Pin::new(&mut s);let mut f=p.as_mut().next_if_eq(&None);assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Some(None)));}
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(7))));
    }
    #[test] fn predicate_panic_consumes_and_terminates() {
        let mut s=stream::iter(vec![Some(1),Some(2)]).peekable();let calls=Cell::new(0);let w=waker();let mut cx=Context::from_waker(&w);
        {let mut p=Pin::new(&mut s);let mut f=p.as_mut().next_if(|_|{calls.set(calls.get()+1);panic!("predicate")});assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());assert!(f.is_terminated());assert!(catch_unwind(AssertUnwindSafe(||Pin::new(&mut f).poll(&mut cx))).is_err());}
        assert_eq!(calls.get(),1);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(2))));
    }
    struct Input {polls:Rc<Cell<usize>>,pending_first:bool}
    impl Stream for Input {
        type Item=Option<i32>;
        fn poll_next(self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<Option<Self::Item>> {let n=self.polls.get()+1;self.polls.set(n);if self.pending_first {if n==1 {cx.waker().wake_by_ref();Poll::Pending}else{Poll::Ready(Some(Some(3)))}}else{assert_eq!(n,1,"input polled after end");Poll::Ready(None)}}
    }
    #[test] fn pending_skips_predicate_then_rejection_restores() {
        let polls=Rc::new(Cell::new(0));let mut s=Input{polls:polls.clone(),pending_first:true}.peekable();let calls=Cell::new(0);let w=waker();let mut cx=Context::from_waker(&w);
        {let mut p=Pin::new(&mut s);let mut f=p.as_mut().next_if(|_|{calls.set(calls.get()+1);false});assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert_eq!(calls.get(),0);assert_eq!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(None));}
        assert_eq!(calls.get(),1);assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(Some(Some(3))));assert_eq!(polls.get(),2);
    }
    #[test] fn ended_input_is_fused_across_peeks() {
        let polls=Rc::new(Cell::new(0));let mut s=Input{polls:polls.clone(),pending_first:false}.peekable();let w=waker();let mut cx=Context::from_waker(&w);
        for _ in 0..2 {assert_eq!(Pin::new(&mut s).poll_peek(&mut cx),Poll::Ready(None));}
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx),Poll::Ready(None));assert!(s.is_terminated());assert_eq!(s.size_hint(),(0,Some(0)));assert_eq!(polls.get(),1);
    }
}
