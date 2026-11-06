package com.pocket.opencode

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: PocketViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        vm.onAppResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (SettingsStore(this).mode == ServerMode.EMBEDDED) {
            ServerService.start(this)
        }
        setContent {
            PocketTheme {
                PocketApp(vm)
            }
        }
    }
}

@Composable
fun PocketApp(vm: PocketViewModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = Bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { PocketTabs(vm) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PocketHeader(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.tab) {
                    0 -> ChatScreen(vm)
                    1 -> ProjectScreen(vm)
                    2 -> ProvidersScreen(vm)
                    3 -> SkillsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }

    PendingDialogs(vm)
}

@Composable
private fun PocketHeader(vm: PocketViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Accent, Color(0xFF6A4FE0))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ">_",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "OPENCODE",
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Text(
                "POCKET · ${vm.serverInfo?.version ?: "ANDROID"}",
                color = Muted,
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusPill(vm.statusLabel, statusTone(vm.status))
    }
}

@Composable
private fun PocketTabs(vm: PocketViewModel) {
    val pending = vm.permissions.size + vm.forms.size
    NavigationBar(
        containerColor = Card,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBarItem(
            selected = vm.tab == 0,
            onClick = { vm.selectTab(0) },
            icon = {
                BadgedBox(
                    badge = {
                        if (pending > 0) {
                            Badge(
                                containerColor = Danger,
                                contentColor = Color(0xFF2A0A10)
                            ) {
                                Text("$pending", fontSize = 9.sp)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Чат")
                }
            },
            label = { Text("Чат", fontSize = 10.5.sp) },
            colors = tabColors()
        )
        NavigationBarItem(
            selected = vm.tab == 1,
            onClick = { vm.selectTab(1) },
            icon = { Icon(Icons.Default.List, contentDescription = "Проект") },
            label = { Text("Проект", fontSize = 10.5.sp) },
            colors = tabColors()
        )
        NavigationBarItem(
            selected = vm.tab == 2,
            onClick = { vm.selectTab(2) },
            icon = { Icon(Icons.Default.Star, contentDescription = "Модели") },
            label = { Text("Модели", fontSize = 10.5.sp) },
            colors = tabColors()
        )
        NavigationBarItem(
            selected = vm.tab == 3,
            onClick = { vm.selectTab(3) },
            icon = { Icon(Icons.Default.Build, contentDescription = "Скилы") },
            label = { Text("Скилы", fontSize = 10.5.sp) },
            colors = tabColors()
        )
        NavigationBarItem(
            selected = vm.tab == 4,
            onClick = { vm.selectTab(4) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Связь") },
            label = { Text("Связь", fontSize = 10.5.sp) },
            colors = tabColors()
        )
    }
}

@Composable
private fun tabColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent,
    selectedTextColor = Accent,
    indicatorColor = AccentDim,
    unselectedIconColor = Dim,
    unselectedTextColor = Dim,
    disabledIconColor = Dim,
    disabledTextColor = Dim
)
