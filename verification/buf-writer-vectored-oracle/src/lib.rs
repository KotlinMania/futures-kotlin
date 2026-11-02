#[cfg(test)]
mod tests {
    use futures_util::io::{AsyncWrite,BufWriter};
    use std::{collections::VecDeque,io::{self,IoSlice},pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Writer {scalar:VecDeque<Poll<io::Result<usize>>>,vector_count:usize,scalar_requests:Vec<Vec<u8>>,vector_requests:Vec<Vec<Vec<u8>>>,output:Vec<u8>}
    fn writer(scalar:Vec<Poll<io::Result<usize>>>,vector_count:usize)->Writer {Writer{scalar:scalar.into(),vector_count,scalar_requests:vec![],vector_requests:vec![],output:vec![]}}
    impl AsyncWrite for Writer {
        fn poll_write(mut self:Pin<&mut Self>,cx:&mut Context<'_>,buf:&[u8])->Poll<io::Result<usize>> {
            self.scalar_requests.push(buf.to_vec());let e=self.scalar.pop_front().expect("unexpected scalar write");if let Poll::Ready(Ok(n))=e {assert!(n<=buf.len());self.output.extend_from_slice(&buf[..n]);}if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn poll_write_vectored(mut self:Pin<&mut Self>,_:&mut Context<'_>,bufs:&[IoSlice<'_>])->Poll<io::Result<usize>> {
            let parts=bufs.iter().map(|s|s.to_vec()).collect::<Vec<_>>();assert_eq!(parts.iter().map(Vec::len).sum::<usize>(),self.vector_count);
            self.vector_requests.push(parts);for s in bufs {self.output.extend_from_slice(s);}Poll::Ready(Ok(self.vector_count))
        }
        fn poll_flush(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {Poll::Ready(Ok(()))}
        fn poll_close(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {Poll::Ready(Ok(()))}
    }
    fn seed(w:Writer,cap:usize)->BufWriter<Writer> {let mut b=BufWriter::with_capacity(cap,w);let w=waker();let mut cx=Context::from_waker(&w);assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"ab"),Poll::Ready(Ok(2))));b}
    #[test] fn small_vectors_buffer_all_ranges() {
        let mut b=seed(writer(vec![],0),8);let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(&b"__cd__"[2..4]),IoSlice::new(b""),IoSlice::new(b"ef")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(4))));assert_eq!(b.buffer(),b"abcdef");assert!(b.get_ref().scalar_requests.is_empty());assert!(b.get_ref().vector_requests.is_empty());
    }
    #[test] fn capacity_vectors_delegate_to_vectored_method() {
        let mut b=BufWriter::with_capacity(4,writer(vec![],4));let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"ab"),IoSlice::new(b"cd")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(4))));assert_eq!(b.get_ref().vector_requests,vec![vec![b"ab".to_vec(),b"cd".to_vec()]]);assert!(b.get_ref().scalar_requests.is_empty());assert_eq!(b.get_ref().output,b"abcd");assert!(b.buffer().is_empty());
    }
    #[test] fn pending_flush_resumes_before_unconsumed_vectors() {
        let mut b=seed(writer(vec![Poll::Pending,Poll::Ready(Ok(2))],4),4);let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"cd"),IoSlice::new(b"ef")];
        assert!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs).is_pending());assert_eq!(b.buffer(),b"ab");assert!(b.get_ref().vector_requests.is_empty());assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(4))));
        assert_eq!(b.get_ref().scalar_requests,vec![b"ab".to_vec(),b"ab".to_vec()]);assert_eq!(b.get_ref().vector_requests,vec![vec![b"cd".to_vec(),b"ef".to_vec()]]);assert_eq!(b.get_ref().output,b"abcdef");
    }
    #[test] fn flush_error_does_not_delegate_vectors() {
        let mut b=seed(writer(vec![Poll::Ready(Err(io::Error::new(io::ErrorKind::Other,"failed")))],4),4);let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"cd"),IoSlice::new(b"ef")];
        let Poll::Ready(Err(e))=Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs) else {panic!("not error")};assert_eq!(e.kind(),io::ErrorKind::Other);assert_eq!(e.to_string(),"failed");assert_eq!(b.buffer(),b"ab");assert!(b.get_ref().vector_requests.is_empty());assert_eq!(&*bufs[0],b"cd");assert_eq!(&*bufs[1],b"ef");
    }
    #[test] fn empty_vectors_with_spare_capacity_make_no_inner_call() {
        let mut b=seed(writer(vec![],0),8);let w=waker();let mut cx=Context::from_waker(&w);let bufs=[IoSlice::new(b"")];
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&bufs),Poll::Ready(Ok(0))));assert_eq!(b.buffer(),b"ab");assert!(b.get_ref().scalar_requests.is_empty());assert!(b.get_ref().vector_requests.is_empty());
    }
    #[test] fn zero_capacity_delegates_empty_vector_list() {
        let mut b=BufWriter::with_capacity(0,writer(vec![],0));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_write_vectored(&mut cx,&[]),Poll::Ready(Ok(0))));assert_eq!(b.get_ref().vector_requests,vec![Vec::<Vec<u8>>::new()]);assert!(b.get_ref().scalar_requests.is_empty());
    }
}
