#if canImport(Testing)
import Testing
import Futures

@Suite struct FuturesExportTests {
    @Test func testAtomicWakerAndCountWaker() {
        let atomic = AtomicWaker.Companion.shared.new()
        #expect(atomic.take() == nil)

        let pair = newCountWaker()
        let waker = pair.first as! any Waker
        let count = pair.second as! AwokenCount
        #expect(count.get() == 0)

        atomic.register(waker: waker)
        atomic.wake()
        #expect(count.get() == 1)
    }

    @Test func testSharedPollAndPollFn() {
        let pending = pollPending()
        #expect(pending.isPending())
        #expect(!pending.isReady())

        let ready = pollReady(value: nil)
        #expect(ready.isReady())
        #expect(!ready.isPending())

        let ctx = noopContext()
        var polled = false
        let fn = PollFn { context in
            if !polled {
                polled = true
                context.wakeByRef()
                return Poll.Companion.shared.pending()
            }
            return Poll.Companion.shared.ready(value: nil)
        }

        let first = fn.poll(context: ctx)
        #expect(first.isPending())
        let second = fn.poll(context: ctx)
        #expect(second.isReady())
    }

    @Test func testSeekFromFullWidthUInt64AndIoError() {
        let zero = io.SeekFrom.Start(offset: 0)
        #expect(zero.offset == 0)

        let highBit = UInt64(Int64.max) + 1
        let startHigh = io.SeekFrom.Start(offset: highBit)
        #expect(startHigh.offset == highBit)

        let startMax = io.SeekFrom.Start(offset: UInt64.max)
        #expect(startMax.offset == UInt64.max)

        let end = io.SeekFrom.End(offset: -16)
        #expect(end.offset == -16)

        let current = io.SeekFrom.Current(offset: 32)
        #expect(current.offset == 32)

        let err = io.IoError.Companion.shared.from(
            kind: .InvalidInput,
            message: "invalid seek to a negative or overflowing position"
        )
        #expect(err.kind == .InvalidInput)
    }
}
#elseif canImport(XCTest)
import XCTest
import Futures

final class FuturesExportTests: XCTestCase {
    func testAtomicWakerAndCountWaker() {
        let atomic = AtomicWaker.Companion.shared.new()
        XCTAssertNil(atomic.take())

        let pair = newCountWaker()
        let waker = pair.first as! any Waker
        let count = pair.second as! AwokenCount
        XCTAssertEqual(count.get(), 0)

        atomic.register(waker: waker)
        atomic.wake()
        XCTAssertEqual(count.get(), 1)
    }

    func testSharedPollAndPollFn() {
        let pending = pollPending()
        XCTAssertTrue(pending.isPending())
        XCTAssertFalse(pending.isReady())

        let ready = pollReady(value: nil)
        XCTAssertTrue(ready.isReady())
        XCTAssertFalse(ready.isPending())

        let ctx = noopContext()
        var polled = false
        let fn = PollFn { context in
            if !polled {
                polled = true
                context.wakeByRef()
                return Poll.Companion.shared.pending()
            }
            return Poll.Companion.shared.ready(value: nil)
        }

        XCTAssertTrue(fn.poll(context: ctx).isPending())
        XCTAssertTrue(fn.poll(context: ctx).isReady())
    }

    func testSeekFromFullWidthUInt64AndIoError() {
        let highBit = UInt64(Int64.max) + 1
        let startHigh = io.SeekFrom.Start(offset: highBit)
        XCTAssertEqual(startHigh.offset, highBit)

        let startMax = io.SeekFrom.Start(offset: UInt64.max)
        XCTAssertEqual(startMax.offset, UInt64.max)

        let err = io.IoError.Companion.shared.from(
            kind: .InvalidInput,
            message: "invalid seek to a negative or overflowing position"
        )
        XCTAssertEqual(err.kind, .InvalidInput)
    }
}
#endif


