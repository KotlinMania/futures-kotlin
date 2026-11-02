#[cfg(test)]
mod tests {
    use futures_util::io::{AsyncWrite,LineWriter};
    use std::{collections::VecDeque,io::{self,IoSlice},pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Writer {counts:VecDeque<usize>,flushes:VecDeque<Poll<io::Result<()>>>,vectors:Vec<Vec<Vec<u8>>>,output:Vec<u8>,flush_calls:usize}
    fn writer(counts:Vec<usize>,flushes:Vec<Poll<io::Result<()>>>)->Writer {Writer{counts:counts.into(),flushes:flushes.into(),vectors:vec![],output:vec![],flush_calls:0}}
    impl AsyncWrite for Writer {
        fn poll_write(self:Pin<&mut Self>,_:&mut Context<'_>,_:&[u8])->Poll<io::Result<usize>> {panic!("unexpected scalar write")}
        fn poll_write_vectored(mut self:Pin<&mut Self>,_:&mut Context<'_>,bufs:&[IoSlice<'_>])->Poll<io::Result<usize>> {
            self.vectors.push(bufs.iter().map(|b|b.to_vec()).collect());let n=self.counts.pop_front().expect("unexpected vector write");assert!(n<=bufs.iter().map(|b|b.len()).sum::<usize>());let mut left=n;
            for b in bufs {let used=left.min(b.len());self.output.extend_from_slice(&b[..used]);left-=used;}Poll::Ready(Ok(n))
        }
        fn poll_flush(mut self:Pin<&mut Self>,cx:&mut Context<'_>)->Poll<io::Result<()>> {self.flush_calls+=1;let e=self.flushes.pop_front().expect("unexpected flush");if e.is_pending() {cx.waker().wake_by_ref();}e}
        fn poll_close(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {Poll::Ready(Ok(()))}
    }
    #[test] fn newline_slice_is_delegated_whole() {
        let mut b=LineWriter::with_capacity(8,writer(vec![4],vec![Poll::Ready(Ok(()))]));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b""),IoSlice::new(&b"__ab\nX__"[2..6]),IoSlice::new(b"TAIL")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(8))));assert_eq!(b.get_ref().vectors,vec![vec![b"".to_vec(),b"ab\nX".to_vec()]]);assert_eq!(b.get_ref().output,b"ab\nX");assert_eq!(b.buffer(),b"TAIL");
    }
    #[test] fn partial_vector_write_buffers_nothing() {
        let mut b=LineWriter::with_capacity(8,writer(vec![1],vec![Poll::Ready(Ok(()))]));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"ab\nX"),IoSlice::new(b"TAIL")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(1))));assert_eq!(b.get_ref().output,b"a");assert!(b.buffer().is_empty());
    }
    #[test] fn no_newlines_use_buffered_vectored_path() {
        let mut b=LineWriter::with_capacity(8,writer(vec![],vec![]));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"ab"),IoSlice::new(b""),IoSlice::new(b"cd")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(4))));assert_eq!(b.buffer(),b"abcd");assert!(b.get_ref().vectors.is_empty());assert_eq!(b.get_ref().flush_calls,0);
    }
    #[test] fn tail_buffering_spans_slices_until_full() {
        let mut b=LineWriter::with_capacity(4,writer(vec![2],vec![Poll::Ready(Ok(()))]));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"a\n"),IoSlice::new(b"bc"),IoSlice::new(b""),IoSlice::new(b"def"),IoSlice::new(b"g")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(6))));assert_eq!(b.get_ref().vectors,vec![vec![b"a\n".to_vec()]]);assert_eq!(b.buffer(),b"bcde");
    }
    #[test] fn pending_flush_then_zero_write_consumes_nothing() {
        let mut b=LineWriter::with_capacity(8,writer(vec![0],vec![Poll::Pending,Poll::Ready(Ok(()))]));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"a\n"),IoSlice::new(b"TAIL")];
        assert!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs).is_pending());assert!(b.get_ref().vectors.is_empty());assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(0))));assert!(b.buffer().is_empty());assert_eq!(b.get_ref().flush_calls,2);
    }
    #[test] fn flush_error_precedes_vector_write() {
        let mut b=LineWriter::with_capacity(8,writer(vec![],vec![Poll::Ready(Err(io::Error::new(io::ErrorKind::TimedOut,"timeout")))]));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"a\n"),IoSlice::new(b"TAIL")];
        let Poll::Ready(Err(e))=Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs) else {panic!("not error")};assert_eq!(e.kind(),io::ErrorKind::TimedOut);assert_eq!(e.to_string(),"timeout");assert!(b.get_ref().vectors.is_empty());assert!(b.buffer().is_empty());
    }
}
