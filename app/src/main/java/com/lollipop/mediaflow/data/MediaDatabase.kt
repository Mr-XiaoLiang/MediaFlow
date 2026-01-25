package com.lollipop.mediaflow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import androidx.core.net.toUri
import com.lollipop.mediaflow.tools.CursorColumn
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.optInt
import com.lollipop.mediaflow.tools.optLong
import com.lollipop.mediaflow.tools.optString

class MediaDatabase(context: Context) : SQLiteOpenHelper(context, "Media.db", null, 1) {

    private val log = registerLog()

    private val cacheMap = mutableMapOf<String, MediaMetadata>()

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(MetadataTable.CREATE_TABLE)
        db?.execSQL(RootUriTable.CREATE_TABLE_PRIVATE)
        db?.execSQL(RootUriTable.CREATE_TABLE_PUBLIC)
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int
    ) {
    }

    fun fillingCache() {
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
                        it.optString(MetadataColumn.DocId),
                        it.optInt(MetadataColumn.Width),
                        it.optInt(MetadataColumn.Height),
                        it.optLong(MetadataColumn.Duration),
                        it.optLong(MetadataColumn.LastModified)
                    )
                    cacheMap[metadata.docId] = metadata
                }
            }
        } catch (e: Exception) {
            log.e("fillingCache", e)
        }
    }

    fun findMediaMetadata(docId: String): MediaMetadata? {
        val cache = cacheMap[docId]
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
                        it.optString(MetadataColumn.DocId),
                        it.optInt(MetadataColumn.Width),
                        it.optInt(MetadataColumn.Height),
                        it.optLong(MetadataColumn.Duration),
                        it.optLong(MetadataColumn.LastModified)
                    )
                    cacheMap[docId] = metadata
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
                cacheMap[item.docId] = item
                try {
                    updateMediaMetadata(this, item)
                } catch (e: Exception) {
                    log.e("updateMediaMetadata", e)
                }
            }
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
        const val COLUMN_LAST_MODIFIED = "lastModified"

        val ALL_COLUMNS = arrayOf(
            COLUMN_DOC_ID,
            COLUMN_WIDTH,
            COLUMN_HEIGHT,
            COLUMN_DURATION,
            COLUMN_LAST_MODIFIED
        )

        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COLUMN_DOC_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_WIDTH INTEGER NOT NULL,
                $COLUMN_HEIGHT INTEGER NOT NULL,
                $COLUMN_DURATION INTEGER NOT NULL,
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

    enum class MetadataColumn(
        override val key: String
    ) : CursorColumn {
        DocId(MetadataTable.COLUMN_DOC_ID),
        Width(MetadataTable.COLUMN_WIDTH),
        Height(MetadataTable.COLUMN_HEIGHT),
        Duration(MetadataTable.COLUMN_DURATION),
        LastModified(MetadataTable.COLUMN_LAST_MODIFIED),
    }

}