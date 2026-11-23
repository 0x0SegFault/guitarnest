import GuitarNestShared
import SwiftUI

/// A chord diagram: six strings (low E on the left), five frets, dots, a bar for a barre, x / o above.
/// `highlight` (string index) lights up the string to pick now.
struct ChordDiagramView: View {
    let name: String
    let shape: ChordShape?
    var width: CGFloat = 64
    var highlight: Int?
    var active = false
    var showText = false

    var body: some View {
        VStack(spacing: 2) {
            Text(name).font(.system(size: max(13, min(26, width / 4)), weight: .heavy)).foregroundStyle(active ? Palette.primary : Color.primary)
            if let shape {
                let frets = Bridge.shared.shapeFrets(shape: shape).map { Int(truncating: $0) }
                let first = Int(Bridge.shared.shapeFirstFret(shape: shape))
                let barre = Bridge.shared.shapeBarre(shape: shape).map { Int(truncating: $0) }
                Canvas { context, size in
                    draw(context: context, size: size, frets: frets, first: first, barre: barre)
                }
                .frame(width: width, height: width * 1.15)
                if showText { Text(Bridge.shared.shapeText(shape: shape)).font(.caption2).foregroundStyle(.secondary) }
            } else {
                Text("?").foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(name + " " + (shape.map { Bridge.shared.shapeText(shape: $0) } ?? ""))
    }

    private func draw(context: GraphicsContext, size: CGSize, frets: [Int], first: Int, barre: [Int]) {
        let left: CGFloat = size.width * 0.16
        let right: CGFloat = size.width * 0.92
        let top: CGFloat = size.height * 0.16
        let bottom: CGFloat = size.height * 0.98
        let gapX: CGFloat = (right - left) / 5
        let gapY: CGFloat = (bottom - top) / 5
        let line: CGFloat = max(1.2, size.width / 70)
        for s in 0..<6 {
            let x = left + CGFloat(s) * gapX
            var path = Path()
            path.move(to: CGPoint(x: x, y: top))
            path.addLine(to: CGPoint(x: x, y: bottom))
            context.stroke(path, with: .color(highlight == s ? Palette.inTune : Color.primary), lineWidth: highlight == s ? line * 3 : line)
        }
        for f in 0...5 {
            let y = top + CGFloat(f) * gapY
            var path = Path()
            path.move(to: CGPoint(x: left, y: y))
            path.addLine(to: CGPoint(x: right, y: y))
            context.stroke(path, with: .color(.primary), lineWidth: f == 0 && first == 1 ? line * 4 : line)
        }
        if first > 1 {
            context.draw(Text("\(first)").font(.system(size: gapY * 0.6)).foregroundStyle(.secondary), at: CGPoint(x: left * 0.45, y: top + gapY * 0.5))
        }
        let radius: CGFloat = gapX * 0.34
        if barre.count == 3 {
            let y = top + (CGFloat(barre[0] - first) + 0.5) * gapY
            let rect = CGRect(x: left + CGFloat(barre[1]) * gapX - radius, y: y - radius, width: CGFloat(barre[2] - barre[1]) * gapX + 2 * radius, height: 2 * radius)
            context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(Palette.primary))
        }
        for (s, fret) in frets.enumerated() {
            let x = left + CGFloat(s) * gapX
            let markY = top - gapY * 0.45
            let lit = highlight == s
            if fret < 0 {
                context.draw(Text("×").font(.system(size: gapY * 0.6)).foregroundStyle(.secondary), at: CGPoint(x: x, y: markY))
            } else if fret == 0 {
                let r = radius * 0.7
                context.stroke(Path(ellipseIn: CGRect(x: x - r, y: markY - r, width: 2 * r, height: 2 * r)), with: .color(lit ? Palette.inTune : .primary), lineWidth: line * 1.5)
            } else {
                let y = top + (CGFloat(fret - first) + 0.5) * gapY
                context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: 2 * radius, height: 2 * radius)), with: .color(lit ? Palette.inTune : Palette.primary))
            }
        }
    }
}
