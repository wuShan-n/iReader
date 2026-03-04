package com.ireader.core.database

import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.reader.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class DbConvertersTest {

    private val converters = DbConverters()

    @Test
    fun `book format should round trip`() {
        BookFormat.entries.forEach { format ->
            val encoded = converters.bookFormatToString(format)
            assertEquals(format, converters.stringToBookFormat(encoded))
        }
    }

    @Test
    fun `source type should round trip`() {
        BookSourceType.entries.forEach { sourceType ->
            val encoded = converters.sourceTypeToString(sourceType)
            assertEquals(sourceType, converters.stringToSourceType(encoded))
        }
    }

    @Test
    fun `reading and index status should round trip`() {
        ReadingStatus.entries.forEach { status ->
            val encoded = converters.readingStatusToString(status)
            assertEquals(status, converters.stringToReadingStatus(encoded))
        }
        IndexState.entries.forEach { state ->
            val encoded = converters.indexStateToString(state)
            assertEquals(state, converters.stringToIndexState(encoded))
        }
    }

    @Test
    fun `import statuses should round trip`() {
        ImportStatus.entries.forEach { status ->
            val encoded = converters.importStatusToString(status)
            assertEquals(status, converters.stringToImportStatus(encoded))
        }
        ImportItemStatus.entries.forEach { status ->
            val encoded = converters.importItemStatusToString(status)
            assertEquals(status, converters.stringToImportItemStatus(encoded))
        }
    }
}
