package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
import com.example.data.model.ReportCardDesignConfig
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
        val excusedText = if (report.excused > 0) "  |  Excused: ${report.excused} (Excluded)" else ""
        val metrics = "Total Countable: ${report.totalDays}  |  Present: ${report.present}  |  Absent: ${report.absent}  |  Late: ${report.late}$excusedText"
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
     * Generate Highly Customized Report Card PDF with theme colors, borders, and custom remarks
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

        var y = 55f

        // 2. Top Header / Institution Name
        val institutionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = primaryColor
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        val instName = if (config.customInstitution.isNotBlank()) config.customInstitution else "ACADEMIC INSTITUTION"
        canvas.drawText(instName.uppercase(Locale.getDefault()), (PAGE_WIDTH / 2).toFloat(), y, institutionPaint)
        y += 24f

        // Report Title & Subtitle
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(config.customReportTitle, (PAGE_WIDTH / 2).toFloat(), y, titlePaint)
        y += 18f

        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(config.customSubtitle, (PAGE_WIDTH / 2).toFloat(), y, subTitlePaint)
        y += 20f

        // Decorative Colored Line
        val accentLinePaint = Paint().apply {
            color = primaryColor
            strokeWidth = 2f
        }
        canvas.drawLine(50f, y, (PAGE_WIDTH - 50).toFloat(), y, accentLinePaint)
        y += 25f

        // 3. Student Profile Card Box
        val studentBoxPaint = Paint().apply {
            color = secondaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 70f, 8f, 8f, studentBoxPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }
        val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = primaryColor
            textSize = 12f
        }

        canvas.drawText("শিক্ষার্থীর নাম: ${report.student.name}", 56f, y + 26f, boldTextPaint)
        canvas.drawText("রোল নম্বর: ${report.student.rollNumber}", (PAGE_WIDTH - 180).toFloat(), y + 26f, boldTextPaint)

        val classStr = "শ্রেণি: ${report.classEntity?.className ?: "N/A"} (${report.classEntity?.section?.ifBlank { "সকল শাখা" } ?: ""})"
        canvas.drawText(classStr, 56f, y + 46f, textPaint)

        if (config.showGuardianPhone && report.student.phone.isNotBlank()) {
            canvas.drawText("অভিভাবক মোবাইল: ${report.student.phone}", (PAGE_WIDTH - 220).toFloat(), y + 46f, textPaint)
        }

        val periodStr = "মূল্যায়ন সময়কাল: ${report.fromDate} থেকে ${report.toDate}"
        canvas.drawText(periodStr, 56f, y + 62f, subTitlePaint.apply { textAlign = Paint.Align.LEFT })
        y += 90f

        // 4. Metric Highlights KPI Grid
        val kpiWidth = (PAGE_WIDTH - 80f - 30f) / 4f
        val kpiHeight = 55f
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
            canvas.drawText(kpiLabels[i], kpiX + (kpiWidth / 2), y + 20f, kpiLabelPaint)

            val kpiValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = if (i == 1) Color.rgb(22, 163, 74) else if (i == 2) Color.rgb(220, 38, 38) else Color.rgb(15, 23, 42)
                textSize = 13f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(kpiValues[i], kpiX + (kpiWidth / 2), y + 42f, kpiValPaint)
        }
        y += kpiHeight + 12f

        if (report.excused > 0) {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235)
                textSize = 8.5f
            }
            canvas.drawText("* অনুমোদিত ছুটি (${report.excused} দিন) মোট দিন ও উপস্থিতি গণনায় অন্তর্ভুক্ত করা হয়নি।", 42f, y, notePaint)
            y += 14f
        } else {
            y += 3f
        }

        // Percentage & Performance Badge
        if (config.showPercentages) {
            val pctBox = Paint().apply {
                color = secondaryColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 40f, 6f, 6f, pctBox)

            val pctText = "উপস্থিতির শতকরা হার: ${String.format(Locale.US, "%.1f", report.percentage)}%"
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = primaryColor
                textSize = 13f
            }
            canvas.drawText(pctText, 56f, y + 25f, pctPaint)

            val badgeText = if (report.percentage >= 80f) "উৎকৃষ্ট (Excellent)" else if (report.percentage >= 60f) "সন্তোষজনক (Good)" else "মনোযোগ প্রয়োজন (Needs Improvement)"
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = badgeColor
                textSize = 11f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(badgeText, (PAGE_WIDTH - 56).toFloat(), y + 25f, badgePaint)
            y += 55f
        }

        // 5. Daily Attendance History (if enabled)
        if (config.showAttendanceTable && report.records.isNotEmpty()) {
            val thBg = Paint().apply {
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.FILL
            }
            canvas.drawRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 20f, thBg)

            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(15, 23, 42)
                textSize = 9.5f
            }
            canvas.drawText("তারিখ (Date)", 50f, y + 14f, thPaint)
            canvas.drawText("উপস্থিতি স্ট্যাটাস", 180f, y + 14f, thPaint)
            canvas.drawText("মন্তব্য ও নোট", 330f, y + 14f, thPaint)
            y += 24f

            val rowLinePaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                strokeWidth = 1f
            }

            val maxRows = 14.coerceAtMost(report.records.size)
            for (idx in 0 until maxRows) {
                val rec = report.records[idx]
                val statusText = when (rec.status.uppercase(Locale.getDefault())) {
                    "PRESENT" -> "উপস্থিত (Present)"
                    "ABSENT" -> "অনুপস্থিত (Absent)"
                    "LATE" -> "বিলম্ব (Late)"
                    else -> rec.status
                }
                canvas.drawText(rec.date, 50f, y + 13f, textPaint.apply { textSize = 9f })
                canvas.drawText(statusText, 180f, y + 13f, textPaint)
                val noteText = rec.remarks.ifBlank { "-" }
                canvas.drawText(noteText, 330f, y + 13f, textPaint)
                canvas.drawLine(40f, y + 18f, (PAGE_WIDTH - 40).toFloat(), y + 18f, rowLinePaint)
                y += 20f
            }
            if (report.records.size > maxRows) {
                val moreText = "... এবং আরও ${report.records.size - maxRows} দিনের উপস্থিতি রেকর্ড"
                canvas.drawText(moreText, 50f, y + 12f, subTitlePaint.apply { textAlign = Paint.Align.LEFT })
                y += 18f
            }
            y += 10f
        }

        // 6. Custom Remarks Section
        if (config.customRemarks.isNotBlank()) {
            val remarksBox = Paint().apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 42f, 6f, 6f, remarksBox)

            val remarkTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = primaryColor
                textSize = 9.5f
            }
            canvas.drawText("প্রধান শিক্ষক ও শ্রেণি শিক্ষকের মূল্যায়ন মন্তব্য:", 50f, y + 16f, remarkTitlePaint)
            canvas.drawText(config.customRemarks, 50f, y + 32f, textPaint.apply { textSize = 9f })
            y += 60f
        }

        // 7. Signature Lines
        if (config.showSignatures) {
            val sigY = (PAGE_HEIGHT - 65f)
            val sigLinePaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                strokeWidth = 1f
            }
            val sigLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 9f
                textAlign = Paint.Align.CENTER
            }

            canvas.drawLine(60f, sigY, 200f, sigY, sigLinePaint)
            canvas.drawText("অভিভাবকের স্বাক্ষর", 130f, sigY + 14f, sigLabelPaint)

            canvas.drawLine((PAGE_WIDTH / 2 - 70).toFloat(), sigY, (PAGE_WIDTH / 2 + 70).toFloat(), sigY, sigLinePaint)
            canvas.drawText("শ্রেণি শিক্ষকের স্বাক্ষর", (PAGE_WIDTH / 2).toFloat(), sigY + 14f, sigLabelPaint)

            canvas.drawLine((PAGE_WIDTH - 200).toFloat(), sigY, (PAGE_WIDTH - 60).toFloat(), sigY, sigLinePaint)
            canvas.drawText("প্রধান শিক্ষক / অধ্যক্ষ", (PAGE_WIDTH - 130).toFloat(), sigY + 14f, sigLabelPaint)
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

    /**
     * Copy PDF to Public Downloads Directory
     */
    fun savePdfToDownloads(context: Context, pdfFile: File): Uri? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AttendanceReports")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(pdfFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    return uri
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "AttendanceReports").apply { mkdirs() }
                val targetFile = File(targetDir, pdfFile.name)
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
