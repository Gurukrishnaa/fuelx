package com.fuelx.model

import android.net.Uri

data class StreamInfo(
    val uri: Uri,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val expiresAtEpochMs: Long? = null
)
