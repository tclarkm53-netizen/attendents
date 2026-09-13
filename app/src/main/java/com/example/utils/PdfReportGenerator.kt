package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.ClassEntity
import com.example.data.local.entity.FeePaymentEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.model.ReportCardDesignConfig
import com.example.data.model.ReportWidgetType
import com.example.data.repository.ClassReportData
import com.example.data.repository.SingleStudentReportData
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width in points
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN = 40f

    /**
     * Generate Class Attendance PDF File
     */
    fun generateClassReportPdf(context: Context, report: ClassReportData, institution: String): File {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 58, 138)
            textSize = 18f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        var y = MARGIN + 20f

        // Draw Header
        val displayInstitution = if (institution.isNotBlank()) institution else "Academic Institution"
        canvas.drawText(displayInstitution.uppercase(Locale.getDefault()), MARGIN, y, titlePaint)
        y += 22f

        paint.color = Color.rgb(30, 58, 138)
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CLASS ATTENDANCE REPORT", MARGIN, y, paint)
        y += 18f

        val classTitle = "Class: ${report.classEntity.className} (Section: ${report.classEntity.section.ifBlank { "N/A" }})"
        val subjectInfo = if (report.classEntity.subject.isNotBlank()) " | Subject: ${report.classEntity.subject}" else ""
        canvas.drawText(classTitle + subjectInfo, MARGIN, y, textPaint)
        y += 14f

        val periodText = "Period: ${report.fromDate} to ${report.toDate}  |  Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        canvas.drawText(periodText, MARGIN, y, subPaint)
        y += 16f

        // Summary Card Box
        val boxPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 42f, boxPaint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawText("Total Students: ${report.totalStudents}", MARGIN + 12f, y + 25f, paint)

        val pctText = "Overall Attendance: ${String.format(Locale.US, "%.1f", report.overallPercentage)}%"
        paint.color = if (report.overallPercentage >= 75f) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38)
        canvas.drawText(pctText, PAGE_WIDTH - MARGIN - 170f, y + 25f, paint)
        y += 56f

        // Table Header
        val thBgPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, thBgPaint)

        val thY = y + 15f
        canvas.drawText("Roll", MARGIN + 6f, thY, headerPaint)
        canvas.drawText("Student Name", MARGIN + 40f, thY, headerPaint)
        canvas.drawText("Total", MARGIN + 230f, thY, headerPaint)
        canvas.drawText("Present", MARGIN + 280f, thY, headerPaint)
        canvas.drawText("Absent", MARGIN + 340f, thY, headerPaint)
        canvas.drawText("Late", MARGIN + 395f, thY, headerPaint)
        canvas.drawText("Att %", MARGIN + 450f, thY, headerPaint)
        y += 26f

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        // Table Rows
        for ((idx, item) in report.items.withIndex()) {
            if (y > PAGE_HEIGHT - MARGIN - 40f) {
                // New page needed
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN + 20f

                // Re-draw small header on subsequent pages
                canvas.drawText("Class Attendance Report (Cont.) - Page $pageNumber", MARGIN, y, subPaint)
                y += 20f
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, thBgPaint)
                val newThY = y + 15f
                canvas.drawText("Roll", MARGIN + 6f, newThY, headerPaint)
                canvas.drawText("Student Name", MARGIN + 40f, newThY, headerPaint)
                canvas.drawText("Total", MARGIN + 230f, newThY, headerPaint)
                canvas.drawText("Present", MARGIN + 280f, newThY, headerPaint)
                canvas.drawText("Absent", MARGIN + 340f, newThY, headerPaint)
                canvas.drawText("Late", MARGIN + 395f, newThY, headerPaint)
                canvas.drawText("Att %", MARGIN + 450f, newThY, headerPaint)
                y += 26f
            }

            if (idx % 2 == 1) {
                val rowBg = Paint().apply {
                    color = Color.rgb(248, 250, 252)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN, y - 4f, PAGE_WIDTH - MARGIN, y + 16f, rowBg)
            }

            canvas.drawText(item.rollNumber, MARGIN + 6f, y + 10f, textPaint)
            val displayName = if (item.name.length > 28) item.name.substring(0, 26) + ".." else item.name
            canvas.drawText(displayName, MARGIN + 40f, y + 10f, textPaint)
            canvas.drawText("${item.totalDays}", MARGIN + 230f, y + 10f, textPaint)
            canvas.drawText("${item.present}", MARGIN + 280f, y + 10f, textPaint)
            canvas.drawText("${item.absent}", MARGIN + 340f, y + 10f, textPaint)
            canvas.drawText("${item.late}", MARGIN + 395f, y + 10f, textPaint)

            val pctColor = if (item.percentage >= 75f) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38)
            val pctPaint = Paint(textPaint).apply {
                color = pctColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("${String.format(Locale.US, "%.1f", item.percentage)}%", MARGIN + 450f, y + 10f, pctPaint)

            canvas.drawLine(MARGIN, y + 16f, PAGE_WIDTH - MARGIN, y + 16f, linePaint)
            y += 20f
        }

        // Footer signatures
        y = (y + 35f).coerceAtMost(PAGE_HEIGHT - MARGIN - 20f)
        canvas.drawLine(MARGIN + 20f, y, MARGIN + 160f, y, linePaint)
        canvas.drawText("Class Teacher's Signature", MARGIN + 25f, y + 14f, subPaint)

        canvas.drawLine(PAGE_WIDTH - MARGIN - 160f, y, PAGE_WIDTH - MARGIN - 20f, y, linePaint)
        canvas.drawText("Principal's Signature", PAGE_WIDTH - MARGIN - 150f, y + 14f, subPaint)

        document.finishPage(page)

        val cleanClassName = report.classEntity.className.replace("\\s+".toRegex(), "_")
        val fileName = "Attendance_Class_${cleanClassName}_${System.currentTimeMillis()}.pdf"
        val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(outputDir, fileName)

        val out = FileOutputStream(outputFile)
        document.writeTo(out)
        out.flush()
        out.close()
        document.close()

        return outputFile
    }

    /**
     * Generate Individual Student Attendance PDF File
     */
    fun generateStudentReportPdf(context: Context, report: SingleStudentReportData, institution: String): File {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 58, 138)
            textSize = 18f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        var y = MARGIN + 20f

        // Institution & Title
        val displayInstitution = if (institution.isNotBlank()) institution else "Academic Institution"
        canvas.drawText(displayInstitution.uppercase(Locale.getDefault()), MARGIN, y, titlePaint)
        y += 22f

        paint.color = Color.rgb(30, 58, 138)
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("STUDENT INDIVIDUAL ATTENDANCE REPORT", MARGIN, y, paint)
        y += 20f

        // Student Details
        val nameText = "Name: ${report.student.name}   |   Roll: ${report.student.rollNumber}"
        canvas.drawText(nameText, MARGIN, y, headerPaint)
        y += 14f

        val classInfo = "Class: ${report.classEntity?.className ?: "N/A"} (${report.classEntity?.section ?: ""})   |   Gender: ${report.student.gender}"
        canvas.drawText(classInfo, MARGIN, y, textPaint)
        y += 14f

        val periodText = "Period: ${report.fromDate} to ${report.toDate}  |  Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        canvas.drawText(periodText, MARGIN, y, subPaint)
        y += 18f

        // Summary Metric Box
        val boxPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 46f, boxPaint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(15, 23, 42)
        val metrics = "Total Working Days: ${report.totalDays}  |  Present: ${report.present}  |  Absent: ${report.absent}  |  Late: ${report.late}"
        canvas.drawText(metrics, MARGIN + 12f, y + 20f, paint)

        val pctPaint = Paint(paint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = if (report.percentage >= 75f) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38)
        }
        val pctString = "Attendance Percentage: ${String.format(Locale.US, "%.1f", report.percentage)}%"
        canvas.drawText(pctString, MARGIN + 12f, y + 36f, pctPaint)
        y += 60f

        // Daily Records Table
        val thBgPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, thBgPaint)

        val thY = y + 15f
        canvas.drawText("Sl", MARGIN + 8f, thY, headerPaint)
        canvas.drawText("Date", MARGIN + 40f, thY, headerPaint)
        canvas.drawText("Attendance Status", MARGIN + 150f, thY, headerPaint)
        canvas.drawText("Remarks / Notes", MARGIN + 310f, thY, headerPaint)
        y += 26f

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        for ((idx, rec) in report.records.withIndex()) {
            if (y > PAGE_HEIGHT - MARGIN - 40f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN + 20f

                canvas.drawText("Student Attendance History (Cont.) - Page $pageNumber", MARGIN, y, subPaint)
                y += 20f
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, thBgPaint)
                val newThY = y + 15f
                canvas.drawText("Sl", MARGIN + 8f, newThY, headerPaint)
                canvas.drawText("Date", MARGIN + 40f, newThY, headerPaint)
                canvas.drawText("Attendance Status", MARGIN + 150f, newThY, headerPaint)
                canvas.drawText("Remarks / Notes", MARGIN + 310f, newThY, headerPaint)
                y += 26f
            }

            if (idx % 2 == 1) {
                val rowBg = Paint().apply {
                    color = Color.rgb(248, 250, 252)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN, y - 4f, PAGE_WIDTH - MARGIN, y + 16f, rowBg)
            }

            canvas.drawText("${idx + 1}", MARGIN + 8f, y + 10f, textPaint)
            canvas.drawText(rec.date, MARGIN + 40f, y + 10f, textPaint)

            val statusColor = when (rec.status) {
                "PRESENT" -> Color.rgb(22, 163, 74)
                "ABSENT" -> Color.rgb(220, 38, 38)
                "LATE" -> Color.rgb(217, 119, 6)
                else -> Color.rgb(37, 99, 235)
            }
            val statusPaint = Paint(textPaint).apply {
                color = statusColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(rec.status, MARGIN + 150f, y + 10f, statusPaint)
            canvas.drawText(rec.remarks.ifBlank { "-" }, MARGIN + 310f, y + 10f, textPaint)

            canvas.drawLine(MARGIN, y + 16f, PAGE_WIDTH - MARGIN, y + 16f, linePaint)
            y += 20f
        }

        // Signatures
        y = (y + 35f).coerceAtMost(PAGE_HEIGHT - MARGIN - 20f)
        canvas.drawLine(MARGIN + 20f, y, MARGIN + 160f, y, linePaint)
        canvas.drawText("Guardian's Signature", MARGIN + 30f, y + 14f, subPaint)

        canvas.drawLine(PAGE_WIDTH - MARGIN - 160f, y, PAGE_WIDTH - MARGIN - 20f, y, linePaint)
        canvas.drawText("Authorized Signatory", PAGE_WIDTH - MARGIN - 150f, y + 14f, subPaint)

        document.finishPage(page)

        val cleanStudentName = report.student.name.replace("\\s+".toRegex(), "_")
        val fileName = "Attendance_${cleanStudentName}_${System.currentTimeMillis()}.pdf"
        val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(outputDir, fileName)

        val out = FileOutputStream(outputFile)
        document.writeTo(out)
        out.flush()
        out.close()
        document.close()

        return outputFile
    }

    /**
     * Convert number to Bengali words for receipt vouchers
     */
    fun convertNumberToBengaliWords(amount: Double): String {
        val num = amount.toLong()
        if (num <= 0L) return "শূন্য টাকা মাত্র"
        val units = mapOf(
            1L to "এক", 2L to "দুই", 3L to "তিন", 4L to "চার", 5L to "পাঁচ",
            6L to "ছয়", 7L to "সাত", 8L to "আট", 9L to "নয়", 10L to "দশ",
            11L to "এগারো", 12L to "বারো", 13L to "তেরো", 14L to "চৌদ্দ", 15L to "পনেরো",
            16L to "ষোল", 17L to "সতেরো", 18L to "আঠারো", 19L to "উনিশ", 20L to "বিশ",
            21L to "একুশ", 22L to "বাইশ", 23L to "তেইশ", 24L to "চব্বিশ", 25L to "পঁচিশ",
            28L to "আঠাশ", 30L to "ত্রিশ", 35L to "পঁয়ত্রিশ", 40L to "চল্লিশ", 50L to "পঞ্চাশ",
            60L to "ষাট", 70L to "সত্তর", 80L to "আশি", 90L to "নব্বই"
        )

        fun smallNumToWords(n: Long): String {
            if (n <= 0L) return ""
            if (units.containsKey(n)) return units[n]!!
            val tens = (n / 10) * 10
            val rem = n % 10
            val tensStr = units[tens] ?: "${tens}"
            val remStr = units[rem] ?: "${rem}"
            return "$tensStr $remStr"
        }

        val crore = num / 10000000L
        val lakh = (num % 10000000L) / 100000L
        val thousand = (num % 100000L) / 1000L
        val hundred = (num % 1000L) / 100L
        val rest = num % 100L

        val parts = mutableListOf<String>()
        if (crore > 0) parts.add("${smallNumToWords(crore)} কোটি")
        if (lakh > 0) parts.add("${smallNumToWords(lakh)} লাখ")
        if (thousand > 0) parts.add("${smallNumToWords(thousand)} হাজার")
        if (hundred > 0) parts.add("${smallNumToWords(hundred)} শত")
        if (rest > 0) parts.add(smallNumToWords(rest))

        return parts.joinToString(" ") + " টাকা মাত্র"
    }

    /**
     * Generate Official Fee Payment Receipt Voucher PDF (with full school voucher layout)
     */
    fun generateFeeReceiptPdf(
        context: Context,
        payment: FeePaymentEntity,
        student: StudentEntity,
        classEntity: ClassEntity?,
        institution: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, 520, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val primaryColor = Color.rgb(13, 71, 161) // Royal Blue
        val accentGreen = Color.rgb(22, 163, 74) // Present Green

        // 1. Draw Double Border
        val borderPaint = Paint().apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(20f, 20f, PAGE_WIDTH - 20f, 500f, 12f, 12f, borderPaint)
        borderPaint.strokeWidth = 1f
        canvas.drawRoundRect(25f, 25f, PAGE_WIDTH - 25f, 495f, 8f, 8f, borderPaint)

        var y = 52f

        // Top Header
        val instPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = primaryColor
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }
        val displayInst = if (institution.isNotBlank()) institution else "শিক্ষাপ্রতিষ্ঠান"
        canvas.drawText(displayInst.uppercase(Locale.getDefault()), (PAGE_WIDTH / 2).toFloat(), y, instPaint)
        y += 20f

        val titleBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 41, 59)
            textSize = 13f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("বেতন ও ফি আদায় রসিদ (MONEY RECEIPT)", (PAGE_WIDTH / 2).toFloat(), y, titleBadgePaint)
        y += 14f

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("অফিস কপি ও ছাত্র কপি সমন্বিত মূল রসিদ", (PAGE_WIDTH / 2).toFloat(), y, subPaint)
        y += 15f

        // Separator line
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }
        canvas.drawLine(35f, y, PAGE_WIDTH - 35f, y, linePaint)
        y += 18f

        // Receipt Meta Bar (Receipt No, Date, Status Badge)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = primaryColor
            textSize = 10.5f
        }

        canvas.drawText("রসিদ নং: ${payment.receiptNo}", 40f, y, boldPaint)
        canvas.drawText("পরিশোধের তারিখ: ${payment.paymentDate}", (PAGE_WIDTH / 2 - 40).toFloat(), y, textPaint)

        // Paid Stamp
        val paidBadgeBg = Paint().apply {
            color = Color.rgb(220, 252, 231)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(PAGE_WIDTH - 135f, y - 14f, PAGE_WIDTH - 40f, y + 6f, 4f, 4f, paidBadgeBg)
        val paidTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = accentGreen
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("পরিশোধিত (PAID)", PAGE_WIDTH - 87f, y, paidTextPaint)
        y += 24f

        // Student Info Box
        val infoBoxPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(35f, y, PAGE_WIDTH - 35f, y + 62f, 6f, 6f, infoBoxPaint)

        val infoY = y + 20f
        canvas.drawText("শিক্ষার্থীর নাম:", 45f, infoY, textPaint)
        canvas.drawText(student.name, 115f, infoY, boldPaint)

        canvas.drawText("রোল নম্বর:", (PAGE_WIDTH / 2 + 10).toFloat(), infoY, textPaint)
        canvas.drawText(student.rollNumber, (PAGE_WIDTH / 2 + 70).toFloat(), infoY, boldPaint)

        val infoY2 = infoY + 20f
        val className = classEntity?.className ?: "N/A"
        val section = if (classEntity != null && classEntity.section.isNotBlank()) " (শাখা: ${classEntity.section})" else ""
        canvas.drawText("শ্রেণি ও শাখা:", 45f, infoY2, textPaint)
        canvas.drawText(className + section, 115f, infoY2, textPaint)

        if (student.phone.isNotBlank()) {
            canvas.drawText("যোগাযোগ:", (PAGE_WIDTH / 2 + 10).toFloat(), infoY2, textPaint)
            canvas.drawText(student.phone, (PAGE_WIDTH / 2 + 70).toFloat(), infoY2, textPaint)
        }
        y += 75f

        // Fee Particulars Table
        val thBg = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(35f, y, PAGE_WIDTH - 35f, y + 22f, thBg)

        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
        }
        canvas.drawText("ক্র.", 45f, y + 15f, thPaint)
        canvas.drawText("বিবরণ (Particulars)", 80f, y + 15f, thPaint)
        canvas.drawText("মাসের বিবরণ", 260f, y + 15f, thPaint)
        canvas.drawText("পরিশোধ পদ্ধতি", 380f, y + 15f, thPaint)
        canvas.drawText("পরিমাণ (টাকা)", PAGE_WIDTH - 110f, y + 15f, thPaint)
        y += 24f

        // Row 1
        canvas.drawText("১.", 45f, y + 16f, textPaint)
        canvas.drawText("শিক্ষার্থীর মাসিক বেতন ও ফি", 80f, y + 16f, textPaint)
        val monthStr = if (payment.monthCovered.isNotBlank()) payment.monthCovered else "নিয়মিত বেতন"
        canvas.drawText(monthStr, 260f, y + 16f, textPaint)
        val noteStr = if (payment.note.isNotBlank()) payment.note else "ক্যাশ / অফলাইন"
        canvas.drawText(noteStr, 380f, y + 16f, textPaint)

        val amtStr = "৳${payment.amountPaid.toInt()}/-"
        canvas.drawText(amtStr, PAGE_WIDTH - 110f, y + 16f, boldPaint)
        canvas.drawLine(35f, y + 24f, PAGE_WIDTH - 35f, y + 24f, linePaint)
        y += 30f

        // Total & In Words Box
        val totalBoxPaint = Paint().apply {
            color = Color.rgb(238, 242, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(35f, y, PAGE_WIDTH - 35f, y + 40f, 6f, 6f, totalBoxPaint)

        val inWords = convertNumberToBengaliWords(payment.amountPaid)
        canvas.drawText("কথায়: $inWords", 45f, y + 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        })

        val totalAmtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = accentGreen
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("মোট পরিশোধ: ৳${payment.amountPaid.toInt()} টাকা", PAGE_WIDTH - 45f, y + 25f, totalAmtPaint)
        y += 55f

        // Signatures Block
        val sigY = y + 26f
        canvas.drawLine(50f, sigY, 180f, sigY, linePaint)
        val sigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("আদায়কারী / ক্যাশিয়ার", 115f, sigY + 12f, sigTextPaint)

        canvas.drawLine(PAGE_WIDTH - 180f, sigY, PAGE_WIDTH - 50f, sigY, linePaint)
        canvas.drawText("প্রধান শিক্ষক / অধ্যক্ষ", PAGE_WIDTH - 115f, sigY + 12f, sigTextPaint)

        // Footer Note
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("* এটি কম্পিউটার জেনারেটেড ডিজিটাল রসিদ। কোনো কাটাকাটি বা ঘষামাজা গ্রহণযোগ্য নয়।", (PAGE_WIDTH / 2).toFloat(), 485f, footerPaint)

        document.finishPage(page)

        val cleanStudentName = student.name.replace("\\s+".toRegex(), "_")
        val fileName = "Receipt_${payment.receiptNo}_${cleanStudentName}.pdf"
        val outputDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val outputFile = File(outputDir, fileName)

        val out = FileOutputStream(outputFile)
        document.writeTo(out)
        out.flush()
        out.close()
        document.close()

        return outputFile
    }

    /**
     * Generate Highly Customized Report Card PDF with theme colors, borders, and custom remarks
     * Renders widgets dynamically according to config.widgetOrder and config.enabledWidgets!
     */
    fun generateCustomReportCardPdf(
        context: Context,
        report: SingleStudentReportData,
        config: ReportCardDesignConfig
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val primaryColor = config.theme.primaryColorArgb
        val secondaryColor = config.theme.secondaryColorArgb
        val badgeColor = config.theme.badgeColorArgb

        // 1. Draw Decorative Border
        val borderPaint = Paint().apply {
            color = primaryColor
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        when (config.borderStyle) {
            "DOUBLE" -> {
                borderPaint.strokeWidth = 3f
                canvas.drawRect(20f, 20f, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f, borderPaint)
                borderPaint.strokeWidth = 1f
                canvas.drawRect(26f, 26f, PAGE_WIDTH - 26f, PAGE_HEIGHT - 26f, borderPaint)
            }
            "ROUNDED" -> {
                borderPaint.strokeWidth = 2.5f
                canvas.drawRoundRect(20f, 20f, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f, 16f, 16f, borderPaint)
            }
            else -> { // MINIMAL
                borderPaint.strokeWidth = 1.5f
                canvas.drawRect(20f, 20f, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f, borderPaint)
            }
        }

        var y = 50f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }
        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = primaryColor
            textSize = 12f
        }
        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }

        // Render widgets in the exact custom order configured by user
        for (widget in config.widgetOrder) {
            if (!config.isWidgetEnabled(widget)) continue

            when (widget) {
                ReportWidgetType.HEADER -> {
                    val institutionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        color = primaryColor
                        textSize = 20f
                        textAlign = if (config.headerAlignment == "LEFT") Paint.Align.LEFT else Paint.Align.CENTER
                    }
                    val headerX = if (config.headerAlignment == "LEFT") 42f else (PAGE_WIDTH / 2).toFloat()
                    val instName = if (config.customInstitution.isNotBlank()) config.customInstitution else "ACADEMIC INSTITUTION"
                    canvas.drawText(instName.uppercase(Locale.getDefault()), headerX, y, institutionPaint)
                    y += 24f

                    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        color = Color.rgb(30, 41, 59)
                        textSize = 14f
                        textAlign = if (config.headerAlignment == "LEFT") Paint.Align.LEFT else Paint.Align.CENTER
                    }
                    canvas.drawText(config.customReportTitle, headerX, y, titlePaint)
                    y += 18f

                    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(100, 116, 139)
                        textSize = 10f
                        textAlign = if (config.headerAlignment == "LEFT") Paint.Align.LEFT else Paint.Align.CENTER
                    }
                    canvas.drawText(config.customSubtitle, headerX, y, subPaint)
                    y += 18f

                    val accentLinePaint = Paint().apply {
                        color = primaryColor
                        strokeWidth = 2f
                    }
                    canvas.drawLine(40f, y, (PAGE_WIDTH - 40).toFloat(), y, accentLinePaint)
                    y += 22f
                }

                ReportWidgetType.STUDENT_INFO -> {
                    val studentBoxPaint = Paint().apply {
                        color = secondaryColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 68f, 8f, 8f, studentBoxPaint)

                    canvas.drawText("শিক্ষার্থীর নাম: ${report.student.name}", 56f, y + 24f, boldTextPaint)
                    canvas.drawText("রোল নম্বর: ${report.student.rollNumber}", (PAGE_WIDTH - 180).toFloat(), y + 24f, boldTextPaint)

                    val classStr = "শ্রেণি: ${report.classEntity?.className ?: "N/A"} (${report.classEntity?.section?.ifBlank { "সকল শাখা" } ?: ""})"
                    canvas.drawText(classStr, 56f, y + 44f, textPaint)

                    if (config.showGuardianPhone && report.student.phone.isNotBlank()) {
                        canvas.drawText("অভিভাবক মোবাইল: ${report.student.phone}", (PAGE_WIDTH - 220).toFloat(), y + 44f, textPaint)
                    }

                    val periodStr = "মূল্যায়ন সময়কাল: ${report.fromDate} থেকে ${report.toDate}"
                    canvas.drawText(periodStr, 56f, y + 60f, subTitlePaint.apply { textAlign = Paint.Align.LEFT })
                    y += 82f
                }

                ReportWidgetType.ATTENDANCE_METRICS -> {
                    val kpiWidth = (PAGE_WIDTH - 80f - 30f) / 4f
                    val kpiHeight = 52f
                    val kpiLabels = listOf("মোট কর্মদিবস", "উপস্থিতি", "অনুপস্থিতি", "বিলম্ব")
                    val kpiValues = listOf("${report.totalDays} দিন", "${report.present} দিন", "${report.absent} দিন", "${report.late} দিন")

                    for (i in 0..3) {
                        val kpiX = 40f + i * (kpiWidth + 10f)
                        val kpiBg = Paint().apply {
                            color = if (i == 1) Color.rgb(240, 253, 244) else if (i == 2) Color.rgb(254, 242, 242) else Color.rgb(248, 250, 252)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(kpiX, y, kpiX + kpiWidth, y + kpiHeight, 6f, 6f, kpiBg)

                        val kpiLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(100, 116, 139)
                            textSize = 9f
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(kpiLabels[i], kpiX + (kpiWidth / 2), y + 18f, kpiLabelPaint)

                        val kpiValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            color = if (i == 1) Color.rgb(22, 163, 74) else if (i == 2) Color.rgb(220, 38, 38) else Color.rgb(15, 23, 42)
                            textSize = 12.5f
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(kpiValues[i], kpiX + (kpiWidth / 2), y + 38f, kpiValPaint)
                    }
                    y += kpiHeight + 14f
                }

                ReportWidgetType.PERCENTAGE_BADGE -> {
                    val pctBox = Paint().apply {
                        color = secondaryColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 38f, 6f, 6f, pctBox)

                    val pctText = "উপস্থিতির শতকরা হার: ${String.format(Locale.US, "%.1f", report.percentage)}%"
                    val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        color = primaryColor
                        textSize = 12.5f
                    }
                    canvas.drawText(pctText, 56f, y + 24f, pctPaint)

                    val badgeText = if (report.percentage >= 80f) "উৎকৃষ্ট (Excellent)" else if (report.percentage >= 60f) "সন্তোষজনক (Good)" else "মনোযোগ প্রয়োজন (Needs Improvement)"
                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        color = badgeColor
                        textSize = 10.5f
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText(badgeText, (PAGE_WIDTH - 56).toFloat(), y + 24f, badgePaint)
                    y += 50f
                }

                ReportWidgetType.ATTENDANCE_TABLE -> {
                    if (report.records.isNotEmpty()) {
                        val thBg = Paint().apply {
                            color = Color.rgb(226, 232, 240)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 18f, thBg)

                        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            color = Color.rgb(15, 23, 42)
                            textSize = 9f
                        }
                        canvas.drawText("তারিখ (Date)", 50f, y + 13f, thPaint)
                        canvas.drawText("উপস্থিতি স্ট্যাটাস", 180f, y + 13f, thPaint)
                        canvas.drawText("মন্তব্য ও নোট", 330f, y + 13f, thPaint)
                        y += 22f

                        val rowLinePaint = Paint().apply {
                            color = Color.rgb(241, 245, 249)
                            strokeWidth = 1f
                        }

                        val maxRows = 10.coerceAtMost(report.records.size)
                        for (idx in 0 until maxRows) {
                            if (y > PAGE_HEIGHT - 120f) break
                            val rec = report.records[idx]
                            val statusText = when (rec.status.uppercase(Locale.getDefault())) {
                                "PRESENT" -> "উপস্থিত (Present)"
                                "ABSENT" -> "অনুপস্থিত (Absent)"
                                "LATE" -> "বিলম্ব (Late)"
                                else -> rec.status
                            }
                            canvas.drawText(rec.date, 50f, y + 12f, textPaint.apply { textSize = 8.5f })
                            canvas.drawText(statusText, 180f, y + 12f, textPaint)
                            val noteText = rec.remarks.ifBlank { "-" }
                            canvas.drawText(noteText, 330f, y + 12f, textPaint)
                            canvas.drawLine(40f, y + 16f, (PAGE_WIDTH - 40).toFloat(), y + 16f, rowLinePaint)
                            y += 18f
                        }
                        y += 8f
                    }
                }

                ReportWidgetType.TEACHER_REMARKS -> {
                    if (config.customRemarks.isNotBlank()) {
                        val remarksBox = Paint().apply {
                            color = Color.rgb(248, 250, 252)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 40f, 6f, 6f, remarksBox)

                        val remarkTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            color = primaryColor
                            textSize = 9.5f
                        }
                        canvas.drawText("প্রধান শিক্ষক ও শ্রেণি শিক্ষকের মূল্যায়ন মন্তব্য:", 50f, y + 15f, remarkTitlePaint)
                        canvas.drawText(config.customRemarks, 50f, y + 30f, textPaint.apply { textSize = 8.5f })
                        y += 50f
                    }
                }

                ReportWidgetType.SIGNATURES -> {
                    val sigY = if (y > PAGE_HEIGHT - 90f) PAGE_HEIGHT - 70f else y + 25f
                    val sigLinePaint = Paint().apply {
                        color = Color.rgb(148, 163, 184)
                        strokeWidth = 1f
                    }
                    val sigLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(71, 85, 105)
                        textSize = 8.5f
                        textAlign = Paint.Align.CENTER
                    }

                    canvas.drawLine(50f, sigY, 170f, sigY, sigLinePaint)
                    canvas.drawText("অভিভাবকের স্বাক্ষর", 110f, sigY + 12f, sigLabelPaint)

                    canvas.drawLine((PAGE_WIDTH / 2 - 60).toFloat(), sigY, (PAGE_WIDTH / 2 + 60).toFloat(), sigY, sigLinePaint)
                    canvas.drawText("শ্রেণি শিক্ষকের স্বাক্ষর", (PAGE_WIDTH / 2).toFloat(), sigY + 12f, sigLabelPaint)

                    canvas.drawLine((PAGE_WIDTH - 170).toFloat(), sigY, (PAGE_WIDTH - 50).toFloat(), sigY, sigLinePaint)
                    canvas.drawText("প্রধান শিক্ষক / অধ্যক্ষ", (PAGE_WIDTH - 110).toFloat(), sigY + 12f, sigLabelPaint)
                    y = sigY + 28f
                }

                ReportWidgetType.FOOTER_INFO -> {
                    val footerY = (PAGE_HEIGHT - 38f).coerceAtLeast(y + 12f)
                    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(148, 163, 184)
                        textSize = 8f
                        textAlign = Paint.Align.CENTER
                    }
                    val genTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    canvas.drawText("রিপোর্ট জেনারেট হয়েছে: $genTime  |  ডিজিটাল ভেরিফায়েড রিপোর্ট কার্ড", (PAGE_WIDTH / 2).toFloat(), footerY, footerPaint)
                    y = footerY + 15f
                }
            }
        }

        document.finishPage(page)

        val cleanStudentName = report.student.name.replace("\\s+".toRegex(), "_")
        val fileName = "ReportCard_${cleanStudentName}_${System.currentTimeMillis()}.pdf"
        val outputDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outputFile = File(outputDir, fileName)

        val out = FileOutputStream(outputFile)
        document.writeTo(out)
        out.flush()
        out.close()
        document.close()

        return outputFile
    }

    /**
     * Trigger Android System Print Service for direct printing or saving as PDF
     */
    fun printPdf(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val input = FileInputStream(pdfFile)
                    val output = FileOutputStream(destination?.fileDescriptor)
                    val buf = ByteArray(1024)
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } > 0) {
                        output.write(buf, 0, bytesRead)
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    input.close()
                    output.close()
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    data class SavedPdfResult(
        val uri: Uri?,
        val folderName: String = "attendent",
        val displayPath: String = "attendent",
        val file: File? = null
    )

    /**
     * Save PDF directly into 'attendent' folder (NOT Downloads)
     */
    fun savePdfToAttendentFolder(context: Context, pdfFile: File): SavedPdfResult {
        var resultUri: Uri? = null
        var displayLocation = "attendent"
        var finalSavedFile: File? = null

        // 1. Direct device root storage: /storage/emulated/0/attendent/
        try {
            val rootDir = Environment.getExternalStorageDirectory()
            val attendentDir = File(rootDir, "attendent")
            if (!attendentDir.exists()) {
                attendentDir.mkdirs()
            }
            if (attendentDir.exists()) {
                val targetFile = File(attendentDir, pdfFile.name)
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                finalSavedFile = targetFile
                displayLocation = "attendent/${pdfFile.name}"
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("application/pdf")
                ) { _, uri ->
                    if (resultUri == null && uri != null) {
                        resultUri = uri
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Documents/attendent folder: /storage/emulated/0/Documents/attendent/
        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val docsAttendentDir = File(docsDir, "attendent")
            if (!docsAttendentDir.exists()) {
                docsAttendentDir.mkdirs()
            }
            if (docsAttendentDir.exists()) {
                val targetFile = File(docsAttendentDir, pdfFile.name)
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (finalSavedFile == null) finalSavedFile = targetFile
                displayLocation = "attendent/${pdfFile.name}"
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. MediaStore API for Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val paths = listOf("Documents/attendent", "attendent")
            for (relPath in paths) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFile.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        values
                    )
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(pdfFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                        resultUri = uri
                        displayLocation = "attendent/${pdfFile.name}"
                        break
                    }
                } catch (e: Exception) {
                    // Try next path
                }
            }
        }

        // 4. App external files directory fallback: Android/data/<package>/files/attendent/
        try {
            val appExtDir = File(context.getExternalFilesDir(null), "attendent").apply { mkdirs() }
            val appTargetFile = File(appExtDir, pdfFile.name)
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(appTargetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (finalSavedFile == null) finalSavedFile = appTargetFile
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Ensure valid Uri for viewing or sharing
        if (resultUri == null) {
            val fileForUri = finalSavedFile ?: pdfFile
            resultUri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileForUri)
            } catch (e: Exception) {
                Uri.fromFile(fileForUri)
            }
        }

        return SavedPdfResult(
            uri = resultUri,
            folderName = "attendent",
            displayPath = displayLocation,
            file = finalSavedFile ?: pdfFile
        )
    }

    /**
     * Backward-compatible alias for saving PDF into 'attendent' folder (NOT Downloads)
     */
    fun savePdfToDownloads(context: Context, pdfFile: File): Uri? {
        return savePdfToAttendentFolder(context, pdfFile).uri
    }
}
