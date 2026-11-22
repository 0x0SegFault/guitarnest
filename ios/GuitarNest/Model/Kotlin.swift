import Foundation
import GuitarNestShared

/// Conversions between Swift and the shared Kotlin types.
enum Convert {
    static func floats(_ values: [Float]) -> KotlinFloatArray {
        let array = KotlinFloatArray(size: Int32(values.count))
        for (i, value) in values.enumerated() {
            array.set(index: Int32(i), value: value)
        }
        return array
    }

    static func bytes(_ data: Data) -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(data.count))
        for (i, byte) in data.enumerated() {
            array.set(index: Int32(i), value: Int8(bitPattern: byte))
        }
        return array
    }

    static func strings(_ tuning: Tuning) -> [Int] {
        tuning.strings.map { Int(truncating: $0) }
    }
}
