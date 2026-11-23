import GuitarNestShared
import UIKit

/// Print or save as PDF: the chord diagrams first, then the tab as monospace text (A4, 80 columns).
enum SongPrinter {
    static func print(_ song: Song) {
        let controller = UIPrintInteractionController.shared
        let info = UIPrintInfo(dictionary: nil)
        info.jobName = song.title
        info.outputType = .grayscale
        controller.printInfo = info
        controller.printingItem = pdf(song)
        controller.present(animated: true)
    }

    static func pdf(_ song: Song) -> Data {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let margin: CGFloat = 36
        let lineHeight: CGFloat = 9.5
        let font = UIFont.monospacedSystemFont(ofSize: 7.5, weight: .regular)
        let bold = UIFont.monospacedSystemFont(ofSize: 7.5, weight: .bold)
        let lines = Bridge.shared.printText(song: song).components(separatedBy: "\n")
        let chords = Bridge.shared.chordsInSong(song: song)
        return UIGraphicsPDFRenderer(bounds: page).pdfData { context in
            context.beginPage()
            var y = margin
            func text(_ line: String, _ face: UIFont) {
                if y > page.height - margin {
                    context.beginPage()
                    y = margin
                }
                (line as NSString).draw(at: CGPoint(x: margin, y: y), withAttributes: [.font: face])
                y += lineHeight
            }
            text(lines.first ?? "", bold)
            if !chords.isEmpty {
                y += lineHeight * 0.5
                text(L10n.t("chords_in_song"), bold)
                for (i, name) in chords.enumerated() {
                    let x = margin + CGFloat(i % 8) * 62
                    let top = y + CGFloat(i / 8) * 64
                    diagram(name, Bridge.shared.shapeFor(song: song, chord: name), x: x, y: top)
                }
                y += CGFloat((chords.count + 7) / 8) * 64 + 6
            }
            for line in lines.dropFirst() { text(line, font) }
        }
    }

    private static func diagram(_ name: String, _ shape: ChordShape?, x: CGFloat, y: CGFloat) {
        (name as NSString).draw(at: CGPoint(x: x + 4, y: y), withAttributes: [.font: UIFont.boldSystemFont(ofSize: 9)])
        guard let shape else { return }
        let frets = Bridge.shared.shapeFrets(shape: shape).map { Int(truncating: $0) }
        let first = Int(Bridge.shared.shapeFirstFret(shape: shape))
        let barre = Bridge.shared.shapeBarre(shape: shape).map { Int(truncating: $0) }
        let left = x + 10
        let top = y + 20
        let gap: CGFloat = 7
        UIColor.black.setStroke()
        UIColor.black.setFill()
        for s in 0..<6 {
            let path = UIBezierPath()
            path.move(to: CGPoint(x: left + CGFloat(s) * gap, y: top))
            path.addLine(to: CGPoint(x: left + CGFloat(s) * gap, y: top + 5 * gap))
            path.lineWidth = 0.7
            path.stroke()
        }
        for f in 0...5 {
            let path = UIBezierPath()
            path.move(to: CGPoint(x: left, y: top + CGFloat(f) * gap))
            path.addLine(to: CGPoint(x: left + 5 * gap, y: top + CGFloat(f) * gap))
            path.lineWidth = f == 0 && first == 1 ? 2 : 0.7
            path.stroke()
        }
        let small: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 6)]
        if first > 1 { ("\(first)" as NSString).draw(at: CGPoint(x: x, y: top), withAttributes: small) }
        if barre.count == 3 {
            let centerY = top + (CGFloat(barre[0] - first) + 0.5) * gap
            let bar = CGRect(x: left + CGFloat(barre[1]) * gap - 2.5, y: centerY - 2.5, width: CGFloat(barre[2] - barre[1]) * gap + 5, height: 5)
            UIBezierPath(roundedRect: bar, cornerRadius: 2.5).fill()
        }
        for (s, fret) in frets.enumerated() {
            let centerX = left + CGFloat(s) * gap
            if fret < 0 {
                ("x" as NSString).draw(at: CGPoint(x: centerX - 1.8, y: top - 8), withAttributes: small)
            } else if fret == 0 {
                ("o" as NSString).draw(at: CGPoint(x: centerX - 1.8, y: top - 8), withAttributes: small)
            } else {
                UIBezierPath(ovalIn: CGRect(x: centerX - 2.6, y: top + (CGFloat(fret - first) + 0.5) * gap - 2.6, width: 5.2, height: 5.2)).fill()
            }
        }
    }
}
