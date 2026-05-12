package com.evdash.app.ui.sniffer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evdash.app.data.RawPacket
import com.evdash.app.protocol.BleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SnifferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: BleManager
) : ViewModel() {

    val packets: StateFlow<List<RawPacket>> = bleManager.packets

    private val _toastMsg = MutableStateFlow<String?>(null)
    val toastMsg: StateFlow<String?> = _toastMsg.asStateFlow()

    fun clearToast() {
        _toastMsg.value = null
    }

    fun clear() {
        bleManager.clearPackets()
    }

    fun saveToFile() {
        viewModelScope.launch {
            val list = packets.value
            if (list.isEmpty()) {
                _toastMsg.value = "没有数据可保存"
                return@launch
            }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "evdash_sniffer_$ts.txt"
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val file = File(dir, fileName)

            file.bufferedWriter().use { writer ->
                list.forEach { pkt ->
                    val dirLabel = if (pkt.direction == com.evdash.app.data.PacketDirection.TX) "TX" else "RX"
                    writer.write("[${pkt.timestampMs}] $dirLabel: ${pkt.hexString()}\n")
                }
            }
            _toastMsg.value = "已保存到 ${file.absolutePath}"
        }
    }
}
