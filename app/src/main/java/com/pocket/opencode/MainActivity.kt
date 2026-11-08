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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

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

private val tabTitles = listOf("Чат", "Проект", "Модели", "Скилы", "Связь")
private val tabSubtitles = listOf(
    "OpenCode Server API v2",
    "Файлы, git и просмотр diff",
    "Провайдеры, ключи и модели",
    "Локальная библиотека навыков",
    "Подключение и встроенный сервер"
)

@Composable
fun PocketApp(vm: PocketViewModel) {
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E1116), Bg, Color(0xFF07080B))
                )
            )
    ) {
        val expanded = maxWidth >= 600.dp
        val pending = vm.permissions.size + vm.forms.size
        val dockSpace by animateDpAsState(
            targetValue = if (expanded || imeVisible) 0.dp else navBottom + 84.dp,
            label = "dockSpace"
        )
        val pagerState = rememberPagerState(
            initialPage = vm.tab.coerceIn(0, tabTitles.lastIndex)
        ) { tabTitles.size }
        val selectedTab = pagerState.targetPage

        // Свайп обновляет выбранный раздел только после полной остановки
        // пагинатора: settledPage меняется и во время программной анимации,
        // из-за чего вкладка могла переключиться на промежуточную.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
                .filter { (scrolling, _) -> !scrolling }
                .map { (_, page) -> page }
                .distinctUntilChanged()
                .collect { page -> if (page != vm.tab) vm.selectTab(page) }
        }
        // Тап по док-бару/рейлу плавно доводит пагинатор до нужной страницы.
        LaunchedEffect(vm.tab) {
            val target = vm.tab.coerceIn(0, tabTitles.lastIndex)
            if (pagerState.targetPage != target) pagerState.animateScrollToPage(target)
        }

        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                PocketRail(
                    selected = selectedTab,
                    pending = pending,
                    status = vm.status,
                    onSelect = { vm.selectTab(it) },
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    PocketHeader(vm, expanded = true, onDebug = { debugOpen = true })
                    TabContent(vm, pagerState, Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                PocketHeader(vm, expanded = false, onDebug = { debugOpen = true })
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = dockSpace)
                        .then(if (vm.tab == 0) Modifier else Modifier.imePadding())
                ) {
                    TabContent(vm, pagerState)
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
                        selected = selectedTab,
                        pending = pending,
                        onSelect = { vm.selectTab(it) }
                    )
                }
            }
        }
    }

    if (debugOpen) {
        DebugSheet { debugOpen = false }
    }
    PendingDialogs(vm)
}

@Composable
private fun TabContent(
    vm: PocketViewModel,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1)
        ),
        key = { it }
    ) { page ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .widthIn(max = if (page == 0) 860.dp else 980.dp)
                    .fillMaxHeight()
            ) {
                when (page) {
                    0 -> ChatScreen(vm)
                    1 -> ProjectScreen(vm)
                    2 -> ProvidersScreen(vm)
                    3 -> SkillsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun PocketHeader(vm: PocketViewModel, expanded: Boolean, onDebug: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!expanded) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Accent, Accent2)))
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onDebug() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ">_",
                    color = Color(0xFF241505),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (expanded) tabTitles.getOrElse(vm.tab) { "OpenCode" } else "OpenCode",
                color = TextMain,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (expanded) {
                    tabSubtitles.getOrElse(vm.tab) { "" }
                } else {
                    "Pocket · ${vm.serverInfo?.version ?: "Android"}"
                },
                color = Dim,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.3.sp,
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
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
