#[cfg(test)]
mod tests {
    use futures_util::{stream::FusedStream, Stream, StreamExt};
    use std::{
        cell::Cell,
        collections::VecDeque,
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
    struct Input {
        events: VecDeque<Step>,
        polls: Rc<Cell<usize>>,
        unknown: bool,
    }
    impl Stream for Input {
        type Item = Option<i32>;
        fn poll_next(mut self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<Self::Item>> {
            self.polls.set(self.polls.get() + 1);
            match self.events.pop_front().expect("unexpected producer poll") {
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
                .filter(|x| matches!(x, Step::Value(_)))
                .count();
            (n, Some(n))
        }
    }
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    #[derive(Debug, PartialEq)]
    struct Snapshot {
        outcome: Poll<Option<Option<i32>>>,
        polls: usize,
        hint: (usize, Option<usize>),
    }
    fn check(
        skip: usize,
        events: Vec<Step>,
        unknown: bool,
        initial: (usize, Option<usize>),
        expected: Vec<Snapshot>,
    ) {
        let polls = Rc::new(Cell::new(0));
        let input = Input {
            events: events.into(),
            polls: polls.clone(),
            unknown,
        };
        let mut stream = input.skip(skip);
        assert_eq!(stream.size_hint(), initial);
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        let mut actual = vec![];
        for _ in &expected {
            let outcome = Pin::new(&mut stream).poll_next(&mut cx);
            actual.push(Snapshot {
                outcome,
                polls: polls.get(),
                hint: stream.size_hint(),
            });
        }
        assert_eq!(actual, expected);
    }
    #[test]
    #[allow(non_snake_case)]
    fn zeroSkipPassThrough() {
        check(
            0,
            vec![
                Step::Value(None),
                Step::Pending,
                Step::Value(Some(2)),
                Step::End,
            ],
            false,
            (2, Some(2)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(Some(None)),
                    polls: 1,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Pending,
                    polls: 2,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(Some(2))),
                    polls: 3,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 4,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn exhaustedSkipPassThrough() {
        check(
            2,
            vec![
                Step::Value(Some(1)),
                Step::Value(Some(2)),
                Step::Value(Some(3)),
                Step::Value(None),
                Step::End,
            ],
            false,
            (2, Some(2)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(Some(Some(3))),
                    polls: 3,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(None)),
                    polls: 4,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 5,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn finiteShortSource() {
        check(
            3,
            vec![Step::Value(Some(1)), Step::End, Step::End],
            false,
            (0, Some(0)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 2,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 3,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn countSurvivesEnd() {
        check(
            3,
            vec![
                Step::Value(Some(10)),
                Step::End,
                Step::Value(Some(20)),
                Step::Value(Some(30)),
                Step::Value(Some(40)),
                Step::End,
            ],
            false,
            (1, Some(1)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 2,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(Some(40))),
                    polls: 5,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 6,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn repeatedEndBeforeItems() {
        check(
            2,
            vec![
                Step::End,
                Step::End,
                Step::Value(Some(1)),
                Step::Value(Some(2)),
                Step::Value(Some(3)),
                Step::End,
            ],
            false,
            (1, Some(1)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 1,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 2,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(Some(3))),
                    polls: 5,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 6,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn pendingDoesNotDecrement() {
        check(
            3,
            vec![
                Step::Value(Some(1)),
                Step::End,
                Step::Pending,
                Step::Value(Some(2)),
                Step::Pending,
                Step::Value(Some(3)),
                Step::Value(Some(4)),
                Step::End,
            ],
            false,
            (1, Some(1)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 2,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Pending,
                    polls: 3,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Pending,
                    polls: 5,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(Some(4))),
                    polls: 7,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 8,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn nullableItemsCountAsValues() {
        check(
            2,
            vec![
                Step::Value(None),
                Step::End,
                Step::Value(None),
                Step::Value(None),
                Step::End,
            ],
            false,
            (1, Some(1)),
            vec![
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 2,
                    hint: (1, Some(1)),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(None)),
                    polls: 4,
                    hint: (0, Some(0)),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 5,
                    hint: (0, Some(0)),
                },
            ],
        );
    }
    #[test]
    #[allow(non_snake_case)]
    fn unknownHintKeepsCounter() {
        check(
            2,
            vec![
                Step::Value(Some(1)),
                Step::End,
                Step::Value(Some(2)),
                Step::Value(Some(3)),
                Step::End,
            ],
            true,
            (0, None),
            vec![
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 2,
                    hint: (0, None),
                },
                Snapshot {
                    outcome: Poll::Ready(Some(Some(3))),
                    polls: 4,
                    hint: (0, None),
                },
                Snapshot {
                    outcome: Poll::Ready(None),
                    polls: 5,
                    hint: (0, None),
                },
            ],
        );
    }
    #[test]
    fn canonical_fuse_stops_after_end() {
        let polls = Rc::new(Cell::new(0));
        let input = Input {
            events: vec![
                Step::Value(Some(1)),
                Step::End,
                Step::Value(Some(2)),
                Step::Value(Some(3)),
            ]
            .into(),
            polls: polls.clone(),
            unknown: false,
        };
        let mut stream = input.skip(3).fuse();
        let w = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&w);
        assert!(!stream.is_terminated());
        assert_eq!(Pin::new(&mut stream).poll_next(&mut cx), Poll::Ready(None));
        assert!(stream.is_terminated());
        assert_eq!(stream.size_hint(), (0, Some(0)));
        assert_eq!(Pin::new(&mut stream).poll_next(&mut cx), Poll::Ready(None));
        assert_eq!(polls.get(), 2);
    }
}
