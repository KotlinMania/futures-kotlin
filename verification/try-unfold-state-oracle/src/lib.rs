#[cfg(test)]
mod tests {
    use futures_util::{stream, Stream};
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
    type Step = Result<Option<(Option<i32>, Option<i32>)>, Option<&'static str>>;
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
    fn callback_panic(init: Option<i32>, expected: &str) {
        let r = record();
        let rr = r.clone();
        let mut s = stream::try_unfold(init, move |state| -> Script {
            rr.borrow_mut().push(format!(
                "call:{}",
                state.map(|n| n.to_string()).unwrap_or("null".into())
            ));
            panic!("callback panic")
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).poll_next(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "callback panic");
        for _ in 0..2 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        }
        assert_eq!(*r.borrow(), vec![expected]);
    }
    #[test]
    fn callback_panic_consumes_seed() {
        callback_panic(Some(0), "call:0");
    }
    #[test]
    fn nullable_callback_panic_consumes_seed() {
        callback_panic(None, "call:null");
    }
    #[test]
    fn callback_panic_after_item_consumes_next_seed() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::try_unfold(Some(0), move |state| {
            if state == Some(0) {
                scripted(
                    rr.clone(),
                    state,
                    vec![Poll::Ready(Ok(Some((Some(7), Some(1)))))],
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
            Poll::Ready(Some(Ok(Some(7))))
        );
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut s).poll_next(&mut cx);
        }))
        .unwrap_err();
        assert_eq!(panic_text(p), "callback panic");
        for _ in 0..2 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        }
        assert_eq!(*r.borrow(), vec!["call:0", "future", "call:1"]);
    }
    #[test]
    fn pending_and_eof_are_retained() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::try_unfold(Some(0), move |state| {
            let events = if state == Some(0) {
                vec![
                    Poll::Pending,
                    Poll::Pending,
                    Poll::Ready(Ok(Some((Some(7), Some(1))))),
                ]
            } else {
                vec![Poll::Ready(Ok(None))]
            };
            scripted(rr.clone(), state, events, false)
        });
        let (c, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Pending);
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Ok(Some(7))))
        );
        for _ in 0..3 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        }
        assert_eq!(
            *r.borrow(),
            vec!["call:0", "future", "future", "future", "call:1", "future"]
        );
        assert_eq!(c.0.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn null_error_is_yielded_once() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::try_unfold(Some(0), move |state| {
            scripted(rr.clone(), state, vec![Poll::Ready(Err(None))], false)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Err(None)))
        );
        for _ in 0..2 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        }
        assert_eq!(*r.borrow(), vec!["call:0", "future"]);
    }
    #[test]
    fn nullable_item_and_state_are_not_eof() {
        let r = record();
        let rr = r.clone();
        let mut calls = 0;
        let mut s = stream::try_unfold(None, move |state| {
            calls += 1;
            let step = if calls == 1 { Some((None, None)) } else { None };
            scripted(rr.clone(), state, vec![Poll::Ready(Ok(step))], false)
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Ok(None)))
        );
        for _ in 0..2 {
            assert_eq!(Pin::new(&mut s).poll_next(&mut cx), Poll::Ready(None));
        }
        assert_eq!(
            *r.borrow(),
            vec!["call:null", "future", "call:null", "future"]
        );
    }
    #[test]
    fn future_panic_retains_active_future() {
        let r = record();
        let rr = r.clone();
        let mut s = stream::try_unfold(Some(0), move |state| {
            scripted(
                rr.clone(),
                state,
                vec![Poll::Ready(Ok(Some((Some(7), Some(1)))))],
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
        assert_eq!(
            Pin::new(&mut s).poll_next(&mut cx),
            Poll::Ready(Some(Ok(Some(7))))
        );
        assert_eq!(*r.borrow(), vec!["call:0", "future", "future"]);
    }
    #[test]
    fn ordinary_and_try_terminal_behavior_differ() {
        let mut ordinary = stream::unfold(0, |_| std::future::ready(None::<(i32, i32)>));
        let mut fallible = stream::try_unfold(0, |_| {
            std::future::ready(Ok::<Option<(i32, i32)>, ()>(None))
        });
        let (_, w) = task();
        let mut cx = Context::from_waker(&w);
        assert_eq!(
            Pin::new(&mut ordinary).poll_next(&mut cx),
            Poll::Ready(None)
        );
        assert_eq!(
            Pin::new(&mut fallible).poll_next(&mut cx),
            Poll::Ready(None)
        );
        let p = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            let _ = Pin::new(&mut ordinary).poll_next(&mut cx);
        }))
        .unwrap_err();
        assert!(panic_text(p).starts_with("Unfold must not be polled after it returned"));
        assert_eq!(
            Pin::new(&mut fallible).poll_next(&mut cx),
            Poll::Ready(None)
        );
    }
}
