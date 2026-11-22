import AVFoundation
import Foundation

/// Metronome audio: clicks are written into the sample stream at exact positions on the audio thread,
/// so the timing does not depend on the UI. The tempo may change at each bar line (speed trainer, play along).
final class MetronomePlayer: ObservableObject {
    struct Position: Equatable {
        var bar: Int
        var beat: Int
        /// Sixteenth-note step in the bar.
        var step: Int
        var bpm: Int
    }

    @Published private(set) var position: Position?

    /// Tempo for a bar, or nil to stop (end of song). Called on the audio thread: keep it simple.
    var tempoForBar: (Int) -> Double? = { _ in nil }
    var onEnd: () -> Void = {}

    private let engine = AVAudioEngine()
    private var node: AVAudioSourceNode?
    private var timer: Timer?
    private let state = RenderState()

    private final class RenderState {
        var sampleRate = 48_000.0
        var beatsPerBar = 4
        var subdivisions = 1
        var bpm = 80.0
        var sample: Int64 = 0
        var nextClick = 0.0
        var sub = 0
        var bar = 0
        var barStart: Int64 = 0
        var clickIndex = -1
        var clickKind = 0
        var finished = false
        var clicks: [[Float]] = []
    }

    var playing: Bool { node != nil }

    func start(beatsPerBar: Int, subdivisions: Int) {
        stop()
        guard let first = tempoForBar(0) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
        let format = engine.outputNode.inputFormat(forBus: 0)
        let rate = format.sampleRate > 0 ? format.sampleRate : 48_000
        let s = state
        s.sampleRate = rate
        s.beatsPerBar = beatsPerBar
        s.subdivisions = max(1, subdivisions)
        s.bpm = first
        s.sample = 0
        s.nextClick = 0
        s.sub = 0
        s.bar = 0
        s.barStart = 0
        s.clickIndex = -1
        s.finished = false
        s.clicks = [Self.tone(1760, 0.9, rate), Self.tone(1175, 0.6, rate), Self.tone(880, 0.3, rate)]
        let tempo = tempoForBar
        let mono = AVAudioFormat(standardFormatWithSampleRate: rate, channels: 1)!
        let source = AVAudioSourceNode(format: mono) { _, _, frameCount, bufferList -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(bufferList)
            guard let out = buffers[0].mData?.assumingMemoryBound(to: Float.self) else { return noErr }
            for frame in 0..<Int(frameCount) {
                if !s.finished && Double(s.sample) >= s.nextClick {
                    s.clickKind = s.sub == 0 ? 0 : (s.sub % s.subdivisions == 0 ? 1 : 2)
                    s.clickIndex = 0
                    s.sub += 1
                    if s.sub == s.beatsPerBar * s.subdivisions {
                        s.sub = 0
                        s.bar += 1
                        s.barStart = Int64((s.nextClick + s.sampleRate * 60 / s.bpm / Double(s.subdivisions)).rounded())
                        if let next = tempo(s.bar) { s.bpm = next } else { s.finished = true }
                    }
                    s.nextClick += s.sampleRate * 60 / s.bpm / Double(s.subdivisions)
                }
                var value: Float = 0
                if s.clickIndex >= 0 {
                    let sound = s.clicks[s.clickKind]
                    value = sound[s.clickIndex]
                    s.clickIndex += 1
                    if s.clickIndex >= sound.count { s.clickIndex = -1 }
                }
                out[frame] = value
                s.sample += 1
            }
            return noErr
        }
        node = source
        engine.attach(source)
        engine.connect(source, to: engine.mainMixerNode, format: mono)
        do { try engine.start() } catch { stop(); return }
        timer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30, repeats: true) { [weak self] _ in self?.tick() }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        if let node {
            engine.stop()
            engine.detach(node)
        }
        node = nil
        position = nil
    }

    private func tick() {
        let s = state
        if s.finished && s.clickIndex < 0 && Double(s.sample) > s.nextClick {
            stop()
            onEnd()
            return
        }
        let samplesPerBeat = s.sampleRate * 60 / s.bpm
        let inBar = max(0, Double(s.sample - s.barStart)) / samplesPerBeat
        position = Position(bar: s.bar, beat: min(s.beatsPerBar - 1, Int(inBar)), step: Int(inBar * 4), bpm: Int(s.bpm.rounded()))
    }

    private static func tone(_ frequency: Double, _ gain: Double, _ rate: Double) -> [Float] {
        (0..<Int(rate * 0.03)).map { i in
            Float(gain * exp(-Double(i) / (rate * 0.006)) * sin(2 * .pi * frequency * Double(i) / rate))
        }
    }
}
