#[cfg(test)]
mod tests {
    use futures_util::{future::FusedFuture, FutureExt, Stream, TryStreamExt};
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
    type Error = Option<&'static str>;
    enum Step {
        Values(Vec<Option<i32>>),
        Error(Error),
        Pending,
        End,
        Panic,
    }
    #[derive(Debug, PartialEq)]
    enum Outcome {
        Pending,
        Ok(Vec<Option<i32>>),
        Err(Error),
        Panic(String),
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    struct Input {
        events: VecDeque<Step>,
        polls: Rc<Cell<usize>>,
    }
    impl Stream for Input {
        type Item = Result<Vec<Option<i32>>, Error>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            match self.events.pop_front().expect("unexpected input poll") {
                Step::Values(v) => Poll::Ready(Some(Ok(v))),
                Step::Error(e) => Poll::Ready(Some(Err(e))),
                Step::Pending => Poll::Pending,
                Step::End => Poll::Ready(None),
                Step::Panic => panic!("stream panic"),
            }
        }
    }
    fn check(events: Vec<Step>, expected: Vec<Outcome>, expected_polls: usize) {
        let polls = Rc::new(Cell::new(0));
        let input = Input {
            events: events.into(),
            polls: polls.clone(),
        };
        let mut future = input.try_concat();
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        let mut actual = vec![];
        for _ in &expected {
            actual.push(
                match catch_unwind(AssertUnwindSafe(|| Pin::new(&mut future).poll(&mut cx))) {
                    Ok(Poll::Pending) => Outcome::Pending,
                    Ok(Poll::Ready(Ok(v))) => Outcome::Ok(v),
                    Ok(Poll::Ready(Err(e))) => Outcome::Err(e),
                    Err(e) => {
                        let message = if let Some(s) = e.downcast_ref::<&str>() {
                            s.to_string()
                        } else {
                            e.downcast_ref::<String>().unwrap().clone()
                        };
                        Outcome::Panic(message)
                    }
                },
            );
        }
        assert_eq!(actual, expected);
        assert_eq!(polls.get(), expected_polls);
    }
    #[test]
    #[allow(non_snake_case)]
    fn emptyAndRepeat() {
        check(
            vec![Step::End, Step::End],
            vec![Outcome::Ok(vec![]), Outcome::Ok(vec![])],
            2,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn completedAccumulatorTaken() {
        check(
            vec![
                Step::Values(vec![Some(1), Some(2)]),
                Step::Values(vec![Some(3)]),
                Step::End,
                Step::End,
            ],
            vec![
                Outcome::Ok(vec![Some(1), Some(2), Some(3)]),
                Outcome::Ok(vec![]),
            ],
            4,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn errorRetainsAccumulation() {
        check(
            vec![
                Step::Values(vec![Some(1)]),
                Step::Error(Some("fail")),
                Step::Values(vec![Some(2)]),
                Step::End,
                Step::End,
            ],
            vec![
                Outcome::Err(Some("fail")),
                Outcome::Ok(vec![Some(1), Some(2)]),
                Outcome::Ok(vec![]),
            ],
            5,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn pendingPreservesAccumulation() {
        check(
            vec![
                Step::Values(vec![Some(1)]),
                Step::Pending,
                Step::Values(vec![Some(2)]),
                Step::End,
                Step::End,
            ],
            vec![
                Outcome::Pending,
                Outcome::Ok(vec![Some(1), Some(2)]),
                Outcome::Ok(vec![]),
            ],
            5,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn nullableItemsAndError() {
        check(
            vec![
                Step::Values(vec![None]),
                Step::Error(None),
                Step::Values(vec![Some(1)]),
                Step::End,
                Step::End,
            ],
            vec![
                Outcome::Err(None),
                Outcome::Ok(vec![None, Some(1)]),
                Outcome::Ok(vec![]),
            ],
            5,
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn panicRetainsAccumulation() {
        check(
            vec![
                Step::Values(vec![Some(1)]),
                Step::Panic,
                Step::Values(vec![Some(2)]),
                Step::End,
                Step::End,
            ],
            vec![
                Outcome::Panic("stream panic".into()),
                Outcome::Ok(vec![Some(1), Some(2)]),
                Outcome::Ok(vec![]),
            ],
            5,
        );
    }
    #[test]
    fn canonical_fuse() {
        let mut f = futures_util::stream::iter(vec![Ok::<Vec<i32>, Error>(vec![1])])
            .try_concat()
            .fuse();
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        assert!(!f.is_terminated());
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(Ok(vec![1])));
        assert!(f.is_terminated());
        assert!(Pin::new(&mut f).poll(&mut cx).is_pending());
    }
}
