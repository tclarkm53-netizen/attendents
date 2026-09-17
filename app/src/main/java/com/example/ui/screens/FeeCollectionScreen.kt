package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FeePaymentEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.repository.StudentFeeSummary
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.LateAmber
import com.example.ui.theme.PresentGreen
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeCollectionScreen(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    val classes by viewModel.classes.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val feeSummaries by viewModel.classFeeSummaries.collectAsState()
    val isFeeLoading by viewModel.isFeeLoading.collectAsState()

    var classDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, DUE, PAID

    var studentForPayment by remember { mutableStateOf<StudentFeeSummary?>(null) }
    var studentForFeeEdit by remember { mutableStateOf<StudentFeeSummary?>(null) }
    var studentForHistory by remember { mutableStateOf<StudentEntity?>(null) }
    var lastReceiptToShow by remember { mutableStateOf<FeePaymentEntity?>(null) }

    LaunchedEffect(selectedClass) {
        if (selectedClass != null) {
            viewModel.loadFeeSummariesForSelectedClass()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Class Selector & Action Bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Class Dropdown
                    ExposedDropdownMenuBox(
                        expanded = classDropdownExpanded,
                        onExpandedChange = { classDropdownExpanded = !classDropdownExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedClass?.let { "${it.className} (${it.section.ifBlank { "সকল শাখা" }})" }
                                ?: "ক্লাস নির্বাচন করুন",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ক্লাস নির্বাচন") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = classDropdownExpanded,
                            onDismissRequest = { classDropdownExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text("${cls.className} (${cls.section.ifBlank { "শাখা নেই" }})") },
                                    onClick = {
                                        viewModel.selectClass(cls)
                                        classDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.loadFeeSummariesForSelectedClass() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search & Filter
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("শিক্ষার্থীর নাম বা রোল দিয়ে খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fee_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterMode == "ALL",
                        onClick = { filterMode = "ALL" },
                        label = { Text("সকল (${feeSummaries.size})", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    val dueCount = feeSummaries.count { !it.isFullyPaid }
                    FilterChip(
                        selected = filterMode == "DUE",
                        onClick = { filterMode = "DUE" },
                        label = { Text("বকেয়া ($dueCount)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    val paidCount = feeSummaries.count { it.isFullyPaid }
                    FilterChip(
                        selected = filterMode == "PAID",
                        onClick = { filterMode = "PAID" },
                        label = { Text("পরিশোধিত ($paidCount)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Aggregate Class Fee Statistics
        if (selectedClass != null && feeSummaries.isNotEmpty()) {
            val totalPayable = feeSummaries.sumOf { it.totalPayable }
            val totalPaid = feeSummaries.sumOf { it.totalPaid }
            val totalDue = feeSummaries.sumOf { it.dueAmount }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("মোট প্রদেয়", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "৳${totalPayable.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("মোট আদায়", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "৳${totalPaid.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PresentGreen
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("মোট বকেয়া", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "৳${totalDue.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (totalDue > 0) AbsentRed else PresentGreen
                        )
                    }
                }
            }
        }

        // Students List
        val filteredList = feeSummaries.filter { summary ->
            val matchesSearch = summary.student.name.contains(searchQuery, ignoreCase = true) ||
                    summary.student.rollNumber.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                "DUE" -> !summary.isFullyPaid
                "PAID" -> summary.isFullyPaid
                else -> true
            }
            matchesSearch && matchesFilter
        }

        if (isFeeLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (selectedClass == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("বেতন দেখতে অনুগ্রহ করে একটি ক্লাস নির্বাচন করুন", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো শিক্ষার্থীর তথ্য পাওয়া যায়নি", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.student.uuid }) { summary ->
                    FeeStudentCard(
                        summary = summary,
                        onCollectPayment = { studentForPayment = summary },
                        onEditFee = { studentForFeeEdit = summary },
                        onViewHistory = { studentForHistory = summary.student }
                    )
                }
            }
        }
    }

    // Payment Collection Dialog
    if (studentForPayment != null) {
        val summary = studentForPayment!!
        PaymentDialog(
            summary = summary,
            onDismiss = { studentForPayment = null },
            onPaymentSuccess = { receipt ->
                studentForPayment = null
                lastReceiptToShow = receipt
                Toast.makeText(context, "বেতন সফলভাবে পরিশোধ হয়েছে ও সার্ভারে সিংক শুরু হয়েছে", Toast.LENGTH_SHORT).show()
            },
            viewModel = viewModel
        )
    }

    // Edit Monthly Fee Dialog
    if (studentForFeeEdit != null) {
        val summary = studentForFeeEdit!!
        EditFeeDialog(
            student = summary.student,
            currentFee = summary.monthlyFee,
            onDismiss = { studentForFeeEdit = null },
            onSaved = { newFee ->
                viewModel.updateStudentMonthlyFee(summary.student.uuid, newFee)
                studentForFeeEdit = null
                Toast.makeText(context, "মাসিক বেতন পরিবর্তন সফল হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Payment History Dialog
    if (studentForHistory != null) {
        val stu = studentForHistory!!
        PaymentHistoryDialog(
            student = stu,
            viewModel = viewModel,
            onDismiss = { studentForHistory = null }
        )
    }

    // Receipt Dialog
    if (lastReceiptToShow != null) {
        ReceiptDetailsDialog(
            payment = lastReceiptToShow!!,
            onDismiss = { lastReceiptToShow = null }
        )
    }
}

@Composable
fun FeeStudentCard(
    summary: StudentFeeSummary,
    onCollectPayment: () -> Unit,
    onEditFee: () -> Unit,
    onViewHistory: () -> Unit
) {
    val student = summary.student
    val isPaid = summary.isFullyPaid

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fee_card_${student.rollNumber}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Roll, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student.rollNumber,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (student.phone.isNotBlank()) {
                            Text(
                                text = "মোবাইল: ${student.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPaid) PresentGreen.copy(alpha = 0.12f) else AbsentRed.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isPaid) PresentGreen else AbsentRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPaid) "পরিশোধিত" else "${summary.dueMonths} মাস বাকি",
                            color = if (isPaid) PresentGreen else AbsentRed,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Day Counting and Fee Calculation Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "ভর্তির তারিখ: ${summary.admissionDate} (${summary.totalDaysEnrolled} দিন)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "বিলকৃত: ${summary.billedMonths} মাস",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "রানিং: ${summary.runningDays} দিন",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "কারেন্ট মাসে অতিবাহিত: ${summary.runningDays} দিন",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Column(
                    modifier = Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("মাসিক: ৳${summary.monthlyFee.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = onEditFee, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Fee", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = "মোট প্রদেয়: ৳${summary.totalPayable.toInt()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "(${summary.billedMonths} মাস × ৳${summary.monthlyFee.toInt()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Paid vs Due Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "পরিশোধিত: ৳${summary.totalPaid.toInt()}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PresentGreen
                    )
                    Text(
                        text = "বকেয়া বেতন: ৳${summary.dueAmount.toInt()}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (summary.dueAmount > 0) AbsentRed else PresentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("রিসিপ্ট হিস্ট্রি", fontSize = 12.sp)
                }

                Button(
                    onClick = onCollectPayment,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("collect_btn_${student.rollNumber}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaid) MaterialTheme.colorScheme.primary else PresentGreen
                    )
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPaid) "অতিরিক্ত/অগ্রিম" else "বেতন আদায়", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PaymentDialog(
    summary: StudentFeeSummary,
    onDismiss: () -> Unit,
    onPaymentSuccess: (FeePaymentEntity) -> Unit,
    viewModel: AttendanceViewModel
) {
    val student = summary.student
    var amountStr by remember {
        val defaultAmount = if (summary.dueAmount > 0) summary.dueAmount else summary.monthlyFee
        mutableStateOf(defaultAmount.toInt().toString())
    }

    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var paymentDate by remember { mutableStateOf(todayStr) }

    // Auto calculate suggested month covered
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    var monthCovered by remember { mutableStateOf(monthName) }
    var note by remember { mutableStateOf("নগদ পরিশোধ") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Paid, contentDescription = null, tint = PresentGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("বেতন আদায় করুন", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Student Summary Header
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${student.name} (রোল: ${student.rollNumber})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "ভর্তি: ${summary.admissionDate} (মোট ${summary.totalDaysEnrolled} দিন) | বিল: ${summary.billedMonths} মাস | রানিং: ${summary.runningDays} দিন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "মাসিক বেতন: ৳${summary.monthlyFee.toInt()} | বকেয়া: ৳${summary.dueAmount.toInt()} (${summary.dueMonths} মাস)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("পরিশোধের পরিমাণ (টাকা)*") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Quick Amount Shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (summary.dueAmount > 0) {
                        OutlinedButton(
                            onClick = { amountStr = summary.dueAmount.toInt().toString() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("পূর্ণ বকেয়া", fontSize = 10.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { amountStr = summary.monthlyFee.toInt().toString() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("১ মাস (৳${summary.monthlyFee.toInt()})", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { amountStr = (summary.monthlyFee * 2).toInt().toString() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("২ মাস", fontSize = 10.sp)
                    }
                }

                OutlinedTextField(
                    value = monthCovered,
                    onValueChange = { monthCovered = it },
                    label = { Text("কোন মাসের বেতন (যেমন: মার্চ ২০২৬)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("পরিশোধের তারিখ (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("মন্তব্য / পরিশোধ মাধ্যম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (amount <= 0.0) return@Button
                    isSubmitting = true
                    viewModel.addFeePayment(
                        student = student,
                        amount = amount,
                        paymentDate = paymentDate,
                        monthCovered = monthCovered,
                        note = note
                    ) { success, _, receipt ->
                        isSubmitting = false
                        if (success && receipt != null) {
                            onPaymentSuccess(receipt)
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("confirm_payment_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PresentGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("পরিশোধ নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun EditFeeDialog(
    student: StudentEntity,
    currentFee: Double,
    onDismiss: () -> Unit,
    onSaved: (Double) -> Unit
) {
    var feeStr by remember { mutableStateOf(currentFee.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("মাসিক বেতন নির্ধারণ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${student.name} (রোল: ${student.rollNumber}) এর প্রতি মাসের বেতন পরিবর্তন করুন:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = feeStr,
                    onValueChange = { feeStr = it },
                    label = { Text("নতুন মাসিক বেতন (টাকা)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newFee = feeStr.toDoubleOrNull() ?: currentFee
                    onSaved(newFee)
                }
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun PaymentHistoryDialog(
    student: StudentEntity,
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    val paymentsFlow = remember(student.uuid) { viewModel.getPaymentsForStudent(student.uuid) }
    val payments by paymentsFlow.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("বেতন জমার ইতিহাস", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                Text(
                    text = "${student.name} (রোল: ${student.rollNumber})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (payments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("এখনও কোনো বেতন জমা হয়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(payments, key = { it.uuid }) { payment ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "৳${payment.amountPaid.toInt()} টাকা",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = PresentGreen
                                        )
                                        Text(
                                            text = payment.paymentDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "রিসিপ্ট: ${payment.receiptNo}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (payment.monthCovered.isNotBlank()) {
                                            Text(
                                                text = payment.monthCovered,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    if (payment.note.isNotBlank()) {
                                        Text(
                                            text = "নোট: ${payment.note}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("ঠিক আছে") }
        }
    )
}

@Composable
fun ReceiptDetailsDialog(
    payment: FeePaymentEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = PresentGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("বেতন পরিশোধ রসিদ (Receipt)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "রসিদ নং: ${payment.receiptNo}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("পরিশোধের তারিখ: ${payment.paymentDate}", style = MaterialTheme.typography.bodySmall)
                    if (payment.monthCovered.isNotBlank()) {
                        Text("মাসের বিবরণ: ${payment.monthCovered}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট পরিশোধিত অংক:", fontWeight = FontWeight.Bold)
                        Text(
                            "৳${payment.amountPaid.toInt()} টাকা",
                            fontWeight = FontWeight.Bold,
                            color = PresentGreen,
                            fontSize = 16.sp
                        )
                    }
                    if (payment.note.isNotBlank()) {
                        Text("পরিশোধ মাধ্যম / নোট: ${payment.note}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "সার্ভারে অটো-সিঙ্ক সক্রিয় আছে।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )
}
