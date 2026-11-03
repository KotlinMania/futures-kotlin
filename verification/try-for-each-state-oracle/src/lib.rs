#[cfg(test)]
mod tests {
    use futures_util::{future::FusedFuture, FutureExt, Stream, TryStreamExt};
    use std::{
        cell::{Cell, RefCell},
        collections::VecDeque,
        future::Future,
        panic::{catch_unwind, AssertUnwindSafe},
        pin::Pin,
        rc::Rc,
        sync::Arc,
        task::{Context, Poll, Wake, Waker},
    };
    type Error = Option<&'static str>;
    #[derive(Clone)]
    enum ActionStep {
        Pending,
        Ok,
        Err(Error),
        Panic,
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    struct Input {
        events: VecDeque<Poll<Option<Result<i32, Error>>>>,
        polls: Rc<Cell<usize>>,
        trace: Rc<RefCell<Vec<String>>>,
    }
    impl Stream for Input {
        type Item = Result<i32, Error>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            self.trace
                .borrow_mut()
                .push(format!("stream:{}", self.polls.get()));
            self.events.pop_front().expect("unexpected stream poll")
        }
    }
    struct Action {
        events: VecDeque<ActionStep>,
        polls: Rc<Cell<usize>>,
        trace: Rc<RefCell<Vec<String>>>,
    }
    impl Future for Action {
        type Output = Result<(), Error>;
        fn poll(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Self::Output> {
            self.polls.set(self.polls.get() + 1);
            self.trace
                .borrow_mut()
                .push(format!("action:{}", self.polls.get()));
            match self.events.pop_front().expect("unexpected action poll") {
                ActionStep::Pending => Poll::Pending,
                ActionStep::Ok => Poll::Ready(Ok(())),
                ActionStep::Err(e) => Poll::Ready(Err(e)),
                ActionStep::Panic => panic!("action panic"),
            }
        }
    }
    fn error(e: Error) -> String {
        format!("err:{}", e.unwrap_or("null"))
    }
    fn check(
        input: Vec<Poll<Option<Result<i32, Error>>>>,
        actions: Vec<ActionStep>,
        expected: &[&str],
        events: &[&str],
        expected_stream: usize,
        expected_action: usize,
    ) {
        let trace = Rc::new(RefCell::new(vec![]));
        let stream_polls = Rc::new(Cell::new(0));
        let action_polls = Rc::new(Cell::new(0));
        let stream = Input {
            events: input.into(),
            polls: stream_polls.clone(),
            trace: trace.clone(),
        };
        let mut future = stream.try_for_each(|value| {
            trace.borrow_mut().push(format!("make:{value}"));
            Action {
                events: actions.clone().into(),
                polls: action_polls.clone(),
                trace: trace.clone(),
            }
        });
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        let mut actual = vec![];
        for expected_poll in expected {
            actual.push(
                match catch_unwind(AssertUnwindSafe(|| Pin::new(&mut future).poll(&mut cx))) {
                    Ok(Poll::Pending) => "pending".into(),
                    Ok(Poll::Ready(Ok(()))) => "ok".into(),
                    Ok(Poll::Ready(Err(e))) => {
                        let payload = expected_poll
                            .strip_prefix("err:")
                            .expect("expected typed error");
                        assert_eq!(
                            e,
                            if payload == "null" {
                                None
                            } else {
                                Some(payload)
                            }
                        );
                        error(e)
                    }
                    Err(e) => {
                        let message = if let Some(s) = e.downcast_ref::<&str>() {
                            s.to_string()
                        } else {
                            e.downcast_ref::<String>().unwrap().clone()
                        };
                        format!("panic:{message}")
                    }
                },
            );
        }
        assert_eq!(actual, expected);
        assert_eq!(*trace.borrow(), events);
        assert_eq!(stream_polls.get(), expected_stream);
        assert_eq!(action_polls.get(), expected_action);
    }
    #[test]
    #[allow(non_snake_case)]
    fn failedCallbackRetained() {
        check(
            vec![Poll::Ready(Some(Ok(1))), Poll::Ready(None)],
            vec![
                ActionStep::Err(Some("fail")),
                ActionStep::Err(Some("again")),
            ],
            &["err:fail", "err:again"],
            &["stream:1", "make:1", "action:1", "action:2"],
            1,
            2,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn errorPendingThenSuccess() {
        check(
            vec![Poll::Ready(Some(Ok(1))), Poll::Ready(None)],
            vec![
                ActionStep::Err(Some("fail")),
                ActionStep::Pending,
                ActionStep::Ok,
            ],
            &["err:fail", "pending", "ok"],
            &[
                "stream:1", "make:1", "action:1", "action:2", "action:3", "stream:2",
            ],
            2,
            3,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn failedFuturePanicOnRepeat() {
        check(
            vec![Poll::Ready(Some(Ok(1))), Poll::Ready(None)],
            vec![ActionStep::Err(Some("fail")), ActionStep::Panic],
            &["err:fail", "panic:action panic"],
            &["stream:1", "make:1", "action:1", "action:2"],
            1,
            2,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn nullableErrorAfterPending() {
        check(
            vec![Poll::Ready(Some(Ok(1))), Poll::Ready(None)],
            vec![
                ActionStep::Pending,
                ActionStep::Err(None),
                ActionStep::Err(None),
            ],
            &["pending", "err:null", "err:null"],
            &["stream:1", "make:1", "action:1", "action:2", "action:3"],
            1,
            3,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn streamErrorContinuation() {
        check(
            vec![
                Poll::Ready(Some(Err(Some("stream")))),
                Poll::Ready(Some(Ok(1))),
                Poll::Ready(None),
            ],
            vec![ActionStep::Ok],
            &["err:stream", "ok"],
            &["stream:1", "stream:2", "make:1", "action:1", "stream:3"],
            3,
            1,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn outerAndInnerPending() {
        check(
            vec![Poll::Pending, Poll::Ready(Some(Ok(1))), Poll::Ready(None)],
            vec![ActionStep::Pending, ActionStep::Ok],
            &["pending", "pending", "ok"],
            &[
                "stream:1", "stream:2", "make:1", "action:1", "action:2", "stream:3",
            ],
            3,
            2,
        );
    }
    #[test]
    fn canonical_fuse_after_error() {
        let mut future = futures_util::stream::iter(vec![Ok::<i32, Error>(1)])
            .try_for_each(|_| futures_util::future::ready(Err::<(), Error>(Some("fail"))))
            .fuse();
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        assert!(!future.is_terminated());
        assert_eq!(
            Pin::new(&mut future).poll(&mut cx),
            Poll::Ready(Err(Some("fail")))
        );
        assert!(future.is_terminated());
        assert!(Pin::new(&mut future).poll(&mut cx).is_pending());
    }
}
