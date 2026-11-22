import AVFoundation
import Foundation
import GuitarNestShared

/// Microphone to pitch with the shared YIN detector. Listens only while the tuner is on screen.
@MainActor
final class TunerEngine: ObservableObject {
    @Published private(set) var frequency: Double?
    @Published private(set) var listening = false
    @Published private(set) var denied = false

    private let engine = AVAudioEngine()
    private let smoother = PitchSmoother(size: 5)
    private var silent = 0

    /// Runs on the audio side: keeps the last window of samples and detects the pitch.
    private final class Worker: @unchecked Sendable {
        let queue = DispatchQueue(label: "tuner")
        let detector: PitchDetector
        let size: Int
        var window: [Float] = []

        init(sampleRate: Double) {
            detector = Bridge.shared.pitchDetector(sampleRate: Int32(sampleRate))
            size = Int(detector.windowSize)
        }

        func add(_ samples: [Float], result: @escaping @Sendable (Double?) -> Void) {
            queue.async {
                self.window.append(contentsOf: samples)
                if self.window.count > self.size { self.window.removeFirst(self.window.count - self.size) }
                guard self.window.count == self.size else { return }
                let pitch = self.detector.detect(samples: Convert.floats(self.window))
                let clear = pitch.map { $0.clarity > 0.85 } ?? false
                result(clear ? pitch?.frequency : nil)
            }
        }
    }

    func start() {
        if let hz = Self.demoFrequency() {
            frequency = hz
            listening = true
            return
        }
        AVAudioApplication.requestRecordPermission { granted in
            Task { @MainActor in
                if granted { self.begin() } else { self.denied = true }
            }
        }
    }

    private func begin() {
        guard !engine.isRunning else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker])
        try? AVAudioSession.sharedInstance().setActive(true)
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else { return }
        let worker = Worker(sampleRate: format.sampleRate)
        input.installTap(onBus: 0, bufferSize: 2048, format: format) { [weak self] buffer, _ in
            guard let channel = buffer.floatChannelData?[0] else { return }
            let samples = Array(UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength)))
            worker.add(samples) { value in
                Task { @MainActor in self?.update(value) }
            }
        }
        do {
            try engine.start()
            listening = true
        } catch {
            input.removeTap(onBus: 0)
        }
    }

    private func update(_ value: Double?) {
        if let value {
            silent = 0
            frequency = smoother.add(frequency: value)
        } else {
            silent += 1
            if silent > 12 {
                smoother.reset()
                frequency = nil
            }
        }
    }

    func stop() {
        if engine.isRunning {
            engine.inputNode.removeTap(onBus: 0)
            engine.stop()
        }
        listening = false
        if Self.demoFrequency() == nil { frequency = nil }
    }

    /// Screenshots in CI: a fixed pitch instead of the microphone (launch argument --demo-hz 110.1).
    static func demoFrequency() -> Double? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "--demo-hz"), i + 1 < args.count else { return nil }
        return Double(args[i + 1])
    }
}
