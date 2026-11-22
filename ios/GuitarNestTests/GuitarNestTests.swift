import GuitarNestShared
import XCTest
@testable import GuitarNest

final class GuitarNestTests: XCTestCase {
    func testSharedCodeFromSwift() throws {
        let example = Bridge.shared.exampleSong()
        let text = Bridge.shared.encodeSong(song: example)
        let back = try Bridge.shared.decodeSong(text: text)
        XCTAssertEqual(back.title, example.title)
        XCTAssertTrue(Bridge.shared.validate(song: back).isEmpty)
        XCTAssertEqual(Bridge.shared.tuningPresets().count, 9)
    }

    func testKotlinErrorsBecomeSwiftErrors() {
        XCTAssertThrowsError(try Bridge.shared.decodeSong(text: "not json"))
    }

    func testBackupRoundTripAndWrongPassword() throws {
        let plain = Data("songs".utf8)
        let blob = try BackupCrypto.encrypt(plain, password: "correct horse", rounds: 10_000)
        XCTAssertEqual(try BackupCrypto.decrypt(blob, password: "correct horse"), plain)
        XCTAssertThrowsError(try BackupCrypto.decrypt(blob, password: "wrong"))
        XCTAssertThrowsError(try BackupCrypto.decrypt(Data("hello".utf8), password: "x"))
    }

    func testModelIsBundledAndRuns() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "basic-pitch", withExtension: "gnm"))
        let pitch = try Bridge.shared.basicPitch(model: KT.bytes(try Data(contentsOf: url)))
        let tone = (0..<22_050 * 4).map { Float(0.3 * sin(2 * Double.pi * 196 * Double($0) / 22_050)) }
        let song = try Bridge.shared.listen(model: pitch, audio: KT.floats(tone), level: Bridge.shared.level(index: 1), title: "Tone") { _, _ in }
        // A single held tone has too few notes for a tab: the result is nil, without crashing.
        XCTAssertNil(song)
    }

    func testTranslationsAreBundled() {
        for language in ["en", "fr", "de", "es", "it"] {
            let path = Bundle.main.path(forResource: language, ofType: "lproj")
            XCTAssertNotNil(path, language)
            let bundle = Bundle(path: path ?? "")
            XCTAssertNotEqual(bundle?.localizedString(forKey: "songs", value: nil, table: nil), "songs", language)
        }
    }
}
