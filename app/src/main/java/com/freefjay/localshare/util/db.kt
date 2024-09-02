package com.freefjay.localshare.util

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.annotation.Keep
import androidx.core.database.getBlobOrNull
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.freefjay.localshare.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.Date
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

@Retention
annotation class Column (
    val value: String = "",
    val type: String = "",
    val isPrimaryKey: Boolean = false
)

data class TableInfo (
    val name: String?,
    val columns: List<ColumnInfo>?
)

data class ColumnInfo (
    val name: String?,
    val type: String?,
    val isPrimaryKey: Boolean?,
    val javaType: Type? = null,
    val kProperty: KProperty<*>? = null
)

@Keep
data class SqliteMaster(val sql: String?)

fun getSqliteType(clazz: Type?): String? {
    return when (clazz) {
        String::class.java -> "TEXT"
        java.lang.Integer::class.java -> "INTEGER"
        java.lang.Long::class.java -> "INTEGER"
        java.lang.Float::class.java -> "REAL"
        java.lang.Double::class.java -> "REAL"
        BigDecimal::class.java -> "NUMERIC"
        ByteArray::class.java -> "BLOB"
        Date::class.java -> "TEXT"
        java.lang.Boolean::class.java -> "INTEGER"
        else -> null
    }
}

lateinit var db: DbOpenHelper

suspend fun queryMap(sql: String?, args: Array<String>? = null): List<Map<String, Any?>> {
    return suspendCoroutine { continuation ->
        val cursor = db.readableDatabase.rawQuery(sql, args)
        cursor.use {
            val list = mutableListOf<MutableMap<String, Any?>>()
            while (cursor.moveToNext()) {
                val map = mutableMapOf<String, Any?>()
                for (index in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(index)
                    if (map.containsKey(name)) {
                        continue
                    }
                    val type = cursor.getType(index)
                    var value: Any? = null
                    when(type) {
                        Cursor.FIELD_TYPE_BLOB -> {
                            value = cursor.getBlob(index)
                        }

                        Cursor.FIELD_TYPE_FLOAT -> {
                            value = cursor.getFloat(index)
                        }

                        Cursor.FIELD_TYPE_INTEGER -> {
                            value = cursor.getInt(index)
                        }

                        Cursor.FIELD_TYPE_NULL -> {
                            value = null
                        }

                        Cursor.FIELD_TYPE_STRING -> {
                            value = cursor.getString(index)
                        }
                    }
                    map[name] = value
                }
                list.add(map)
            }
            continuation.resume(list)
        }
    }
}

suspend inline fun <reified T> queryList(sql: String?, args: Array<String>? = null): List<T> {
    return suspendCoroutine { continuation ->
        val cursor = db.readableDatabase.rawQuery(sql, args)
        cursor.use {
            val list = mutableListOf<T>()
            val clazz = T::class
            val constructor = clazz.constructors.first()
            while (cursor.moveToNext()) {
                list.add(constructor.call(*(constructor.parameters.map { parameter ->
                    val colName = parameter.name?.toUnderCase()
                    if (colName != null) {
                        val index = cursor.getColumnIndex(colName)
                        when (parameter.type.javaType) {
                            String::class.java -> cursor.getStringOrNull(index)
                            java.lang.Integer::class.java -> cursor.getIntOrNull(index)
                            java.lang.Long::class.java -> cursor.getLongOrNull(index)
                            java.lang.Float::class.java -> cursor.getFloatOrNull(index)
                            java.lang.Double::class.java -> cursor.getDoubleOrNull(index)
                            BigDecimal::class.java -> if (cursor.getStringOrNull(index) != null) BigDecimal(cursor.getStringOrNull(index)) else null
                            ByteArray::class.java -> cursor.getBlobOrNull(index)
                            Date::class.java -> cursor.getStringOrNull(index)?.toDate()
                            Boolean::class.java -> (cursor.getIntOrNull(index)).let { if (it == 0) false else null }
                            else -> null
                        }
                    } else {
                        null
                    }
                }.toTypedArray())))
            }
            continuation.resume(list)
        }
    }
}

