package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClassStudentScreen
import com.example.ui.screens.FeeCollectionScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SyncSettingsScreen
import com.example.ui.theme.PresentGreen
import com.example.ui.viewmodel.AttendanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: AttendanceViewModel = viewModel()
) {
    val activeUser by viewModel.activeUser.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }

    if (activeUser == null) {
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
                        // Quick Sync Shortcut in TopAppBar
                        IconButton(
                            onClick = {
                                viewModel.triggerSync()
                            },
                            modifier = Modifier.testTag("top_bar_sync_icon")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unsyncedCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("$unsyncedCount", fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
