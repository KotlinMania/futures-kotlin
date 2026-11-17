#[cfg(test)]
mod tests {
    use futures_util::{stream::FusedStream, Stream, StreamExt};
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
    enum Step {
        Value(Option<i32>),
        Pending,
        End,
    }
    enum TaskStep {
        Ready(Option<i32>),
        Pending,
        Panic,
    }
    struct Input {
        events: VecDeque<Step>,
        polls: Rc<Cell<usize>>,
        unknown: bool,
    }
    impl Stream for Input {
        type Item = Option<i32>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            match self.events.pop_front().unwrap_or(Step::End) {
                Step::Value(v) => Poll::Ready(Some(v)),
                Step::Pending => Poll::Pending,
                Step::End => Poll::Ready(None),
            }
        }
        fn size_hint(&self) -> (usize, Option<usize>) {
            if self.unknown {
                return (0, None);
            }
            let n = self
                .events
                .iter()
                .filter(|e| matches!(e, Step::Value(_)))
                .count();
            (n, Some(n))
        }
    }
    impl FusedStream for Input {
        fn is_terminated(&self) -> bool {
            self.events.is_empty()
        }
    }
    struct Task {
        events: VecDeque<TaskStep>,
        polls: Rc<RefCell<Vec<usize>>>,
        index: usize,
    }
    impl Future for Task {
        type Output = Option<i32>;
        fn poll(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Self::Output> {
            self.polls.borrow_mut()[self.index] += 1;
            match self.events.pop_front().expect("unexpected callback poll") {
                TaskStep::Ready(v) => Poll::Ready(v),
                TaskStep::Pending => Poll::Pending,
                TaskStep::Panic => panic!("callback panic"),
            }
        }
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    #[derive(Debug, PartialEq)]
    enum Outcome {
        Value(i32),
        Pending,
        End,
        Panic(String),
    }
    #[derive(Debug, PartialEq)]
    struct Snapshot {
        outcome: Outcome,
        source_polls: usize,
        task_polls: Vec<usize>,
        inputs: Vec<Option<i32>>,
        hint: (usize, Option<usize>),
        terminated: bool,
    }
    fn check(events: Vec<Step>, plans: Vec<Vec<TaskStep>>, unknown: bool, expected: Vec<Snapshot>) {
        let source_polls = Rc::new(Cell::new(0));
        let task_polls = Rc::new(RefCell::new(vec![]));
        let inputs = Rc::new(RefCell::new(vec![]));
        let input = Input {
            events: events.into(),
            polls: source_polls.clone(),
            unknown,
        };
        let mut plans: VecDeque<_> = plans.into();
        let counts = task_polls.clone();
        let seen = inputs.clone();
        let mut stream = input.filter_map(move |item| {
            seen.borrow_mut().push(item);
            let index = counts.borrow().len();
            counts.borrow_mut().push(0);
            Task {
                events: plans.pop_front().expect("unexpected transform").into(),
                polls: counts.clone(),
                index,
            }
        });
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        let mut actual = vec![];
        for _ in &expected {
            let outcome = match catch_unwind(AssertUnwindSafe(|| {
                Pin::new(&mut stream).poll_next(&mut cx)
            })) {
                Ok(Poll::Pending) => Outcome::Pending,
                Ok(Poll::Ready(None)) => Outcome::End,
                Ok(Poll::Ready(Some(v))) => Outcome::Value(v),
                Err(e) => Outcome::Panic(if let Some(s) = e.downcast_ref::<&str>() {
                    s.to_string()
                } else {
                    e.downcast_ref::<String>().unwrap().clone()
                }),
            };
            actual.push(Snapshot {
                outcome,
                source_polls: source_polls.get(),
                task_polls: task_polls.borrow().clone(),
                inputs: inputs.borrow().clone(),
                hint: stream.size_hint(),
                terminated: stream.is_terminated(),
            });
        }
        assert_eq!(actual, expected);
    }
    #[test]
    #[allow(non_snake_case)]
    fn awaitWithoutReadingAhead() {
        check(
            vec![Step::Value(Some(1)), Step::Value(Some(2)), Step::End],
            vec![
                vec![TaskStep::Pending, TaskStep::Ready(Some(10))],
                vec![TaskStep::Ready(Some(20))],
            ],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Pending,
                    source_polls: 1,
                    task_polls: vec![1],
                    inputs: vec![Some(1)],
                    hint: (0, Some(2)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(10),
                    source_polls: 1,
                    task_polls: vec![2],
                    inputs: vec![Some(1)],
                    hint: (0, Some(1)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(20),
                    source_polls: 2,
                    task_polls: vec![2, 1],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(0)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 3,
                    task_polls: vec![2, 1],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn readyNoneSkipsInSamePoll() {
        check(
            vec![Step::Value(Some(1)), Step::Value(Some(2)), Step::End],
            vec![vec![TaskStep::Ready(None)], vec![TaskStep::Ready(Some(20))]],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Value(20),
                    source_polls: 2,
                    task_polls: vec![1, 1],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(0)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 3,
                    task_polls: vec![1, 1],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn pendingThenNoneKeepsOrder() {
        check(
            vec![Step::Value(Some(1)), Step::Value(Some(2)), Step::End],
            vec![
                vec![TaskStep::Pending, TaskStep::Ready(None)],
                vec![TaskStep::Pending, TaskStep::Ready(Some(20))],
            ],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Pending,
                    source_polls: 1,
                    task_polls: vec![1],
                    inputs: vec![Some(1)],
                    hint: (0, Some(2)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Pending,
                    source_polls: 2,
                    task_polls: vec![2, 1],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(1)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(20),
                    source_polls: 2,
                    task_polls: vec![2, 2],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(0)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 3,
                    task_polls: vec![2, 2],
                    inputs: vec![Some(1), Some(2)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn sourcePendingDelaysCallback() {
        check(
            vec![Step::Pending, Step::Value(Some(7)), Step::End],
            vec![vec![TaskStep::Ready(Some(70))]],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Pending,
                    source_polls: 1,
                    task_polls: vec![],
                    inputs: vec![],
                    hint: (0, Some(1)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(70),
                    source_polls: 2,
                    task_polls: vec![1],
                    inputs: vec![Some(7)],
                    hint: (0, Some(0)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 3,
                    task_polls: vec![1],
                    inputs: vec![Some(7)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn nullableInputsAndEmptyResult() {
        check(
            vec![Step::Value(None), Step::Value(Some(2)), Step::End],
            vec![vec![TaskStep::Ready(None)], vec![TaskStep::Ready(Some(2))]],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Value(2),
                    source_polls: 2,
                    task_polls: vec![1, 1],
                    inputs: vec![None, Some(2)],
                    hint: (0, Some(0)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 3,
                    task_polls: vec![1, 1],
                    inputs: vec![None, Some(2)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn pendingLastItemPreventsTermination() {
        check(
            vec![Step::Value(Some(1))],
            vec![vec![TaskStep::Pending, TaskStep::Ready(Some(10))]],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Pending,
                    source_polls: 1,
                    task_polls: vec![1],
                    inputs: vec![Some(1)],
                    hint: (0, Some(1)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(10),
                    source_polls: 1,
                    task_polls: vec![2],
                    inputs: vec![Some(1)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn unknownUpperRemainsUnknown() {
        check(
            vec![Step::Value(Some(1)), Step::End],
            vec![vec![TaskStep::Pending, TaskStep::Ready(Some(10))]],
            true,
            vec![
                Snapshot {
                    outcome: Outcome::Pending,
                    source_polls: 1,
                    task_polls: vec![1],
                    inputs: vec![Some(1)],
                    hint: (0, None),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(10),
                    source_polls: 1,
                    task_polls: vec![2],
                    inputs: vec![Some(1)],
                    hint: (0, None),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 2,
                    task_polls: vec![2],
                    inputs: vec![Some(1)],
                    hint: (0, None),
                    terminated: true,
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn emptySourceDoesNotCallTransform() {
        check(
            vec![Step::End],
            vec![],
            false,
            vec![Snapshot {
                outcome: Outcome::End,
                source_polls: 1,
                task_polls: vec![],
                inputs: vec![],
                hint: (0, Some(0)),
                terminated: true,
            }],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn futurePanicRetainsPending() {
        check(
            vec![Step::Value(Some(1)), Step::End],
            vec![vec![TaskStep::Panic, TaskStep::Ready(Some(10))]],
            false,
            vec![
                Snapshot {
                    outcome: Outcome::Panic("callback panic".into()),
                    source_polls: 1,
                    task_polls: vec![1],
                    inputs: vec![Some(1)],
                    hint: (0, Some(1)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::Value(10),
                    source_polls: 1,
                    task_polls: vec![2],
                    inputs: vec![Some(1)],
                    hint: (0, Some(0)),
                    terminated: false,
                },
                Snapshot {
                    outcome: Outcome::End,
                    source_polls: 2,
                    task_polls: vec![2],
                    inputs: vec![Some(1)],
                    hint: (0, Some(0)),
                    terminated: true,
                },
            ],
        );
    }
}
