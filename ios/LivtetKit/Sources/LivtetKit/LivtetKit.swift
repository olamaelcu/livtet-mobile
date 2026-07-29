import FastULID
import Foundation
import LivtetKitFFI

public typealias DbId = ULID

public extension ULID {
    var hexString: String {
        self.ulidData.map { String(format: "%02x", $0) }.joined()
    }

    static func fromHexString(_ hex: String) -> ULID? {
        guard hex.count == 32 else { return nil }
        var bytes = Data()
        var index = hex.startIndex
        for _ in 0..<16 {
            let nextIndex = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<nextIndex], radix: 16) else { return nil }
            bytes.append(byte)
            index = nextIndex
        }
        return ULID(ulidData: bytes)
    }
}
