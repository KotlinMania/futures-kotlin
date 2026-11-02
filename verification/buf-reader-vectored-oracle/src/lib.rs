#[cfg(test)]
mod tests {
    use futures_util::io::{AsyncRead,AsyncBufRead,BufReader};
    use std::{collections::VecDeque,io::{self,IoSliceMut,Read},pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Reader {scalar:VecDeque<Poll<io::Result<Vec<u8>>>>,vector:VecDeque<Poll<io::Result<Vec<u8>>>>,scalar_requests:Vec<usize>,vector_requests:Vec<Vec<usize>>}
    fn reader(scalar:Vec<Poll<io::Result<Vec<u8>>>>,vector:Vec<Poll<io::Result<Vec<u8>>>>)->Reader {Reader{scalar:scalar.into(),vector:vector.into(),scalar_requests:vec![],vector_requests:vec![]}}
    fn bytes(b:&[u8])->Poll<io::Result<Vec<u8>>> {Poll::Ready(Ok(b.to_vec()))}
    impl AsyncRead for Reader {
        fn poll_read(mut self:Pin<&mut Self>,cx:&mut Context<'_>,buf:&mut[u8])->Poll<io::Result<usize>> {
            self.scalar_requests.push(buf.len());match self.scalar.pop_front().expect("unexpected scalar read") {
                Poll::Pending=>{cx.waker().wake_by_ref();Poll::Pending},Poll::Ready(Err(e))=>Poll::Ready(Err(e)),
                Poll::Ready(Ok(b))=>{assert!(b.len()<=buf.len());buf[..b.len()].copy_from_slice(&b);Poll::Ready(Ok(b.len()))}
            }
        }
        fn poll_read_vectored(mut self:Pin<&mut Self>,cx:&mut Context<'_>,bufs:&mut[IoSliceMut<'_>])->Poll<io::Result<usize>> {
            self.vector_requests.push(bufs.iter().map(|s|s.len()).collect());match self.vector.pop_front().expect("unexpected vector read") {
                Poll::Pending=>{cx.waker().wake_by_ref();Poll::Pending},Poll::Ready(Err(e))=>Poll::Ready(Err(e)),
                Poll::Ready(Ok(b))=>{let mut rem=b.as_slice();let n=rem.read_vectored(bufs).unwrap();assert_eq!(n,b.len());Poll::Ready(Ok(n))}
            }
        }
    }
    #[test] fn small_vectors_scatter_all_ranges() {
        let mut b=BufReader::with_capacity(8,reader(vec![bytes(b"abcdef")],vec![]));let w=waker();let mut cx=Context::from_waker(&w);let(mut a,mut c)=(*b"____",*b"____");
        {let mut bufs=[IoSliceMut::new(&mut a[1..3]),IoSliceMut::new(&mut[]),IoSliceMut::new(&mut c[1..3])];assert!(matches!(Pin::new(&mut b).poll_read_vectored(&mut cx,&mut bufs),Poll::Ready(Ok(4))));}
        assert_eq!(&a,b"_ab_");assert_eq!(&c,b"_cd_");assert_eq!(b.buffer(),b"ef");assert_eq!(b.get_ref().scalar_requests,vec![8]);assert!(b.get_ref().vector_requests.is_empty());
    }
    #[test] fn empty_buffer_capacity_vectors_bypass() {
        let mut b=BufReader::with_capacity(4,reader(vec![],vec![bytes(b"abcd")]));let w=waker();let mut cx=Context::from_waker(&w);let(mut a,mut c)=([0;2],[0;2]);
        {let mut bufs=[IoSliceMut::new(&mut a),IoSliceMut::new(&mut c)];assert!(matches!(Pin::new(&mut b).poll_read_vectored(&mut cx,&mut bufs),Poll::Ready(Ok(4))));}
        assert_eq!(&a,b"ab");assert_eq!(&c,b"cd");assert_eq!(b.get_ref().vector_requests,vec![vec![2,2]]);assert!(b.get_ref().scalar_requests.is_empty());assert!(b.buffer().is_empty());
    }
    #[test] fn existing_buffer_prevents_large_bypass() {
        let mut b=BufReader::with_capacity(4,reader(vec![bytes(b"abcd")],vec![]));let w=waker();let mut cx=Context::from_waker(&w);assert!(Pin::new(&mut b).poll_fill_buf(&mut cx).is_ready());Pin::new(&mut b).consume(1);let(mut a,mut c)=(*b"__",*b"____");
        {let mut bufs=[IoSliceMut::new(&mut a),IoSliceMut::new(&mut c)];assert!(matches!(Pin::new(&mut b).poll_read_vectored(&mut cx,&mut bufs),Poll::Ready(Ok(3))));}
        assert_eq!(&a,b"bc");assert_eq!(&c,b"d___");assert!(b.buffer().is_empty());assert_eq!(b.get_ref().scalar_requests,vec![4]);assert!(b.get_ref().vector_requests.is_empty());
    }
    #[test] fn direct_pending_resumes_without_scalar() {
        let mut b=BufReader::with_capacity(4,reader(vec![],vec![Poll::Pending,bytes(b"abcd")]));let w=waker();let mut cx=Context::from_waker(&w);let(mut a,mut c)=([0;2],[0;2]);let mut bufs=[IoSliceMut::new(&mut a),IoSliceMut::new(&mut c)];
        assert!(Pin::new(&mut b).poll_read_vectored(&mut cx,&mut bufs).is_pending());assert!(matches!(Pin::new(&mut b).poll_read_vectored(&mut cx,&mut bufs),Poll::Ready(Ok(4))));assert_eq!(b.get_ref().vector_requests,vec![vec![2,2],vec![2,2]]);assert!(b.get_ref().scalar_requests.is_empty());
    }
    #[test] fn direct_error_preserves_error() {
        let mut b=BufReader::with_capacity(4,reader(vec![],vec![Poll::Ready(Err(io::Error::new(io::ErrorKind::TimedOut,"timeout")))]));let w=waker();let mut cx=Context::from_waker(&w);let(mut a,mut c)=([0;2],[0;2]);let mut bufs=[IoSliceMut::new(&mut a),IoSliceMut::new(&mut c)];
        let Poll::Ready(Err(e))=Pin::new(&mut b).poll_read_vectored(&mut cx,&mut bufs) else {panic!("not error")};assert_eq!(e.kind(),io::ErrorKind::TimedOut);assert_eq!(e.to_string(),"timeout");assert!(b.get_ref().scalar_requests.is_empty());assert!(b.buffer().is_empty());
    }
    #[test] fn empty_vectors_still_fill_buffer() {
        let mut b=BufReader::with_capacity(4,reader(vec![bytes(b"ab")],vec![]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_read_vectored(&mut cx,&mut[]),Poll::Ready(Ok(0))));assert_eq!(b.buffer(),b"ab");assert_eq!(b.get_ref().scalar_requests,vec![4]);
    }
}
