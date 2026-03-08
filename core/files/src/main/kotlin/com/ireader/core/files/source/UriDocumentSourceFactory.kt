package com.ireader.core.files.source

import android.content.Context
import android.net.Uri
import com.ireader.reader.api.open.DocumentSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface UriDocumentSourceFactory {
    fun create(uri: Uri): DocumentSource
}

@Singleton
class DefaultUriDocumentSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : UriDocumentSourceFactory {
    override fun create(uri: Uri): DocumentSource = ContentUriDocumentSource(context, uri)
}
