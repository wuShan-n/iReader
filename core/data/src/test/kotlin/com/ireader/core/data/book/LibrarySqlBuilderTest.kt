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
}