suspend inline fun <reified T> queryOne(sql: String?, args: Array<String>? = null): T? {
    Log.i(TAG, "queryOne: $sql")
    val list = queryList<T>(sql = sql, args = args)
    return list.firstOrNull()
}

suspend inline fun <reified T : Any> queryById(id: Any?): T? {
    val tableInfo = resolveTableInfo(T::class)
    val primaryColumnInfo = tableInfo.columns?.find { it.isPrimaryKey == true } ?: return null
    return queryOne("select * from ${tableInfo.name} where ${primaryColumnInfo.name} = ${sqlValue(primaryColumnInfo, id)}")
}

inline fun <reified T : Any> getValue(columnInfo: ColumnInfo, data: T?): Any {
    val value = columnInfo.kProperty?.getter?.call(data)
    return sqlValue(columnInfo, value)
}

fun sqlValue(columnInfo: ColumnInfo, value: Any?): Any {
    if (value == null) {
        return "null"
    }
    if (columnInfo.type == "TEXT") {
        return if (columnInfo.javaType == Date::class.java) {
            "\"${(value as Date).format()}\""
        } else {
            "\"${value}\""
        }
    }
    if (columnInfo.javaType == Boolean::class.java) {
        return (value as Boolean).let { if (it) 1 else 0 }
    }
    return value
}

suspend inline fun <reified T : Any> save(data: T) {
    return suspendCoroutine { continuation ->
        val clazz = T::class
        val tableInfo = resolveTableInfo(clazz)
        val tableName = tableInfo.name
        val columns = tableInfo.columns
        val primaryKeyColumn = columns?.find { it.isPrimaryKey == true }
        val primaryValue = if (primaryKeyColumn != null) primaryKeyColumn.kProperty?.getter?.call(data) else null
        if (primaryKeyColumn == null || primaryValue == null) {
            val sql = """
                    insert into ${tableName} (${columns?.joinToString(", ") { item -> item.name?: "" }}) values (
                    ${columns?.map{ item -> getValue(item, data) }?.joinToString(", ")})
                """.trimIndent()
            Log.i(TAG, "save: $sql")
            val sqlStatement = db.writableDatabase.compileStatement(sql)
            val id = sqlStatement.executeInsert()
            Log.i(TAG, "primaryKeyColumn: ${primaryKeyColumn}")
            if ((primaryKeyColumn?.kProperty is KMutableProperty) && primaryKeyColumn.javaType == java.lang.Long::class.java && id != -1L) {
                primaryKeyColumn.kProperty.setter.call(data, id)
            }
        } else {
            val sql = """
                    update ${tableName} set ${
                columns.joinToString(", ") { "${it.name} = ${getValue(it, data)}" }
            } where ${primaryKeyColumn.name} = ${getValue(primaryKeyColumn, data)}
            """.trimIndent()
            Log.i(TAG, "update: ${sql}")
            val sqlStatement = db.writableDatabase.compileStatement(sql)
            sqlStatement.executeUpdateDelete()
        }
        continuation.resume(Unit)
    }
}

suspend inline fun <reified T : Any> delete(id: Any?): Int? {
    return suspendCoroutine { continuation ->
        continuation.resume(
            if (id != null) {
                val tableInfo = resolveTableInfo(T::class)
                val tableName = tableInfo.name
                val columns = tableInfo.columns
                val primaryKeyColumn = columns?.find { it.isPrimaryKey == true }
                val sql = "delete from $tableName where ${primaryKeyColumn?.name} = ${
                    if (primaryKeyColumn?.type == "TEXT") {
                        "\"${id}\""
                    } else {
                        id.toString()
                    }
                }"
                Log.i(TAG, "delete: $sql")
                val sqlStatement = db.writableDatabase.compileStatement(sql)
                sqlStatement.executeUpdateDelete()
            } else 0
        )
    }
}

