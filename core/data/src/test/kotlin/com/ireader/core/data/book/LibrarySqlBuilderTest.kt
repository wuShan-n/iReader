package com.ireader.core.data.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySqlBuilderTest {

    @Test
    fun `keyword query should use fts match`() {
        val query = LibraryQuery(keyword = "three body")

        val sql = LibrarySqlBuilder.build(query).sql

        assertTrue(sql.contains("books_fts MATCH ?"))
        assertTrue(sql.contains("SELECT rowid FROM books_fts"))
        assertFalse(sql.contains("books.title"))
    }

    @Test
    fun `empty keyword should not add fts clause`() {
        val query = LibraryQuery(keyword = "   ")

        val sql = LibrarySqlBuilder.build(query).sql

        assertFalse(sql.contains("books_fts MATCH ?"))
    }

    @Test
    fun `filters should append favorite status index format and collection clauses`() {
        val query = LibraryQuery(
            keyword = null,
            statuses = setOf(ReadingStatus.READING, ReadingStatus.FINISHED),
            indexStates = setOf(IndexState.ERROR),
            formats = setOf(com.ireader.reader.model.BookFormat.EPUB),
            onlyFavorites = true,
            collectionId = 7L
        )

        val sql = LibrarySqlBuilder.build(query).sql

        assertTrue(sql.contains("books.favorite = 1"))
        assertTrue(sql.contains("books.readingStatus IN (?,?)"))
        assertTrue(sql.contains("books.indexState IN (?)"))
        assertTrue(sql.contains("books.format IN (?)"))
        assertTrue(sql.contains("book_collection bc"))
        assertTrue(sql.contains("bc.collectionId = ?"))
    }

    @Test
    fun `title sort should use case-insensitive title order`() {
        val query = LibraryQuery(sort = LibrarySort.TITLE_AZ)

        val sql = LibrarySqlBuilder.build(query).sql

        assertTrue(sql.contains("ORDER BY COALESCE(books.title, books.fileName, '') COLLATE NOCASE ASC"))
    }

    @Test
    fun `progress sort should use progression desc then updated desc`() {
        val query = LibraryQuery(sort = LibrarySort.PROGRESSION_DESC)

        val sql = LibrarySqlBuilder.build(query).sql

        assertTrue(sql.contains("ORDER BY COALESCE(progress.progression, 0.0) DESC, books.updatedAtEpochMs DESC"))
    }

    @Test
    fun `keyword with quote should still use fts clause`() {
        val query = LibraryQuery(keyword = """the "three" body""")

        val sql = LibrarySqlBuilder.build(query).sql

        assertTrue(sql.contains("books_fts MATCH ?"))
        assertFalse(sql.contains("COALESCE(books.title, '') LIKE ?"))
    }
}
