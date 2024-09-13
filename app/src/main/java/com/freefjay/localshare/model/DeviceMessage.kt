package com.freefjay.localshare.model

import androidx.annotation.Keep
import com.freefjay.localshare.util.Column
import java.util.Date

@Keep
data class DeviceMessage(
    @Column(isPrimaryKey = true)
    var id: Long? = null,
    var createdTime: Date? = null,
    var deviceId: Long? = null,
    var type: String? = null,
    var content: String? = null,
    var filepath: String? = null,
    var fileUri: String? = null,
    var filename: String? = null,
    var fileHash: String? = null,
    var size: Long? = null,
    var oppositeId: Long? = null,
    var sendSuccess: Boolean? = null,
    var downloadSuccess: Boolean? = null,
    var downloadSize: Long? = null,
    var savePath: String? = null,
    var saveUri: String? = null
)

@Keep
data class DeviceMessageParams(
    var sendId: Long?,
    var clientCode: String?,
    var content: String?,
    var filename: String?,
    var size: Long?,
)

@Keep
data class DownloadInfo(
    val size: Long,
    val hash: String
)