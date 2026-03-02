package com.ireader.core.database

import androidx.room.TypeConverter
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.reader.model.BookFormat

class DbConverters {
    @TypeConverter
    fun bookFormatToString(value: BookFormat): String = value.name

    @TypeConverter
    fun stringToBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun sourceTypeToString(value: BookSourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): BookSourceType = BookSourceType.valueOf(value)

    @TypeConverter
    fun readingStatusToString(value: ReadingStatus): String = value.name

    @TypeConverter
    fun stringToReadingStatus(value: String): ReadingStatus = ReadingStatus.valueOf(value)

    @TypeConverter
    fun indexStateToString(value: IndexState): String = value.name

    @TypeConverter
    fun stringToIndexState(value: String): IndexState = IndexState.valueOf(value)

    @TypeConverter
    fun importStatusToString(value: ImportStatus): String = value.name

    @TypeConverter
    fun stringToImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)

    @TypeConverter
    fun importItemStatusToString(value: ImportItemStatus): String = value.name

    @TypeConverter
    fun stringToImportItemStatus(value: String): ImportItemStatus = ImportItemStatus.valueOf(value)
}
