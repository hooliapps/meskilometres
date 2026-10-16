import KilometresCore
import UIKit

/// Rapport PDF avec le moteur de texte du systeme : toutes les ecritures,
/// sans police embarquee. A4, l'avertissement en pied de chaque page.
enum PdfRenderer {
	private static let pageW: CGFloat = 595
	private static let pageH: CGFloat = 842
	private static let margin: CGFloat = 36
	private static let footerH: CGFloat = 44

	static func render(_ doc: ReportDocument) -> Data {
		let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: pageW, height: pageH))
		let title: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 18)]
		let body: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10)]
		let bold: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 10)]
		let small: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 7), .foregroundColor: UIColor.darkGray]
		let width = pageW - 2 * margin

		return renderer.pdfData { ctx in
			var page = 0
			var y: CGFloat = 0

			func footer() {
				let fy = pageH - footerH
				UIColor.lightGray.setStroke()
				let line = UIBezierPath(); line.move(to: CGPoint(x: margin, y: fy)); line.addLine(to: CGPoint(x: pageW - margin, y: fy)); line.stroke()
				(doc.footer as NSString).draw(in: CGRect(x: margin, y: fy + 4, width: width, height: footerH - 16), withAttributes: small)
				("\(doc.generated) · \(page)" as NSString).draw(at: CGPoint(x: margin, y: pageH - 14), withAttributes: small)
			}
			func newPage() {
				if page > 0 { footer() }
				ctx.beginPage()
				page += 1
				y = margin
			}
			func height(_ s: String, _ attrs: [NSAttributedString.Key: Any], _ w: CGFloat) -> CGFloat {
				(s as NSString).boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs, context: nil).height
			}
			func ensure(_ h: CGFloat) { if page == 0 || y + h > pageH - footerH - 8 { newPage() } }
			func paragraph(_ s: String, _ attrs: [NSAttributedString.Key: Any], gap: CGFloat = 4) {
				let h = height(s, attrs, width)
				ensure(h)
				(s as NSString).draw(in: CGRect(x: margin, y: y, width: width, height: h), withAttributes: attrs)
				y += h + gap
			}
			func table(_ t: ReportDocument.Table) {
				let cols = t.headers.count
				var weights = [CGFloat](repeating: 1, count: cols)
				for i in t.rightAligned { weights[Int(truncating: i)] = 0.7 }
				if cols > 2 { weights[1] = 1.4; weights[2] = 1.4 }
				let total = weights.reduce(0, +)
				let colW = weights.map { width * $0 / total }
				func row(_ cells: [String], _ attrs: [NSAttributedString.Key: Any], background: UIColor?) {
					let heights = cells.enumerated().map { height($1, attrs, colW[$0] - 4) }
					let h = (heights.max() ?? 0) + 6
					ensure(h)
					if let background { background.setFill(); UIBezierPath(rect: CGRect(x: margin, y: y, width: width, height: h)).fill() }
					var x = margin
					for (i, cell) in cells.enumerated() {
						let style = NSMutableParagraphStyle()
						style.alignment = t.rightAligned.contains(KotlinInt(int: Int32(i))) ? .right : .left
						var a = attrs; a[.paragraphStyle] = style
						(cell as NSString).draw(in: CGRect(x: x + 2, y: y + 3, width: colW[i] - 4, height: heights[i]), withAttributes: a)
						x += colW[i]
					}
					y += h
					UIColor.lightGray.setStroke()
					let line = UIBezierPath(); line.move(to: CGPoint(x: margin, y: y)); line.addLine(to: CGPoint(x: pageW - margin, y: y)); line.stroke()
				}
				row(t.headers, bold, background: UIColor(white: 0.9, alpha: 1))
				for r in t.rows { row(r, body, background: nil) }
			}

			newPage()
			paragraph(doc.title, title, gap: 2)
			paragraph(doc.subtitle, body)
			for l in doc.headerLines { paragraph(l, body) }
			y += 8
			if !doc.summary.isEmpty {
				paragraph(doc.summaryTitle, bold)
				for pair in doc.summary {
					ensure(14)
					(pair.first! as NSString).draw(at: CGPoint(x: margin, y: y), withAttributes: body)
					let v = pair.second! as NSString
					let w = v.size(withAttributes: body).width
					v.draw(at: CGPoint(x: pageW - margin - w, y: y), withAttributes: body)
					y += 14
				}
				for n in doc.notes { paragraph("• \(n)", small) }
				y += 8
			}
			table(doc.tripsTable)
			if let e = doc.expensesTable {
				y += 12
				paragraph(doc.expensesTitle, bold)
				table(e)
			}
			footer()
		}
	}
}
