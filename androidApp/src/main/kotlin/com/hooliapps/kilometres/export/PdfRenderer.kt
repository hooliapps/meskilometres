package com.hooliapps.kilometres.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.hooliapps.kilometres.core.export.ReportDocument
import java.io.ByteArrayOutputStream

/**
 * Dessine le rapport avec le moteur de texte du systeme : toutes les
 * ecritures, toutes les polices, sans rien embarquer. Format A4, marges de
 * 36 points, l'avertissement en pied de CHAQUE page.
 */
object PdfRenderer {
	private const val PAGE_W = 595
	private const val PAGE_H = 842
	private const val MARGIN = 36f
	private const val FOOTER_H = 44f

	fun render(doc: ReportDocument): ByteArray {
		val pdf = PdfDocument()
		val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
		val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
		val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
		val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = Color.DKGRAY }
		val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
		val headerBg = Paint().apply { color = Color.rgb(230, 230, 230) }

		var page: PdfDocument.Page? = null
		var canvas: Canvas? = null
		var y = 0f
		var pageNo = 0
		val width = PAGE_W - 2 * MARGIN

		fun footer(c: Canvas) {
			val fy = PAGE_H - FOOTER_H
			c.drawLine(MARGIN, fy, PAGE_W - MARGIN, fy, line)
			val layout = StaticLayout.Builder.obtain(doc.footer, 0, doc.footer.length, small, width.toInt()).build()
			c.save(); c.translate(MARGIN, fy + 4f); layout.draw(c); c.restore()
			c.drawText("${doc.generated} · $pageNo", MARGIN, PAGE_H - 10f, small)
		}

		fun newPage() {
			page?.let { p -> footer(canvas!!); pdf.finishPage(p) }
			pageNo++
			page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
			canvas = page!!.canvas
			y = MARGIN
		}

		fun ensure(h: Float) {
			if (canvas == null || y + h > PAGE_H - FOOTER_H - 8f) newPage()
		}

		fun paragraph(text: String, paint: TextPaint, gap: Float = 4f) {
			val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt()).build()
			ensure(layout.height.toFloat())
			canvas!!.save(); canvas!!.translate(MARGIN, y); layout.draw(canvas!!); canvas!!.restore()
			y += layout.height + gap
		}

		fun table(t: ReportDocument.Table) {
			val cols = t.headers.size
			val weights = FloatArray(cols) { 1f }.also { w -> t.rightAligned.forEach { w[it] = 0.7f }; if (cols > 2) { w[1] = 1.4f; w[2] = 1.4f } }
			val total = weights.sum()
			val colW = weights.map { width * it / total }
			fun row(cells: List<String>, paint: TextPaint, bg: Paint?) {
				val layouts = cells.mapIndexed { i, s ->
					val align = if (i in t.rightAligned) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
					StaticLayout.Builder.obtain(s, 0, s.length, paint, (colW[i] - 4f).toInt().coerceAtLeast(10)).setAlignment(align).build()
				}
				val h = (layouts.maxOfOrNull { it.height } ?: 0) + 6f
				ensure(h)
				val c = canvas!!
				if (bg != null) c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + h, bg)
				var x = MARGIN
				layouts.forEachIndexed { i, l ->
					c.save(); c.translate(x + 2f, y + 3f); l.draw(c); c.restore()
					x += colW[i]
				}
				y += h
				c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
			}
			row(t.headers, bold, headerBg)
			for (r in t.rows) row(r, body, null)
		}

		newPage()
		paragraph(doc.title, title, 2f)
		paragraph(doc.subtitle, body)
		for (l in doc.headerLines) paragraph(l, body)
		y += 8f
		if (doc.summary.isNotEmpty()) {
			paragraph(doc.summaryTitle, bold)
			for ((k, v) in doc.summary) {
				ensure(14f)
				canvas!!.drawText(k, MARGIN, y + 10f, body)
				val w = body.measureText(v)
				canvas!!.drawText(v, PAGE_W - MARGIN - w, y + 10f, body)
				y += 14f
			}
			for (n in doc.notes) paragraph("• $n", small)
			y += 8f
		}
		table(doc.tripsTable)
		doc.expensesTable?.let { t ->
			y += 12f
			paragraph(doc.expensesTitle, bold)
			table(t)
		}
		footer(canvas!!)
		pdf.finishPage(page!!)
		val out = ByteArrayOutputStream()
		pdf.writeTo(out)
		pdf.close()
		return out.toByteArray()
	}
}
