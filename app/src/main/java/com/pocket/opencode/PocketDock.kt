package com.pocket.opencode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DockItem(val label: String, val icon: ImageVector)

private val dockItems = listOf(
    DockItem("Чат", Icons.Outlined.ChatBubbleOutline),
    DockItem("Проект", Icons.Outlined.FolderOpen),
    DockItem("Модели", Icons.Outlined.AutoAwesome),
    DockItem("Скилы", Icons.Outlined.Bolt),
    DockItem("Настройки", Icons.Outlined.Settings),
)

@Composable
fun PocketDock(selected: Int, pending: Int, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Card2.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, Line.copy(alpha = 0.9f)),
        shadowElevation = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            dockItems.forEachIndexed { index, item ->
                DockButton(
                    item = item,
                    active = selected == index,
                    pending = if (index == 0) pending else 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun DockButton(
    item: DockItem,
    active: Boolean,
    pending: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val labelColor by animateColorAsState(
        targetValue = if (active) Accent else Muted,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dockLabel"
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) Accent else Muted,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dockIcon"
    )
    val pillColor by animateColorAsState(
        targetValue = if (active) Accent.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dockPill"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.92f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dockScale"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 29.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(pillColor),
            contentAlignment = Alignment.Center
        ) {
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
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            color = labelColor,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * Вертикальный вариант навигации для планшетов, складных устройств и
 * альбомной ориентации. Те же пять разделов, но крупнее и в зоне пальца.
 */
@Composable
fun PocketRail(
    selected: Int,
    pending: Int,
    status: ConnState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier,
        color = Card.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Line.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(88.dp)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ">_",
                    color = Accent,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(18.dp))
            dockItems.forEachIndexed { index, item ->
                val active = selected == index
                val tint by animateColorAsState(
                    targetValue = if (active) Accent else Muted,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "railTint"
                )
                val activeBg by animateColorAsState(
                    targetValue = if (active) Accent.copy(alpha = 0.13f) else Color.Transparent,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "railBg"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(activeBg)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BadgedBox(
                        badge = {
                            if (index == 0 && pending > 0) {
                                Badge(
                                    containerColor = Danger,
                                    contentColor = Color(0xFF2A0A10)
                                ) {
                                    Text("$pending", fontSize = 9.sp)
                                }
                            }
                        }
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        color = tint,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusTone(status))
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (status) {
                    ConnState.CONNECTED -> "online"
                    ConnState.CONNECTING, ConnState.RECONNECTING -> "связь"
                    ConnState.ERROR -> "ошибка"
                    ConnState.IDLE -> "offline"
                },
                color = Dim,
                fontSize = 9.sp,
                lineHeight = 11.sp
            )
        }
    }
}
