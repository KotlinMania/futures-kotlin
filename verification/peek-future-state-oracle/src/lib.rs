#[cfg(test)]
mod tests {
    use futures_util::{future::FusedFuture, stream::FusedStream, Stream, StreamExt};
    use std::{
        cell::Cell,
        collections::VecDeque,
        future::Future,
        panic::{catch_unwind, AssertUnwindSafe},
        pin::Pin,
        rc::Rc,
        sync::Arc,
        task::{Context, Poll, Wake, Waker},
    };
    enum Step {
        Value(Option<i32>),
        Pending,
        End,
        Panic,
    }
    struct Input {
        events: VecDeque<Step>,
        polls: Rc<Cell<usize>>,
    }
    impl Stream for Input {
        type Item = Option<i32>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            match self.events.pop_front().expect("unexpected producer poll") {
                Step::Value(v) => Poll::Ready(Some(v)),
                Step::Pending => Poll::Pending,
                Step::End => Poll::Ready(None),
                Step::Panic => panic!("source panic"),
            }
        }
    }
    fn input(events: Vec<Step>) -> (Input, Rc<Cell<usize>>) {
        let polls = Rc::new(Cell::new(0));
        (
            Input {
                events: events.into(),
                polls: polls.clone(),
            },
            polls,
        )
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    fn waker() -> Waker {
        Waker::from(Arc::new(Noop))
    }
    fn message(err: Box<dyn std::any::Any + Send>) -> String {
        if let Some(s) = err.downcast_ref::<&str>() {
            s.to_string()
        } else {
            err.downcast_ref::<String>().unwrap().clone()
        }
    }
    #[test]
    fn ready_value_is_cached_and_future_is_one_shot() {
        let (input, polls) = input(vec![Step::Value(Some(7))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(7)))
        );
        let err = catch_unwind(AssertUnwindSafe(|| {
            let _ = Pin::new(&mut future).poll(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(message(err), "Peek polled after completion");
        drop(future);
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(Some(7)))
        );
        assert_eq!(polls.get(), 1);
    }
    #[test]
    fn lifecycle_is_per_future() {
        let (input, polls) = input(vec![Step::Value(Some(7))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        assert!(!future.is_terminated());
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(7)))
        );
        assert!(future.is_terminated());
        drop(future);
        assert!(!stream.is_terminated());
        let mut second = Pin::new(&mut stream).peek();
        assert!(!second.is_terminated());
        assert_eq!(
            Pin::new(&mut second).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(7)))
        );
        assert!(second.is_terminated());
        drop(second);
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(Some(7)))
        );
        assert_eq!(polls.get(), 1);
    }
    #[test]
    fn pending_retains_receiver() {
        let (input, polls) = input(vec![Step::Pending, Step::Value(Some(9))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        assert!(!future.is_terminated());
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Pending
        );
        assert!(!future.is_terminated());
        assert_eq!(polls.get(), 1);
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(9)))
        );
        assert!(future.is_terminated());
        drop(future);
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(Some(9)))
        );
        assert_eq!(polls.get(), 2);
    }
    #[test]
    fn eof_completes_and_fuses_source() {
        let (input, polls) = input(vec![Step::End, Step::Value(Some(99))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        assert!(!future.is_terminated());
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(None)
        );
        assert!(future.is_terminated());
        drop(future);
        assert!(stream.is_terminated());
        let mut second = Pin::new(&mut stream).peek();
        assert!(!second.is_terminated());
        assert_eq!(
            Pin::new(&mut second).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(None)
        );
        assert!(second.is_terminated());
        drop(second);
        assert_eq!(Pin::new(&mut stream).poll_next(&mut cx), Poll::Ready(None));
        assert_eq!(polls.get(), 1);
    }
    #[test]
    fn nullable_item_remains_distinct_and_queued() {
        let (input, polls) = input(vec![Step::Value(None), Step::End]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(None))
        );
        assert!(future.is_terminated());
        drop(future);
        assert!(!stream.is_terminated());
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(None))
        );
        assert_eq!(polls.get(), 1);
        assert_eq!(Pin::new(&mut stream).poll_next(&mut cx), Poll::Ready(None));
        assert_eq!(polls.get(), 2);
    }
    #[test]
    fn source_panic_does_not_take_receiver() {
        let (input, polls) = input(vec![Step::Panic, Step::Value(Some(8))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        let err = catch_unwind(AssertUnwindSafe(|| {
            let _ = Pin::new(&mut future).poll(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(message(err), "source panic");
        assert!(!future.is_terminated());
        assert_eq!(polls.get(), 1);
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(8)))
        );
        assert!(future.is_terminated());
        drop(future);
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(Some(8)))
        );
        assert_eq!(polls.get(), 2);
    }
    #[test]
    fn cancelling_pending_allows_new_peek() {
        let (input, polls) = input(vec![Step::Pending, Step::Value(Some(6))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut future = Pin::new(&mut stream).peek();
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Pending
        );
        drop(future);
        let mut second = Pin::new(&mut stream).peek();
        assert_eq!(
            Pin::new(&mut second).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(6)))
        );
        drop(second);
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(Some(6)))
        );
        assert_eq!(polls.get(), 2);
    }
    #[test]
    fn already_cached_item_needs_no_new_source_poll() {
        let (input, polls) = input(vec![Step::Value(Some(5))]);
        let mut stream = input.peekable();
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut stream).poll_peek(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(5)))
        );
        let mut future = Pin::new(&mut stream).peek();
        assert!(!future.is_terminated());
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx).map(|v| v.copied()),
            Poll::Ready(Some(Some(5)))
        );
        assert!(future.is_terminated());
        drop(future);
        assert_eq!(
            Pin::new(&mut stream).poll_next(&mut cx),
            Poll::Ready(Some(Some(5)))
        );
        assert_eq!(polls.get(), 1);
    }
}
