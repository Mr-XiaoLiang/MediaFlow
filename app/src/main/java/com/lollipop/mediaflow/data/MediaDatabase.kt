package com.lollipop.mediaflow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.DocumentsContract
import androidx.core.database.sqlite.transaction
import androidx.core.net.toUri
import com.lollipop.mediaflow.tools.CursorColumn
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.optInt
import com.lollipop.mediaflow.tools.optLong
import com.lollipop.mediaflow.tools.optString
import com.lollipop.mediaflow.tools.put

class MediaDatabase(context: Context) : SQLiteOpenHelper(context, "Media.db", null, 3) {

    private val log = registerLog()

    private val mediaMetadataCacheMap = mutableMapOf<String, MediaMetadata>()

    override fun onCreate(db: SQLiteDatabase?) {
        createTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion == newVersion) {
            return
        }
        createTable(db)
    }

    private fun createTable(db: SQLiteDatabase?) {
        db?.execSQL(MetadataTable.CREATE_TABLE)
        db?.execSQL(RootUriTable.CREATE_TABLE_PRIVATE)
        db?.execSQL(RootUriTable.CREATE_TABLE_PUBLIC)
        db?.execSQL(LocalCacheTable.CREATE_TABLE_PRIVATE)
        db?.execSQL(LocalCacheTable.CREATE_TABLE_PUBLIC)
    }

    fun fillingMetadataCache() {
        try {
            readableDatabase.query(
                MetadataTable.TABLE_NAME,
                MetadataTable.ALL_COLUMNS,
                null,
                null,
                null,
                null,
                null
            ).use {
                while (it.moveToNext()) {
                    val metadata = MediaMetadata(
                        docId = it.optString(MetadataColumn.DocId),
                        width = it.optInt(MetadataColumn.Width),
                        height = it.optInt(MetadataColumn.Height),
                        duration = it.optLong(MetadataColumn.Duration),
                        rotation = it.optInt(MetadataColumn.Rotation),
                        lastModified = it.optLong(MetadataColumn.LastModified),
                    )
                    mediaMetadataCacheMap[metadata.docId] = metadata
                }
            }
        } catch (e: Exception) {
            log.e("fillingMetadataCache", e)
        }
    }

    fun findMediaMetadata(docId: String): MediaMetadata? {
        val cache = mediaMetadataCacheMap[docId]
        if (cache != null) {
            return cache
        }
        try {
            readableDatabase.query(
                MetadataTable.TABLE_NAME,
                MetadataTable.ALL_COLUMNS,
                "${MetadataTable.COLUMN_DOC_ID} = ?",
                arrayOf(docId),
                null,
                null,
                null
            ).use {
                if (it.moveToFirst()) {
                    val metadata = MediaMetadata(
                        docId = it.optString(MetadataColumn.DocId),
                        width = it.optInt(MetadataColumn.Width),
                        height = it.optInt(MetadataColumn.Height),
                        duration = it.optLong(MetadataColumn.Duration),
                        rotation = it.optInt(MetadataColumn.Rotation),
                        lastModified = it.optLong(MetadataColumn.LastModified),
                    )
                    mediaMetadataCacheMap[docId] = metadata
                    return metadata
                }
            }
        } catch (e: Exception) {
            log.e("findMediaMetadata", e)
        }
        return null
    }

    fun updateMediaMetadata(metadataList: List<MediaMetadata>) {
        val database = writableDatabase
        database.transaction {
            for (item in metadataList) {
                mediaMetadataCacheMap[item.docId] = item
                try {
                    updateMediaMetadata(this, item)
                } catch (e: Exception) {
                    log.e("updateMediaMetadata", e)
                }
            }
        }
    }

    fun updateMediaMetadata(metadata: MediaMetadata) {
        val database = writableDatabase
        mediaMetadataCacheMap[metadata.docId] = metadata
        try {
            updateMediaMetadata(database, metadata)
        } catch (e: Exception) {
            log.e("updateMediaMetadata", e)
        }
    }

    fun loadRootUri(visibility: MediaVisibility): List<RootUri> {
        val result = mutableListOf<RootUri>()
        try {
            val tableName = when (visibility) {
                MediaVisibility.Public -> RootUriTable.TABLE_NAME_PUBLIC
                MediaVisibility.Private -> RootUriTable.TABLE_NAME_PRIVATE
            }
            readableDatabase.query(
                tableName,
                arrayOf(
                    RootUriTable.COLUMN_ROOT_URI,
                    RootUriTable.COLUMN_NAME
                ),
                null,
                null,
                null,
                null,
                null
            ).use {
                while (it.moveToNext()) {
                    result.add(
                        RootUri(
                            uri = it.optString(RootUriTable.COLUMN_ROOT_URI).toUri(),
                            visibility = visibility,
                            name = it.optString(RootUriTable.COLUMN_NAME)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.e("loadRootUri", e)
        }
        return result
    }

    fun saveRootUri(info: RootUri) {
        try {
            val values = ContentValues().apply {
                put(RootUriTable.COLUMN_ROOT_URI, info.uri.toString())
                put(RootUriTable.COLUMN_NAME, info.name)
            }

            // CONFLICT_REPLACE 对应 INSERT OR REPLACE
            // 返回值是新插入行的 Row ID，如果失败返回 -1
            val tableName = when (info.visibility) {
                MediaVisibility.Public -> RootUriTable.TABLE_NAME_PUBLIC
                MediaVisibility.Private -> RootUriTable.TABLE_NAME_PRIVATE
            }
            val rowId = writableDatabase.insertWithOnConflict(
                tableName,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            log.i("insertRootUri, tableName = $tableName, rowId = $rowId")
        } catch (e: Exception) {
            log.e("insertRootUri", e)
        }
    }

    fun updateCache(
        visibility: MediaVisibility,
        updateContent: (newLine: (CacheInfo) -> Unit) -> Unit
    ) {
        val tableName = when (visibility) {
            MediaVisibility.Public -> LocalCacheTable.TABLE_NAME_PUBLIC
            MediaVisibility.Private -> LocalCacheTable.TABLE_NAME_PRIVATE
        }
        val database = writableDatabase
        var lineCount = 0
        database.transaction {
            updateContent { info ->
                lineCount++
                try {
                    val values = ContentValues().apply {
                        put(CacheColumn.DocId, info.docId)
                        put(CacheColumn.ParentId, info.parentId)
                        put(CacheColumn.DisplayName, info.displayName)
                        put(CacheColumn.MimeType, info.mimeType)
                        put(CacheColumn.Size, info.size)
                        put(CacheColumn.ModeId, info.modeId)
                        put(CacheColumn.LastModified, info.lastModified)
                        put(CacheColumn.Uri, info.uri)
                        put(CacheColumn.RootUri, info.rootUri)
                        put(CacheColumn.FilePath, info.filePath)
                        put(CacheColumn.MediaType, info.mediaType)
                    }

                    // CONFLICT_REPLACE 对应 INSERT OR REPLACE
                    // 返回值是新插入行的 Row ID，如果失败返回 -1
                    writableDatabase.insertWithOnConflict(
                        tableName,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                } catch (e: Exception) {
                    log.e("updateCache", e)
                }
            }
        }
        log.i("updateCache, lineCount = $lineCount")
    }

    fun fillingCache(visibility: MediaVisibility, lineCallback: (CacheInfo) -> Unit) {
        try {
            val tableName = when (visibility) {
                MediaVisibility.Public -> LocalCacheTable.TABLE_NAME_PUBLIC
                MediaVisibility.Private -> LocalCacheTable.TABLE_NAME_PRIVATE
            }
            readableDatabase.query(
                tableName,
                LocalCacheTable.ALL_COLUMNS,
                null,
                null,
                null,
                null,
                null
            ).use {
                val outInfo = CacheInfo()
                while (it.moveToNext()) {
                    outInfo.docId = it.optString(CacheColumn.DocId)
                    outInfo.parentId = it.optString(CacheColumn.ParentId)
                    outInfo.mimeType = it.optString(CacheColumn.MimeType)
                    outInfo.displayName = it.optString(CacheColumn.DisplayName)
                    outInfo.size = it.optLong(CacheColumn.Size)
                    outInfo.lastModified = it.optLong(CacheColumn.LastModified)
                    outInfo.modeId = it.optLong(CacheColumn.ModeId)
                    outInfo.uri = it.optString(CacheColumn.Uri)
                    outInfo.rootUri = it.optString(CacheColumn.RootUri)
                    outInfo.filePath = it.optString(CacheColumn.FilePath)
                    outInfo.mediaType = it.optString(CacheColumn.MediaType)
                    lineCallback(outInfo)
                }
            }
        } catch (e: Exception) {
            log.e("fillingCache", e)
        }
    }

    fun deleteCache(visibility: MediaVisibility, minModeId: Long) {
        try {
            val tableName = when (visibility) {
                MediaVisibility.Public -> LocalCacheTable.TABLE_NAME_PUBLIC
                MediaVisibility.Private -> LocalCacheTable.TABLE_NAME_PRIVATE
            }
            val database = writableDatabase
            // 删除旧数据
            val count = database.delete(
                tableName,
                "${LocalCacheTable.COLUMN_MODE_ID} < ?",
                arrayOf(minModeId.toString())
            )
            log.i("deleteCache: minModeId = $minModeId, count = $count")
        } catch (e: Throwable) {
            log.e("deleteCache", e)
        }
    }

    fun deleteRootUri(uriString: String, visibility: MediaVisibility) {
        try {
            val tableName = when (visibility) {
                MediaVisibility.Public -> RootUriTable.TABLE_NAME_PUBLIC
                MediaVisibility.Private -> RootUriTable.TABLE_NAME_PRIVATE
            }
            writableDatabase.delete(
                tableName,
                "${RootUriTable.COLUMN_ROOT_URI} = ?",
                arrayOf(uriString)
            )
        } catch (e: Exception) {
            log.e("deleteRootUri", e)
        }
    }

    private fun updateMediaMetadata(database: SQLiteDatabase, metadata: MediaMetadata) {
        // 删除旧数据
        database.delete(
            MetadataTable.TABLE_NAME,
            "${MetadataTable.COLUMN_DOC_ID} = ?",
            arrayOf(metadata.docId)
        )
        // 插入新数据
        database.insert(
            MetadataTable.TABLE_NAME,
            null,
            ContentValues().apply {
                put(MetadataColumn.DocId.key, metadata.docId)
                put(MetadataColumn.Width.key, metadata.width)
                put(MetadataColumn.Height.key, metadata.height)
                put(MetadataColumn.Duration.key, metadata.duration)
                put(MetadataColumn.Rotation.key, metadata.rotation)
                put(MetadataColumn.LastModified.key, metadata.lastModified)
            }
        )
    }

    object MetadataTable {
        const val TABLE_NAME = "MediaMetadata"
        const val COLUMN_DOC_ID = "docId"
        const val COLUMN_WIDTH = "width"
        const val COLUMN_HEIGHT = "height"
        const val COLUMN_DURATION = "duration"
        const val COLUMN_ROTATION = "rotation"
        const val COLUMN_LAST_MODIFIED = "lastModified"

        val ALL_COLUMNS = arrayOf(
            COLUMN_DOC_ID,
            COLUMN_WIDTH,
            COLUMN_HEIGHT,
            COLUMN_DURATION,
            COLUMN_ROTATION,
            COLUMN_LAST_MODIFIED
        )

        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COLUMN_DOC_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_WIDTH INTEGER NOT NULL,
                $COLUMN_HEIGHT INTEGER NOT NULL,
                $COLUMN_DURATION INTEGER NOT NULL,
                $COLUMN_ROTATION INTEGER NOT NULL,
                $COLUMN_LAST_MODIFIED INTEGER NOT NULL
            )
        """

    }

    object RootUriTable {
        const val TABLE_NAME_PRIVATE = "RootUriPrivate"
        const val TABLE_NAME_PUBLIC = "RootUriPublic"
        const val COLUMN_ROOT_URI = "rootUri"
        const val COLUMN_NAME = "name"

        const val CREATE_TABLE_PUBLIC = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME_PUBLIC (
                $COLUMN_ROOT_URI TEXT PRIMARY KEY NOT NULL,
                $COLUMN_NAME TEXT NOT NULL
            )
        """

        const val CREATE_TABLE_PRIVATE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME_PRIVATE (
                $COLUMN_ROOT_URI TEXT PRIMARY KEY NOT NULL,
                $COLUMN_NAME TEXT NOT NULL
            )
        """

    }

    object LocalCacheTable {
        const val TABLE_NAME_PUBLIC = "MediaCachePublic"
        const val TABLE_NAME_PRIVATE = "MediaCachePrivate"

        const val COLUMN_DOCUMENT_ID = DocumentsContract.Document.COLUMN_DOCUMENT_ID
        const val COLUMN_DISPLAY_NAME = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        const val COLUMN_MIME_TYPE = DocumentsContract.Document.COLUMN_MIME_TYPE
        const val COLUMN_SIZE = DocumentsContract.Document.COLUMN_SIZE
        const val COLUMN_LAST_MODIFIED = DocumentsContract.Document.COLUMN_LAST_MODIFIED
        const val COLUMN_PARENT_ID = "parent_document_id"
        const val COLUMN_MODE_ID = "mode_id"
        const val COLUMN_URI = "file_uri"
        const val COLUMN_ROOT_URI = "root_uri"
        const val COLUMN_FILE_PATH = "file_path"
        const val COLUMN_MEDIA_TYPE = "media_type"

        val ALL_COLUMNS = arrayOf(
            COLUMN_DOCUMENT_ID,
            COLUMN_DISPLAY_NAME,
            COLUMN_MIME_TYPE,
            COLUMN_SIZE,
            COLUMN_LAST_MODIFIED,
            COLUMN_PARENT_ID,
            COLUMN_MODE_ID,
            COLUMN_URI,
            COLUMN_ROOT_URI,
            COLUMN_FILE_PATH,
            COLUMN_MEDIA_TYPE
        )

        const val CREATE_TABLE_PUBLIC = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME_PUBLIC (
                $COLUMN_DOCUMENT_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_DISPLAY_NAME TEXT NOT NULL,
                $COLUMN_MIME_TYPE TEXT NOT NULL,
                $COLUMN_SIZE INTEGER NOT NULL,
                $COLUMN_LAST_MODIFIED INTEGER NOT NULL,
                $COLUMN_PARENT_ID TEXT NOT NULL,
                $COLUMN_MODE_ID INTEGER NOT NULL,
                $COLUMN_URI TEXT NOT NULL,
                $COLUMN_ROOT_URI TEXT NOT NULL,
                $COLUMN_FILE_PATH TEXT NOT NULL,
                $COLUMN_MEDIA_TYPE TEXT NOT NULL
            )
        """

        const val CREATE_TABLE_PRIVATE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME_PRIVATE (
                $COLUMN_DOCUMENT_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_DISPLAY_NAME TEXT NOT NULL,
                $COLUMN_MIME_TYPE TEXT NOT NULL,
                $COLUMN_SIZE INTEGER NOT NULL,
                $COLUMN_LAST_MODIFIED INTEGER NOT NULL,
                $COLUMN_PARENT_ID TEXT NOT NULL,
                $COLUMN_MODE_ID INTEGER NOT NULL,
                $COLUMN_URI TEXT NOT NULL,
                $COLUMN_ROOT_URI TEXT NOT NULL,
                $COLUMN_FILE_PATH TEXT NOT NULL,
                $COLUMN_MEDIA_TYPE TEXT NOT NULL
            )
        """

    }

    enum class MetadataColumn(
        override val key: String
    ) : CursorColumn {
        DocId(MetadataTable.COLUMN_DOC_ID),
        Width(MetadataTable.COLUMN_WIDTH),
        Height(MetadataTable.COLUMN_HEIGHT),
        Duration(MetadataTable.COLUMN_DURATION),
        Rotation(MetadataTable.COLUMN_ROTATION),
        LastModified(MetadataTable.COLUMN_LAST_MODIFIED),
    }

    enum class CacheColumn(
        override val key: String
    ) : CursorColumn {
        DocId(LocalCacheTable.COLUMN_DOCUMENT_ID),
        DisplayName(LocalCacheTable.COLUMN_DISPLAY_NAME),
        MimeType(LocalCacheTable.COLUMN_MIME_TYPE),
        Size(LocalCacheTable.COLUMN_SIZE),
        ParentId(LocalCacheTable.COLUMN_PARENT_ID),
        LastModified(LocalCacheTable.COLUMN_LAST_MODIFIED),
        ModeId(LocalCacheTable.COLUMN_MODE_ID),
        Uri(LocalCacheTable.COLUMN_URI),
        RootUri(LocalCacheTable.COLUMN_ROOT_URI),
        FilePath(LocalCacheTable.COLUMN_FILE_PATH),
        MediaType(LocalCacheTable.COLUMN_MEDIA_TYPE)
    }

    class CacheInfo {
        var docId = ""
        var displayName = ""
        var mimeType = ""
        var size = 0L
        var parentId = ""
        var uri = ""
        var rootUri = ""
        var lastModified = 0L
        var modeId = 0L
        var filePath = ""
        var mediaType = ""
    }

}