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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ClassEntity
import com.example.data.local.entity.StudentEntity
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ClassStudentScreen(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) } // 0 = Classes, 1 = Students

    val classes by viewModel.classes.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val students by viewModel.studentsInSelectedClass.collectAsState()

    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var classToDelete by remember { mutableStateOf<ClassEntity?>(null) }
    var studentToDelete by remember { mutableStateOf<StudentEntity?>(null) }
    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }

    var studentSearchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Tab Row
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ক্লাস তালিকা (${classes.size})", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_classes")
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("শিক্ষার্থীবৃন্দ (${students.size})", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_students")
                )
            }

            if (currentTab == 0) {
                // Classes List
                if (classes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "কোনো ক্লাস যোগ করা হয়নি",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "নিচের '+' বাটনে ট্যাপ করে নতুন ক্লাস যোগ করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(classes, key = { it.uuid }) { cls ->
                            val isSelected = cls.uuid == selectedClass?.uuid
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("class_item_${cls.className}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                onClick = {
                                    viewModel.selectClass(cls)
                                    currentTab = 1
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.School,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = cls.className,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val sub = if (cls.subject.isNotBlank()) " | বিষয়: ${cls.subject}" else ""
                                            Text(
                                                text = "শাখা/সেকশন: ${cls.section.ifBlank { "ডিফল্ট" }}$sub",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { classToDelete = cls },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Class",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Students Tab
                Column(modifier = Modifier.fillMaxSize()) {
                    // Class Selector Bar for Student Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ক্লাস ফিল্টার করুন:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { showAddClassDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("নতুন ক্লাস", fontSize = 12.sp)
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
                        }
                    }

                    // Search input
                    OutlinedTextField(
                        value = studentSearchQuery,
                        onValueChange = { studentSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .testTag("student_search_input"),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("নাম বা রোল দিয়ে খুঁজুন...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    val filteredStudents = students.filter {
                        it.name.contains(studentSearchQuery, ignoreCase = true) ||
                                it.rollNumber.contains(studentSearchQuery, ignoreCase = true)
                    }

                    if (selectedClass == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("শিক্ষার্থী দেখতে প্রথমে একটি ক্লাস নির্বাচন করুন")
                        }
                    } else if (filteredStudents.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (studentSearchQuery.isNotBlank()) "খুঁজে পাওয়া যায়নি"
                                    else "এই ক্লাসে কোনো শিক্ষার্থী নেই"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { showAddStudentDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("শিক্ষার্থী যোগ করুন")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredStudents, key = { it.uuid }) { student ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("student_row_${student.rollNumber}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = student.rollNumber,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = student.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "লিঙ্গ: ${student.gender} ${if (student.phone.isNotBlank()) " | 📞 " + student.phone else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    ) {
                                                        Text(
                                                            text = "মাসিক বেতন: ৳${student.monthlyFee.toInt()}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "ভর্তি: ${student.admissionDate}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { studentToEdit = student },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit Student",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { studentToDelete = student },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Student",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                if (currentTab == 0) {
                    showAddClassDialog = true
                } else {
                    if (classes.isEmpty()) {
                        Toast.makeText(context, "শিক্ষার্থী যোগ করতে প্রথমে একটি ক্লাস তৈরি করুন", Toast.LENGTH_SHORT).show()
                        showAddClassDialog = true
                    } else {
                        showAddStudentDialog = true
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_item_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (currentTab == 0) "নতুন ক্লাস" else "নতুন শিক্ষার্থী", fontWeight = FontWeight.Bold)
            }
        }

        // Add Class Dialog
        if (showAddClassDialog) {
            var className by remember { mutableStateOf("") }
            var section by remember { mutableStateOf("") }
            var subject by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddClassDialog = false },
                title = { Text("নতুন ক্লাস তৈরি করুন", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = className,
                            onValueChange = { className = it },
                            label = { Text("ক্লাসের নাম (যেমন: দশম শ্রেণি / Class 10)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = section,
                            onValueChange = { section = it },
                            label = { Text("শাখা / সেকশন (যেমন: A / ক)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("বিষয় (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (className.isBlank()) {
                                Toast.makeText(context, "ক্লাসের নাম প্রদান করুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.addClass(className, section, subject) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) showAddClassDialog = false
                            }
                        }
                    ) {
                        Text("যোগ করুন")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddClassDialog = false }) {
                        Text("বাতিল")
                    }
                }
            )
        }

        // Add Student Dialog (With Class Selector)
        if (showAddStudentDialog) {
            var selectedClassInDialog by remember {
                mutableStateOf(selectedClass ?: classes.firstOrNull())
            }
            var classDropdownExpanded by remember { mutableStateOf(false) }
            var roll by remember { mutableStateOf("") }
            var studentName by remember { mutableStateOf("") }
            var gender by remember { mutableStateOf("Male") }
            var phone by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var monthlyFeeStr by remember { mutableStateOf("") }
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            var admissionDate by remember { mutableStateOf(todayStr) }

            val admCal = Calendar.getInstance()
            val admDatePicker = remember {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        admissionDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    },
                    admCal.get(Calendar.YEAR),
                    admCal.get(Calendar.MONTH),
                    admCal.get(Calendar.DAY_OF_MONTH)
                )
            }

            AlertDialog(
                onDismissRequest = { showAddStudentDialog = false },
                title = { Text("নতুন শিক্ষার্থী যোগ করুন", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Class Selector Field
                        Text("ছাত্রের শ্রেণি / ক্লাস নির্বাচন করুন *", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { classDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedClassInDialog?.let { "${it.className} (${if (it.section.isNotBlank()) "শাখা: ${it.section}" else "সকল শাখা"})" }
                                            ?: "ক্লাস নির্বাচন করুন",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selectedClassInDialog != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Class")
                            }

                            DropdownMenu(
                                expanded = classDropdownExpanded,
                                onDismissRequest = { classDropdownExpanded = false }
                            ) {
                                classes.forEach { cls ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${cls.className} ${if (cls.section.isNotBlank()) "(${cls.section})" else ""}",
                                                fontWeight = if (cls.uuid == selectedClassInDialog?.uuid) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedClassInDialog = cls
                                            classDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "+ নতুন ক্লাস তৈরি করুন",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        classDropdownExpanded = false
                                        showAddClassDialog = true
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = roll,
                            onValueChange = { roll = it },
                            label = { Text("রোল নম্বর (Roll Number)*") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = studentName,
                            onValueChange = { studentName = it },
                            label = { Text("শিক্ষার্থীর নাম (Student Name)*") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = monthlyFeeStr,
                            onValueChange = { monthlyFeeStr = it },
                            label = { Text("মাসিক বেতন (টাকা/মাস)*") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = admissionDate,
                            onValueChange = { admissionDate = it },
                            label = { Text("ভর্তির তারিখ (YYYY-MM-DD)*") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { admDatePicker.show() }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("লিঙ্গ:", style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = gender == "Male", onClick = { gender = "Male" })
                                Text("ছেলে", fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = gender == "Female", onClick = { gender = "Female" })
                                Text("মেয়ে", fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = gender == "Other", onClick = { gender = "Other" })
                                Text("অন্যান্য", fontSize = 13.sp)
                            }
                        }
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("অভিভাবকের ফোন নম্বর (ঐচ্ছিক)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedClassInDialog == null) {
                                Toast.makeText(context, "অনুগ্রহ করে ছাত্রের শ্রেণি নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (roll.isBlank() || studentName.isBlank()) {
                                Toast.makeText(context, "রোল এবং নাম আবশ্যক", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val chosenClass = selectedClassInDialog!!
                            val parsedFee = monthlyFeeStr.toDoubleOrNull() ?: 0.0
                            viewModel.addStudent(
                                classUuid = chosenClass.uuid,
                                roll = roll,
                                name = studentName,
                                gender = gender,
                                phone = phone,
                                email = email,
                                monthlyFee = parsedFee,
                                admissionDate = admissionDate
                            ) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    viewModel.selectClass(chosenClass)
                                    showAddStudentDialog = false
                                }
                            }
                        }
                    ) {
                        Text("যুক্ত করুন")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddStudentDialog = false }) {
                        Text("বাতিল")
                    }
                }
            )
        }

        // Delete Class Confirmation
        if (classToDelete != null) {
            AlertDialog(
                onDismissRequest = { classToDelete = null },
                title = { Text("ক্লাস মুছে ফেলবেন?") },
                text = { Text("আপনি কি নিশ্চিতভাবে '${classToDelete?.className}' মুছে ফেলতে চান?") },
                confirmButton = {
                    Button(
                        onClick = {
                            classToDelete?.let { viewModel.deleteClass(it.uuid) }
                            classToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("মুছুন")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { classToDelete = null }) { Text("বাতিল") }
                }
            )
        }

        // Edit Student Dialog
        if (studentToEdit != null) {
            val student = studentToEdit!!
            var editRoll by remember(student) { mutableStateOf(student.rollNumber) }
            var editName by remember(student) { mutableStateOf(student.name) }
            var editPhone by remember(student) { mutableStateOf(student.phone) }
            var editMonthlyFeeStr by remember(student) {
                mutableStateOf(
                    if (student.monthlyFee % 1.0 == 0.0) student.monthlyFee.toInt().toString()
                    else student.monthlyFee.toString()
                )
            }
            val defaultAdm = if (student.admissionDate.isNotBlank()) student.admissionDate else {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(if (student.createdAt > 0L) student.createdAt else System.currentTimeMillis()))
            }
            var editAdmissionDate by remember(student) { mutableStateOf(defaultAdm) }

            val editCal = Calendar.getInstance()
            val admParts = editAdmissionDate.split("-")
            val defYear = admParts.getOrNull(0)?.toIntOrNull() ?: editCal.get(Calendar.YEAR)
            val defMonth = (admParts.getOrNull(1)?.toIntOrNull()?.minus(1)) ?: editCal.get(Calendar.MONTH)
            val defDay = admParts.getOrNull(2)?.toIntOrNull() ?: editCal.get(Calendar.DAY_OF_MONTH)

            val editDatePicker = remember(student) {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        editAdmissionDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    },
                    defYear,
                    defMonth,
                    defDay
                )
            }

            AlertDialog(
                onDismissRequest = { studentToEdit = null },
                title = { Text("শিক্ষার্থীর তথ্য ও মাসিক বিল পরিবর্তন", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editRoll,
                            onValueChange = { editRoll = it },
                            label = { Text("রোল নম্বর (Roll Number)*") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("শিক্ষার্থীর নাম (Student Name)*") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editMonthlyFeeStr,
                            onValueChange = { editMonthlyFeeStr = it },
                            label = { Text("মাসিক বিল / বেতন (টাকা)*") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editAdmissionDate,
                            onValueChange = { editAdmissionDate = it },
                            label = { Text("ভর্তির তারিখ (YYYY-MM-DD)*") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { editDatePicker.show() }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("অভিভাবকের ফোন নম্বর") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editRoll.isBlank() || editName.isBlank()) {
                                Toast.makeText(context, "রোল এবং নাম আবশ্যক", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val parsedFee = editMonthlyFeeStr.toDoubleOrNull() ?: 0.0
                            val finalAdmDate = if (editAdmissionDate.isNotBlank()) editAdmissionDate else defaultAdm
                            viewModel.updateStudent(
                                studentUuid = student.uuid,
                                roll = editRoll,
                                name = editName,
                                phone = editPhone,
                                monthlyFee = parsedFee,
                                admissionDate = finalAdmDate
                            ) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    studentToEdit = null
                                }
                            }
                        }
                    ) {
                        Text("সংরক্ষণ করুন")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { studentToEdit = null }) {
                        Text("বাতিল")
                    }
                }
            )
        }

        // Delete Student Confirmation
        if (studentToDelete != null) {
            AlertDialog(
                onDismissRequest = { studentToDelete = null },
                title = { Text("শিক্ষার্থী মুছে ফেলবেন?") },
                text = { Text("আপনি কি নিশ্চিতভাবে '${studentToDelete?.name}' (রোল: ${studentToDelete?.rollNumber}) মুছে ফেলতে চান?") },
                confirmButton = {
                    Button(
                        onClick = {
                            studentToDelete?.let { viewModel.deleteStudent(it.uuid) }
                            studentToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("মুছুন")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { studentToDelete = null }) { Text("বাতিল") }
                }
            )
        }
    }
}
