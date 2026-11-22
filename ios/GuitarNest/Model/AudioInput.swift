import AVFoundation
import Foundation

/// Audio for "Tab from audio": a file the player owns, or a microphone recording, as mono 22050 Hz.
/// Nothing is saved: the samples stay in memory until the tab is made.
enum AudioInput {
    static let rate = 22_050.0
    static let maxSeconds = 15.0 * 60

    static func decode(_ url: URL) throws -> [Float] {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let file = try AVAudioFile(forReading: url)
        let seconds = Double(file.length) / file.processingFormat.sampleRate
        guard seconds <= maxSeconds else { throw AppError(L10n.t("listen_too_long")) }
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)) else {
            throw AppError(L10n.t("import_failed"))
        }
        try file.read(into: buffer)
        return try convert(buffer)
    }

    /// Any PCM buffer to mono 22050 Hz float.
    static func convert(_ buffer: AVAudioPCMBuffer) throws -> [Float] {
        guard let target = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: rate, channels: 1, interleaved: false),
              let converter = AVAudioConverter(from: buffer.format, to: target) else { throw AppError(L10n.t("import_failed")) }
        converter.downmix = true
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * rate / buffer.format.sampleRate) + 1024
        guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { throw AppError(L10n.t("import_failed")) }
        var done = false
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if done {
                status.pointee = .endOfStream
                return nil
            }
            done = true
            status.pointee = .haveData
            return buffer
        }
        if let error { throw error }
        guard let data = out.floatChannelData?[0] else { return [] }
        return Array(UnsafeBufferPointer(start: data, count: Int(out.frameLength)))
    }
}

/// Microphone recording into memory.
@MainActor
final class Recorder: ObservableObject {
    @Published private(set) var seconds: Double = 0
    @Published private(set) var level: Float = 0
    @Published private(set) var active = false

    private let engine = AVAudioEngine()
    private var buffers: [AVAudioPCMBuffer] = []
    private var format: AVAudioFormat?

    func start(denied: @escaping () -> Void) {
        AVAudioApplication.requestRecordPermission { granted in
            Task { @MainActor in
                if granted { self.begin() } else { denied() }
            }
        }
    }

    private func begin() {
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker])
        try? AVAudioSession.sharedInstance().setActive(true)
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        self.format = format
        buffers = []
        seconds = 0
        input.installTap(onBus: 0, bufferSize: 4096, format: format) { [weak self] buffer, _ in
            guard let copy = AVAudioPCMBuffer(pcmFormat: buffer.format, frameCapacity: buffer.frameLength) else { return }
            copy.frameLength = buffer.frameLength
            if let from = buffer.floatChannelData, let to = copy.floatChannelData {
                for c in 0..<Int(buffer.format.channelCount) {
                    to[c].update(from: from[c], count: Int(buffer.frameLength))
                }
            }
            var sum: Float = 0
            if let data = buffer.floatChannelData?[0] {
                for i in 0..<Int(buffer.frameLength) { sum += data[i] * data[i] }
            }
            let rms = sqrt(sum / Float(max(1, buffer.frameLength)))
            Task { @MainActor in
                guard let self, self.active else { return }
                self.buffers.append(copy)
                self.seconds += Double(copy.frameLength) / copy.format.sampleRate
                self.level = rms
                if self.seconds > AudioInput.maxSeconds { _ = self.stop() }
            }
        }
        do {
            try engine.start()
            active = true
        } catch {
            input.removeTap(onBus: 0)
        }
    }

    /// Stops and returns mono 22050 Hz audio.
    func stop() -> [Float] {
        guard active else { return [] }
        active = false
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        guard let format, let first = buffers.first else { return [] }
        let total = buffers.reduce(0) { $0 + Int($1.frameLength) }
        guard let joined = AVAudioPCMBuffer(pcmFormat: first.format, frameCapacity: AVAudioFrameCount(total)) else { return [] }
        var position = 0
        for b in buffers {
            for c in 0..<Int(format.channelCount) {
                joined.floatChannelData![c].advanced(by: position).update(from: b.floatChannelData![c], count: Int(b.frameLength))
            }
            position += Int(b.frameLength)
        }
        joined.frameLength = AVAudioFrameCount(total)
        buffers = []
        return (try? AudioInput.convert(joined)) ?? []
    }
}
