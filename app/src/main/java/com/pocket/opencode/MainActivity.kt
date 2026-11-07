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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
        if (SettingsStore(this).mode == ServerMode.EMBEDDED) {
            // На Android 8–10 каталог /sdcard/OpenCode открывается обычным
            // runtime-разрешением. На 11+ используется отдельный экран
            // «Доступ ко всем файлам» из настроек приложения.
            if (Workspace.needsLegacyStoragePermission(this)) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQ_STORAGE
                )
            }
            ServerService.start(this)
        }
        setContent {
            PocketTheme {
                PocketApp(vm)
            }
        }
    }

    private companion object {
        const val REQ_NOTIFICATIONS = 1
        const val REQ_STORAGE = 2
    }
}

@Composable
fun PocketApp(vm: PocketViewModel) {
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val dockSpace by animateDpAsState(
        targetValue = if (imeVisible) 0.dp else navBottom + 82.dp,
        label = "dockSpace"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0918), Bg, Color(0xFF050409))
                )
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PocketHeader(vm, onDebug = { debugOpen = true })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = dockSpace)
                    .then(if (vm.tab == 0) Modifier else Modifier.imePadding())
            ) {
                when (vm.tab) {
                    0 -> ChatScreen(vm)
                    1 -> ProjectScreen(vm)
                    2 -> ProvidersScreen(vm)
                    3 -> SkillsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                PocketDock(
                    selected = vm.tab,
                    pending = vm.permissions.size + vm.forms.size,
                    onSelect = { vm.selectTab(it) }
                )
            }
        }
    }

    if (debugOpen) {
        DebugSheet { debugOpen = false }
    }
    PendingDialogs(vm)
}

@Composable
private fun PocketHeader(vm: PocketViewModel, onDebug: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Accent, Accent2)))
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onDebug() })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                ">_",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "OpenCode",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
            Text(
                "Pocket · ${vm.serverInfo?.version ?: "Android"}",
                color = Dim,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val tone = statusTone(vm.status)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tone)
            )
            Text(
                vm.statusLabel,
                color = tone,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
