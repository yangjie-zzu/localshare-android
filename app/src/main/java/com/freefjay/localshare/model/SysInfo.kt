package com.freefjay.localshare.model

import androidx.annotation.Keep
import com.freefjay.localshare.util.Column

@Keep
data class SysInfo(
    @Column(isPrimaryKey = true)
    var id: Long? = null,
    var name: String? = null,
    var value: String? = null
)