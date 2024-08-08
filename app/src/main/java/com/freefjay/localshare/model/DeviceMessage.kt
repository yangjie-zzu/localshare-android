package com.freefjay.localshare.model

import androidx.annotation.Keep
import com.freefjay.localshare.util.Column
import java.util.Date

@Keep
data class DeviceMessage(
    @Column(isPrimaryKey = true)
    var id: Long? = null,
    var dataType: String? = null,
    var content: String? = null,
    var filename: String? = null,
    var clientId: String? = null,
    var deviceId: Long? = null,
    var type: String? = null,
    var createdTime: Date? = null,
    var seen: Boolean? = null
)