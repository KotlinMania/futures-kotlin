#[cfg(test)]
mod tests {
    use futures_util::{stream, stream::FusedStream, Stream, StreamExt};
    use std::{
        cell::RefCell,
        collections::VecDeque,
        future::Future,
        pin::Pin,
        rc::Rc,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
        task::{Context, Poll, Wake, Waker},
    };
    type Step = Option<(Option<i32>, Option<i32>)>;
    type Record = Rc<RefCell<Vec<String>>>;
    struct Script {
        record: Record,
        events: VecDeque<Poll<Step>>,
        panic_first: bool,
    }
    impl Future for Script {
        type Output = Step;
        fn poll(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Step> {
            self.record.borrow_mut().push("future".into());
            if self.panic_first {
                self.panic_first = false;
                panic!("future panic");
            }
            let p = self.events.pop_front().expect("unexpected future repoll");
            if p.is_pending() {
                cx.waker().wake_by_ref();
            }
            p
        }
    }
    fn scripted(
        r: Record,
        state: Option<i32>,
        events: Vec<Poll<Step>>,
        panic_first: bool,
    ) -> Script {
        r.borrow_mut().push(format!(
            "call:{}",
            state.map(|n| n.to_string()).unwrap_or("null".into())
        ));
        Script {
            record: r,
            events: events.into(),
            panic_first,
        }
    }
    struct Count(AtomicUsize);
    impl Wake for Count {
        fn wake(self: Arc<Self>) {
            self.0.fetch_add(1, Ordering::SeqCst);
        }
    }
    fn task() -> (Arc<Count>, Waker) {
        let c = Arc::new(Count(AtomicUsize::new(0)));
        let w = Waker::from(c.clone());
        (c, w)
    }
    fn record() -> Record {
        Rc::new(RefCell::new(vec![]))
    }
    fn panic_text(p: Box<dyn std::any::Any + Send>) -> String {
        if let Some(s) = p.downcast_ref::<&str>() {
            s.to_string()
        } else {
            p.downcast_ref::<String>().unwrap().clone()
        }
    }
    fn terminal_panic<S: Stream + Unpin>(s: &mut S, cx: &mut Context<'_>) {
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(s).poll_next(cx);
        }))
        .unwrap_err();
        assert!(panic_text(p).starts_with("Unfold must not be polled after it returned"));
    }
    #[test]
    fn immediate_eof_repoll_panics() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| {
            scripted(rr.clone(), state, vec![Poll::Ready(None)], false)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(!s.is_terminated());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        assert!(s.is_terminated());
        terminal_panic(&mut s, &mut cx);
        assert_eq!(*r.borrow(), vec!["call:0", "future"]);
    }
    #[test]
    fn value_then_eof_repoll_panics() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| {
            let step = if state == Some(0) {
                Some((Some(7), Some(1)))
            } else {
                None
            };
            scripted(rr.clone(), state, vec![Poll::Ready(step)], false)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Some(7)))
        );
        assert!(!s.is_terminated());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        assert!(s.is_terminated());
        terminal_panic(&mut s, &mut cx);
        assert_eq!(*r.borrow(), vec!["call:0", "future", "call:1", "future"]);
    }
    #[test]
    fn callback_panic_consumes_seed() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| -> Script {
            rr.borrow_mut().push(format!("call:{}", state.unwrap()));
            panic!("callback panic");
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).poll_next(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "callback panic");
        assert!(s.is_terminated());
        terminal_panic(&mut s, &mut cx);
        assert_eq!(*r.borrow(), vec!["call:0"]);
    }
    #[test]
    fn callback_panic_after_value_consumes_next_seed() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| {
            if state == Some(0) {
                scripted(
                    rr.clone(),
                    state,
                    vec![Poll::Ready(Some((Some(7), Some(1))))],
                    false,
                )
            } else {
                rr.borrow_mut().push("call:1".into());
                panic!("callback panic")
            }
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Some(7)))
        );
        assert!(!s.is_terminated());
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).poll_next(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "callback panic");
        assert!(s.is_terminated());
        terminal_panic(&mut s, &mut cx);
        assert_eq!(*r.borrow(), vec!["call:0", "future", "call:1"]);
    }
    #[test]
    fn pending_nullable_item_and_seed() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| {
            let events = if state == Some(0) {
                vec![
                    Poll::Pending,
                    Poll::Pending,
                    Poll::Ready(Some((None, None))),
                ]
            } else {
                vec![Poll::Ready(None)]
            };
            scripted(rr.clone(), state, events, false)
        });
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        assert!(!s.is_terminated());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(Some(None)));
        assert!(!s.is_terminated());
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        terminal_panic(&mut s, &mut cx);
        assert_eq!(
            *r.borrow(),
            vec![
                "call:0",
                "future",
                "future",
                "future",
                "call:null",
                "future"
            ]
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn future_panic_keeps_active_future() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| {
            scripted(
                rr.clone(),
                state,
                vec![Poll::Ready(Some((Some(7), Some(1))))],
                true,
            )
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).poll_next(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "future panic");
        assert!(!s.is_terminated());
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Some(7)))
        );
        assert_eq!(*r.borrow(), vec!["call:0", "future", "future"]);
    }
    #[test]
    fn canonical_fuse_stabilizes_eof() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(None, move |state| {
            scripted(rr.clone(), state, vec![Poll::Ready(None)], false)
        })
        .fuse();
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert!(!s.is_terminated());
        for _ in 0..3 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
            assert!(s.is_terminated());
        }
        assert_eq!(*r.borrow(), vec!["call:null", "future"]);
    }
    #[test]
    fn nonnull_sequence_control() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::unfold(Some(0), move |state| {
            let step = state.filter(|n| *n < 2).map(|n| (Some(n * 2), Some(n + 1)));
            scripted(rr.clone(), state, vec![Poll::Ready(step)], false)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Some(0)))
        );
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Some(2)))
        );
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        assert!(s.is_terminated());
        assert_eq!(
            *r.borrow(),
            vec!["call:0", "future", "call:1", "future", "call:2", "future"]
        );
    }
}
