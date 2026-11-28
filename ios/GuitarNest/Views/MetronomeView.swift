import GuitarNestShared
import SwiftUI

struct MetronomeView: View {
    @EnvironmentObject var player: MetronomePlayer
    @AppStorage("metronome.bpm") private var bpm = 80
    @AppStorage("metronome.beats") private var beatsPerBar = 4
    @AppStorage("metronome.subdivisions") private var subdivisions = 1
    @AppStorage("metronome.pattern") private var patternId = ""
    @State private var trainer = false
    @State private var target = 120
    @State private var taps: [Date] = []
    @State private var mine = false

    private var pattern: StrumPattern? { Bridge.shared.presetPatterns().first { $0.id == patternId } }

    var body: some View {
        let position = mine ? player.position : nil
        ScrollView {
            VStack(spacing: 16) {
                HStack(spacing: 4) {
                    step("−5", -5)
                    step("−1", -1)
                    VStack {
                        Text("\(trainer ? (position?.bpm ?? bpm) : bpm)").font(.system(size: 56, weight: .heavy))
                        Text("BPM").font(.caption.bold())
                    }
                    .frame(minWidth: 110)
                    step("+1", 1)
                    step("+5", 5)
                }
                Slider(value: Binding(get: { Double(bpm) }, set: { bpm = Int($0) }), in: 30...250)
                HStack(spacing: 12) {
                    if mine {
                        Button { stop() } label: { Label(L10n.t("stop"), systemImage: "stop.fill") }.buttonStyle(.borderedProminent)
                    } else {
                        Button { start() } label: { Label(L10n.t("start"), systemImage: "play.fill") }.buttonStyle(.borderedProminent)
                    }
                    Button(L10n.t("tap_tempo")) { tap() }.buttonStyle(.bordered)
                }
                let many = beatsPerBar > 7
                HStack(spacing: many ? 5 : 10) {
                    ForEach(0..<beatsPerBar, id: \.self) { b in
                        let size: CGFloat = b == 0 ? (many ? 22 : 28) : (many ? 17 : 22)
                        Circle()
                            .fill(position?.beat == b ? (b == 0 ? Palette.primary : Palette.inTune) : Palette.outlineVariant)
                            .frame(width: size, height: size)
                    }
                }
                if let pattern {
                    StrumRow(steps: pattern.steps, perBeat: Int(pattern.perBeat), active: position.map { $0.step * Int(pattern.perBeat) / 4 }, big: true)
                }
                choice(L10n.t("beats_per_bar"), options: [2, 3, 4, 5, 6, 7, 12].map { ("\($0)", $0) }, selected: $beatsPerBar)
                choice(L10n.t("clicks"), options: [("♩", 1), ("♪♪", 2), ("3", 3), ("♬♬", 4)], selected: $subdivisions)
                VStack(alignment: .leading) {
                    Text(L10n.t("strumming_pattern")).font(.caption.bold()).foregroundStyle(.secondary)
                    Picker(L10n.t("strumming_pattern"), selection: $patternId) {
                        Text(L10n.t("none")).tag("")
                        ForEach(Bridge.shared.presetPatterns().filter { Bridge.shared.patternKind(pattern: $0) == "strum" }, id: \.id) { Text($0.name).tag($0.id) }
                    }
                    .pickerStyle(.menu)
                    // A 12-beat flamenco compás sets 12 beats per bar, so its accents land on the right beats.
                    .onChange(of: patternId) { _, _ in
                        if let p = pattern, Bridge.shared.patternBeats(pattern: p) != 4 { beatsPerBar = Int(Bridge.shared.patternBeats(pattern: p)) } else if beatsPerBar == 6 || beatsPerBar == 12 { beatsPerBar = 4 }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Toggle(isOn: $trainer) {
                    VStack(alignment: .leading) {
                        Text(L10n.t("speed_trainer"))
                        Text(L10n.t("speed_trainer_detail", bpm, target, 5, 4)).font(.caption).foregroundStyle(.secondary)
                    }
                }
                if trainer {
                    Stepper("\(L10n.t("target_tempo")): \(target)", value: $target, in: 30...250, step: 5)
                }
            }
            .padding()
        }
        .navigationTitle(L10n.t("metronome"))
        .onDisappear { if mine { stop() } }
        .onAppear { if Launch.play && Launch.tab == .metronome && !mine { patternId = patternId.isEmpty ? "folk" : patternId; start() } }
    }

    private func step(_ label: String, _ delta: Int) -> some View {
        Button(label) { bpm = max(30, min(250, bpm + delta)) }.buttonStyle(.bordered).frame(width: 56)
    }

    private func choice(_ title: String, options: [(String, Int)], selected: Binding<Int>) -> some View {
        VStack(alignment: .leading) {
            Text(title).font(.caption.bold()).foregroundStyle(.secondary)
            Picker(title, selection: selected) {
                ForEach(options, id: \.1) { Text($0.0).tag($0.1) }
            }
            .pickerStyle(.segmented)
        }
    }

    private func start() {
        let trainerPlan = Bridge.shared.speedTrainer(start: Int32(bpm), target: Int32(target), step: 5, barsPerStep: 4)
        var plan: [Double] = []
        for bar in 0..<2_000 { plan.append(trainer ? Double(trainerPlan.tempoForBar(bar: Int32(bar))) : Double(bpm)) }
        player.tempoForBar = { bar in plan[min(bar, plan.count - 1)] }
        player.onEnd = { mine = false }
        player.start(beatsPerBar: beatsPerBar, subdivisions: subdivisions)
        mine = true
    }

    private func stop() {
        player.stop()
        mine = false
    }

    /// Tempo from taps: median of the last intervals.
    private func tap() {
        let now = Date()
        if let last = taps.last, now.timeIntervalSince(last) > 2 { taps.removeAll() }
        taps.append(now)
        if taps.count > 6 { taps.removeFirst() }
        guard taps.count >= 3 else { return }
        let intervals = zip(taps.dropFirst(), taps).map { $0.timeIntervalSince($1) }.sorted()
        bpm = max(30, min(250, Int(60 / intervals[intervals.count / 2])))
    }
}
