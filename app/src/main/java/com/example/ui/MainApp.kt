package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.SyncStatus
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClassStudentScreen
import com.example.ui.screens.FeeCollectionScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SyncSettingsScreen
import com.example.ui.theme.PresentGreen
import com.example.ui.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val isAuthChecking by viewModel.isAuthChecking.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    if (isAuthChecking) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    } else if (activeUser == null) {
        AuthScreen(viewModel = viewModel)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (selectedNavIndex) {
                                    0 -> "হাজিরা খাতা (Attendance)"
                                    1 -> "ক্লাস ও শিক্ষার্থী (Class & Student)"
                                    2 -> "অ্যাটেন্ডেন্স রিপোর্ট (Reports)"
                                    3 -> "কালেক্ট বেতন (Fee Collection)"
                                    else -> "সিংক ও সেটিংস (Sync Settings)"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            activeUser?.institution?.let { inst ->
                                if (inst.isNotBlank()) {
                                    Text(
                                        text = inst,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        // Visual Sync Indicator Chip in TopAppBar
                        SyncStatusChip(
                            syncStatus = syncStatus,
                            unsyncedCount = unsyncedCount,
                            lastSyncTimestamp = lastSyncTimestamp,
                            onClick = {
                                viewModel.triggerSync()
                                val timeText = if (lastSyncTimestamp > 0) {
                                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))
                                } else "কখনও নয়"

                                val toastMsg = when (syncStatus) {
                                    is SyncStatus.Syncing -> "সার্ভারের সাথে ডেটা সিংক চলছে..."
                                    is SyncStatus.Error -> "সার্ভারে পুনঃসংযোগ চেষ্টা করা হচ্ছে... (সর্বশেষ সিংক: $timeText)"
                                    else -> {
                                        if (unsyncedCount > 0) {
                                            "${unsyncedCount}টি পেন্ডিং রেকর্ড সিংক করা হচ্ছে..."
                                        } else {
                                            "অ্যাপটি সার্ভারের সাথে সম্পূর্ণ সিংকড (সর্বশেষ: $timeText)। রিফ্রেশ করা হচ্ছে..."
                                        }
                                    }
                                }
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = selectedNavIndex == 0,
                        onClick = { selectedNavIndex = 0 },
                        icon = {
                            Icon(Icons.Default.FactCheck, contentDescription = "Attendance")
                        },
                        label = { Text("হাজিরা", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_attendance")
                    )

                    NavigationBarItem(
                        selected = selectedNavIndex == 1,
                        onClick = { selectedNavIndex = 1 },
                        icon = {
                            Icon(Icons.Default.Groups, contentDescription = "Classes & Students")
                        },
                        label = { Text("ক্লাস/ছাত্র", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_classes_students")
                    )

                    NavigationBarItem(
                        selected = selectedNavIndex == 2,
                        onClick = { selectedNavIndex = 2 },
                        icon = {
                            Icon(Icons.Default.Assessment, contentDescription = "Reports")
                        },
                        label = { Text("রিপোর্ট", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_reports")
                    )

                    NavigationBarItem(
                        selected = selectedNavIndex == 3,
                        onClick = { selectedNavIndex = 3 },
                        icon = {
                            Icon(Icons.Default.Payments, contentDescription = "Fee Collection")
                        },
                        label = { Text("কালেক্ট বেতন", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_fee_collection")
                    )

                    NavigationBarItem(
                        selected = selectedNavIndex == 4,
                        onClick = { selectedNavIndex = 4 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unsyncedCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("$unsyncedCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        label = { Text("সেটিংস", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Visual Sync Status Bar / Strip (Shows sync progress or offline status)
                SyncStatusBarStrip(
                    syncStatus = syncStatus,
                    unsyncedCount = unsyncedCount,
                    onSyncClick = {
                        viewModel.triggerSync()
                        Toast.makeText(context, "সিংক শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Crossfade(targetState = selectedNavIndex, label = "ScreenTransition") { index ->
                        when (index) {
                            0 -> AttendanceScreen(
                                viewModel = viewModel,
                                onNavigateToClasses = { selectedNavIndex = 1 }
                            )
                            1 -> ClassStudentScreen(viewModel = viewModel)
                            2 -> ReportScreen(viewModel = viewModel)
                            3 -> FeeCollectionScreen(viewModel = viewModel)
                            4 -> SyncSettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual interactive indicator chip in the TopAppBar displaying live sync state with the backend server.
 */
@Composable
fun SyncStatusChip(
    syncStatus: SyncStatus,
    unsyncedCount: Int,
    lastSyncTimestamp: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSyncing = syncStatus is SyncStatus.Syncing
    val isError = syncStatus is SyncStatus.Error
    val hasPending = unsyncedCount > 0

    val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
    val rotationAngle by if (isSyncing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val (bgColor, contentColor, borderColor) = when {
        isSyncing -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
        isError -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
        hasPending -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
        )
        else -> Triple(
            PresentGreen.copy(alpha = 0.15f),
            PresentGreen,
            PresentGreen.copy(alpha = 0.35f)
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .padding(end = 12.dp)
            .testTag("sync_status_indicator")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            when {
                isSyncing -> {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Syncing",
                        tint = contentColor,
                        modifier = Modifier
                            .size(15.dp)
                            .rotate(rotationAngle)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "সিংক হচ্ছে...",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        fontSize = 11.sp
                    )
                }
                isError -> {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Sync Error",
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (hasPending) "$unsyncedCount পেন্ডিং" else "অফলাইন",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        fontSize = 11.sp
                    )
                }
                hasPending -> {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Pending Sync",
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$unsyncedCount পেন্ডিং",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        fontSize = 11.sp
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced",
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "সিংকড",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Contextual Status Bar strip placed directly under the TopAppBar
 * showing live synchronization progress and clear offline/pending indicators.
 */
@Composable
fun SyncStatusBarStrip(
    syncStatus: SyncStatus,
    unsyncedCount: Int,
    onSyncClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        if (syncStatus is SyncStatus.Syncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }

        AnimatedVisibility(
            visible = syncStatus is SyncStatus.Error || unsyncedCount > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val isError = syncStatus is SyncStatus.Error
            Surface(
                color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.CloudOff else Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isError) {
                                val err = (syncStatus as SyncStatus.Error).message
                                if (err.contains("Failed to connect", ignoreCase = true) || err.contains("Unable to resolve", ignoreCase = true)) {
                                    "সার্ভারে সংযোগ বিচ্ছিন্ন। অফলাইনে কাজ চলছে (${unsyncedCount}টি পেন্ডিং)"
                                } else {
                                    "সিংক সমস্যা: ${err.take(40)}..."
                                }
                            } else {
                                "অফলাইন পরিবর্তন: ${unsyncedCount}টি রেকর্ড ব্যাকএন্ডে পাঠানোর অপেক্ষায়"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = onSyncClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("সিংক", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

