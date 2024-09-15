package com.freefjay.localshare.util

import android.content.res.AssetFileDescriptor
import android.util.Log
import com.freefjay.localshare.TAG
import com.freefjay.localshare.globalActivity
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.http.defaultForFileExtension
import io.ktor.server.http.content.LastModifiedVersion
import io.ktor.util.cio.toByteReadChannel
import io.ktor.util.read
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.use
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.channels.FileChannel
import kotlin.coroutines.CoroutineContext

class LocalFileInfoContent(
    private val fileInfo: FileInfo,
    override val contentType: ContentType = ContentType.defaultForFileExtension(fileInfo.name?.substringAfterLast('.', "") ?: "")
) : OutgoingContent.ReadChannelContent() {

    override val contentLength: Long get() = fileInfo.size ?: 0

    init {
        Log.i(TAG, "${fileInfo.name}, ${fileInfo.size}, ${fileInfo.lastModified}")
        val lastModifiedVersion = fileInfo.lastModified
        versions += LastModifiedVersion(lastModifiedVersion ?: 0)
    }

    private fun getFileDescriptor(): AssetFileDescriptor {
        return globalActivity.contentResolver.openAssetFileDescriptor(fileInfo.uri, "r") ?: throw FileNotFoundException("文件数据不存在")
    }

    private fun readChannel(
        start: Long = 0,
        endInclusive: Long = -1,
        coroutineContext: CoroutineContext = Dispatchers.IO
    ): ByteReadChannel {
        Log.i(TAG, "readChannel测试")
        val fileLength = fileInfo.size ?: 0
        return CoroutineScope(coroutineContext).writer(CoroutineName("file-reader") + coroutineContext, autoFlush = false) {
            require(start >= 0L) { "start position shouldn't be negative but it is $start" }
            require(endInclusive <= fileLength - 1) {
                "endInclusive points to the position out of the file: file size = $fileLength, endInclusive = $endInclusive"
            }

            getFileDescriptor().use {
                val fileInputStream = FileInputStream(it.fileDescriptor)
                @Suppress("BlockingMethodInNonBlockingContext")
                fileInputStream.use {
                    val fileChannel: FileChannel = fileInputStream.channel
                    if (start > 0) {
                        fileChannel.position(start)
                    }

                    if (endInclusive == -1L) {
                        @Suppress("DEPRECATION")
                        channel.writeSuspendSession {
                            while (true) {
                                val buffer = request(1)
                                if (buffer == null) {
                                    channel.flush()
                                    tryAwait(1)
                                    continue
                                }

                                val rc = fileChannel.read(buffer)
                                if (rc == -1) break
                                written(rc)
                            }
                        }

                        return@use
                    }

                    var position = start
                    channel.writeWhile { buffer ->
                        val fileRemaining = endInclusive - position + 1
                        val rc = if (fileRemaining < buffer.remaining()) {
                            val l = buffer.limit()
                            buffer.limit(buffer.position() + fileRemaining.toInt())
                            val r = fileChannel.read(buffer)
                            buffer.limit(l)
                            r
                        } else {
                            fileChannel.read(buffer)
                        }

                        if (rc > 0) position += rc

                        rc != -1 && position <= endInclusive
                    }
                }
            }
        }.channel
    }

    // TODO: consider using WriteChannelContent to avoid piping
    // Or even make it dual-content so engine implementation can choose
    override fun readFrom(): ByteReadChannel = this.readChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = this.readChannel(range.start, range.endInclusive)
}