#[cfg(test)]
mod tests {
    use futures_util::{Sink, SinkExt};
    use std::{
        cell::RefCell,
        pin::Pin,
        rc::Rc,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
        task::{Context, Poll, Wake, Waker},
    };
    type Outcome = Poll<Result<(), Option<&'static str>>>;
    #[derive(Clone, Copy)]
    enum Op {
        Ready,
        Flush,
        Close,
    }
    impl Op {
        fn name(self) -> &'static str {
            match self {
                Self::Ready => "ready",
                Self::Flush => "flush",
                Self::Close => "close",
            }
        }
    }
    struct Output {
        name: &'static str,
        result: Outcome,
        trace: Rc<RefCell<Vec<String>>>,
    }
    impl Output {
        fn event(&self, op: Op, cx: &mut Context<'_>) -> Outcome {
            self.trace
                .borrow_mut()
                .push(format!("{}.{}", self.name, op.name()));
            if self.result.is_pending() {
                cx.waker().wake_by_ref();
            }
            self.result
        }
    }
    impl Sink<i32> for Output {
        type Error = Option<&'static str>;
        fn poll_ready(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Outcome {
            self.event(Op::Ready, cx)
        }
        fn start_send(self: Pin<&mut Self>, _: i32) -> Result<(), Self::Error> {
            panic!("unexpected send")
        }
        fn poll_flush(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Outcome {
            self.event(Op::Flush, cx)
        }
        fn poll_close(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Outcome {
            self.event(Op::Close, cx)
        }
    }
    struct Count(AtomicUsize);
    impl Wake for Count {
        fn wake(self: Arc<Self>) {
            self.0.fetch_add(1, Ordering::SeqCst);
        }
    }
    fn check(
        op: Op,
        left: Outcome,
        right: Outcome,
        expected: Outcome,
        right_polled: bool,
        wakes: usize,
    ) {
        let trace = Rc::new(RefCell::new(vec![]));
        let l = Output {
            name: "left",
            result: left,
            trace: trace.clone(),
        };
        let r = Output {
            name: "right",
            result: right,
            trace: trace.clone(),
        };
        let mut sink = l.fanout(r);
        let count = Arc::new(Count(AtomicUsize::new(0)));
        let w = Waker::from(count.clone());
        let mut cx = Context::from_waker(&w);
        let result = match op {
            Op::Ready => Pin::new(&mut sink).poll_ready(&mut cx),
            Op::Flush => Pin::new(&mut sink).poll_flush(&mut cx),
            Op::Close => Pin::new(&mut sink).poll_close(&mut cx),
        };
        assert_eq!(result, expected);
        let mut want = vec![format!("left.{}", op.name())];
        if right_polled {
            want.push(format!("right.{}", op.name()));
        }
        assert_eq!(*trace.borrow(), want);
        assert_eq!(count.0.load(Ordering::SeqCst), wakes);
    }
    fn left_error(op: Op) {
        check(
            op,
            Poll::Ready(Err(Some("left"))),
            Poll::Ready(Err(Some("right"))),
            Poll::Ready(Err(Some("left"))),
            false,
            0,
        );
    }
    fn null_left_error(op: Op) {
        check(
            op,
            Poll::Ready(Err(None)),
            Poll::Pending,
            Poll::Ready(Err(None)),
            false,
            0,
        );
    }
    fn controls(op: Op) {
        let ok = Poll::Ready(Ok(()));
        let pending = Poll::Pending;
        let err = Poll::Ready(Err(Some("right")));
        for (l, r, want, wakes) in [
            (ok, ok, ok, 0),
            (ok, pending, pending, 1),
            (pending, ok, pending, 1),
            (pending, pending, pending, 2),
            (pending, err, err, 1),
            (ok, err, err, 0),
        ] {
            check(op, l, r, want, true, wakes);
        }
    }
    #[test]
    fn ready_left_error() {
        left_error(Op::Ready);
    }
    #[test]
    fn flush_left_error() {
        left_error(Op::Flush);
    }
    #[test]
    fn close_left_error() {
        left_error(Op::Close);
    }
    #[test]
    fn ready_null_left_error() {
        null_left_error(Op::Ready);
    }
    #[test]
    fn flush_null_left_error() {
        null_left_error(Op::Flush);
    }
    #[test]
    fn close_null_left_error() {
        null_left_error(Op::Close);
    }
    #[test]
    fn ready_pending_and_right_error_controls() {
        controls(Op::Ready);
    }
    #[test]
    fn flush_pending_and_right_error_controls() {
        controls(Op::Flush);
    }
    #[test]
    fn close_pending_and_right_error_controls() {
        controls(Op::Close);
    }
}
