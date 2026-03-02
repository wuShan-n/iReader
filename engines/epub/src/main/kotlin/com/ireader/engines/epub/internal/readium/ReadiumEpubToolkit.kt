package com.ireader.engines.epub.internal.readium

import android.content.Context
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal class ReadiumEpubToolkit(
    context: Context,
    contentProtections: List<ContentProtection> = emptyList(),
    pdfFactory: PdfDocumentFactory<*>? = null
) {
    private val appContext = context.applicationContext

    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = pdfFactory
    )
    val publicationOpener = PublicationOpener(
        publicationParser = publicationParser,
        contentProtections = contentProtections
    )
}
