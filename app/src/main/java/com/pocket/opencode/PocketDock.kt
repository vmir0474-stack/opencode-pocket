package com.pocket.opencode

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SettingsInputAntenna
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DockItem(val label: String, val icon: ImageVector)

private val dockItems = listOf(
    DockItem("Чат", Icons.Outlined.ChatBubbleOutline),
    DockItem("Проект", Icons.Outlined.FolderOpen),
    DockItem("Модели", Icons.Outlined.AutoAwesome),
    DockItem("Скилы", Icons.Outlined.Bolt),
    DockItem("Связь", Icons.Outlined.SettingsInputAntenna),
)

@Composable
fun PocketDock(selected: Int, pending: Int, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Card2.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.14f)),
        shadowElevation = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockItems.forEachIndexed { index, item ->
                val active = selected == index
                val labelColor by animateColorAsState(
                    targetValue = if (active) Accent else Dim,
                    label = "dockLabel"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (active) Accent else Muted,
                    label = "dockIcon"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (active) {
                                    Brush.linearGradient(
                                        listOf(
                                            Accent.copy(alpha = 0.24f),
                                            Accent2.copy(alpha = 0.14f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                }
                            )
                            .then(
                                if (active) {
                                    Modifier.border(
                                        1.dp,
                                        Accent.copy(alpha = 0.24f),
                                        RoundedCornerShape(15.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (pending > 0 && index == 0) {
                                    Badge(
                                        containerColor = Danger,
                                        contentColor = Color(0xFF2A0A10)
                                    ) {
                                        Text("$pending", fontSize = 8.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        color = labelColor,
                        fontSize = 9.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = if (active) 0.2.sp else 0.sp
                    )
                }
            }
        }
    }
}
