#if canImport(Testing)
import Testing
import Futures

@Suite struct FuturesExportTests {
    @Test func testSwiftModuleLoads() {
        let waker = AtomicWaker()
        #expect(waker.take() == nil)
    }
}
#elseif canImport(XCTest)
import XCTest
import Futures

final class FuturesExportTests: XCTestCase {
    func testSwiftModuleLoads() {
        let waker = AtomicWaker()
        XCTAssertNil(waker.take())
    }
}
#endif

