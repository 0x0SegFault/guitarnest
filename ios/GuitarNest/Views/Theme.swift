import SwiftUI
import UIKit

/// Warm spruce and rosewood tones, the same palette as Android; green is kept for "in tune".
enum Palette {
    static let primary = dynamic(light: 0x8B4A1C, dark: 0xFFB68A)
    static let primaryContainer = dynamic(light: 0xFFDCC6, dark: 0x6F3406)
    static let surface = dynamic(light: 0xFFF8F4, dark: 0x19120E)
    static let card = dynamic(light: 0xFBEBE2, dark: 0x261E19)
    static let outline = dynamic(light: 0x85736A, dark: 0xA08D83)
    static let outlineVariant = dynamic(light: 0xD7C2B7, dark: 0x52443B)
    static let inTune = dynamic(light: 0x2E8B57, dark: 0x5BD08F)
    static let close = dynamic(light: 0xC98A12, dark: 0xF2B53B)
    static let off = dynamic(light: 0xC4452F, dark: 0xFF7A62)

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        })
    }
}

/// Strumming arrows; the active step is highlighted while playing.
struct StrumRow: View {
    let steps: String
    let perBeat: Int
    var active: Int?
    var big = false

    var body: some View {
        // Long patterns (a 12-beat compás) get smaller cells so they fit the screen.
        let long = steps.count > 8
        let cell: CGFloat = big ? (long ? 25 : 34) : 18
        HStack(spacing: big ? (long ? 3 : 6) : 2) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, stroke in
                let on = index == active
                ZStack {
                    RoundedRectangle(cornerRadius: 6).fill(on ? Palette.primary : Color.clear)
                    symbol(stroke)
                        .font(.system(size: big ? (long ? 16 : 22) : 13, weight: index % max(1, perBeat) == 0 ? .heavy : .regular))
                        .foregroundStyle(on ? Color.white : (stroke == "-" ? Palette.outline : Color.primary))
                }
                .frame(width: cell, height: cell)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(steps.map(Self.spoken).joined(separator: " "))
    }

    private static func spoken(_ stroke: Character) -> String {
        switch stroke {
        case "D": return "down"
        case "U": return "up"
        case "x": return "mute"
        case "R": return "rasgueado"
        case "B": return "bass"
        case "b": return "alternate bass"
        case "-": return "rest"
        default: return "string \(stroke)"
        }
    }

    /// Strumming: arrows, R (rasgueado), × (muted). Picking: B (bass), b (alternate bass), string numbers.
    @ViewBuilder private func symbol(_ stroke: Character) -> some View {
        switch stroke {
        case "D": Image(systemName: "arrow.down")
        case "U": Image(systemName: "arrow.up")
        case "x": Text("×")
        case "-": Text("·")
        default: Text(String(stroke)).foregroundStyle(stroke == "R" ? Palette.inTune : Color.primary)
        }
    }
}
