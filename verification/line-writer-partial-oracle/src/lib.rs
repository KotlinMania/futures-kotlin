#[cfg(test)]
mod tests {
    use futures_util::io::{AsyncWrite,LineWriter};
    use std::{collections::VecDeque,io,pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Writer {counts:VecDeque<usize>,output:Vec<u8>,requests:Vec<Vec<u8>>,flushes:usize}
    fn writer(counts:Vec<usize>)->Writer {Writer{counts:counts.into(),output:vec![],requests:vec![],flushes:0}}
    impl AsyncWrite for Writer {
        fn poll_write(mut self:Pin<&mut Self>,_:&mut Context<'_>,buf:&[u8])->Poll<io::Result<usize>> {
            self.requests.push(buf.to_vec());let n=self.counts.pop_front().expect("unexpected write");assert!(n<=buf.len());self.output.extend_from_slice(&buf[..n]);Poll::Ready(Ok(n))
        }
        fn poll_flush(mut self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {self.flushes+=1;Poll::Ready(Ok(()))}
        fn poll_close(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {Poll::Ready(Ok(()))}
    }
    #[test] fn short_completed_line_excludes_tail() {
        let mut b=LineWriter::with_capacity(8,writer(vec![1,2]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"ab\nTAIL"),Poll::Ready(Ok(3))));assert_eq!(b.buffer(),b"b\n");assert_eq!(b.get_ref().output,b"a");
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"z"),Poll::Ready(Ok(1))));assert_eq!(b.get_ref().output,b"ab\n");assert_eq!(b.buffer(),b"z");assert_eq!(b.get_ref().flushes,1);
    }
    #[test] fn long_completed_line_ends_at_last_window_newline() {
        let mut b=LineWriter::with_capacity(4,writer(vec![1]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"a\nb\nc\nTAIL"),Poll::Ready(Ok(4))));assert_eq!(b.buffer(),b"\nb\n");assert_eq!(b.get_ref().requests,vec![b"a\nb\nc\n".to_vec()]);
    }
    #[test] fn offset_slice_excludes_outside_bytes() {
        let mut b=LineWriter::with_capacity(8,writer(vec![1]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,&b"__ab\nTAIL__"[2..9]),Poll::Ready(Ok(3))));assert_eq!(b.buffer(),b"b\n");assert_eq!(b.get_ref().requests,vec![b"ab\n".to_vec()]);
    }
    #[test] fn window_without_newline_buffers_whole_window() {
        let mut b=LineWriter::with_capacity(4,writer(vec![1]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"abcdef\nTAIL"),Poll::Ready(Ok(5))));assert_eq!(b.buffer(),b"bcde");
    }
    #[test] fn complete_written_line_can_buffer_tail() {
        let mut b=LineWriter::with_capacity(8,writer(vec![3]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"ab\nTAIL"),Poll::Ready(Ok(7))));assert_eq!(b.buffer(),b"TAIL");assert_eq!(b.get_ref().output,b"ab\n");
    }
    #[test] fn zero_write_does_not_buffer_input() {
        let mut b=LineWriter::with_capacity(8,writer(vec![0]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"ab\nTAIL"),Poll::Ready(Ok(0))));assert!(b.buffer().is_empty());
    }
}
