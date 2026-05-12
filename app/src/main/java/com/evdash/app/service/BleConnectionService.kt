package com.evdash.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.evdash.app.MainActivity
import com.evdash.app.R
import com.evdash.app.data.ConnectionState
import com.evdash.app.protocol.BleManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务：保持 BLE 连接在后台存活。
 * 连接建立后启动，断开或用户主动停止时结束。
 */
@AndroidEntryPoint
class BleConnectionService : Service() {

    @Inject
    lateinit var bleManager: BleManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var lastNotifyTimeMs: Long = 0L
    private var lastConnectionState: ConnectionState? = null

    companion object {
        private const val CHANNEL_ID = "evdash_ble_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.evdash.app.STOP_BLE_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, BleConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleConnectionService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            bleManager.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("初始化...", "等待连接"))

        scope.launch {
            bleManager.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        val name = bleManager.connectedDeviceName.value ?: "控制器"
                        updateNotification(
                            title = "已连接: $name",
                            text = buildTelemetrySummary()
                        )
                    }
                    ConnectionState.CONNECTING -> {
                        updateNotification(title = "连接中...", text = "正在建立 BLE 连接")
                    }
                    ConnectionState.SCANNING -> {
                        updateNotification(title = "扫描中...", text = "搜索 BLE 设备")
                    }
                    ConnectionState.DISCONNECTED -> {
                        stopSelf()
                    }
                    else -> {
                        // 兜底:未预期状态保持当前通知不变
                    }
                }
            }
        }

        scope.launch {
            bleManager.telemetry.collectLatest { snap ->
                if (bleManager.connectionState.value == ConnectionState.CONNECTED) {
                    val now = System.currentTimeMillis()
                    val stateChanged = snap.connectionState != lastConnectionState
                    if (stateChanged || now - lastNotifyTimeMs >= 1000) {
                        val name = bleManager.connectedDeviceName.value ?: "控制器"
                        updateNotification(
                            title = "已连接: $name",
                            text = buildTelemetrySummary(snap)
                        )
                        lastNotifyTimeMs = now
                        lastConnectionState = snap.connectionState
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE 连接状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持电动车控制器 BLE 连接在后台运行"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BleConnectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "断开", stopIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildTelemetrySummary(telemetry: com.evdash.app.data.TelemetrySnapshot? = null): String {
        return if (telemetry != null) {
            "${"%.0f".format(telemetry.speedKmh)} km/h | 电量 ${"%.0f".format(telemetry.socPercent)}% | 续航 ${"%.0f".format(telemetry.estimatedRangeKm)} km"
        } else {
            "等待遥测数据..."
        }
    }
}
