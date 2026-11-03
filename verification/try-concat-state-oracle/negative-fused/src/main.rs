use futures_util::{future::FusedFuture, stream, TryStreamExt};
fn requires_fused<T: FusedFuture>(_: T) {}
fn main() {
    requires_fused(stream::iter(vec![Ok::<Vec<i32>, &str>(vec![1])]).try_concat());
}
