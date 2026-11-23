import AVFoundation
import GuitarNestShared

/// Reference sounds for the tuner (a plucked string, a tuning fork), made by the shared synth.
@MainActor
final class TonePlayer {
    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private let rate = 48_000.0
    private var ready = false

    func pluck(midi: Int, a4: Double) {
        let f = Bridge.shared.frequencyOf(midi: Int32(midi), a4: a4)
        play(Bridge.shared.pluck(frequency: f, sampleRate: Int32(rate)))
    }

    func fork(a4: Double) {
        play(Bridge.shared.fork(frequency: a4, sampleRate: Int32(rate)))
    }

    private func play(_ samples: KotlinFloatArray) {
        guard let format = AVAudioFormat(standardFormatWithSampleRate: rate, channels: 1) else { return }
        if !ready {
            engine.attach(node)
            engine.connect(node, to: engine.mainMixerNode, format: format)
            ready = true
        }
        let count = Int(samples.size)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(count)), let data = buffer.floatChannelData?[0] else { return }
        for i in 0..<count { data[i] = samples.get(index: Int32(i)) }
        buffer.frameLength = AVAudioFrameCount(count)
        if !engine.isRunning { try? engine.start() }
        node.stop()
        node.scheduleBuffer(buffer, at: nil, options: .interrupts)
        node.play()
    }
}
