import GuitarNestShared
import SwiftUI

struct TunerView: View {
    @StateObject private var engine = TunerEngine()
    @AppStorage("tuner.tuning") private var tuningId = "standard"
    @AppStorage("tuner.a4") private var a4 = 440
    @State private var locked: Int?

    private var tuning: Tuning { Bridge.shared.tuningById(id: tuningId) ?? Bridge.shared.tuningPresets()[0] }

    var body: some View {
        let reading = currentReading()
        let color = tone(reading.cents)
        ScrollView {
            VStack(spacing: 18) {
                Picker(L10n.t("tuning"), selection: $tuningId) {
                    ForEach(Bridge.shared.tuningPresets(), id: \.id) { Text(tuningLabel($0)).tag($0.id) }
                }
                .pickerStyle(.menu)
                if engine.denied { Text(L10n.t("mic_why")).foregroundStyle(Palette.off) }
                ZStack(alignment: .bottom) {
                    Gauge(cents: reading.cents, color: color).frame(height: 190)
                    VStack(spacing: 0) {
                        Text(reading.note).font(.system(size: 72, weight: .heavy)).foregroundStyle(color)
                        Text(reading.cents.map { String(format: "%+d ¢", Int($0.rounded())) } ?? " ").foregroundStyle(.secondary)
                    }
                }
                HStack {
                    Text(status(reading.cents)).font(.title2.bold()).foregroundStyle(color)
                    if let f = engine.frequency { Text(String(format: "· %.1f Hz", f)).foregroundStyle(.secondary) }
                }
                strings(reading, color)
                HStack {
                    Text(L10n.t("reference_pitch"))
                    Spacer()
                    Stepper("A4 = \(a4) Hz", value: $a4, in: 415...466).fixedSize()
                }
            }
            .padding()
        }
        .navigationTitle(L10n.t("tuner"))
        .onAppear { engine.start() }
        .onDisappear { engine.stop() }
        .onChange(of: tuningId) { locked = nil }
    }

    private struct Reading { var note: String; var cents: Double?; var string: Int? }

    private func currentReading() -> Reading {
        guard let f = engine.frequency else { return Reading(note: "–", cents: nil, string: locked) }
        let a = Double(a4)
        let target = locked ?? Int(tuning.nearestString(frequency: f, a4: a))
        let cents = tuning.centsOff(string: Int32(target), frequency: f, a4: a)
        if abs(cents) <= 100 { return Reading(note: String(tuning.names[target].dropLast()), cents: cents, string: target) }
        let nearest = Bridge.shared.noteReading(frequency: f, a4: a)
        return Reading(note: String(nearest.name.dropLast()), cents: nearest.cents, string: target)
    }

    private func tone(_ cents: Double?) -> Color {
        guard let cents else { return Palette.outline }
        if abs(cents) <= 5 { return Palette.inTune }
        return abs(cents) <= 15 ? Palette.close : Palette.off
    }

    private func status(_ cents: Double?) -> String {
        guard let cents else { return L10n.t(engine.listening ? "play_a_string" : "not_listening") }
        if abs(cents) <= 5 { return L10n.t("in_tune") }
        return L10n.t(cents < 0 ? "too_low" : "too_high")
    }

    /// The six strings; the string being tuned lights up. Tap to lock a string, tap again for automatic.
    private func strings(_ reading: Reading, _ color: Color) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(tuning.names.enumerated()), id: \.offset) { i, name in
                let on = reading.string == i && (reading.cents != nil || locked == i)
                Button {
                    locked = locked == i ? nil : i
                } label: {
                    VStack(spacing: 0) {
                        Text(String(name.dropLast())).font(.headline)
                            .foregroundStyle(on ? Color.white : Color.primary)
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(on ? color : Color.clear))
                            .overlay(Circle().stroke(locked == i ? color : Palette.outlineVariant, lineWidth: 2))
                        Rectangle().fill(on ? color : Palette.outline).frame(width: CGFloat(4.5 - Double(i) * 0.6) * (on ? 1.6 : 1), height: 110)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.t("string_description", 6 - i, name))
            }
        }
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 20).fill(Palette.card))
    }
}

/// Arc from -50 to +50 cents with a green zone of ±5 cents; a short pointer near the scale.
private struct Gauge: View {
    let cents: Double?
    let color: Color

    var body: some View {
        Canvas { context, size in
            let radius: CGFloat = min(size.width * 0.42, size.height * 0.9)
            let centre = CGPoint(x: size.width / 2, y: size.height * 0.98)
            func point(_ c: Double, _ r: CGFloat) -> CGPoint {
                let angle = (270.0 + c / 50.0 * 70.0) * Double.pi / 180
                return CGPoint(x: centre.x + r * CGFloat(cos(angle)), y: centre.y + r * CGFloat(sin(angle)))
            }
            var arc = Path()
            arc.addArc(center: centre, radius: radius, startAngle: .degrees(200), endAngle: .degrees(340), clockwise: false)
            context.stroke(arc, with: .color(Palette.outlineVariant), style: StrokeStyle(lineWidth: 10, lineCap: .round))
            var good = Path()
            good.addArc(center: centre, radius: radius, startAngle: .degrees(263), endAngle: .degrees(277), clockwise: false)
            context.stroke(good, with: .color(Palette.inTune), style: StrokeStyle(lineWidth: 10, lineCap: .round))
            if let cents {
                let c = max(-50, min(50, cents))
                var pointer = Path()
                pointer.move(to: point(c, radius * 0.72))
                pointer.addLine(to: point(c, radius))
                context.stroke(pointer, with: .color(color), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                let tip = point(c, radius)
                context.fill(Path(ellipseIn: CGRect(x: tip.x - 9, y: tip.y - 9, width: 18, height: 18)), with: .color(color))
            }
        }
        .accessibilityHidden(true)
    }
}
