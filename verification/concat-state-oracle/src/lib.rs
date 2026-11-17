#[cfg(test)]
mod tests {
    use futures_util::{
        future::FusedFuture,
        stream::{self, FusedStream},
        Stream, StreamExt,
    };
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
        Values(Vec<Option<i32>>),
        Pending,
        InactivePending,
        End,
        Panic,
    }
    struct Input {
        events: VecDeque<Step>,
        terminated: bool,
        polls: Rc<Cell<usize>>,
    }
    impl Stream for Input {
        type Item = Vec<Option<i32>>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            match self.events.pop_front().unwrap_or(Step::End) {
                Step::Values(v) => Poll::Ready(Some(v)),
                Step::Pending => Poll::Pending,
                Step::InactivePending => {
                    self.terminated = true;
                    Poll::Pending
                }
                Step::End => {
                    self.terminated = true;
                    Poll::Ready(None)
                }
                Step::Panic => panic!("source panic"),
            }
        }
    }
    impl FusedStream for Input {
        fn is_terminated(&self) -> bool {
            self.terminated
        }
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    fn waker() -> Waker {
        Waker::from(Arc::new(Noop))
    }
    #[derive(Debug, PartialEq)]
    enum Outcome {
        Ready(Vec<Option<i32>>),
        Pending,
        Panic(String),
    }
    #[derive(Debug, PartialEq)]
    struct Snapshot {
        outcome: Outcome,
        polls: usize,
        terminated: bool,
    }
    fn check(events: Vec<Step>, expected: Vec<Snapshot>) {
        let polls = Rc::new(Cell::new(0));
        let s = Input {
            events: events.into(),
            terminated: false,
            polls: polls.clone(),
        };
        let mut f = s.concat();
        assert!(!f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        let mut actual = vec![];
        for _ in &expected {
            let outcome = match catch_unwind(AssertUnwindSafe(|| Pin::new(&mut f).poll(&mut cx))) {
                Ok(Poll::Ready(v)) => Outcome::Ready(v),
                Ok(Poll::Pending) => Outcome::Pending,
                Err(e) => Outcome::Panic(if let Some(s) = e.downcast_ref::<&str>() {
                    s.to_string()
                } else {
                    e.downcast_ref::<String>().unwrap().clone()
                }),
            };
            actual.push(Snapshot {
                outcome,
                polls: polls.get(),
                terminated: f.is_terminated(),
            });
        }
        assert_eq!(actual, expected);
    }
    #[test]
    fn empty_source_before_poll() {
        let mut f = stream::empty::<Vec<Option<i32>>>().concat();
        assert!(f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Ready(vec![]));
        assert!(f.is_terminated());
    }
    #[test]
    fn inactive_pending_source_without_accumulator() {
        let mut f = stream::pending::<Vec<Option<i32>>>().concat();
        assert!(f.is_terminated());
        let w = waker();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut f).poll(&mut cx), Poll::Pending);
        assert!(f.is_terminated());
    }
    #[test]
    fn pending_preserves_nullable_chunks() {
        check(
            vec![
                Step::Values(vec![Some(1), None]),
                Step::Pending,
                Step::Values(vec![Some(2)]),
                Step::End,
            ],
            vec![
                Snapshot {
                    outcome: Outcome::Pending,
                    polls: 2,
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Ready(vec![Some(1), None, Some(2)]),
                    polls: 4,
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    fn some_empty_accumulator_is_not_none() {
        check(
            vec![Step::Values(vec![]), Step::InactivePending],
            vec![Snapshot {
                outcome: Outcome::Pending,
                polls: 2,
                terminated: false,
            }],
        );
    }
    #[test]
    fn completed_accumulator_is_taken() {
        check(
            vec![Step::Values(vec![Some(1), None]), Step::End, Step::End],
            vec![
                Snapshot {
                    outcome: Outcome::Ready(vec![Some(1), None]),
                    polls: 2,
                    terminated: true,
                },
                Snapshot {
                    outcome: Outcome::Ready(vec![]),
                    polls: 3,
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    fn source_panic_retains_accumulator() {
        check(
            vec![
                Step::Values(vec![Some(1)]),
                Step::Panic,
                Step::Values(vec![Some(2)]),
                Step::End,
                Step::End,
            ],
            vec![
                Snapshot {
                    outcome: Outcome::Panic("source panic".into()),
                    polls: 2,
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Ready(vec![Some(1), Some(2)]),
                    polls: 4,
                    terminated: true,
                },
                Snapshot {
                    outcome: Outcome::Ready(vec![]),
                    polls: 5,
                    terminated: true,
                },
            ],
        );
    }
}
