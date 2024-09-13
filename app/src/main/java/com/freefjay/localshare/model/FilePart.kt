package com.freefjay.localshare.model

import androidx.annotation.Keep
import com.freefjay.localshare.util.Column

@Keep
data class FilePart(
    @Column(isPrimaryKey = true)
    var id: Long? = null,
    var deviceMessageId: Long? = null,
    var fileHash: String? = null,
    var start: Long? = null,
    var end: Long? = null
)