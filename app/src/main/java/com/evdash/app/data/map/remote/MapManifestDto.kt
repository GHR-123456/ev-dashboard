package com.evdash.app.data.map.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 远端清单 JSON 的反序列化结构。字段约定见
 * `.claude/plans/curried-percolating-owl.md` 中的更新协议章节。
 */
@Serializable
data class MapManifestDto(
    @SerialName("latestVersion") val latestVersion: String,
    @SerialName("minAppVersion") val minAppVersion: Int = 1,
    @SerialName("packageUrl") val packageUrl: String,
    @SerialName("sizeBytes") val sizeBytes: Long,
    @SerialName("sha256") val sha256: String,
    @SerialName("releasedAt") val releasedAt: String? = null,
    @SerialName("notes") val notes: String? = null
)
