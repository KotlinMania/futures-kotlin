#[cfg(test)]
mod tests {
    use futures_util::io::{AsyncWrite,BufWriter};
    use std::{collections::VecDeque,io,pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Writer {events:VecDeque<Poll<io::Result<usize>>>,output:Vec<u8>,requests:Vec<Vec<u8>>,flushes:usize}
    fn writer(events:Vec<Poll<io::Result<usize>>>)->Writer {Writer{events:events.into(),output:vec![],requests:vec![],flushes:0}}
    fn count(n:usize)->Poll<io::Result<usize>> {Poll::Ready(Ok(n))}
    fn error(kind:io::ErrorKind,msg:&'static str)->Poll<io::Result<usize>> {Poll::Ready(Err(io::Error::new(kind,msg)))}
    impl AsyncWrite for Writer {
        fn poll_write(mut self:Pin<&mut Self>,cx:&mut Context<'_>,buf:&[u8])->Poll<io::Result<usize>> {
            self.requests.push(buf.to_vec());let e=self.events.pop_front().expect("script exhausted");
            if let Poll::Ready(Ok(n))=e {assert!(n<=buf.len());self.output.extend_from_slice(&buf[..n]);}
            if e.is_pending() {cx.waker().wake_by_ref();}e
        }
        fn poll_flush(mut self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {self.flushes+=1;Poll::Ready(Ok(()))}
        fn poll_close(self:Pin<&mut Self>,_:&mut Context<'_>)->Poll<io::Result<()>> {Poll::Ready(Ok(()))}
    }
    fn seeded(w:Writer)->BufWriter<Writer> {let mut b=BufWriter::with_capacity(4,w);let w=waker();let mut cx=Context::from_waker(&w);for bytes in [b"ab",b"cd"] {assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,bytes),Poll::Ready(Ok(2))));}b}
    fn requests(b:&BufWriter<Writer>)->Vec<&[u8]> {b.get_ref().requests.iter().map(Vec::as_slice).collect()}
    #[test] fn partial_error_drains_written_prefix() {
        let mut b=seeded(writer(vec![count(2),error(io::ErrorKind::Other,"failed")]));let w=waker();let mut cx=Context::from_waker(&w);
        let Poll::Ready(Err(e))=Pin::new(&mut b).poll_flush(&mut cx) else {panic!("not error")};assert_eq!(e.kind(),io::ErrorKind::Other);assert_eq!(e.to_string(),"failed");
        assert_eq!(b.buffer(),b"cd");assert_eq!(b.get_ref().output,b"ab");assert_eq!(b.get_ref().flushes,0);
    }
    #[test] fn partial_zero_drains_written_prefix() {
        let mut b=seeded(writer(vec![count(2),count(0)]));let w=waker();let mut cx=Context::from_waker(&w);
        let Poll::Ready(Err(e))=Pin::new(&mut b).poll_flush(&mut cx) else {panic!("not error")};assert_eq!(e.kind(),io::ErrorKind::WriteZero);assert_eq!(e.to_string(),"failed to write the buffered data");assert_eq!(b.buffer(),b"cd");
    }
    #[test] fn new_write_uses_freed_capacity() {
        let mut b=seeded(writer(vec![count(2),error(io::ErrorKind::Other,"failed"),Poll::Pending]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_flush(&mut cx),Poll::Ready(Err(_))));assert!(matches!(Pin::new(&mut b).poll_write(&mut cx,b"ef"),Poll::Ready(Ok(2))));
        assert_eq!(b.buffer(),b"cdef");assert_eq!(requests(&b),vec![b"abcd".as_slice(),b"cd".as_slice()]);
    }
    #[test] fn retry_does_not_write_prefix_again() {
        let mut b=seeded(writer(vec![count(2),error(io::ErrorKind::Other,"failed"),count(2)]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut b).poll_flush(&mut cx),Poll::Ready(Err(_))));assert!(matches!(Pin::new(&mut b).poll_flush(&mut cx),Poll::Ready(Ok(()))));
        assert_eq!(b.get_ref().output,b"abcd");assert_eq!(requests(&b),vec![b"abcd".as_slice(),b"cd".as_slice(),b"cd".as_slice()]);assert!(b.buffer().is_empty());assert_eq!(b.get_ref().flushes,1);
    }
    #[test] fn pending_preserves_cursor_until_ready() {
        let mut b=seeded(writer(vec![count(2),Poll::Pending,count(2)]));let w=waker();let mut cx=Context::from_waker(&w);
        assert!(Pin::new(&mut b).poll_flush(&mut cx).is_pending());assert_eq!(b.buffer(),b"abcd");assert!(matches!(Pin::new(&mut b).poll_flush(&mut cx),Poll::Ready(Ok(()))));
        assert_eq!(b.get_ref().output,b"abcd");assert_eq!(requests(&b),vec![b"abcd".as_slice(),b"cd".as_slice(),b"cd".as_slice()]);
    }
    #[test] fn error_before_progress_keeps_all_bytes() {
        let mut b=seeded(writer(vec![error(io::ErrorKind::TimedOut,"timeout")]));let w=waker();let mut cx=Context::from_waker(&w);
        let Poll::Ready(Err(e))=Pin::new(&mut b).poll_flush(&mut cx) else {panic!("not error")};assert_eq!(e.kind(),io::ErrorKind::TimedOut);assert_eq!(e.to_string(),"timeout");assert_eq!(b.buffer(),b"abcd");
    }
}
