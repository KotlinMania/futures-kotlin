#[cfg(test)]
mod tests {
    use futures_util::io::{AsyncRead,AsyncReadExt};
    use std::{collections::VecDeque,future::Future,io,pin::Pin,sync::Arc,task::{Context,Poll,Wake,Waker}};
    struct Noop;
    impl Wake for Noop {fn wake(self:Arc<Self>) {}}
    fn waker()->Waker {Waker::from(Arc::new(Noop))}
    struct Reader {events:VecDeque<Poll<io::Result<Vec<u8>>>>}
    fn reader(events:Vec<Poll<io::Result<Vec<u8>>>>)->Reader {Reader{events:events.into()}}
    fn bytes(b:&[u8])->Poll<io::Result<Vec<u8>>> {Poll::Ready(Ok(b.to_vec()))}
    fn error(kind:io::ErrorKind,message:&'static str)->Poll<io::Result<Vec<u8>>> {Poll::Ready(Err(io::Error::new(kind,message)))}
    impl AsyncRead for Reader {
        fn poll_read(mut self:Pin<&mut Self>,cx:&mut Context<'_>,buf:&mut[u8])->Poll<io::Result<usize>> {
            match self.events.pop_front().expect("script exhausted") {
                Poll::Pending=>{cx.waker().wake_by_ref();Poll::Pending},
                Poll::Ready(Err(e))=>Poll::Ready(Err(e)),
                Poll::Ready(Ok(b))=>{assert!(b.len()<=buf.len());buf[..b.len()].copy_from_slice(&b);Poll::Ready(Ok(b.len()))}
            }
        }
    }
    #[test] fn valid_partial_data_is_restored_on_read_error() {
        let mut r=reader(vec![bytes(&[0xc3,0xa9]),error(io::ErrorKind::Other,"reader failed")]);let mut out="old:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        let Poll::Ready(Err(e))=Pin::new(&mut r.read_to_string(&mut out)).poll(&mut cx) else {panic!("not error")};
        assert_eq!(e.kind(),io::ErrorKind::Other);assert_eq!(e.to_string(),"reader failed");assert_eq!(out,"old:é");
    }
    #[test] fn invalid_utf8_eof_leaves_moved_destination_empty() {
        let mut r=reader(vec![bytes(&[0xff]),bytes(&[])]);let mut out="old:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        let Poll::Ready(Err(e))=Pin::new(&mut r.read_to_string(&mut out)).poll(&mut cx) else {panic!("not error")};
        assert_eq!(e.kind(),io::ErrorKind::InvalidData);assert_eq!(e.to_string(),"stream did not contain valid UTF-8");assert_eq!(out,"");
    }
    #[test] fn invalid_utf8_preserves_original_read_error() {
        let mut r=reader(vec![bytes(&[0xc3]),error(io::ErrorKind::TimedOut,"timed out")]);let mut out="old:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        let Poll::Ready(Err(e))=Pin::new(&mut r.read_to_string(&mut out)).poll(&mut cx) else {panic!("not error")};
        assert_eq!(e.kind(),io::ErrorKind::TimedOut);assert_eq!(e.to_string(),"timed out");assert_eq!(out,"");
    }
    #[test] fn split_utf8_across_pending_is_validated_at_completion() {
        let mut r=reader(vec![bytes(&[0xf0,0x9f]),Poll::Pending,bytes(&[0x98,0x80]),bytes(&[])]);let mut out="旧:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        {let mut f=r.read_to_string(&mut out);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());assert!(matches!(Pin::new(&mut f).poll(&mut cx),Poll::Ready(Ok(4))));}
        assert_eq!(out,"旧:😀");
    }
    #[test] fn dropping_pending_read_does_not_restore_original() {
        let mut r=reader(vec![bytes(b"a"),Poll::Pending]);let mut out="old:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        {let mut f=r.read_to_string(&mut out);assert!(Pin::new(&mut f).poll(&mut cx).is_pending());}
        assert_eq!(out,"");
    }
    #[test] fn success_count_excludes_original_utf8_prefix() {
        let mut r=reader(vec![bytes(&[0xc3,0xa9]),bytes(&[])]);let mut out="旧:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut r.read_to_string(&mut out)).poll(&mut cx),Poll::Ready(Ok(2))));assert_eq!(out,"旧:é");
    }
    #[test] fn immediate_eof_restores_original_with_zero_new_bytes() {
        let mut r=reader(vec![bytes(&[])]);let mut out="旧:".to_string();let w=waker();let mut cx=Context::from_waker(&w);
        assert!(matches!(Pin::new(&mut r.read_to_string(&mut out)).poll(&mut cx),Poll::Ready(Ok(0))));assert_eq!(out,"旧:");
    }
}
