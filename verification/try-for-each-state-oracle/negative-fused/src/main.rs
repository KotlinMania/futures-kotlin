use futures_util::{
    future::{self, FusedFuture},
    stream, TryStreamExt,
};
fn requires_fused<T: FusedFuture>(_: T) {}
fn main() {
    requires_fused(
        stream::iter(vec![Ok::<i32, &str>(1)]).try_for_each(|_| future::ready(Ok::<(), &str>(()))),
    );
}
