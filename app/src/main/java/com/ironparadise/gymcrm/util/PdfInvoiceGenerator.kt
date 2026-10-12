package com.ironparadise.gymcrm.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.ironparadise.gymcrm.data.model.SaleWithDetails
import java.io.File
import java.io.FileOutputStream

object PdfInvoiceGenerator {

    fun generateInvoicePdf(context: Context, saleDetails: SaleWithDetails): File {
        val sale = saleDetails.sale
        val items = saleDetails.items

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 (595x842 pt)
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        // Paint definitions
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

        // Header Background Banner (Stark Deep Black for Gym Branding)
        val bannerPaint = Paint().apply { color = Color.parseColor("#111111") }
        canvas.drawRect(0f, 0f, 595f, 130f, bannerPaint)

        // Accent gold line
        val goldLinePaint = Paint().apply {
            color = Color.parseColor("#FFB300")
            strokeWidth = 3f
        }
        canvas.drawLine(0f, 130f, 595f, 130f, goldLinePaint)

        // Title Paint
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("IRON PARADISE GYM", 36f, 48f, titlePaint)

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#D0D0D0")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("SUPPLEMENT INVENTORY & SALES CRM", 36f, 70f, subtitlePaint)
        canvas.drawText("Phone: +91 98765 43210  |  Email: contact@ironparadise.com", 36f, 88f, subtitlePaint)
        canvas.drawText("Address: Iron Paradise Fitness Complex, Main Gym Road", 36f, 104f, subtitlePaint)

        // TAX INVOICE tag on top right
        val tagPaint = Paint().apply {
            color = Color.parseColor("#FFB300")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("INVOICE", 559f, 50f, tagPaint)

        // Invoice Meta & Customer Box
        var yPos = 160f
        val labelPaint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.parseColor("#111111")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Left Column: Customer details
        canvas.drawText("BILLED TO:", 36f, yPos, labelPaint)
        canvas.drawText(sale.customerName, 36f, yPos + 18f, valuePaint)
        canvas.drawText("Phone: ${sale.customerPhone}", 36f, yPos + 34f, subtitlePaint.apply { color = Color.parseColor("#333333") })
        saleDetails.customer?.address?.let {
            canvas.drawText("Address: $it", 36f, yPos + 50f, subtitlePaint)
        }

        // Right Column: Invoice Info
        val rightAlignValuePaint = Paint().apply {
            color = Color.parseColor("#111111")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val rightAlignLabelPaint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        canvas.drawText("INVOICE NO:", 559f, yPos, rightAlignLabelPaint)
        canvas.drawText(sale.invoiceNumber, 559f, yPos + 18f, rightAlignValuePaint)
        canvas.drawText("DATE: ${Formatters.formatShortDate(sale.date)}", 559f, yPos + 34f, rightAlignValuePaint)
        canvas.drawText("PAYMENT: ${sale.paymentMethod}", 559f, yPos + 50f, rightAlignValuePaint)

        // Divider
        yPos = 240f
        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
        }
        canvas.drawLine(36f, yPos, 559f, yPos, linePaint)

        // Table Header
        yPos = 265f
        val tableHeaderBg = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(36f, yPos - 16f, 559f, yPos + 10f, tableHeaderBg)

        val thPaint = Paint().apply {
            color = Color.parseColor("#222222")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("ITEM / PRODUCT", 46f, yPos, thPaint)
        canvas.drawText("QTY", 330f, yPos, thPaint)
        canvas.drawText("PRICE", 400f, yPos, thPaint)
        canvas.drawText("TOTAL", 510f, yPos, thPaint)

        // Table Rows
        yPos += 26f
        val rowPaint = Paint().apply {
            color = Color.parseColor("#222222")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val rowBoldPaint = Paint().apply {
            color = Color.parseColor("#111111")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        for (item in items) {
            canvas.drawText(item.productName, 46f, yPos, rowBoldPaint)
            canvas.drawText("${item.quantity}", 335f, yPos, rowPaint)
            canvas.drawText(Formatters.formatCurrency(item.unitPrice), 400f, yPos, rowPaint)
            canvas.drawText(Formatters.formatCurrency(item.total), 510f, yPos, rowBoldPaint)

            canvas.drawLine(36f, yPos + 10f, 559f, yPos + 10f, linePaint)
            yPos += 26f
        }

        // Totals Section
        yPos += 15f
        val totalLabelPaint = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val totalValPaint = Paint().apply {
            color = Color.parseColor("#111111")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        canvas.drawText("Subtotal:", 460f, yPos, totalLabelPaint)
        canvas.drawText(Formatters.formatCurrency(sale.subtotal), 559f, yPos, totalValPaint)
        yPos += 20f

        if (sale.discount > 0) {
            canvas.drawText("Discount:", 460f, yPos, totalLabelPaint)
            canvas.drawText("- ${Formatters.formatCurrency(sale.discount)}", 559f, yPos, totalValPaint)
            yPos += 20f
        }

        // Grand Total Box
        val grandTotalBg = Paint().apply { color = Color.parseColor("#111111") }
        canvas.drawRect(330f, yPos - 5f, 559f, yPos + 32f, grandTotalBg)

        val grandTotalLabelPaint = Paint().apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val grandTotalValPaint = Paint().apply {
            color = Color.parseColor("#FFB300")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("GRAND TOTAL:", 450f, yPos + 18f, grandTotalLabelPaint)
        canvas.drawText(Formatters.formatCurrency(sale.grandTotal), 545f, yPos + 18f, grandTotalValPaint)

        // Footer
        val footerY = 760f
        canvas.drawLine(36f, footerY, 559f, footerY, linePaint)

        val footerHeading = Paint().apply {
            color = Color.parseColor("#111111")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("THANK YOU FOR CHOOSING IRON PARADISE GYM", 297f, footerY + 28f, footerHeading)

        val footerSub = Paint().apply {
            color = Color.parseColor("#777777")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Fuel Your Beast. Push Your Limits. No Excuses.", 297f, footerY + 44f, footerSub)
        canvas.drawText("This is a computer generated invoice and does not require a physical signature.", 297f, footerY + 58f, footerSub)

        pdfDoc.finishPage(page)

        // Write to output file
        val filename = "Invoice_${sale.invoiceNumber}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, filename)
        val outputStream = FileOutputStream(file)
        pdfDoc.writeTo(outputStream)
        outputStream.close()
        pdfDoc.close()

        return file
    }
}
