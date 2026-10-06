package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClassEntity
import com.example.data.local.entity.StudentEntity
import com.example.ui.theme.AbsentContainer
import com.example.ui.theme.AbsentOnContainer
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.ExcusedBlue
import com.example.ui.theme.ExcusedContainer
import com.example.ui.theme.ExcusedOnContainer
import com.example.ui.theme.LateAmber
import com.example.ui.theme.LateContainer
import com.example.ui.theme.LateOnContainer
import com.example.ui.theme.PresentContainer
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentOnContainer
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    onNavigateToClasses: () -> Unit
) {
    val context = LocalContext.current
    val classes by viewModel.classes.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val students by viewModel.studentsInSelectedClass.collectAsState()
    val attendanceMap by viewModel.attendanceMap.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedAttendanceChanges.collectAsState()
    val isCurrentDateSavedInDb by viewModel.isCurrentDateSavedInDb.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var editingRemarksStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var currentRemarksText by remember { mutableStateOf("") }

    // Date picker dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember(selectedDate) {
        val parsed = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)
        } catch (e: Exception) {
            null
        }
        if (parsed != null) calendar.time = parsed

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                viewModel.setSelectedDate(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val todayDateStr = viewModel.getTodayDateString()
    val isToday = (selectedDate == todayDateStr)
    val isPast = (selectedDate < todayDateStr)
    val isFuture = (selectedDate > todayDateStr)

    // Filter students by admission date: students admitted after selectedDate are hidden
    val visibleStudents = remember(students, selectedDate) {
        students.filter { viewModel.isStudentAdmittedOnOrBefore(it, selectedDate) }
    }

    // Counts based on visible admitted students (memoized to eliminate scroll & tap lag)
    val totalStudents = visibleStudents.size
    val counts = remember(visibleStudents, attendanceMap) {
        var p = 0
        var a = 0
        var l = 0
        var e = 0
        var u = 0
        visibleStudents.forEach { s ->
            val record = attendanceMap[s.uuid]
            val effectiveStatus = record?.first ?: ""
            when (effectiveStatus) {
                "PRESENT" -> p++
                "ABSENT" -> a++
                "LATE" -> l++
                "EXCUSED" -> e++
                else -> u++
            }
        }
        arrayOf(p, a, l, e, u)
    }
    val presentCount = counts[0]
    val absentCount = counts[1]
    val lateCount = counts[2]
    val excusedCount = counts[3]
    val unmarkedCount = counts[4]
    val hasEntry = remember(totalStudents, unmarkedCount) {
        totalStudents > 0 && unmarkedCount < totalStudents
    }

    val isFriday = remember(selectedDate) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdf.parse(selectedDate)
            val cal = Calendar.getInstance().apply { time = parsed ?: Date() }
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        } catch (e: Exception) {
            false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Section: Class Selector & Date Controls
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Class Selector Chip Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ক্লাস নির্বাচন:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (classes.isEmpty()) {
                            TextButton(onClick = onNavigateToClasses) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ক্লাস যোগ করুন")
                            }
                        }
                    }

                    if (classes.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            classes.forEach { cls ->
                                val isSelected = cls.uuid == selectedClass?.uuid
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectClass(cls) },
                                    label = {
                                        Text(
                                            text = "${cls.className} (${cls.section.ifBlank { "All" }})",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "কোনো ক্লাস নেই। ক্লাস যোগ করতে পাশের ট্যাবে যান।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Date Navigator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { viewModel.changeDateByDays(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { datePickerDialog.show() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedDate,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { viewModel.changeDateByDays(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                        }
                    }

                    // Date Mode & Entry Status Indicator Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isFuture -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                hasUnsavedChanges -> LateContainer
                                isCurrentDateSavedInDb && hasEntry -> PresentContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isFuture -> Icons.Default.Lock
                                        hasUnsavedChanges -> Icons.Default.Info
                                        isCurrentDateSavedInDb && hasEntry -> Icons.Default.CheckCircle
                                        else -> Icons.Default.CalendarMonth
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isFuture -> MaterialTheme.colorScheme.error
                                        hasUnsavedChanges -> LateAmber
                                        isCurrentDateSavedInDb && hasEntry -> PresentGreen
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = when {
                                            isFuture -> "🔒 অগ্রিম তারিখ (${selectedDate}) — ভবিষ্যতের হাজিরা লক করা আছে"
                                            hasUnsavedChanges -> "⚠️ হাজিরা এডিট করা হয়েছে (${totalStudents - unmarkedCount}/${totalStudents}) — আনসেভড"
                                            isCurrentDateSavedInDb && hasEntry -> "✅ এই তারিখের (${selectedDate}) হাজিরা সংরক্ষিত (${totalStudents - unmarkedCount}/${totalStudents})"
                                            else -> "⚪ এই তারিখে এখনও কোনো হাজিরা নেওয়া হয়নি (নন-এন্ট্রি ডেট)"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = when {
                                            isFuture -> MaterialTheme.colorScheme.onErrorContainer
                                            hasUnsavedChanges -> LateOnContainer
                                            isCurrentDateSavedInDb && hasEntry -> PresentOnContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subHint = when {
                                        isFuture -> "অগ্রিম তারিখে হাজিরা মার্ক বা সেভ করা যাবে না"
                                        hasUnsavedChanges -> "রিপোর্টে যুক্ত করতে নিচের 'সেভ করুন (Save)' বাটনে চাপুন"
                                        !hasEntry -> "হাজিরা দিতে শিক্ষার্থীদের চেকবক্সে টিক দিন বা 'সবাই উপস্থিত' চাপুন"
                                        else -> ""
                                    }
                                    if (subHint.isNotBlank()) {
                                        Text(
                                            text = subHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (!isToday) {
                                TextButton(
                                    onClick = { viewModel.setSelectedDate(todayDateStr) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("আজকের তারিখ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Friday Auto-Skip Notification Banner
                    if (isFriday) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🕌 শুক্রবার - সাপ্তাহিক ছুটি (অটো-স্কিপ সক্রিয়)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "শুক্রবার রিপোর্টে কাউন্ট হবে না ও গড় উপস্থিতিতে প্রভাব ফেলবে না (সপ্তাহে ৬ দিন ও মাসে ২৪ দিন = ১০০% উপস্থিতি)।",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stats Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge(label = "ভর্তিকৃত", count = totalStudents, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        StatBadge(label = "উপস্থিত", count = presentCount, color = PresentGreen, modifier = Modifier.weight(1f))
                        StatBadge(label = "অনুপস্থিত", count = absentCount, color = AbsentRed, modifier = Modifier.weight(1f))
                        StatBadge(label = "অনির্ধারিত", count = unmarkedCount, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }

                    // Quick Actions (Master Toggleable Checkbox & Mark All Buttons) - Locked on Future Dates
                    if (visibleStudents.isNotEmpty() && !isFuture) {
                        val selectAllState = remember(presentCount, totalStudents) {
                            when {
                                totalStudents == 0 || presentCount == 0 -> ToggleableState.Off
                                presentCount == totalStudents -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .clickable {
                                    if (selectAllState == ToggleableState.On) {
                                        viewModel.markAll("ABSENT")
                                    } else {
                                        viewModel.markAll("PRESENT")
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                TriStateCheckbox(
                                    state = selectAllState,
                                    onClick = {
                                        if (selectAllState == ToggleableState.On) {
                                            viewModel.markAll("ABSENT")
                                        } else {
                                            viewModel.markAll("PRESENT")
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = PresentGreen,
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .testTag("select_all_attendance_checkbox")
                                        .semantics {
                                            contentDescription = "Toggle all students present"
                                        }
                                )
                                Text(
                                    text = "সবাই উপস্থিত মার্ক করুন (${presentCount}/${totalStudents})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.markAll("ABSENT") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = AbsentRed)
                                ) {
                                    Text("সবাই অনুপস্থিত", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                TextButton(
                                    onClick = { viewModel.unmarkAll() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                ) {
                                    Text("ক্লিয়ার", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Student Attendance List
            if (selectedClass == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("অনুগ্রহ করে একটি ক্লাস নির্বাচন করুন", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("এই ক্লাসে কোনো শিক্ষার্থী যোগ করা হয়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateToClasses) {
                            Text("শিক্ষার্থী যোগ করুন")
                        }
                    }
                }
            } else if (visibleStudents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "এই তারিখে (${selectedDate}) ক্লাসের কোনো শিক্ষার্থী ভর্তিকৃত ছিল না।",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "(শিক্ষার্থীদের ভর্তির তারিখ এই তারিখের পরবর্তী সময়)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("student_attendance_list"),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleStudents, key = { it.uuid }) { student ->
                        val record = attendanceMap[student.uuid]
                        val currentStatus = record?.first ?: ""
                        val currentRemarks = record?.second ?: ""

                        StudentAttendanceCard(
                            student = student,
                            status = currentStatus,
                            remarks = currentRemarks,
                            isEditable = true,
                            onStatusSelected = { newStatus ->
                                if (currentStatus == newStatus) {
                                    viewModel.markStudent(student.uuid, "") // unmark if tapped again
                                } else {
                                    viewModel.markStudent(student.uuid, newStatus)
                                }
                            },
                            onCheckboxToggled = { isChecked ->
                                val newStatus = if (isChecked) "PRESENT" else "ABSENT"
                                viewModel.markStudent(student.uuid, newStatus)
                            },
                            onEditRemarks = {
                                editingRemarksStudent = student
                                currentRemarksText = currentRemarks
                            }
                        )
                    }
                }
            }
        }

        // Save Attendance Floating Action Button - Available for any selected date
        if (visibleStudents.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    viewModel.saveAttendance { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("save_attendance_button"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Attendance")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সেভ করুন (Save)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Edit Remarks Dialog
        if (editingRemarksStudent != null) {
            AlertDialog(
                onDismissRequest = { editingRemarksStudent = null },
                title = { Text("মন্তব্য যোগ করুন (${editingRemarksStudent?.name})") },
                text = {
                    OutlinedTextField(
                        value = currentRemarksText,
                        onValueChange = { currentRemarksText = it },
                        label = { Text("মন্তব্য / নোট (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            editingRemarksStudent?.let { s ->
                                val status = attendanceMap[s.uuid]?.first ?: "PRESENT"
                                viewModel.markStudent(s.uuid, status, currentRemarksText)
                            }
                            editingRemarksStudent = null
                        }
                    ) {
                        Text("সেভ")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingRemarksStudent = null }) {
                        Text("বাতিল")
                    }
                }
            )
        }
    }
}

@Composable
fun StatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StudentAttendanceCard(
    student: StudentEntity,
    status: String,
    remarks: String,
    isEditable: Boolean = true,
    onStatusSelected: (String) -> Unit,
    onCheckboxToggled: (Boolean) -> Unit = {},
    onEditRemarks: () -> Unit
) {
    val isPresent = status == "PRESENT"
    val statusBadgeText = when (status) {
        "PRESENT" -> "✅ উপস্থিত (Present)"
        "ABSENT" -> "❌ অনুপস্থিত (Absent)"
        "LATE" -> "⏰ দেরি (Late)"
        "EXCUSED" -> "📅 ছুটি (Excused)"
        else -> "⚪ চেকবক্সে টিক দিন (Unmarked)"
    }
    val statusBadgeColor = when (status) {
        "PRESENT" -> PresentGreen
        "ABSENT" -> AbsentRed
        "LATE" -> LateAmber
        "EXCUSED" -> ExcusedBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_card_${student.rollNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = isEditable) {
                            onCheckboxToggled(!isPresent)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPresent) PresentContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student.rollNumber,
                            fontWeight = FontWeight.Bold,
                            color = if (isPresent) PresentGreen else MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subInfo = if (remarks.isNotBlank()) {
                            "$statusBadgeText • নোট: $remarks"
                        } else {
                            statusBadgeText
                        }
                        Text(
                            text = subInfo,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (status.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = statusBadgeColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isEditable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onEditRemarks,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Remarks",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Checkbox(
                            checked = isPresent,
                            onCheckedChange = { checked ->
                                onCheckboxToggled(checked)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PresentGreen,
                                checkmarkColor = Color.White,
                                uncheckedColor = if (status == "ABSENT") AbsentRed else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .testTag("student_checkbox_${student.rollNumber}")
                                .semantics {
                                    contentDescription = "Mark ${student.name} attendance"
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isEditable) {
                // 4-way Status Selector: P, A, L, E (Editable for Today)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusButton(
                        label = "P",
                        subText = "উপস্থিত",
                        isSelected = status == "PRESENT",
                        activeColor = PresentGreen,
                        activeBg = PresentContainer,
                        activeTextColor = PresentOnContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onStatusSelected("PRESENT") }
                    )

                    StatusButton(
                        label = "A",
                        subText = "অনুপস্থিত",
                        isSelected = status == "ABSENT",
                        activeColor = AbsentRed,
                        activeBg = AbsentContainer,
                        activeTextColor = AbsentOnContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onStatusSelected("ABSENT") }
                    )

                    StatusButton(
                        label = "L",
                        subText = "দেরি",
                        isSelected = status == "LATE",
                        activeColor = LateAmber,
                        activeBg = LateContainer,
                        activeTextColor = LateOnContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onStatusSelected("LATE") }
                    )

                    StatusButton(
                        label = "E",
                        subText = "ছুটি",
                        isSelected = status == "EXCUSED",
                        activeColor = ExcusedBlue,
                        activeBg = ExcusedContainer,
                        activeTextColor = ExcusedOnContainer,
                        modifier = Modifier.weight(1f),
                        onClick = { onStatusSelected("EXCUSED") }
                    )
                }
            } else {
                // Read-only Lock View for Past / Future Dates
                val isRecorded = status in listOf("PRESENT", "ABSENT", "LATE", "EXCUSED")
                val statusBg = when (status) {
                    "PRESENT" -> PresentContainer
                    "ABSENT" -> AbsentContainer
                    "LATE" -> LateContainer
                    "EXCUSED" -> ExcusedContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                val statusColor = when (status) {
                    "PRESENT" -> PresentGreen
                    "ABSENT" -> AbsentRed
                    "LATE" -> LateAmber
                    "EXCUSED" -> ExcusedBlue
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val statusTextColor = when (status) {
                    "PRESENT" -> PresentOnContainer
                    "ABSENT" -> AbsentOnContainer
                    "LATE" -> LateOnContainer
                    "EXCUSED" -> ExcusedOnContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val statusText = when (status) {
                    "PRESENT" -> "উপস্থিত (Present)"
                    "ABSENT" -> "অনুপস্থিত (Absent)"
                    "LATE" -> "দেরিতে উপস্থিতি (Late)"
                    "EXCUSED" -> "ছুটি অনুমোদিত (Excused)"
                    else -> "হাজিরা নেওয়া হয়নি (Not Recorded)"
                }
                val statusIcon = when (status) {
                    "PRESENT" -> Icons.Default.CheckCircle
                    "ABSENT" -> Icons.Default.Cancel
                    "LATE" -> Icons.Default.Schedule
                    "EXCUSED" -> Icons.Default.EventBusy
                    else -> Icons.Default.Lock
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.Bold,
                            color = statusTextColor,
                            fontSize = 13.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Read only",
                            tint = statusColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "লক করা",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusTextColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusButton(
    label: String,
    subText: String,
    isSelected: Boolean,
    activeColor: Color,
    activeBg: Color,
    activeTextColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) activeBg else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) activeColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = if (isSelected) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
