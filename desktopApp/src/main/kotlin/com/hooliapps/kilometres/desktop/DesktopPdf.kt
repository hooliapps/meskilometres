package com.hooliapps.kilometres.desktop

import com.hooliapps.kilometres.core.export.ReportDocument
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Rapport PDF sur ordinateur : chaque page est dessinee avec les polices du
 * systeme (toutes les ecritures), puis inseree en image dans un PDF ecrit a
 * la main. Aucune bibliotheque. Le texte n'est pas selectionnable, mais le
 * rendu est fidele et imprimable ; les editions mobiles produisent du texte.
 */
object DesktopPdf {
	private const val W = 1240
	private const val H = 1754
	private const val MARGIN = 72
	private const val FOOTER = 90

	fun render(doc: ReportDocument): ByteArray {
		val pages = mutableListOf<BufferedImage>()
		var img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
		var g = img.createGraphics().also(::prepare)
		var y = MARGIN
		val title = Font(Font.SANS_SERIF, Font.BOLD, 34)
		val body = Font(Font.SANS_SERIF, Font.PLAIN, 19)
		val bold = Font(Font.SANS_SERIF, Font.BOLD, 19)
		val small = Font(Font.SANS_SERIF, Font.PLAIN, 14)
		val width = W - 2 * MARGIN

		fun footer(g: Graphics2D, n: Int) {
			g.color = Color.LIGHT_GRAY
			g.drawLine(MARGIN, H - FOOTER, W - MARGIN, H - FOOTER)
			g.color = Color.DARK_GRAY
			g.font = small
			var fy = H - FOOTER + 22
			for (line in wrap(g, doc.footer, width)) { g.drawString(line, MARGIN, fy); fy += 17 }
			g.drawString("${doc.generated} · $n", MARGIN, H - 20)
		}

		fun newPage() {
			footer(g, pages.size + 1)
			pages += img
			img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
			g = img.createGraphics().also(::prepare)
			y = MARGIN
		}

		fun ensure(h: Int) { if (y + h > H - FOOTER - 10) newPage() }

		fun paragraph(text: String, font: Font, gap: Int = 6) {
			g.font = font
			g.color = Color.BLACK
			val lines = wrap(g, text, width)
			val lh = g.fontMetrics.height
			ensure(lines.size * lh)
			for (l in lines) { g.drawString(l, MARGIN, y + g.fontMetrics.ascent); y += lh }
			y += gap
		}

		fun table(t: ReportDocument.Table) {
			val cols = t.headers.size
			val weights = FloatArray(cols) { 1f }.also { w -> t.rightAligned.forEach { w[it] = 0.7f }; if (cols > 2) { w[1] = 1.4f; w[2] = 1.4f } }
			val total = weights.sum()
			val colW = weights.map { (width * it / total).toInt() }
			fun row(cells: List<String>, font: Font, bg: Color?) {
				g.font = font
				val lh = g.fontMetrics.height
				val wrapped = cells.mapIndexed { i, s -> wrap(g, s, colW[i] - 8) }
				val h = (wrapped.maxOfOrNull { it.size } ?: 1) * lh + 8
				ensure(h)
				if (bg != null) { g.color = bg; g.fillRect(MARGIN, y, width, h) }
				g.color = Color.BLACK
				var x = MARGIN
				wrapped.forEachIndexed { i, lines ->
					var ly = y + 4 + g.fontMetrics.ascent
					for (l in lines) {
						val tx = if (i in t.rightAligned) x + colW[i] - 4 - g.fontMetrics.stringWidth(l) else x + 4
						g.drawString(l, tx, ly)
						ly += lh
					}
					x += colW[i]
				}
				y += h
				g.color = Color.LIGHT_GRAY
				g.drawLine(MARGIN, y, W - MARGIN, y)
			}
			row(t.headers, bold, Color(230, 230, 230))
			for (r in t.rows) row(r, body, null)
		}

		paragraph(doc.title, title, 2)
		paragraph(doc.subtitle, body)
		for (l in doc.headerLines) paragraph(l, body)
		y += 12
		if (doc.summary.isNotEmpty()) {
			paragraph(doc.summaryTitle, bold)
			g.font = body
			for ((k, v) in doc.summary) {
				ensure(26)
				g.color = Color.BLACK
				g.drawString(k, MARGIN, y + g.fontMetrics.ascent)
				g.drawString(v, W - MARGIN - g.fontMetrics.stringWidth(v), y + g.fontMetrics.ascent)
				y += 26
			}
			for (n in doc.notes) paragraph("• $n", small)
			y += 12
		}
		table(doc.tripsTable)
		doc.expensesTable?.let { t -> y += 18; paragraph(doc.expensesTitle, bold); table(t) }
		footer(g, pages.size + 1)
		pages += img
		return buildPdf(pages)
	}

	private fun prepare(g: Graphics2D) {
		g.color = Color.WHITE
		g.fillRect(0, 0, W, H)
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
	}

	private fun wrap(g: Graphics2D, text: String, maxWidth: Int): List<String> {
		val fm = g.fontMetrics
		val out = mutableListOf<String>()
		for (para in text.split('\n')) {
			var line = StringBuilder()
			for (word in para.split(' ')) {
				val candidate = if (line.isEmpty()) word else "$line $word"
				if (fm.stringWidth(candidate) <= maxWidth || line.isEmpty()) {
					line = StringBuilder(candidate)
				} else {
					out += line.toString()
					line = StringBuilder(word)
				}
			}
			out += line.toString()
		}
		return out
	}

	/** PDF A4 : une image JPEG par page, plein cadre. */
	private fun buildPdf(pages: List<BufferedImage>): ByteArray {
		val out = ByteArrayOutputStream()
		val offsets = mutableListOf<Int>()
		fun obj(n: Int, content: ByteArray) {
			offsets += out.size()
			out.write("$n 0 obj\n".toByteArray(Charsets.US_ASCII))
			out.write(content)
			out.write("\nendobj\n".toByteArray(Charsets.US_ASCII))
		}
		out.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
		val pageIds = pages.indices.map { 3 + it * 3 }
		obj(1, "<< /Type /Catalog /Pages 2 0 R >>".toByteArray())
		obj(2, "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>".toByteArray())
		pages.forEachIndexed { i, img ->
			val pageId = pageIds[i]
			val jpeg = ByteArrayOutputStream().also { ImageIO.write(img, "jpeg", it) }.toByteArray()
			val content = "q 595.28 0 0 841.89 0 0 cm /Im1 Do Q".toByteArray()
			obj(pageId, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.28 841.89] /Resources << /XObject << /Im1 ${pageId + 2} 0 R >> >> /Contents ${pageId + 1} 0 R >>".toByteArray())
			obj(pageId + 1, "<< /Length ${content.size} >>\nstream\n".toByteArray() + content + "\nendstream".toByteArray())
			obj(pageId + 2, "<< /Type /XObject /Subtype /Image /Width ${img.width} /Height ${img.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n".toByteArray() + jpeg + "\nendstream".toByteArray())
		}
		val xref = out.size()
		val count = offsets.size + 1
		out.write("xref\n0 $count\n0000000000 65535 f \n".toByteArray())
		for (o in offsets) out.write("%010d 00000 n \n".format(o).toByteArray())
		out.write("trailer\n<< /Size $count /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray())
		return out.toByteArray()
	}
}