suspend fun executeSql(sql: String?, bindArgs: Array<Any>? = null) {
    suspendCoroutine { continuation ->
        db.writableDatabase.execSQL(sql, bindArgs ?: arrayOf())
        continuation.resume(Unit)
    }
}

fun <T : Any> resolveTableInfo(kClazz: KClass<T>): TableInfo {
    val propertyMap = mutableMapOf<String, KProperty<*>>()
    kClazz.declaredMemberProperties.forEach {
        propertyMap[it.name] = it
    }
    return TableInfo(
        name = kClazz.simpleName?.toFirstLower()?.toUnderCase(),
        columns = kClazz.primaryConstructor?.parameters?.map {
            val column = it.findAnnotation<Column>()
            ColumnInfo(
                name = if (column?.value?.isNotBlank() == true) column.value else it.name?.toUnderCase(),
                type = if (column?.type?.isNotBlank() == true) column.type else getSqliteType(it.type.javaType) ?: throw RuntimeException("不支持的类型：${kClazz.simpleName} ${it.type.javaType}"),
                isPrimaryKey = column?.isPrimaryKey == true,
                kProperty = propertyMap[it.name],
                javaType = it.type.javaType
            )
        }
    )
}

suspend inline fun <T : Any> updateTableStruct(kClazz: KClass<T>) {
    val tableInfo = resolveTableInfo(kClazz)
    Log.i(TAG, "tableInfo: ${tableInfo}")
    val oldTable = queryList<SqliteMaster>("select * from sqlite_master where type = 'table' and name = '${tableInfo.name}'").firstOrNull()
    Log.i(TAG, "updateTableStruct: ${oldTable}")
    if (oldTable == null) {
        val sql = """
                    create table ${tableInfo.name}(${tableInfo.columns?.joinToString(", ") { "${it.name} ${it.type}${if (it.isPrimaryKey == true) " primary key" else ""}" }})
                """.trimIndent()
        Log.i(TAG, "建表sql: $sql")
        executeSql(sql)
        Log.i(TAG, "建表${tableInfo.name}成功")
    } else {
        val pattern = Pattern.compile("${tableInfo.name}\\s*(.*)")
        val match = oldTable.sql?.let { pattern.matcher(it) }
        if (match?.find() == true) {
            var columnBody = match.group(1)
            columnBody = columnBody?.substring(1, columnBody.length - 1)
            val oldColumns = columnBody?.split(",")?.map {
                val columnAry = it.trim().split("\\s+".toRegex())
                val name = columnAry[0]
                val type = columnAry[1]
                val isPrimaryKey = it.lowercase().contains("primary")
                return@map ColumnInfo(name=name, type = type, isPrimaryKey = isPrimaryKey)
            }
            Log.i(TAG, "oldColumns: ${oldColumns}")
            val oldColumnMap = mutableMapOf<String?, ColumnInfo>()
            oldColumns?.forEach {
                oldColumnMap[it.name] = it
            }
            val addColumns = mutableListOf<ColumnInfo>()
            tableInfo.columns?.forEach {
                val oldColumn = oldColumnMap[it.name]
                if (oldColumn == null) {
                    addColumns.add(it)
                } else {
                    if (
                        it.type?.trim()?.lowercase() != oldColumn.type?.trim()?.lowercase()
                        || it.isPrimaryKey != oldColumn.isPrimaryKey
                    ) {
                        Log.w(TAG, "列不一样，$it, $oldColumn", )
                    }
                }
            }
            addColumns.forEach {
                val sql = "alter table ${tableInfo.name} add column ${it.name} ${it.type} ${if (it.isPrimaryKey == true) "primary key" else ""}"
                Log.i(TAG, "新增列: $sql")
                executeSql(sql)
                Log.i(TAG, "新增列成功")
            }
        }
    }
}

class DbOpenHelper(context: Context, name: String): SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase?) {

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }

}