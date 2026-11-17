use futures_util::{Stream, StreamExt};
use std::{
    pin::Pin,
    task::{Context, Poll},
};
struct Plain;
impl Stream for Plain {
    type Item = i32;
    fn poll_next(self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Option<i32>> {
        Poll::Ready(None)
    }
}
fn main() {
    let mut stream = Plain;
    let _ = stream.select_next_some();
}
