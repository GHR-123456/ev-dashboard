package com.evdash.app.ui.sniffer

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evdash.app.data.PacketDirection
import com.evdash.app.data.RawPacket
import com.evdash.app.ui.theme.BackgroundGradientEnd
import com.evdash.app.ui.theme.BackgroundGradientStart
import com.evdash.app.ui.theme.DangerRed
import com.evdash.app.ui.theme.ElectricCyan
import com.evdash.app.ui.theme.EvGreen
import com.evdash.app.ui.theme.SurfaceVariantLight
import com.evdash.app.ui.theme.TextDim
import com.evdash.app.ui.theme.TextHigh
import com.evdash.app.ui.theme.TextMid
import kotlinx.coroutines.delay

@Composable
fun SnifferScreen(
    modifier: Modifier = Modifier,
    viewModel: SnifferViewModel = hiltViewModel()
) {
    val packets by viewModel.packets.collectAsStateWithLifecycle()
    val toastMsg by viewModel.toastMsg.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 仅当用户已经在列表底部附近时，才自动滚动到最后一条；上滚查看历史时不打扰
    LaunchedEffect(packets.size) {
        if (packets.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val total = info.totalItemsCount
        if (total - 1 - lastVisible <= 3) {
            listState.scrollToItem(packets.size - 1)
        }
    }

    // toast 显示后短暂保留，然后清空
    LaunchedEffect(toastMsg) {
        if (!toastMsg.isNullOrEmpty()) {
            delay(2500)
            viewModel.clearToast()
        }
    }

    val brush = remember {
        Brush.linearGradient(
            colors = listOf(BackgroundGradientStart, BackgroundGradientEnd),
            start = Offset(0f, 0f),
            end = Offset(0f, Float.POSITIVE_INFINITY)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "协议嗅探",
            style = MaterialTheme.typography.headlineLarge,
            color = TextHigh
        )
        Text(
            text = "实时显示 BLE 收发字节流，用于控制器协议逆向分析",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim
        )

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatChip("总包数", "${packets.size}", ElectricCyan)
            StatChip("RX", "${packets.count { it.direction == PacketDirection.RX }}", EvGreen)
            StatChip("TX", "${packets.count { it.direction == PacketDirection.TX }}", ElectricCyan)
        }

        // Packet list
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            if (packets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "等待数据...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextDim
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(packets, key = { it.timestampMs }) { packet ->
                        PacketRow(packet)
                    }
                }
            }
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.clear() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("清空", color = DangerRed)
            }
            Button(
                onClick = { viewModel.saveToFile() },
                enabled = packets.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EvGreen, contentColor = Color.Black),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存到文件")
            }
        }

        val msg = toastMsg
        if (!msg.isNullOrEmpty()) {
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = TextMid,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PacketRow(packet: RawPacket) {
    val isTx = packet.direction == PacketDirection.TX
    val dirColor = if (isTx) ElectricCyan else EvGreen
    val dirLabel = if (isTx) "TX" else "RX"
    val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        .format(java.util.Date(packet.timestampMs))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceVariantLight.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = timeStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextDim,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dirColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dirLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = dirColor
            )
        }
        Text(
            text = packet.hexString(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TextHigh,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${packet.data.size} B",
            fontSize = 11.sp,
            color = TextDim
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextDim)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
