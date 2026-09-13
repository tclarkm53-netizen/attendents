package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.entity.StudentEntity
import com.example.data.model.REPORT_THEMES
import com.example.data.model.ReportCardDesignConfig
import com.example.data.model.ReportCardTheme
import com.example.data.repository.SingleStudentReportData
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.PresentGreen
import com.example.ui.viewmodel.AttendanceViewModel
import com.example.utils.PdfReportGenerator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    var reportType by remember { mutableIntStateOf(0) } // 0 = Class Report, 1 = Student Report, 2 = Custom Card Design & Live Preview

    val classes by viewModel.classes.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val students by viewModel.studentsInSelectedClass.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()

    var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }

    // Date range setup
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val now = Calendar.getInstance()

    val startOfMonthCal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    var fromDate by remember { mutableStateOf(dateFormat.format(startOfMonthCal.time)) }
    var toDate by remember { mutableStateOf(dateFormat.format(now.time)) }
    var datePreset by remember { mutableStateOf("THIS_MONTH") }

    val classReportData by viewModel.classReportData.collectAsState()
    val studentReportData by viewModel.studentReportData.collectAsState()
    val isLoading by viewModel.isReportLoading.collectAsState()

    var classDropdownExpanded by remember { mutableStateOf(false) }
    var studentDropdownExpanded by remember { mutableStateOf(false) }

    // Custom Design Configuration State
    var designConfig by remember(activeUser) {
        mutableStateOf(
            ReportCardDesignConfig(
                customInstitution = activeUser?.institution ?: "",
                customReportTitle = "শিক্ষার্থী উপস্থিতি ও মূল্যায়ন রিপোর্ট কার্ড",
                customSubtitle = "মাসিক মূল্যায়ন বিবরণী",
                customRemarks = "উপস্থিতির হার সন্তোষজনক। নিয়মিত অধ্যবসায় কাম্য।",
                theme = REPORT_THEMES[0],
                borderStyle = "DOUBLE",
                showSignatures = true,
                showPercentages = true,
                showGuardianPhone = true,
                showAttendanceTable = true
            )
        )
    }

    // Auto-select first student if available
    LaunchedEffect(students) {
        if (selectedStudent == null || students.none { it.uuid == selectedStudent?.uuid }) {
            selectedStudent = students.firstOrNull()
        }
    }

    // Auto trigger report when parameters change
    LaunchedEffect(reportType, selectedClass, selectedStudent, fromDate, toDate) {
        if (reportType == 0 && selectedClass != null) {
            viewModel.loadClassReport(selectedClass!!.uuid, fromDate, toDate)
        } else if ((reportType == 1 || reportType == 2) && selectedStudent != null) {
            viewModel.loadStudentReport(selectedStudent!!.uuid, fromDate, toDate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Row: Class Report, Student Report, and Custom Card Design & Preview
        TabRow(
            selectedTabIndex = reportType,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = reportType == 0,
                onClick = { reportType = 0 },
                text = { Text("ক্লাস রিপোর্ট", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_class_report")
            )
            Tab(
                selected = reportType == 1,
                onClick = { reportType = 1 },
                text = { Text("স্টুডেন্ট রিপোর্ট", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_student_report")
            )
            Tab(
                selected = reportType == 2,
                onClick = { reportType = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("কার্ড ডিজাইন ও প্রিভিউ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                modifier = Modifier.testTag("tab_custom_card_preview")
            )
        }

        // Shared Filter Controls Card (Class, Student, Date)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Class Dropdown
                ExposedDropdownMenuBox(
                    expanded = classDropdownExpanded,
                    onExpandedChange = { classDropdownExpanded = !classDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedClass?.className ?: "ক্লাস নির্বাচন করুন",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("ক্লাস") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = classDropdownExpanded,
                        onDismissRequest = { classDropdownExpanded = false }
                    ) {
                        classes.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text("${cls.className} (${cls.section.ifBlank { "সকল শাখা" }})") },
                                onClick = {
                                    viewModel.selectClass(cls)
                                    classDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (reportType == 1 || reportType == 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Student Dropdown
                    ExposedDropdownMenuBox(
                        expanded = studentDropdownExpanded,
                        onExpandedChange = { studentDropdownExpanded = !studentDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedStudent?.let { "রোল ${it.rollNumber}: ${it.name}" } ?: "শিক্ষার্থী নির্বাচন করুন",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("শিক্ষার্থী") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = studentDropdownExpanded,
                            onDismissRequest = { studentDropdownExpanded = false }
                        ) {
                            students.forEach { stu ->
                                DropdownMenuItem(
                                    text = { Text("রোল ${stu.rollNumber}: ${stu.name}") },
                                    onClick = {
                                        selectedStudent = stu
                                        studentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = datePreset == "THIS_MONTH",
                        onClick = {
                            datePreset = "THIS_MONTH"
                            val cal = Calendar.getInstance()
                            toDate = dateFormat.format(cal.time)
                            cal.set(Calendar.DAY_OF_MONTH, 1)
                            fromDate = dateFormat.format(cal.time)
                        },
                        label = { Text("চলতি মাস", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = datePreset == "LAST_30",
                        onClick = {
                            datePreset = "LAST_30"
                            val cal = Calendar.getInstance()
                            toDate = dateFormat.format(cal.time)
                            cal.add(Calendar.DAY_OF_YEAR, -30)
                            fromDate = dateFormat.format(cal.time)
                        },
                        label = { Text("গত ৩০ দিন", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = datePreset == "TODAY",
                        onClick = {
                            datePreset = "TODAY"
                            val todayStr = dateFormat.format(Date())
                            fromDate = todayStr
                            toDate = todayStr
                        },
                        label = { Text("আজকে", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "সময়কাল: $fromDate থেকে $toDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action Buttons for Standard Reports (Class / Student)
        if (reportType == 0 || reportType == 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val institution = activeUser?.institution ?: ""
                        if (reportType == 0) {
                            val report = classReportData
                            if (report == null || report.items.isEmpty()) {
                                Toast.makeText(context, "প্রিন্ট করার মতো কোনো ডাটা নেই", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val pdf = PdfReportGenerator.generateClassReportPdf(context, report, institution)
                            PdfReportGenerator.printPdf(context, pdf, "Class_Report_${report.classEntity.className}")
                        } else {
                            val report = studentReportData
                            if (report == null) {
                                Toast.makeText(context, "প্রিন্ট করার মতো কোনো ডাটা নেই", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val pdf = PdfReportGenerator.generateStudentReportPdf(context, report, institution)
                            PdfReportGenerator.printPdf(context, pdf, "Student_Report_${report.student.name}")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("print_report_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("প্রিন্ট করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        val institution = activeUser?.institution ?: ""
                        if (reportType == 0) {
                            val report = classReportData
                            if (report == null || report.items.isEmpty()) {
                                Toast.makeText(context, "সেভ করার মতো কোনো ডাটা নেই", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            val pdf = PdfReportGenerator.generateClassReportPdf(context, report, institution)
                            val res = PdfReportGenerator.savePdfToAttendentFolder(context, pdf)
                            Toast.makeText(context, if (res.uri != null) "PDF সংরক্ষিত: attendent ফোল্ডারে" else "PDF সেভ হয়েছে", Toast.LENGTH_LONG).show()
                        } else {
                            val report = studentReportData
                            if (report == null) {
                                Toast.makeText(context, "সেভ করার মতো কোনো ডাটা নেই", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            val pdf = PdfReportGenerator.generateStudentReportPdf(context, report, institution)
                            val res = PdfReportGenerator.savePdfToAttendentFolder(context, pdf)
                            Toast.makeText(context, if (res.uri != null) "PDF সংরক্ষিত: attendent ফোল্ডারে" else "PDF সেভ হয়েছে", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("download_pdf_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF সেভ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (reportType == 1) {
                    OutlinedButton(
                        onClick = { reportType = 2 },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ডিজাইন", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Content Area
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else when (reportType) {
            0 -> ClassReportView(report = classReportData)
            1 -> StudentReportView(report = studentReportData, onCustomizeCard = { reportType = 2 })
            2 -> CustomCardDesignAndPreviewView(
                report = studentReportData,
                config = designConfig,
                onConfigChange = { designConfig = it },
                onPrintCustomCard = { pdf ->
                    PdfReportGenerator.printPdf(context, pdf, "Custom_ReportCard_${studentReportData?.student?.name ?: "Student"}")
                },
                onSaveCustomCard = { pdf ->
                    val res = PdfReportGenerator.savePdfToAttendentFolder(context, pdf)
                    Toast.makeText(context, if (res.uri != null) "কাস্টমাইজড রিপোর্ট কার্ড সেভ হয়েছে: attendent ফোল্ডারে" else "PDF সেভ হয়েছে", Toast.LENGTH_LONG).show()
                },
                onShareCustomCard = { pdf ->
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "রিপোর্ট কার্ড শেয়ার করুন"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "শেয়ার করতে সমস্যা: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun ClassReportView(report: com.example.data.repository.ClassReportData?) {
    if (report == null || report.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("এই সময়ের জন্য কোনো অ্যাটেন্ডেন্স রেকর্ড নেই")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ক্লাস: ${report.classEntity.className}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "মোট শিক্ষার্থী: ${report.totalStudents}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.overallPercentage)}%",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (report.overallPercentage >= 75f) PresentGreen else AbsentRed
                                )
                                Text("গড় উপস্থিতি", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (report.overallPercentage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (report.overallPercentage >= 75f) PresentGreen else AbsentRed
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("রোল ও নাম", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                    Text("মোট", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.7f))
                    Text("উপস্থিত", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text("অনুপস্থিত", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text("শতকরা", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.9f))
                }
            }

            items(report.items, key = { it.studentUuid }) { item ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "রোল: ${item.rollNumber}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${item.totalDays}", fontSize = 12.sp, modifier = Modifier.weight(0.7f))
                        Text("${item.present}", fontSize = 12.sp, color = PresentGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                        Text("${item.absent}", fontSize = 12.sp, color = AbsentRed, modifier = Modifier.weight(0.8f))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", item.percentage)}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.percentage >= 75f) PresentGreen else AbsentRed,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudentReportView(
    report: SingleStudentReportData?,
    onCustomizeCard: () -> Unit
) {
    if (report == null || report.records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("এই শিক্ষার্থীর কোনো উপস্থিতি রেকর্ড পাওয়া যায়নি")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = report.student.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "রোল: ${report.student.rollNumber} | শ্রেণি: ${report.classEntity?.className ?: "N/A"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = onCustomizeCard,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("কার্ড ডিজাইন", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("মোট ক্লাস: ${report.totalDays}", style = MaterialTheme.typography.bodyMedium)
                                Text("উপস্থিত: ${report.present} দিন", color = PresentGreen, fontWeight = FontWeight.Bold)
                                Text("অনুপস্থিত: ${report.absent} দিন", color = AbsentRed)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", report.percentage)}%",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (report.percentage >= 75f) PresentGreen else AbsentRed
                                )
                                Text("উপস্থিতির হার", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "তারিখ অনুযায়ী উপস্থিতির বিবরণ:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(report.records, key = { it.uuid }) { record ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = record.date, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (record.remarks.isNotBlank()) {
                                Text(
                                    text = "মন্তব্য: ${record.remarks}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val statusColor = when (record.status) {
                            "PRESENT" -> PresentGreen
                            "ABSENT" -> AbsentRed
                            "LATE" -> Color(0xFFD97706)
                            else -> Color(0xFF2563EB)
                        }
                        val statusLabel = when (record.status) {
                            "PRESENT" -> "উপস্থিত (P)"
                            "ABSENT" -> "অনুপস্থিত (A)"
                            "LATE" -> "দেরি (L)"
                            else -> "ছুটি (E)"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Card Design Management & Live WYSIWYG Preview Component
 */
@Composable
fun CustomCardDesignAndPreviewView(
    report: SingleStudentReportData?,
    config: ReportCardDesignConfig,
    onConfigChange: (ReportCardDesignConfig) -> Unit,
    onPrintCustomCard: (File) -> Unit,
    onSaveCustomCard: (File) -> Unit,
    onShareCustomCard: (File) -> Unit
) {
    val context = LocalContext.current
    var isGeneratingPdf by remember { mutableStateOf(false) }

    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("প্রিভিউ দেখতে প্রথমে একটি ক্লাস ও শিক্ষার্থী নির্বাচন করুন")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Design Management Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "রিপোর্ট কার্ডের ডিজাইন ও কাস্টম ডেটা ম্যানেজ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Theme Color Palette Picker
                Text(
                    text = "থিম ও রঙ নির্বাচন করুন (Theme Color):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    REPORT_THEMES.forEach { theme ->
                        val isSelected = config.theme.id == theme.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(theme.primaryColorArgb))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onConfigChange(config.copy(theme = theme)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "নির্বাচিত থিম: ${config.theme.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Border Style Picker
                Text(
                    text = "বর্ডার স্টাইল (Border Style):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = config.borderStyle == "DOUBLE",
                        onClick = { onConfigChange(config.copy(borderStyle = "DOUBLE")) },
                        label = { Text("ডাবল বর্ডার", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = config.borderStyle == "ROUNDED",
                        onClick = { onConfigChange(config.copy(borderStyle = "ROUNDED")) },
                        label = { Text("রাউন্ডেড আধুনিক", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = config.borderStyle == "MINIMAL",
                        onClick = { onConfigChange(config.copy(borderStyle = "MINIMAL")) },
                        label = { Text("মিনিমাল স্লিম", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Custom Text Fields
                OutlinedTextField(
                    value = config.customInstitution,
                    onValueChange = { onConfigChange(config.copy(customInstitution = it)) },
                    label = { Text("প্রতিষ্ঠানের নাম (Header)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.customReportTitle,
                    onValueChange = { onConfigChange(config.copy(customReportTitle = it)) },
                    label = { Text("রিপোর্ট কার্ডের মূল শিরোনাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.customSubtitle,
                    onValueChange = { onConfigChange(config.copy(customSubtitle = it)) },
                    label = { Text("উপ-শিরোনাম / মূল্যায়ন পর্ব (Subtitle)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = config.customRemarks,
                    onValueChange = { onConfigChange(config.copy(customRemarks = it)) },
                    label = { Text("শিক্ষক ও অধ্যক্ষের মূল্যায়ন মন্তব্য") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Feature Switches
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("স্বাক্ষর লাইন দেখান (Guardian & Principal)", fontSize = 12.sp)
                        Switch(
                            checked = config.showSignatures,
                            onCheckedChange = { onConfigChange(config.copy(showSignatures = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("শতকরা হার ও পারফর্মেন্স ব্যাজ দেখান", fontSize = 12.sp)
                        Switch(
                            checked = config.showPercentages,
                            onCheckedChange = { onConfigChange(config.copy(showPercentages = it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("দৈনিক হাজিরার সংক্ষিপ্ত তালিকা অন্তর্ভুক্ত করুন", fontSize = 12.sp)
                        Switch(
                            checked = config.showAttendanceTable,
                            onCheckedChange = { onConfigChange(config.copy(showAttendanceTable = it)) }
                        )
                    }
                }
            }
        }

        // Live Action Export Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val pdf = PdfReportGenerator.generateCustomReportCardPdf(context, report, config)
                    onPrintCustomCard(pdf)
                },
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("কার্ড প্রিন্ট করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val pdf = PdfReportGenerator.generateCustomReportCardPdf(context, report, config)
                    onSaveCustomCard(pdf)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF সেভ", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val pdf = PdfReportGenerator.generateCustomReportCardPdf(context, report, config)
                    onShareCustomCard(pdf)
                },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("শেয়ার", fontSize = 12.sp)
            }
        }

        // Live Card Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color(config.theme.primaryColorArgb))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "লাইভ কার্ড প্রিভিউ (Live Card Preview):",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        // 5. Interactive Live WYSIWYG Report Card Preview
        InteractiveReportCardPreview(report = report, config = config)
    }
}

/**
 * Visual Component that renders a WYSIWYG live preview of the Report Card
 */
@Composable
fun InteractiveReportCardPreview(
    report: SingleStudentReportData,
    config: ReportCardDesignConfig
) {
    val themePrimary = Color(config.theme.primaryColorArgb)
    val themeSecondary = Color(config.theme.secondaryColorArgb)
    val badgeColor = Color(config.theme.badgeColorArgb)

    val borderModifier = when (config.borderStyle) {
        "DOUBLE" -> Modifier
            .border(3.dp, themePrimary, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .border(1.dp, themePrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        "ROUNDED" -> Modifier
            .border(2.5.dp, themePrimary, RoundedCornerShape(16.dp))
        else -> Modifier
            .border(1.5.dp, themePrimary, RoundedCornerShape(4.dp))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
        shape = if (config.borderStyle == "ROUNDED") RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp),
        color = Color.White,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Institution Name & Title
            val instName = if (config.customInstitution.isNotBlank()) config.customInstitution else "ACADEMIC INSTITUTION"
            Text(
                text = instName.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = themePrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = config.customReportTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (config.customSubtitle.isNotBlank()) {
                Text(
                    text = config.customSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 2.dp, color = themePrimary)
            Spacer(modifier = Modifier.height(12.dp))

            // Student Profile Container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = themeSecondary
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "শিক্ষার্থীর নাম: ${report.student.name}",
                            fontWeight = FontWeight.Bold,
                            color = themePrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "রোল: ${report.student.rollNumber}",
                            fontWeight = FontWeight.Bold,
                            color = themePrimary,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "শ্রেণি: ${report.classEntity?.className ?: "N/A"} (${report.classEntity?.section?.ifBlank { "সকল শাখা" } ?: ""})",
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B)
                        )
                        if (config.showGuardianPhone && report.student.phone.isNotBlank()) {
                            Text(
                                text = "মোবাইল: ${report.student.phone}",
                                fontSize = 11.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                    Text(
                        text = "সময়কাল: ${report.fromDate} থেকে ${report.toDate}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // KPI Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KpiCard(title = "মোট দিন", value = "${report.totalDays}", color = Color(0xFFF1F5F9), valueColor = Color.Black, modifier = Modifier.weight(1f))
                KpiCard(title = "উপস্থিত", value = "${report.present}", color = Color(0xFFF0FDF4), valueColor = PresentGreen, modifier = Modifier.weight(1f))
                KpiCard(title = "অনুপস্থিত", value = "${report.absent}", color = Color(0xFFFEF2F2), valueColor = AbsentRed, modifier = Modifier.weight(1f))
                KpiCard(title = "বিলম্ব", value = "${report.late}", color = Color(0xFFFFFBEB), valueColor = Color(0xFFD97706), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Percentage Badge
            if (config.showPercentages) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = themeSecondary
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "উপস্থিতির শতকরা হার: ${String.format(Locale.US, "%.1f", report.percentage)}%",
                            fontWeight = FontWeight.Bold,
                            color = themePrimary,
                            fontSize = 12.sp
                        )

                        val badgeText = if (report.percentage >= 80f) "উৎকৃষ্ট (Excellent)" else if (report.percentage >= 60f) "সন্তোষজনক (Good)" else "মনোযোগ প্রয়োজন"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Attendance Table Summary (if enabled)
            if (config.showAttendanceTable && report.records.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("উপস্থিতির সংক্ষিপ্ত বিবরণী (সর্বশেষ ৫ রেকর্ড):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themePrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val previewRows = report.records.take(5)
                        previewRows.forEach { rec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rec.date, fontSize = 10.sp, color = Color(0xFF334155))
                                val label = when (rec.status) {
                                    "PRESENT" -> "উপস্থিত"
                                    "ABSENT" -> "অনুপস্থিত"
                                    "LATE" -> "বিলম্ব"
                                    else -> rec.status
                                }
                                val color = when (rec.status) {
                                    "PRESENT" -> PresentGreen
                                    "ABSENT" -> AbsentRed
                                    else -> Color(0xFFD97706)
                                }
                                Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Remarks Box
            if (config.customRemarks.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("মূল্যায়ন মন্তব্য:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themePrimary)
                        Text(config.customRemarks, fontSize = 11.sp, color = Color(0xFF334155))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Signatures
            if (config.showSignatures) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.width(80.dp).height(1.dp).background(Color(0xFF94A3B8)))
                        Text("অভিভাবকের স্বাক্ষর", fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.width(80.dp).height(1.dp).background(Color(0xFF94A3B8)))
                        Text("শ্রেণি শিক্ষকের স্বাক্ষর", fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.width(80.dp).height(1.dp).background(Color(0xFF94A3B8)))
                        Text("অধ্যক্ষ / প্রধান শিক্ষক", fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    color: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 9.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
