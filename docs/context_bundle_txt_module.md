# Context Bundle

Generated at: 2026-03-03T00:05:16+08:00
Feature: txt模块
Resolved files requested: 90
Written files: 90
Missing files: 0
Skipped files: 0

## File Index

- [x] app/build.gradle.kts
- [x] app/src/main/AndroidManifest.xml
- [x] app/src/main/java/com/ireader/di/ReaderRuntimeModule.kt
- [x] core/data/build.gradle.kts
- [x] core/data/src/main/AndroidManifest.xml
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/BookIndexer.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/BookRepo.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/LibraryBookItem.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/LibraryQuery.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/LibrarySort.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/LibrarySqlBuilder.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/LocatorJsonCodec.kt
- [x] core/data/src/main/kotlin/com/ireader/core/data/book/ProgressRepo.kt
- [x] core/files/build.gradle.kts
- [x] core/files/src/main/AndroidManifest.xml
- [x] core/files/src/main/kotlin/com/ireader/core/files/scan/TreeScanner.kt
- [x] core/files/src/main/kotlin/com/ireader/core/files/source/ContentUriDocumentSource.kt
- [x] core/files/src/main/kotlin/com/ireader/core/files/source/DocumentSource.kt
- [x] core/files/src/main/kotlin/com/ireader/core/files/source/FileDocumentSource.kt
- [x] core/model/build.gradle.kts
- [x] core/model/src/main/kotlin/com/ireader/reader/model/BookFormat.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/DocumentCapabilities.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/DocumentId.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/DocumentMetadata.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/Geometry.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/Link.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/Locator.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/OutlineNode.kt
- [x] core/model/src/main/kotlin/com/ireader/reader/model/annotation/Annotation.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/annotation/Decoration.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/EngineRegistry.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/NavigatorCapableSession.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderDocument.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderEngine.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderSession.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderError.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderResult.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderResultExt.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/open/OpenOptions.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/provider/AnnotationProvider.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/LayoutConstraints.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/ReaderController.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/ReaderNavigatorAdapter.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderConfig.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderPage.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderPolicy.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderState.kt
- [x] core/reader/api/src/main/kotlin/com/ireader/reader/api/render/TextMapping.kt
- [x] core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/format/DefaultBookFormatDetector.kt
- [x] core/work/build.gradle.kts
- [x] core/work/src/main/AndroidManifest.xml
- [x] core/work/src/main/kotlin/com/ireader/core/work/ImportWorker.kt
- [x] core/work/src/main/kotlin/com/ireader/core/work/index/DefaultBookIndexer.kt
- [x] engines/txt/build.gradle.kts
- [x] engines/txt/src/main/AndroidManifest.xml
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/TxtEngine.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/TxtEngineConfig.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/LocatorConfig.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtController.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtLocatorMapper.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtNavigationHistory.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtPageSliceCache.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtTextMapping.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtCharsetDetector.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtDocument.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtTextNormalizer.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/IntArrayList.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/KeyHash.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/PageStartsCodec.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/PageStartsIndex.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/PaginationCache.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/RenderKey.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/TxtLastPositionStore.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/TxtPager.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/TxtPaginationStore.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/InMemoryAnnotationProvider.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtOutlineCache.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtOutlineProvider.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtOutlineTreeBuilder.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtSearchProvider.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtTextProvider.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/session/TxtSession.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/BomUtil.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/ChunkIndex.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/InMemoryTxtTextStore.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/IndexedTxtTextStore.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/TxtTextStore.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/TxtTextStoreFactory.kt
- [x] engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/util/ReaderErrors.kt
- [x] settings.gradle.kts

## app/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.application")
    id("com.ireader.android.compose")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader"

    defaultConfig {
        applicationId = "com.ireader"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:work"))
    implementation(project(":core:reader:runtime"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:annotations"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(project(":engines:txt"))
    implementation(project(":engines:epub"))
    implementation(project(":engines:pdf"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:name=".IReaderApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Ireader">
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Ireader">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

## app/src/main/java/com/ireader/di/ReaderRuntimeModule.kt

```kotlin
package com.ireader.di

import android.content.Context
import com.ireader.engines.epub.EpubEngine
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.runtime.DefaultReaderRuntime
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.registry.EngineRegistryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderRuntimeModule {

    @Provides
    @Singleton
    fun provideEngineRegistry(
        @ApplicationContext context: Context
    ): EngineRegistry {
        return EngineRegistryImpl(
            setOf(
                TxtEngine(
                    config = TxtEngineConfig(
                        cacheDir = context.cacheDir,
                        persistPagination = true,
                        persistOutline = false
                    )
                ),
                EpubEngine(context = context),
                PdfEngine(context = context)
            )
        )
    }

    @Provides
    @Singleton
    fun provideReaderRuntime(
        engineRegistry: EngineRegistry
    ): ReaderRuntime {
        return DefaultReaderRuntime(engineRegistry)
    }
}
```

## core/data/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
}
```

## core/data/src/main/AndroidManifest.xml

```xml
<manifest package="com.ireader.core.data" />
```

## core/data/src/main/kotlin/com/ireader/core/data/book/BookIndexer.kt

```kotlin
package com.ireader.core.data.book

interface BookIndexer {
    suspend fun index(bookId: Long): Result<Unit>
    suspend fun reindex(bookId: Long): Result<Unit>
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/BookRepo.kt

```kotlin
package com.ireader.core.data.book

import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.BookCollectionEntity
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BookRepo @Inject constructor(
    private val bookDao: BookDao,
    private val collectionDao: CollectionDao,
    private val bookCollectionDao: BookCollectionDao
) {
    suspend fun upsert(entity: BookEntity): Long = bookDao.upsert(entity)

    suspend fun findByFingerprint(fingerprint: String): BookEntity? = bookDao.findByFingerprint(fingerprint)

    suspend fun getById(bookId: Long): BookEntity? = bookDao.getById(bookId)

    suspend fun getByDocumentId(documentId: String): BookEntity? = bookDao.getByDocumentId(documentId)

    suspend fun deleteById(bookId: Long) = bookDao.deleteById(bookId)

    fun observeById(bookId: Long): Flow<BookEntity?> = bookDao.observeById(bookId)

    fun observeLibrary(query: LibraryQuery): Flow<List<LibraryBookItem>> {
        val sql = LibrarySqlBuilder.build(query)
        return bookDao.observeLibrary(sql).map { rows ->
            rows.map { row ->
                LibraryBookItem(
                    book = row.book,
                    progression = (row.progression ?: 0.0).coerceIn(0.0, 1.0),
                    progressUpdatedAtEpochMs = row.progressUpdatedAtEpochMs
                )
            }
        }
    }

    suspend fun setIndexState(bookId: Long, state: IndexState, error: String? = null) {
        bookDao.updateIndexState(
            bookId = bookId,
            state = state,
            error = error,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun touchLastOpened(bookId: Long) {
        val now = System.currentTimeMillis()
        bookDao.updateLastOpened(
            bookId = bookId,
            lastOpenedAt = now,
            updatedAt = now
        )
    }

    suspend fun setFavorite(bookId: Long, favorite: Boolean) {
        bookDao.updateFavorite(
            bookId = bookId,
            favorite = favorite,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun setReadingStatus(bookId: Long, status: ReadingStatus) {
        bookDao.updateReadingStatus(
            bookId = bookId,
            status = status,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun observeCollections(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

    suspend fun createCollection(name: String): Long {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Collection name cannot be blank" }

        val existing = collectionDao.getByName(normalized)
        if (existing != null) {
            return existing.collectionId
        }

        return collectionDao.upsert(
            CollectionEntity(
                name = normalized,
                createdAtEpochMs = System.currentTimeMillis(),
                sortOrder = 0
            )
        )
    }

    suspend fun addToCollection(bookId: Long, collectionId: Long) {
        bookCollectionDao.insert(BookCollectionEntity(bookId = bookId, collectionId = collectionId))
    }

    suspend fun removeFromCollection(bookId: Long, collectionId: Long) {
        bookCollectionDao.delete(bookId = bookId, collectionId = collectionId)
    }

    suspend fun listCollectionIdsForBook(bookId: Long): List<Long> {
        return bookCollectionDao.listCollectionIdsForBook(bookId)
    }
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/LibraryBookItem.kt

```kotlin
package com.ireader.core.data.book

import com.ireader.core.database.book.BookEntity

data class LibraryBookItem(
    val book: BookEntity,
    val progression: Double,
    val progressUpdatedAtEpochMs: Long?
)
```

## core/data/src/main/kotlin/com/ireader/core/data/book/LibraryQuery.kt

```kotlin
package com.ireader.core.data.book

import com.ireader.core.database.book.ReadingStatus

data class LibraryQuery(
    val keyword: String? = null,
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED,
    val statuses: Set<ReadingStatus> = emptySet(),
    val onlyFavorites: Boolean = false,
    val collectionId: Long? = null
)
```

## core/data/src/main/kotlin/com/ireader/core/data/book/LibrarySort.kt

```kotlin
package com.ireader.core.data.book

enum class LibrarySort {
    RECENTLY_UPDATED,
    RECENTLY_ADDED,
    LAST_OPENED,
    TITLE_AZ,
    AUTHOR_AZ,
    PROGRESSION_DESC
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/LibrarySqlBuilder.kt

```kotlin
package com.ireader.core.data.book

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object LibrarySqlBuilder {
    fun build(query: LibraryQuery): SupportSQLiteQuery {
        val sql = StringBuilder(
            """
            SELECT books.*, progress.progression AS progression, progress.updatedAtEpochMs AS progressUpdatedAtEpochMs
            FROM books
            LEFT JOIN progress ON progress.bookId = books.bookId
            WHERE 1 = 1
            """.trimIndent()
        )
        val args = mutableListOf<Any>()

        query.keyword
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { keyword ->
                sql.append(
                    " AND (COALESCE(books.title, '') LIKE ? OR COALESCE(books.author, '') LIKE ? OR books.fileName LIKE ?)"
                )
                val pattern = "%$keyword%"
                args += pattern
                args += pattern
                args += pattern
            }

        if (query.onlyFavorites) {
            sql.append(" AND books.favorite = 1")
        }

        if (query.statuses.isNotEmpty()) {
            val placeholders = query.statuses.joinToString(separator = ",") { "?" }
            sql.append(" AND books.readingStatus IN ($placeholders)")
            query.statuses.forEach { args += it.name }
        }

        query.collectionId?.let { collectionId ->
            sql.append(
                " AND EXISTS (SELECT 1 FROM book_collection bc WHERE bc.bookId = books.bookId AND bc.collectionId = ?)"
            )
            args += collectionId
        }

        sql.append(" ORDER BY ")
        sql.append(
            when (query.sort) {
                LibrarySort.RECENTLY_UPDATED -> "books.updatedAtEpochMs DESC"
                LibrarySort.RECENTLY_ADDED -> "books.addedAtEpochMs DESC"
                LibrarySort.LAST_OPENED -> "COALESCE(books.lastOpenedAtEpochMs, 0) DESC, books.updatedAtEpochMs DESC"
                LibrarySort.TITLE_AZ -> "COALESCE(books.title, books.fileName, '') COLLATE NOCASE ASC"
                LibrarySort.AUTHOR_AZ -> "COALESCE(books.author, '') COLLATE NOCASE ASC, COALESCE(books.title, books.fileName, '') COLLATE NOCASE ASC"
                LibrarySort.PROGRESSION_DESC -> "COALESCE(progress.progression, 0.0) DESC, books.updatedAtEpochMs DESC"
            }
        )

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/LocatorJsonCodec.kt

```kotlin
package com.ireader.core.data.book

import com.ireader.reader.model.Locator
import org.json.JSONObject

object LocatorJsonCodec {
    fun encode(locator: Locator): String {
        val extras = JSONObject().apply {
            locator.extras.forEach { (key, value) -> put(key, value) }
        }

        return JSONObject()
            .put("scheme", locator.scheme)
            .put("value", locator.value)
            .put("extras", extras)
            .toString()
    }

    fun decode(json: String): Locator? {
        return runCatching {
            val root = JSONObject(json)
            val scheme = root.optString("scheme").trim()
            val value = root.optString("value").trim()
            if (scheme.isEmpty() || value.isEmpty()) {
                return null
            }

            val extrasObject = root.optJSONObject("extras") ?: JSONObject()
            val extras = buildMap {
                val iterator = extrasObject.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    put(key, extrasObject.optString(key))
                }
            }

            Locator(
                scheme = scheme,
                value = value,
                extras = extras
            )
        }.getOrNull()
    }
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/ProgressRepo.kt

```kotlin
package com.ireader.core.data.book

import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ProgressRepo @Inject constructor(
    private val progressDao: ProgressDao
) {
    suspend fun getByBookId(bookId: Long): ProgressEntity? = progressDao.getByBookId(bookId)

    fun observeByBookId(bookId: Long): Flow<ProgressEntity?> = progressDao.observeByBookId(bookId)

    suspend fun upsert(bookId: Long, locatorJson: String, progression: Double, updatedAtEpochMs: Long) {
        progressDao.upsert(
            ProgressEntity(
                bookId = bookId,
                locatorJson = locatorJson,
                progression = progression.coerceIn(0.0, 1.0),
                updatedAtEpochMs = updatedAtEpochMs
            )
        )
    }

    suspend fun deleteByBookId(bookId: Long) {
        progressDao.deleteByBookId(bookId)
    }
}
```

## core/files/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.core.files"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.documentfile)
}
```

## core/files/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

## core/files/src/main/kotlin/com/ireader/core/files/scan/TreeScanner.kt

```kotlin
package com.ireader.core.files.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TreeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scan(treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = ArrayList<Uri>(128)
        traverse(root, out)
        return out
    }

    private fun traverse(node: DocumentFile, out: MutableList<Uri>) {
        val children = node.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                traverse(child, out)
            } else if (child.isFile && isSupported(child.name)) {
                out += child.uri
            }
        }
    }

    private fun isSupported(name: String?): Boolean {
        val value = name?.lowercase() ?: return false
        return value.endsWith(".epub") || value.endsWith(".pdf") || value.endsWith(".txt")
    }
}
```

## core/files/src/main/kotlin/com/ireader/core/files/source/ContentUriDocumentSource.kt

```kotlin
package com.ireader.core.files.source

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.ireader.core.files.source.DocumentSource
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentUriDocumentSource(
    context: Context,
    override val uri: Uri,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentSource {
    private val resolver: ContentResolver = context.contentResolver

    override val displayName: String? by lazy { queryString(OpenableColumns.DISPLAY_NAME) }
    override val sizeBytes: Long? by lazy { queryLong(OpenableColumns.SIZE) }
    override val mimeType: String? by lazy { resolver.getType(uri) }

    override suspend fun openInputStream(): InputStream = withContext(ioDispatcher) {
        resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open $uri")
    }

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = withContext(ioDispatcher) {
        resolver.openFileDescriptor(uri, mode)
    }

    private fun queryCursor(): Cursor? {
        return resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )
    }

    private fun queryString(column: String): String? {
        return queryCursor()?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }
    }

    private fun queryLong(column: String): Long? {
        return queryCursor()?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }
}
```

## core/files/src/main/kotlin/com/ireader/core/files/source/DocumentSource.kt

```kotlin
package com.ireader.core.files.source

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream

interface DocumentSource {
    val uri: Uri
    val displayName: String?
    val mimeType: String?
    val sizeBytes: Long?

    suspend fun openInputStream(): InputStream
    suspend fun openFileDescriptor(mode: String = "r"): ParcelFileDescriptor?
}
```

## core/files/src/main/kotlin/com/ireader/core/files/source/FileDocumentSource.kt

```kotlin
package com.ireader.core.files.source

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileDocumentSource(
    private val file: File,
    override val displayName: String? = file.name,
    override val mimeType: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentSource {
    override val uri: Uri = Uri.fromFile(file)
    override val sizeBytes: Long? = file.length()

    override suspend fun openInputStream(): InputStream = withContext(ioDispatcher) {
        FileInputStream(file)
    }

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = withContext(ioDispatcher) {
        val pfdMode = when (mode) {
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        ParcelFileDescriptor.open(file, pfdMode)
    }
}
```

## core/model/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.kotlin.library")
}
```

## core/model/src/main/kotlin/com/ireader/reader/model/BookFormat.kt

```kotlin
package com.ireader.reader.model

enum class BookFormat { TXT, EPUB, PDF }
```

## core/model/src/main/kotlin/com/ireader/reader/model/DocumentCapabilities.kt

```kotlin
package com.ireader.reader.model

data class DocumentCapabilities(
    // TXT/EPUB = true
    val reflowable: Boolean,
    // PDF = true
    val fixedLayout: Boolean,
    val outline: Boolean,
    val search: Boolean,
    // 复制/分享/导出
    val textExtraction: Boolean,
    // 能否把标注映射到页面（reflow/fixed）
    val annotations: Boolean,
    // 是否能提供可点击链接信息（内部/外部）
    val links: Boolean,
)
```

## core/model/src/main/kotlin/com/ireader/reader/model/DocumentId.kt

```kotlin
package com.ireader.reader.model

@JvmInline
value class DocumentId(
    val value: String,
)

@JvmInline
value class SessionId(
    val value: String,
)
```

## core/model/src/main/kotlin/com/ireader/reader/model/DocumentMetadata.kt

```kotlin
package com.ireader.reader.model

data class DocumentMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val identifier: String? = null, // ISBN / OPF id / 自定义
    val extra: Map<String, String> = emptyMap(),
)
```

## core/model/src/main/kotlin/com/ireader/reader/model/Geometry.kt

```kotlin
package com.ireader.reader.model

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

/**
 * 归一化矩形：0..1 相对当前页内容坐标，UI 自行映射到像素。
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(p: NormalizedPoint): Boolean = p.x in left..right && p.y in top..bottom
}
```

## core/model/src/main/kotlin/com/ireader/reader/model/Link.kt

```kotlin
package com.ireader.reader.model

sealed interface LinkTarget {
    data class Internal(
        val locator: Locator,
    ) : LinkTarget

    data class External(
        val url: String,
    ) : LinkTarget
}

data class DocumentLink(
    val target: LinkTarget,
    val title: String? = null,
    /**
     * fixed-layout（PDF）通常能给出 bounds；reflow 文本页不一定有
     */
    val bounds: List<NormalizedRect>? = null,
)
```

## core/model/src/main/kotlin/com/ireader/reader/model/Locator.kt

```kotlin
package com.ireader.reader.model

/**
 * scheme + value 统一表达位置。
 * - TXT: txt.offset -> value=字符偏移(建议) 或字节偏移(需固定编码策略)
 * - EPUB: epub.cfi  -> value=CFI 字符串
 * - PDF: pdf.page   -> value=页索引(0-based 建议) 或 1-based(需统一)
 */
data class Locator(
    val scheme: String,
    val value: String,
    val extras: Map<String, String> = emptyMap(),
)

data class LocatorRange(
    val start: Locator,
    val end: Locator,
    val extras: Map<String, String> = emptyMap(),
)

object LocatorSchemes {
    const val TXT_OFFSET = "txt.offset"
    const val EPUB_CFI = "epub.cfi"
    const val PDF_PAGE = "pdf.page"

    /**
     * 可选：当你对 reflow 做“稳定分页”缓存时使用（与 LayoutConstraints+RenderConfig 强绑定）
     */
    const val REFLOW_PAGE = "reflow.page"
}

/**
 * 可选：Locator 归一化接口（同一引擎内部建议实现）
 * 比如把 "page=12" 的各种写法统一成 0-based，或者把 EPUB href#anchor 解析成 CFI。
 */
interface LocatorNormalizer {
    suspend fun normalize(locator: Locator): Locator
}
```

## core/model/src/main/kotlin/com/ireader/reader/model/OutlineNode.kt

```kotlin
package com.ireader.reader.model

data class OutlineNode(
    val title: String,
    val locator: Locator,
    val children: List<OutlineNode> = emptyList(),
)
```

## core/model/src/main/kotlin/com/ireader/reader/model/annotation/Annotation.kt

```kotlin
package com.ireader.reader.model.annotation

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect

@JvmInline
value class AnnotationId(
    val value: String,
)

enum class AnnotationType { HIGHLIGHT, UNDERLINE, NOTE, BOOKMARK }

data class AnnotationStyle(
    val colorArgb: Int? = null,
    val opacity: Float? = null,
    val extra: Map<String, String> = emptyMap(),
)

/**
 * 锚点：reflow 用 LocatorRange；fixed（PDF）用 page + rects（归一化坐标）
 * 这样你可以：
 * - TXT/EPUB：高亮范围稳定
 * - PDF：高亮可以精确到矩形区域
 */
sealed interface AnnotationAnchor {
    data class ReflowRange(
        val range: LocatorRange,
    ) : AnnotationAnchor

    data class FixedRects(
        // 通常 scheme=pdf.page
        val page: Locator,
        // 归一化矩形列表
        val rects: List<NormalizedRect>,
    ) : AnnotationAnchor
}

data class Annotation(
    val id: AnnotationId,
    val type: AnnotationType,
    val anchor: AnnotationAnchor,
    // NOTE 文本等
    val content: String? = null,
    val style: AnnotationStyle = AnnotationStyle(),
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val extra: Map<String, String> = emptyMap(),
)

/**
 * 创建标注时通常不带 id/时间
 */
data class AnnotationDraft(
    val type: AnnotationType,
    val anchor: AnnotationAnchor,
    val content: String? = null,
    val style: AnnotationStyle = AnnotationStyle(),
    val extra: Map<String, String> = emptyMap(),
)
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/annotation/Decoration.kt

```kotlin
package com.ireader.reader.api.annotation

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.AnnotationStyle

/**
 * Decoration 是“渲染叠加层”的通用表达。
 * - reflow：按 LocatorRange
 * - fixed：按 page + rects
 */
sealed interface Decoration {

    data class Reflow(
        val range: LocatorRange,
        val style: AnnotationStyle
    ) : Decoration

    data class Fixed(
        val page: Locator,
        val rects: List<NormalizedRect>,
        val style: AnnotationStyle
    ) : Decoration
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/EngineRegistry.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.model.BookFormat

interface EngineRegistry {
    fun engineFor(format: BookFormat): ReaderEngine?
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/NavigatorCapableSession.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.render.ReaderNavigatorAdapter

/**
 * Session exposing a native navigator host instead of RenderPage-based output.
 */
interface NavigatorCapableSession : ReaderSession {
    val navigatorAdapter: ReaderNavigatorAdapter
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderDocument.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import java.io.Closeable

interface ReaderDocument : Closeable {
    val id: DocumentId
    val format: com.ireader.reader.model.BookFormat
    val capabilities: DocumentCapabilities
    val openOptions: OpenOptions

    suspend fun metadata(): ReaderResult<DocumentMetadata>

    /**
     * 会话：包含当前阅读位置、渲染设置、缓存等
     */
    suspend fun createSession(
        initialLocator: Locator? = null,
        initialConfig: RenderConfig = RenderConfig.Default
    ): ReaderResult<ReaderSession>
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderEngine.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.api.open.OpenOptions
import com.ireader.core.files.source.DocumentSource

interface ReaderEngine {
    val supportedFormats: Set<BookFormat>

    /**
     * 打开文档：解析、解压、建立索引等
     * 失败返回 ReaderResult.Err，避免异常散落 UI 层。
     */
    suspend fun open(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderSession.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.io.Closeable

interface ReaderSession : Closeable {
    val id: SessionId

    /**
     * 核心控制器：导航 + 渲染 + 状态流
     */
    val controller: ReaderController

    /**
     * 可选能力（按 capabilities 决定是否为 null）
     */
    val outline: OutlineProvider?
    val search: SearchProvider?
    val text: TextProvider?
    val annotations: AnnotationProvider?
    val resources: ResourceProvider? // EPUB 常用：给 WebView/资源加载器
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderError.kt

```kotlin
package com.ireader.reader.api.error

/**
 * api 层错误类型：保证 UI/业务可以稳定识别并做兜底。
 */
sealed class ReaderError(
    message: String? = null,
    cause: Throwable? = null,
    val code: String
) : RuntimeException(message, cause) {

    class UnsupportedFormat(
        val detected: String? = null,
        message: String? = "Unsupported format",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "UNSUPPORTED_FORMAT")

    class NotFound(
        message: String? = "File not found",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "NOT_FOUND")

    class PermissionDenied(
        message: String? = "Permission denied",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "PERMISSION_DENIED")

    class InvalidPassword(
        message: String? = "Invalid password",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "INVALID_PASSWORD")

    class CorruptOrInvalid(
        message: String? = "Corrupt or invalid document",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "CORRUPT_OR_INVALID")

    class DrmRestricted(
        message: String? = "DRM restricted",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "DRM_RESTRICTED")

    class Io(
        message: String? = "I/O error",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "IO")

    class Cancelled(
        message: String? = "Cancelled",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "CANCELLED")

    class Internal(
        message: String? = "Internal error",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "INTERNAL")
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderResult.kt

```kotlin
package com.ireader.reader.api.error

sealed interface ReaderResult<out T> {
    data class Ok<T>(val value: T) : ReaderResult<T>
    data class Err(val error: ReaderError) : ReaderResult<Nothing>
}

inline fun <T> ReaderResult<T>.getOrNull(): T? =
    when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> null
    }

inline fun <T> ReaderResult<T>.getOrThrow(): T =
    when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> throw error
    }
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderResultExt.kt

```kotlin
package com.ireader.reader.api.error

inline fun <T, R> ReaderResult<T>.map(transform: (T) -> R): ReaderResult<R> =
    when (this) {
        is ReaderResult.Ok -> ReaderResult.Ok(transform(value))
        is ReaderResult.Err -> this
    }

inline fun <T, R> ReaderResult<T>.flatMap(transform: (T) -> ReaderResult<R>): ReaderResult<R> =
    when (this) {
        is ReaderResult.Ok -> transform(value)
        is ReaderResult.Err -> this
    }

inline fun <T> ReaderResult<T>.mapError(transform: (ReaderError) -> ReaderError): ReaderResult<T> =
    when (this) {
        is ReaderResult.Ok -> this
        is ReaderResult.Err -> ReaderResult.Err(transform(error))
    }

inline fun <T, R> ReaderResult<T>.fold(
    onOk: (T) -> R,
    onErr: (ReaderError) -> R
): R =
    when (this) {
        is ReaderResult.Ok -> onOk(value)
        is ReaderResult.Err -> onErr(error)
    }

inline fun <T> ReaderResult<T>.onOk(block: (T) -> Unit): ReaderResult<T> =
    also { if (this is ReaderResult.Ok) block(value) }

inline fun <T> ReaderResult<T>.onErr(block: (ReaderError) -> Unit): ReaderResult<T> =
    also { if (this is ReaderResult.Err) block(error) }
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/open/OpenOptions.kt

```kotlin
package com.ireader.reader.api.open

import com.ireader.reader.model.BookFormat

data class OpenOptions(
    val hintFormat: BookFormat? = null,
    val password: String? = null,      // PDF
    val textEncoding: String? = null,  // TXT: "UTF-8"/"GBK"/...
    val extra: Map<String, String> = emptyMap()
)
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/provider/AnnotationProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow

data class AnnotationQuery(
    val page: Locator? = null,            // fixed 查询（PDF）
    val range: LocatorRange? = null       // reflow 查询（TXT/EPUB）
)

interface AnnotationProvider {

    fun observeAll(): Flow<List<Annotation>>

    suspend fun listAll(): ReaderResult<List<Annotation>>

    suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>>

    suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation>

    suspend fun update(annotation: Annotation): ReaderResult<Unit>

    suspend fun delete(id: AnnotationId): ReaderResult<Unit>

    /**
     * 提供给渲染层的 Decoration（默认可以由 annotation->decoration 直接映射）
     * 引擎也可以做更复杂的映射（比如 EPUB CFI 对应到章节分页后的 charRange）。
     */
    suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>>
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/LayoutConstraints.kt

```kotlin
package com.ireader.reader.api.render

data class LayoutConstraints(
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val density: Float,
    val fontScale: Float
)
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/ReaderController.kt

```kotlin
package com.ireader.reader.api.render

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

sealed interface ReaderEvent {
    data class PageChanged(val locator: Locator) : ReaderEvent
    data class Rendered(val pageId: PageId, val metrics: RenderMetrics?) : ReaderEvent
    data class Error(val throwable: Throwable) : ReaderEvent
}

/**
 * Session 内的“唯一总控”：UI 层只需要跟它交互即可。
 * - 负责：布局约束、配置变更、跳转/翻页、产出 RenderPage
 * - 维护：状态流/事件流
 */
interface ReaderController : Closeable {

    val state: StateFlow<RenderState>
    val events: Flow<ReaderEvent>

    suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit>

    suspend fun setConfig(config: RenderConfig): ReaderResult<Unit>

    /**
     * 渲染当前 locator
     */
    suspend fun render(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun next(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun prev(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goTo(locator: Locator, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    /**
     * 按进度跳转：percent 0..1
     * TXT/EPUB 可能是近似（取决于实现）。
     */
    suspend fun goToProgress(percent: Double, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    /**
     * 预取（可 no-op），通常用于：翻页更顺滑 / PDF 下一页 tiles 预热
     */
    suspend fun prefetchNeighbors(count: Int = 1): ReaderResult<Unit>

    /**
     * 当标注/装饰变化时，通知引擎让下一次 render 使用最新 decorations（或内部 cache 失效）。
     */
    suspend fun invalidate(reason: InvalidateReason = InvalidateReason.CONTENT_CHANGED): ReaderResult<Unit>
}

enum class InvalidateReason {
    CONTENT_CHANGED,   // 标注/主题变化导致页面装饰变动
    CONFIG_CHANGED,    // 字体/缩放等
    LAYOUT_CHANGED     // 横竖屏/窗口变化
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/ReaderNavigatorAdapter.kt

```kotlin
package com.ireader.reader.api.render

import androidx.fragment.app.Fragment
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter for navigator-first engines (eg. Readium EPUB) which render with a Fragment.
 */
interface ReaderNavigatorAdapter : Closeable {
    fun createFragment(): Fragment

    val locatorFlow: StateFlow<Locator?>

    suspend fun goTo(locator: Locator): ReaderResult<Unit>

    suspend fun submitConfig(config: RenderConfig.ReflowText): ReaderResult<Unit>

    suspend fun next(): ReaderResult<Unit>

    suspend fun prev(): ReaderResult<Unit>
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderConfig.kt

```kotlin
package com.ireader.reader.api.render

sealed interface RenderConfig {

    data class ReflowText(
        val fontSizeSp: Float = 18f,
        val lineHeightMult: Float = 1.5f,
        val paragraphSpacingDp: Float = 6f,
        val pagePaddingDp: Float = 16f,
        val fontFamilyName: String? = null,
        val hyphenation: Boolean = false,
        val extra: Map<String, String> = emptyMap()
    ) : RenderConfig

    data class FixedPage(
        val fitMode: FitMode = FitMode.FIT_WIDTH,
        val zoom: Float = 1.0f,
        val rotationDegrees: Int = 0,
        val extra: Map<String, String> = emptyMap()
    ) : RenderConfig

    enum class FitMode { FIT_WIDTH, FIT_HEIGHT, FIT_PAGE, FREE }

    companion object {
        val Default: RenderConfig = ReflowText()
    }
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderPage.kt

```kotlin
package com.ireader.reader.api.render

import android.graphics.Bitmap
import android.net.Uri
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

@JvmInline
value class PageId(val value: String)

/**
 * 渲染产物统一封装：内容 + 可选链接 + 可选装饰(标注高亮等) + 性能指标
 */
data class RenderPage(
    val id: PageId,
    val locator: Locator,
    val content: RenderContent,
    val links: List<DocumentLink> = emptyList(),
    val decorations: List<Decoration> = emptyList(),
    val metrics: RenderMetrics? = null
)

sealed interface RenderContent {
    data class Text(
        val text: CharSequence,
        val mapping: TextMapping? = null
    ) : RenderContent

    /**
     * EPUB 常用：inline html 或者直接给一个可加载的 Uri
     * - Inline：适合引擎自己拼装 html
     * - Uri：适合引擎把章节写入/映射到本地资源系统
     */
    data class Html(
        val inlineHtml: String? = null,
        val contentUri: Uri? = null,
        val baseUri: Uri? = null
    ) : RenderContent

    data class BitmapPage(
        val bitmap: Bitmap
    ) : RenderContent

    /**
     * PDF 大图/高倍率缩放建议用 Tiles：避免一次性巨大 bitmap。
     */
    data class Tiles(
        val pageWidthPx: Int,
        val pageHeightPx: Int,
        val tileProvider: TileProvider
    ) : RenderContent
}

data class RenderMetrics(
    val renderTimeMs: Long,
    val cacheHit: Boolean
)
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderPolicy.kt

```kotlin
package com.ireader.reader.api.render

/**
 * 渲染策略：用于优化体验
 * - PDF：先出 DRAFT（低清）再补 FINAL（高清）
 * - TXT/EPUB：通常忽略 quality，但可用于“快速分页->精分页”
 */
data class RenderPolicy(
    val quality: Quality = Quality.FINAL,
    val allowCache: Boolean = true,
    val prefetchNeighbors: Int = 1 // 渲染后预取前后页
) {
    enum class Quality { DRAFT, FINAL }

    companion object {
        val Default = RenderPolicy()
    }
}
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/RenderState.kt

```kotlin
package com.ireader.reader.api.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression

data class NavigationAvailability(
    val canGoPrev: Boolean,
    val canGoNext: Boolean
)

data class RenderState(
    val locator: Locator,
    val progression: Progression,
    val nav: NavigationAvailability,
    val titleInView: String? = null,
    val config: RenderConfig
)
```

## core/reader/api/src/main/kotlin/com/ireader/reader/api/render/TextMapping.kt

```kotlin
package com.ireader.reader.api.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

/**
 * reflow 文本映射：用于 “字符索引 <-> Locator/Range”
 * 支撑：复制、标注范围定位、搜索命中定位等。
 */
interface TextMapping {
    fun locatorAt(charIndex: Int): Locator
    fun rangeFor(startChar: Int, endChar: Int): LocatorRange
    fun charRangeFor(range: LocatorRange): IntRange?
}
```

## core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/format/DefaultBookFormatDetector.kt

```kotlin
package com.ireader.reader.runtime.format

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.runtime.error.toReaderError
import java.io.BufferedInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")
class DefaultBookFormatDetector(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BookFormatDetector {

    override suspend fun detect(
        source: DocumentSource,
        hint: BookFormat?
    ): ReaderResult<BookFormat> = withContext(ioDispatcher) {
        try {
            // 1) hint
            if (hint != null) return@withContext ReaderResult.Ok(hint)

            // 2) mime
            detectFromMime(source.mimeType)?.let { return@withContext ReaderResult.Ok(it) }

            // 3) extension
            detectFromName(source.displayName)?.let { return@withContext ReaderResult.Ok(it) }

            // 4) magic sniff
            sniffFromContent(source)
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    private fun detectFromMime(mimeType: String?): BookFormat? {
        val mime = mimeType?.lowercase(Locale.US)?.trim() ?: return null
        return when {
            mime == "application/pdf" -> BookFormat.PDF
            mime == "application/epub+zip" -> BookFormat.EPUB
            mime == "text/plain" -> BookFormat.TXT
            // 有些文件管理器会给 text/*，可按需要放宽
            mime.startsWith("text/") -> BookFormat.TXT
            else -> null
        }
    }

    private fun detectFromName(displayName: String?): BookFormat? {
        val name = displayName?.lowercase(Locale.US)?.trim() ?: return null
        return when {
            name.endsWith(".pdf") -> BookFormat.PDF
            name.endsWith(".epub") -> BookFormat.EPUB
            name.endsWith(".txt") -> BookFormat.TXT
            else -> null
        }
    }

    private suspend fun sniffFromContent(source: DocumentSource): ReaderResult<BookFormat> {
        val header = readHeader(source, bytes = 8)

        if (looksLikePdf(header)) return ReaderResult.Ok(BookFormat.PDF)

        if (looksLikeZip(header)) {
            val epub = looksLikeEpubZip(source)
            return if (epub) {
                ReaderResult.Ok(BookFormat.EPUB)
            } else {
                ReaderResult.Err(ReaderError.UnsupportedFormat(detected = "zip"))
            }
        }

        // 兜底策略：本地阅读器很多“未知类型”其实就是 txt（尤其是 mime 缺失时）
        return ReaderResult.Ok(BookFormat.TXT)
    }

    private suspend fun readHeader(source: DocumentSource, bytes: Int): ByteArray {
        return source.openInputStream().use { input ->
            val buf = ByteArray(bytes)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
    }

    private fun looksLikePdf(header: ByteArray): Boolean {
        // "%PDF"
        return header.size >= 4 &&
            header[0] == 0x25.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() &&
            header[3] == 0x46.toByte()
    }

    private fun looksLikeZip(header: ByteArray): Boolean {
        // "PK.."
        return header.size >= 2 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte()
    }

    private suspend fun looksLikeEpubZip(source: DocumentSource): Boolean {
        // 重新开流：zip 探测需要从头读
        source.openInputStream().use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var hasContainerXml = false

                while (true) {
                    coroutineContext.ensureActive()

                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    if (name == "mimetype") {
                        // epub 标准：mimetype 文件内容为 "application/epub+zip"
                        val content = readSmallAscii(zis, limitBytes = 64).trim()
                        if (content == "application/epub+zip") return true
                    }

                    if (name.equals("META-INF/container.xml", ignoreCase = true)) {
                        hasContainerXml = true
                        // 有 container.xml 基本可以视为 epub
                        return true
                    }
                }

                return hasContainerXml
            }
        }
    }

    private fun readSmallAscii(zis: ZipInputStream, limitBytes: Int): String {
        val buf = ByteArray(limitBytes)
        val read = zis.read(buf)
        return if (read <= 0) "" else String(buf, 0, read, Charsets.US_ASCII)
    }
}
```

## core/work/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.library")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.core.work"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:model"))
    implementation(project(":core:reader:runtime"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
```

## core/work/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application>
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
    </application>
</manifest>
```

## core/work/src/main/kotlin/com/ireader/core/work/ImportWorker.kt

```kotlin
package com.ireader.core.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.hash.Fingerprint
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.core.files.scan.TreeScanner
import com.ireader.core.files.source.ContentUriDocumentSource
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.enrich.EnrichWorker
import com.ireader.core.work.enrich.EnrichWorkerInput
import com.ireader.core.work.notification.ImportForeground
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.format.BookFormatDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val jobRepo: ImportJobRepo,
    private val itemRepo: ImportItemRepo,
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val treeScanner: TreeScanner,
    private val formatDetector: BookFormatDetector
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val currentJob = jobRepo.get(jobId) ?: return@withContext Result.failure()

        if (currentJob.sourceTreeUri != null) {
            val existingItems = itemRepo.list(jobId)
            if (existingItems.isEmpty()) {
                val uris = treeScanner.scan(Uri.parse(currentJob.sourceTreeUri))
                val now = System.currentTimeMillis()
                val scannedItems = uris.map { uri ->
                    val source = ContentUriDocumentSource(applicationContext, uri)
                    ImportItemEntity(
                        jobId = jobId,
                        uri = uri.toString(),
                        displayName = source.displayName,
                        mimeType = source.mimeType,
                        sizeBytes = source.sizeBytes,
                        status = ImportItemStatus.PENDING,
                        bookId = null,
                        fingerprintSha256 = null,
                        errorCode = null,
                        errorMessage = null,
                        updatedAtEpochMs = now
                    )
                }
                itemRepo.upsertAll(scannedItems)
                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.QUEUED,
                    total = scannedItems.size,
                    done = 0,
                    currentTitle = null,
                    errorMessage = null,
                    now = now
                )
            }
        }

        val duplicateStrategy = runCatching {
            DuplicateStrategy.valueOf(currentJob.duplicateStrategy)
        }.getOrDefault(DuplicateStrategy.SKIP)

        val initial = jobRepo.get(jobId) ?: currentJob
        var done = initial.done
        var total = initial.total

        jobRepo.updateProgress(
            jobId = jobId,
            status = ImportStatus.RUNNING,
            total = total,
            done = done,
            currentTitle = null,
            errorMessage = null,
            now = System.currentTimeMillis()
        )

        val pendingItems = itemRepo.listPendingOrFailed(jobId)
        if (total == 0) {
            total = pendingItems.size
        }

        return@withContext try {
            setForegroundSafe(done, total, null)

            for (item in pendingItems) {
                currentCoroutineContext().ensureActive()

                val title = item.displayName ?: item.uri
                val now = System.currentTimeMillis()

                itemRepo.update(
                    jobId = jobId,
                    uri = item.uri,
                    status = ImportItemStatus.RUNNING,
                    bookId = null,
                    fingerprint = null,
                    errorCode = null,
                    errorMessage = null,
                    now = now
                )
                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.RUNNING,
                    total = total,
                    done = done,
                    currentTitle = title,
                    errorMessage = null,
                    now = now
                )
                setForegroundSafe(done, total, title)

                val source = ContentUriDocumentSource(applicationContext, Uri.parse(item.uri))
                val outcome = runCatching { importOne(source, duplicateStrategy) }
                    .getOrElse { throwable ->
                        val (code, message) = throwable.toImportError()
                        ImportOneResult.Fail(code, message)
                    }

                when (outcome) {
                    is ImportOneResult.Ok -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.SUCCEEDED,
                            bookId = outcome.bookId,
                            fingerprint = outcome.fingerprint,
                            errorCode = null,
                            errorMessage = null,
                            now = System.currentTimeMillis()
                        )
                    }

                    is ImportOneResult.Skipped -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.SKIPPED,
                            bookId = outcome.bookId,
                            fingerprint = outcome.fingerprint,
                            errorCode = null,
                            errorMessage = "duplicate",
                            now = System.currentTimeMillis()
                        )
                    }

                    is ImportOneResult.Fail -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.FAILED,
                            bookId = null,
                            fingerprint = null,
                            errorCode = outcome.code,
                            errorMessage = outcome.message,
                            now = System.currentTimeMillis()
                        )
                    }
                }

                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.RUNNING,
                    total = total,
                    done = done,
                    currentTitle = null,
                    errorMessage = null,
                    now = System.currentTimeMillis()
                )
                setForegroundSafe(done, total, null)
            }

            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.SUCCEEDED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = null,
                now = System.currentTimeMillis()
            )

            val enrichWork = OneTimeWorkRequestBuilder<EnrichWorker>()
                .setInputData(EnrichWorkerInput.data(jobId))
                .addTag(WorkNames.tagEnrichForJob(jobId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WorkNames.uniqueEnrichForJob(jobId),
                ExistingWorkPolicy.KEEP,
                enrichWork
            )

            Result.success()
        } catch (cancelled: CancellationException) {
            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.CANCELLED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = "Cancelled",
                now = System.currentTimeMillis()
            )
            throw cancelled
        } catch (throwable: Throwable) {
            val (_, message) = throwable.toImportError()
            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.FAILED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = message,
                now = System.currentTimeMillis()
            )
            Result.failure()
        }
    }

    private sealed interface ImportOneResult {
        data class Ok(val bookId: Long, val fingerprint: String) : ImportOneResult
        data class Skipped(val bookId: Long?, val fingerprint: String) : ImportOneResult
        data class Fail(val code: String, val message: String) : ImportOneResult
    }

    private suspend fun importOne(
        source: ContentUriDocumentSource,
        duplicateStrategy: DuplicateStrategy
    ): ImportOneResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fileName = source.displayName ?: "unknown"
        val extension = guessExtension(fileName, source.mimeType)
        val tempFile = storage.importTempFile()
        val digest = Fingerprint.newSha256()

        try {
            val copiedBytes = copyWithDigest(source, tempFile, digest)
            val fingerprint = Fingerprint.sha256Hex(digest.digest())
            val existing = bookRepo.findByFingerprint(fingerprint)

            if (existing != null && duplicateStrategy == DuplicateStrategy.SKIP) {
                runCatching { tempFile.delete() }
                return@withContext ImportOneResult.Skipped(existing.bookId, fingerprint)
            }

            val detectedFormat = detectFormatFromFile(tempFile, fileName)

            if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                storage.deleteCanonical(existing.bookId)
                val finalFile = storage.canonicalFile(existing.bookId, extension)
                storage.atomicMove(tempFile, finalFile)

                val updated = existing.copy(
                    sourceUri = source.uri.toString(),
                    sourceType = BookSourceType.IMPORTED_COPY,
                    format = detectedFormat,
                    fileName = fileName,
                    mimeType = source.mimeType,
                    fileSizeBytes = copiedBytes,
                    lastModifiedEpochMs = finalFile.lastModified(),
                    canonicalPath = finalFile.absolutePath,
                    fingerprintSha256 = fingerprint,
                    coverPath = null,
                    indexState = IndexState.PENDING,
                    indexError = null,
                    updatedAtEpochMs = now
                )
                bookRepo.upsert(updated)
                return@withContext ImportOneResult.Ok(existing.bookId, fingerprint)
            }

            val title = fileName.substringBeforeLast('.', fileName).ifBlank { "Untitled" }
            val placeholder = BookEntity(
                documentId = null,
                sourceUri = source.uri.toString(),
                sourceType = BookSourceType.IMPORTED_COPY,
                format = detectedFormat,
                fileName = fileName,
                mimeType = source.mimeType,
                fileSizeBytes = copiedBytes,
                lastModifiedEpochMs = null,
                canonicalPath = "",
                fingerprintSha256 = fingerprint,
                title = title,
                author = null,
                language = null,
                identifier = null,
                series = null,
                description = null,
                coverPath = null,
                favorite = false,
                readingStatus = ReadingStatus.UNREAD,
                indexState = IndexState.PENDING,
                indexError = null,
                capabilitiesJson = null,
                addedAtEpochMs = now,
                updatedAtEpochMs = now,
                lastOpenedAtEpochMs = null
            )
            val insertResult = bookRepo.upsert(placeholder)
            val insertedId = resolveInsertedBookId(insertResult, fingerprint)
                ?: throw IllegalStateException("Cannot resolve inserted book id")

            val finalFile = storage.canonicalFile(insertedId, extension)
            storage.atomicMove(tempFile, finalFile)

            val inserted = placeholder.copy(
                bookId = insertedId,
                canonicalPath = finalFile.absolutePath,
                lastModifiedEpochMs = finalFile.lastModified()
            )
            bookRepo.upsert(inserted)

            return@withContext ImportOneResult.Ok(insertedId, fingerprint)
        } catch (throwable: Throwable) {
            runCatching { tempFile.delete() }
            val (code, message) = throwable.toImportError()
            ImportOneResult.Fail(code, message)
        }
    }

    private suspend fun resolveInsertedBookId(insertResult: Long, fingerprint: String): Long? {
        if (insertResult > 0L) {
            return insertResult
        }
        return bookRepo.findByFingerprint(fingerprint)?.bookId
    }

    private suspend fun copyWithDigest(
        source: ContentUriDocumentSource,
        outputFile: File,
        digest: java.security.MessageDigest
    ): Long {
        var total = 0L
        source.openInputStream().use { rawInput ->
            BufferedInputStream(rawInput).use { input ->
                outputFile.outputStream().use { fileOutput ->
                    BufferedOutputStream(fileOutput).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) {
                                break
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            total += read
                        }
                        output.flush()
                    }
                }
            }
        }
        return total
    }

    private suspend fun detectFormatFromFile(file: File, displayName: String): BookFormat {
        val source = FileDocumentSource(file, displayName = displayName)
        return when (val result = formatDetector.detect(source, hint = null)) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> BookFormat.TXT
        }
    }

    private fun guessExtension(displayName: String, mimeType: String?): String {
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".epub") -> "epub"
            lowerName.endsWith(".pdf") -> "pdf"
            lowerName.endsWith(".txt") -> "txt"
            mimeType == "application/epub+zip" -> "epub"
            mimeType == "application/pdf" -> "pdf"
            else -> "txt"
        }
    }

    private suspend fun setForegroundSafe(done: Int, total: Int, currentTitle: String?) {
        runCatching {
            setForeground(ImportForeground.info(applicationContext, done, total, currentTitle))
        }
    }

    companion object {
        private const val KEY_JOB_ID = "job_id"
        private const val BUFFER_SIZE = 128 * 1024

        fun input(jobId: String): Data {
            return Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .build()
        }
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/index/DefaultBookIndexer.kt

```kotlin
package com.ireader.core.work.index

import com.ireader.core.data.book.BookIndexer
import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.IndexState
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.enrich.BitmapIO
import com.ireader.core.work.enrich.CoverRenderer
import com.ireader.core.work.enrich.epub.EpubZipEnricher
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderRuntime
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import org.json.JSONObject

@Singleton
class DefaultBookIndexer @Inject constructor(
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val runtime: ReaderRuntime
) : BookIndexer {

    override suspend fun index(bookId: Long): Result<Unit> = indexInternal(bookId = bookId, force = false)

    override suspend fun reindex(bookId: Long): Result<Unit> = indexInternal(bookId = bookId, force = true)

    private suspend fun indexInternal(bookId: Long, force: Boolean): Result<Unit> {
        val book = bookRepo.getById(bookId)
            ?: return Result.failure(IllegalStateException("book not found: $bookId"))

        val file = File(book.canonicalPath)
        if (!file.exists()) {
            bookRepo.setIndexState(bookId = bookId, state = IndexState.MISSING, error = "file missing")
            return Result.failure(IllegalStateException("book file missing"))
        }

        val source = FileDocumentSource(
            file = file,
            displayName = book.fileName,
            mimeType = book.mimeType
        )
        val probeResult = runtime.probe(
            source = source,
            options = OpenOptions(hintFormat = book.format)
        )
        if (probeResult !is ReaderResult.Ok) {
            val message = when (probeResult) {
                is ReaderResult.Err -> probeResult.error.toString()
                is ReaderResult.Ok -> null
            }
            bookRepo.setIndexState(bookId = bookId, state = IndexState.ERROR, error = message)
            return Result.failure(IllegalStateException(message ?: "probe failed"))
        }

        val probe = probeResult.value
        val metadata = probe.metadata
        val now = System.currentTimeMillis()
        val finalCoverPath = resolveCoverPath(book = book, probe = probe, file = file, force = force)

        val updated = book.copy(
            documentId = probe.documentId ?: book.documentId,
            format = probe.format,
            title = metadataField(metadata?.title, book.title, fallback = fallbackTitle(book.fileName)),
            author = metadataField(metadata?.author, book.author, fallback = null),
            language = metadataField(metadata?.language, book.language, fallback = null),
            identifier = metadataField(metadata?.identifier, book.identifier, fallback = null),
            series = metadataField(metadata?.extra?.get("series"), book.series, fallback = null),
            description = metadataField(metadata?.extra?.get("description"), book.description, fallback = null),
            coverPath = finalCoverPath,
            indexState = IndexState.INDEXED,
            indexError = null,
            capabilitiesJson = encodeCapabilities(probe.capabilities),
            updatedAtEpochMs = now
        )

        bookRepo.upsert(updated)
        return Result.success(Unit)
    }

    private fun fallbackTitle(fileName: String): String {
        return fileName.substringBeforeLast('.', fileName).ifBlank { "Untitled" }
    }

    private fun metadataField(newValue: String?, oldValue: String?, fallback: String?): String? {
        val normalizedNew = newValue?.trim().orEmpty()
        if (normalizedNew.isNotEmpty()) {
            return normalizedNew
        }
        val normalizedOld = oldValue?.trim().orEmpty()
        if (normalizedOld.isNotEmpty()) {
            return normalizedOld
        }
        return fallback
    }

    private fun encodeCapabilities(capabilities: DocumentCapabilities?): String? {
        capabilities ?: return null
        return JSONObject()
            .put("reflowable", capabilities.reflowable)
            .put("fixedLayout", capabilities.fixedLayout)
            .put("outline", capabilities.outline)
            .put("search", capabilities.search)
            .put("textExtraction", capabilities.textExtraction)
            .put("annotations", capabilities.annotations)
            .put("links", capabilities.links)
            .toString()
    }

    private fun resolveCoverPath(
        book: BookEntity,
        probe: BookProbeResult,
        file: File,
        force: Boolean
    ): String? {
        val existing = book.coverPath?.takeIf { path -> path.isNotBlank() && File(path).exists() }
        if (existing != null && !force) {
            return existing
        }

        val coverFile = storage.coverFile(book.bookId)
        val title = book.title ?: fallbackTitle(book.fileName)

        val extracted = when (probe.format) {
            BookFormat.EPUB -> {
                val coverPathInZip = probe.metadata?.extra?.get("coverPath")
                coverPathInZip?.let { pathInZip ->
                    EpubZipEnricher.tryExtractCoverToPng(
                        file = file,
                        coverPathInZip = pathInZip,
                        outFile = coverFile,
                        reqWidth = 720,
                        reqHeight = 960
                    )
                } ?: false
            }

            BookFormat.PDF,
            BookFormat.TXT -> false
        }

        if (!extracted) {
            val placeholder = CoverRenderer.placeholderBitmap(720, 960, title)
            BitmapIO.savePng(coverFile, placeholder)
        }

        return coverFile.absolutePath
    }
}
```

## engines/txt/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.library")
}

android {
    namespace = "com.ireader.engines.txt"
}

dependencies {
    implementation(project(":core:reader:api"))
    implementation(project(":engines:engine-common"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16")
}
```

## engines/txt/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/TxtEngine.kt

```kotlin
package com.ireader.engines.txt

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.engines.txt.internal.open.TxtCharsetDetector
import com.ireader.engines.txt.internal.open.TxtDocument
import com.ireader.engines.txt.internal.util.toReaderError
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TxtEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    config: TxtEngineConfig = TxtEngineConfig()
) : ReaderEngine {
    private val config: TxtEngineConfig = config.normalized()

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        try {
            val charset = TxtCharsetDetector.detect(source, options.textEncoding, ioDispatcher)
            ReaderResult.Ok(
                TxtDocument(
                    source = source,
                    openOptions = options,
                    charset = charset,
                    ioDispatcher = ioDispatcher,
                    config = config
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/TxtEngineConfig.kt

```kotlin
package com.ireader.engines.txt

import java.io.File

data class TxtEngineConfig(
    val cacheDir: File? = null,
    val persistPagination: Boolean = true,
    val persistOutline: Boolean = false,
    val paginationWriteEveryNewStarts: Int = 12,
    val persistLastPosition: Boolean = true,
    val lastPositionMinIntervalMs: Long = 1500L,
    val outlineAsTree: Boolean = true,

    // Storage / memory
    val inMemoryThresholdBytes: Long = 20L * 1024L * 1024L,
    val indexedWindowCacheChars: Int = 128 * 1024,

    // Pagination / rendering
    val chunkSizeChars: Int = 32 * 1024,
    val pageCacheSize: Int = 24,
    val prefetchAhead: Int = 6,
    val prefetchBehind: Int = 2,

    // Locator / relocation
    val snippetLength: Int = 48,
    val locatorSampleStrideChars: Int = 32 * 1024,
    val locatorSampleWindowChars: Int = 512,
    val locatorMaxSamples: Int = 512,
    val locatorSmallDocumentFullScanThresholdChars: Int = 600_000,
    val locatorSnippetWindowMinChars: Int = 4_096,
    val locatorSnippetWindowMaxChars: Int = 256_000,
    val locatorSnippetWindowCapChars: Int = 1_000_000,

    // Provider defaults
    val maxSearchHitsDefault: Int = 500,
    val maxTextExtractChars: Int = 200_000
) {
    fun normalized(): TxtEngineConfig {
        val safeSnippetLength = snippetLength.coerceIn(24, 256)
        val safeMinWindow = locatorSnippetWindowMinChars.coerceIn(512, 128 * 1024)
        val safeMaxWindow = locatorSnippetWindowMaxChars
            .coerceAtLeast(safeMinWindow)
            .coerceIn(2 * 1024, 2 * 1024 * 1024)
        val safeCapWindow = locatorSnippetWindowCapChars
            .coerceAtLeast(safeMaxWindow)
            .coerceIn(8 * 1024, 8 * 1024 * 1024)

        return copy(
            paginationWriteEveryNewStarts = paginationWriteEveryNewStarts.coerceAtLeast(1),
            lastPositionMinIntervalMs = lastPositionMinIntervalMs.coerceAtLeast(0L),
            inMemoryThresholdBytes = inMemoryThresholdBytes.coerceAtLeast(1L * 1024L * 1024L),
            indexedWindowCacheChars = indexedWindowCacheChars.coerceIn(8 * 1024, 512 * 1024),
            chunkSizeChars = chunkSizeChars.coerceIn(2_048, 256 * 1024),
            pageCacheSize = pageCacheSize.coerceIn(4, 128),
            prefetchAhead = prefetchAhead.coerceIn(0, 8),
            prefetchBehind = prefetchBehind.coerceIn(0, 4),
            snippetLength = safeSnippetLength,
            locatorSampleStrideChars = locatorSampleStrideChars
                .coerceAtLeast(safeSnippetLength * 4)
                .coerceAtMost(2 * 1024 * 1024),
            locatorSampleWindowChars = locatorSampleWindowChars
                .coerceAtLeast(safeSnippetLength * 2)
                .coerceIn(128, 8 * 1024),
            locatorMaxSamples = locatorMaxSamples.coerceIn(16, 4_096),
            locatorSmallDocumentFullScanThresholdChars = locatorSmallDocumentFullScanThresholdChars.coerceIn(
                64 * 1024,
                8 * 1024 * 1024
            ),
            locatorSnippetWindowMinChars = safeMinWindow,
            locatorSnippetWindowMaxChars = safeMaxWindow,
            locatorSnippetWindowCapChars = safeCapWindow,
            maxSearchHitsDefault = maxSearchHitsDefault.coerceIn(1, 5_000),
            maxTextExtractChars = maxTextExtractChars.coerceIn(1_024, 2_000_000)
        )
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/LocatorConfig.kt

```kotlin
package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.TxtEngineConfig

internal data class LocatorConfig(
    val snippetLength: Int = 48,
    val sampleStrideChars: Int = 32 * 1024,
    val sampleWindowChars: Int = 512,
    val maxSamples: Int = 512,
    val smallDocumentFullScanThresholdChars: Int = 600_000,
    val snippetWindowMinChars: Int = 4_096,
    val snippetWindowMaxChars: Int = 256_000,
    val snippetWindowCapChars: Int = 1_000_000
)

internal fun TxtEngineConfig.toLocatorConfig(): LocatorConfig {
    return LocatorConfig(
        snippetLength = snippetLength,
        sampleStrideChars = locatorSampleStrideChars,
        sampleWindowChars = locatorSampleWindowChars,
        maxSamples = locatorMaxSamples,
        smallDocumentFullScanThresholdChars = locatorSmallDocumentFullScanThresholdChars,
        snippetWindowMinChars = locatorSnippetWindowMinChars,
        snippetWindowMaxChars = locatorSnippetWindowMaxChars,
        snippetWindowCapChars = locatorSnippetWindowCapChars
    )
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtController.kt

```kotlin
package com.ireader.engines.txt.internal.controller

import android.os.SystemClock
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.paging.PageSlice
import com.ireader.engines.txt.internal.paging.PageStartsIndex
import com.ireader.engines.txt.internal.paging.RenderKey
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPager
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.internal.util.toReaderError
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.Progression
import kotlin.math.min
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal class TxtController(
    private val store: TxtTextStore,
    private val pager: TxtPager,
    private val ioDispatcher: CoroutineDispatcher,
    private val annotations: AnnotationProvider?,
    private val paginationStore: TxtPaginationStore,
    private val lastPositionStore: TxtLastPositionStore,
    private val explicitInitial: Boolean,
    private val documentId: DocumentId,
    initialStartChar: Int = 0,
    private val locatorMapper: TxtLocatorMapper,
    private val engineConfig: TxtEngineConfig
) : ReaderController {
    private companion object {
        private const val PROGRESSIVE_BATCH_PAGES = 12
    }

    private data class RenderSnapshot(
        val revision: Long,
        val startChar: Int,
        val constraints: LayoutConstraints,
        val config: RenderConfig.ReflowText,
        val renderKey: RenderKey
    )

    private data class InflightKey(
        val renderKey: RenderKey,
        val startChar: Int
    )

    private data class CommitResult(
        val page: RenderPage,
        val locator: Locator
    )

    private data class CloseState(
        val key: RenderKey?,
        val start: Int,
        val total: Int,
        val starts: PageStartsIndex?
    )

    private val mutex = Mutex()
    private val pageCache = TxtPageSliceCache(maxPages = engineConfig.pageCacheSize)
    private val navigationHistory = TxtNavigationHistory()
    private val persistenceQueue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    private val inflightSlices = mutableMapOf<InflightKey, Deferred<PageSlice>>()

    private var constraints: LayoutConstraints? = null
    private var config: RenderConfig.ReflowText = RenderConfig.ReflowText()

    private var currentStartChar: Int = initialStartChar.coerceAtLeast(0)
    private var currentSlice: PageSlice? = null
    private var totalCharsCache: Int = -1

    private var renderKey: RenderKey? = null
    private var pageStarts: PageStartsIndex? = null
    private var restoredForKey: RenderKey? = null
    private var charsPerPageEstimate: Int = 0
    private var lastSavedStartChar: Int = -1
    private var lastSavedAtMs: Long = 0L
    private var stateRevision: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private var progressiveJob: Job? = null
    private var progressiveKey: RenderKey? = null
    private var progressiveFrontierStart: Int = 0
    private var knownPageCount: Int? = null
    private val persistenceWorker = persistenceScope.launch {
        for (task in persistenceQueue) {
            runCatching { task() }
        }
    }

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 64)
    override val events: Flow<ReaderEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(
        RenderState(
            locator = locatorMapper.locatorForBoundaryOffset(currentStartChar, (currentStartChar + 1).coerceAtLeast(1)),
            progression = Progression(percent = 0.0, label = "0%"),
            nav = NavigationAvailability(canGoPrev = false, canGoNext = false),
            titleInView = null,
            config = config
        )
    )
    override val state: StateFlow<RenderState> = _state

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        val restoreKey = mutex.withLock {
            this.constraints = constraints
            clearTransientStateLocked()

            val total = totalCharsCache
            currentStartChar = if (total <= 0) {
                0
            } else {
                currentStartChar.coerceIn(0, total - 1)
            }

            ensureRenderBucketLocked()
            val key = renderKey
            val starts = pageStarts
            if (!explicitInitial && key != null && starts != null && restoredForKey != key) {
                restoredForKey = key
                key
            } else {
                null
            }
        }

        if (restoreKey != null) {
            val total = totalChars()
            val restoredLocator = lastPositionStore.load(restoreKey)
            if (restoredLocator != null && total > 0) {
                mutex.withLock {
                    if (renderKey == restoreKey && this.constraints != null) {
                        currentStartChar = locatorMapper.offsetForLocator(restoredLocator, total)
                            .coerceIn(0, total - 1)
                        pageStarts?.seedIfEmpty(0, currentStartChar)
                        pageStarts?.addStart(currentStartChar)
                        currentSlice = null
                        bumpRevisionLocked()
                    }
                }
            }
        }

        return ReaderResult.Ok(Unit)
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        return mutex.withLock {
            this.config = when (config) {
                is RenderConfig.ReflowText -> config
                else -> RenderConfig.ReflowText()
            }
            clearTransientStateLocked()
            _state.value = _state.value.copy(config = this.config)
            ensureRenderBucketLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        repeat(2) {
            val snapshot = mutex.withLock {
                createSnapshotLocked()
            } ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

            try {
                val rendered = renderFromSnapshot(snapshot, policy)
                if (rendered != null) {
                    return ReaderResult.Ok(rendered)
                }
            } catch (t: Throwable) {
                _events.tryEmit(ReaderEvent.Error(t))
                return ReaderResult.Err(t.toReaderError())
            }
        }

        return ReaderResult.Err(ReaderError.Internal("Render conflicted with newer state"))
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val slice = ensureCurrentSlice(snapshot, policy.allowCache)
            ?: return ReaderResult.Err(ReaderError.Internal("Failed to render current page"))

        val total = totalChars()
        if (slice.endChar >= total) {
            return render(policy)
        }

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) return@withLock
            setCurrentStartLocked(slice.endChar, pushHistory = true)
        }

        return render(policy)
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val hasHistoryOrProgress = mutex.withLock {
            snapshotMatchesLocked(snapshot, requireStartMatch = true) &&
                (currentStartChar > 0 || !navigationHistory.isEmpty())
        }
        if (!hasHistoryOrProgress) {
            return render(policy)
        }

        val historyStart = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
                null
            } else {
                navigationHistory.popOrNull()
            }
        }

        val destination = historyStart ?: computePrevStart(snapshot)

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destination, pushHistory = false)
        }

        return render(policy)
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        return when (locator.scheme) {
            LocatorSchemes.TXT_OFFSET -> goToTxtOffset(locator, policy)
            LocatorSchemes.REFLOW_PAGE -> goToReflowPage(locator, policy)
            else -> ReaderResult.Err(ReaderError.Internal("Unsupported locator: ${locator.scheme}"))
        }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val total = totalChars()
        if (total <= 0) {
            mutex.withLock {
                setCurrentStartLocked(0, pushHistory = false)
            }
            return render(policy)
        }

        val safePercent = percent.coerceIn(0.0, 1.0)
        val target = ((total - 1) * safePercent).toInt().coerceIn(0, total - 1)
        val destinationStart = findReasonablePageStart(snapshot, target)

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destinationStart, pushHistory = true)
        }

        return render(policy)
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) return ReaderResult.Ok(Unit)

        val snapshot = mutex.withLock { createSnapshotLocked() } ?: return ReaderResult.Ok(Unit)
        val current = ensureCurrentSlice(snapshot, allowCache = true) ?: return ReaderResult.Ok(Unit)
        val total = totalChars()

        val behindCount = min(count, engineConfig.prefetchBehind.coerceAtLeast(0))
        val behindStarts = mutableListOf<Int>()
        mutex.withLock {
            val starts = pageStarts
            if (starts != null) {
                var probe = current.startChar - 1
                repeat(behindCount) {
                    if (probe < 0) return@repeat
                    val prevStart = starts.floor(probe) ?: return@repeat
                    if (prevStart >= current.startChar) return@repeat
                    behindStarts.add(prevStart)
                    probe = prevStart - 1
                }
            }
        }

        val semaphore = Semaphore(permits = 2)
        coroutineScope {
            val jobs = ArrayList<Deferred<Unit>>(behindStarts.size + 1)

            jobs += async {
                var nextStart = current.endChar
                repeat(count) {
                    if (nextStart >= total) return@async
                    val nextSlice = semaphore.withPermit {
                        getOrBuildSlice(
                            startChar = nextStart,
                            constraints = snapshot.constraints,
                            config = snapshot.config,
                            renderKey = snapshot.renderKey,
                            allowCache = true
                        ).first
                    }
                    if (nextSlice.endChar <= nextStart) return@async
                    nextStart = nextSlice.endChar
                }
            }

            for (start in behindStarts) {
                jobs += async<Unit> {
                    semaphore.withPermit {
                        getOrBuildSlice(
                            startChar = start,
                            constraints = snapshot.constraints,
                            config = snapshot.config,
                            renderKey = snapshot.renderKey,
                            allowCache = true
                        )
                    }
                    Unit
                }
            }

            jobs.awaitAll()
        }

        return ReaderResult.Ok(Unit)
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            clearTransientStateLocked()
            ReaderResult.Ok(Unit)
        }
    }

    override fun close() {
        prefetchJob?.cancel()
        progressiveJob?.cancel()
        runBlocking {
            val closeState = mutex.withLock {
                cancelProgressiveLocked(resetState = false)
                cancelInflightLocked()
                val key = renderKey
                val start = currentSlice?.startChar ?: currentStartChar
                val total = if (totalCharsCache > 0) totalCharsCache else (start + 1)
                val starts = pageStarts
                CloseState(key = key, start = start, total = total, starts = starts)
            }

            val key = closeState.key
            if (key != null) {
                val start = closeState.start
                val total = closeState.total
                val starts = closeState.starts
                val locator = locatorMapper.locatorForOffsetFast(start, total.coerceAtLeast(1))
                runCatching {
                    persistenceQueue.send {
                        lastPositionStore.save(key, locator)
                        starts?.let { paginationStore.flush(key, it) }
                    }
                }
            }

            persistenceQueue.close()
            persistenceWorker.join()
        }

        persistenceScope.cancel()
        scope.cancel()
    }

    private suspend fun goToTxtOffset(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val total = totalChars()
        val c = mutex.withLock { constraints }
        if (c == null) {
            val offset = locatorMapper.offsetForLocator(locator, total).coerceAtLeast(0)
            mutex.withLock {
                setCurrentStartLocked(offset, pushHistory = false)
            }
            return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))
        }

        if (total <= 0) {
            mutex.withLock {
                setCurrentStartLocked(0, pushHistory = false)
            }
            return render(policy)
        }

        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val target = locatorMapper.offsetForLocator(locator, total)
        val clampedTarget = target.coerceIn(0, total - 1)
        val destinationStart = findReasonablePageStart(snapshot, clampedTarget)

        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destinationStart, pushHistory = true)
        }

        return render(policy)
    }

    private suspend fun goToReflowPage(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val snapshot = mutex.withLock { createSnapshotLocked() }
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val pageIndex = parsePageIndex(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid reflow page locator"))

        val destination = findPageStartForIndex(snapshot, pageIndex)
        mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) return@withLock
            setCurrentStartLocked(destination, pushHistory = true)
        }

        return render(policy)
    }

    private suspend fun renderFromSnapshot(
        snapshot: RenderSnapshot,
        policy: RenderPolicy
    ): RenderPage? {
        val startMs = SystemClock.elapsedRealtime()
        val (slice, cacheHit) = getOrBuildSlice(
            startChar = snapshot.startChar,
            constraints = snapshot.constraints,
            config = snapshot.config,
            renderKey = snapshot.renderKey,
            allowCache = policy.allowCache
        )

        val total = totalChars()
        val safeTotal = total.coerceAtLeast(1)
        val baseLocator = locatorMapper.locatorForOffsetFast(slice.startChar, safeTotal)
        val endLocator = locatorMapper.locatorForBoundaryOffset(slice.endChar, safeTotal)
        val decorations = loadDecorations(baseLocator, endLocator)

        val committed = mutex.withLock {
            commitRenderLocked(
                snapshot = snapshot,
                slice = slice,
                totalChars = total,
                locator = baseLocator,
                decorations = decorations,
                cacheHit = cacheHit,
                startMs = startMs
            )
        } ?: return null

        _events.tryEmit(ReaderEvent.PageChanged(committed.locator))
        _events.tryEmit(ReaderEvent.Rendered(committed.page.id, committed.page.metrics))
        schedulePrefetch(policy.prefetchNeighbors)
        scheduleProgressivePagination()
        return committed.page
    }

    private suspend fun ensureCurrentSlice(
        snapshot: RenderSnapshot,
        allowCache: Boolean
    ): PageSlice? {
        val existing = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
                null
            } else {
                currentSlice?.takeIf { it.startChar == snapshot.startChar }
            }
        }
        if (existing != null) return existing

        val (slice, _) = getOrBuildSlice(
            startChar = snapshot.startChar,
            constraints = snapshot.constraints,
            config = snapshot.config,
            renderKey = snapshot.renderKey,
            allowCache = allowCache
        )

        mutex.withLock {
            if (snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
                currentSlice = slice
            }
        }

        return slice
    }

    private suspend fun getOrBuildSlice(
        startChar: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        renderKey: RenderKey,
        allowCache: Boolean
    ): Pair<PageSlice, Boolean> {
        if (allowCache) {
            val cached = mutex.withLock { pageCache.get(startChar, renderKey) }
            if (cached != null) return cached to true
        }

        val inflightKey = InflightKey(renderKey = renderKey, startChar = startChar)
        val deferred = mutex.withLock {
            inflightSlices[inflightKey] ?: scope.async(start = CoroutineStart.LAZY) {
                pager.pageAt(startChar, constraints, config)
            }.also { created ->
                inflightSlices[inflightKey] = created
            }
        }

        val built = try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (inflightSlices[inflightKey] === deferred) {
                    inflightSlices.remove(inflightKey)
                }
            }
        }

        if (allowCache) {
            mutex.withLock {
                if (this.renderKey == renderKey) {
                    pageCache.put(built, renderKey)
                }
            }
        }

        return built to false
    }

    private suspend fun totalChars(): Int {
        val cached = totalCharsCache
        if (cached >= 0) return cached

        val loaded = store.totalChars().coerceAtLeast(0)
        return mutex.withLock {
            if (totalCharsCache < 0) {
                totalCharsCache = loaded
            }
            totalCharsCache.coerceAtLeast(0)
        }
    }

    private suspend fun findReasonablePageStart(
        snapshot: RenderSnapshot,
        targetChar: Int
    ): Int {
        val total = totalChars()
        if (total <= 0) return 0

        val target = targetChar.coerceIn(0, total - 1)
        var start = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                0
            } else {
                pageStarts?.floor(target) ?: 0
            }
        }.coerceIn(0, total - 1)

        repeat(32) {
            val (slice, _) = getOrBuildSlice(
                startChar = start,
                constraints = snapshot.constraints,
                config = snapshot.config,
                renderKey = snapshot.renderKey,
                allowCache = true
            )

            mutex.withLock {
                if (snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                    pageStarts?.addStart(slice.startChar)
                    if (slice.endChar in 1 until total) {
                        pageStarts?.addStart(slice.endChar)
                    }
                }
            }

            if (target < slice.endChar || slice.endChar >= total || slice.endChar <= start) {
                return slice.startChar
            }
            start = slice.endChar
        }

        return start
    }

    private suspend fun computePrevStart(snapshot: RenderSnapshot): Int {
        if (snapshot.startChar <= 0) return 0

        val known = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                null
            } else {
                pageStarts?.floor(snapshot.startChar - 1)
            }
        }
        if (known != null && known < snapshot.startChar) {
            return known
        }

        val total = totalChars()
        val estimate = pager.estimateCharsPerPage(snapshot.constraints, snapshot.config)
        var probe = (snapshot.startChar - (estimate * 2)).coerceAtLeast(0)
        var previousStart = 0

        repeat(24) {
            val (slice, _) = getOrBuildSlice(
                startChar = probe,
                constraints = snapshot.constraints,
                config = snapshot.config,
                renderKey = snapshot.renderKey,
                allowCache = true
            )

            mutex.withLock {
                if (snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                    pageStarts?.addStart(slice.startChar)
                    if (slice.endChar in 1 until total) {
                        pageStarts?.addStart(slice.endChar)
                    }
                }
            }

            if (slice.endChar >= snapshot.startChar || slice.endChar <= probe) {
                return previousStart.coerceAtLeast(0)
            }
            previousStart = slice.startChar
            probe = slice.endChar
        }

        return (snapshot.startChar - estimate).coerceAtLeast(0)
    }

    private suspend fun findPageStartForIndex(snapshot: RenderSnapshot, targetPageIndex: Int): Int {
        val safeTarget = targetPageIndex.coerceAtLeast(0)
        if (safeTarget == 0) return 0

        val known = mutex.withLock {
            if (!snapshotMatchesLocked(snapshot, requireStartMatch = false)) {
                Triple<Int?, Int?, Int?>(null, null, null)
            } else {
                val starts = pageStarts
                val knownCount = knownPageCount
                val boundedTarget = if (knownCount != null && knownCount > 0) {
                    safeTarget.coerceAtMost(knownCount - 1)
                } else {
                    safeTarget
                }
                val direct = starts?.getAtOrNull(boundedTarget)
                val lastKnown = if (knownCount != null && knownCount > 0) {
                    starts?.getAtOrNull(knownCount - 1)
                } else {
                    null
                }
                Triple(direct, lastKnown, knownCount)
            }
        }
        val directKnown = known.first
        if (directKnown != null) return directKnown
        val lastKnown = known.second
        val knownCount = known.third
        if (knownCount != null && lastKnown != null && safeTarget >= knownCount) {
            return lastKnown
        }

        val total = totalChars()
        if (total <= 0) return 0

        val estimate = pager.estimateCharsPerPage(snapshot.constraints, snapshot.config).coerceAtLeast(1)
        val estimatedTotalPages = ((total + estimate - 1) / estimate).coerceAtLeast(1)
        val percent = (safeTarget.toDouble() / estimatedTotalPages.toDouble()).coerceIn(0.0, 1.0)
        val targetChar = ((total - 1) * percent).toInt().coerceIn(0, total - 1)
        return findReasonablePageStart(snapshot, targetChar)
    }

    private suspend fun loadDecorations(locator: Locator, endLocator: Locator): List<com.ireader.reader.api.annotation.Decoration> {
        val provider = annotations ?: return emptyList()
        return when (
            val result = provider.decorationsFor(
                AnnotationQuery(
                    range = LocatorRange(
                        start = locator,
                        end = endLocator
                    )
                )
            )
        ) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> emptyList()
        }
    }

    private fun commitRenderLocked(
        snapshot: RenderSnapshot,
        slice: PageSlice,
        totalChars: Int,
        locator: Locator,
        decorations: List<com.ireader.reader.api.annotation.Decoration>,
        cacheHit: Boolean,
        startMs: Long
    ): CommitResult? {
        if (!snapshotMatchesLocked(snapshot, requireStartMatch = true)) {
            return null
        }

        currentSlice = slice
        currentStartChar = slice.startChar

        val starts = pageStarts ?: return null
        val newAdds = recordPageBoundaries(starts, slice, totalChars)
        if (newAdds > 0) {
            renderKey?.let { key ->
                enqueuePersistence {
                    paginationStore.maybePersist(key, starts, newAdds)
                }
            }
        }

        val knownPages = knownPageCount?.coerceAtLeast(1)
        val estimate = charsPerPageEstimate.coerceAtLeast(1)
        val totalPagesEstimate = ((totalChars + estimate - 1) / estimate).coerceAtLeast(1)
        val pageCount = knownPages ?: totalPagesEstimate
        val currentPage = (starts.floorIndexOf(slice.startChar) + 1).coerceAtLeast(1)
        val safeCurrentPage = currentPage.coerceAtMost(pageCount)
        val pageIndex = (safeCurrentPage - 1).coerceAtLeast(0)
        val percent = if (totalChars <= 0) {
            0.0
        } else {
            (slice.startChar.toDouble() / totalChars.toDouble()).coerceIn(0.0, 1.0)
        }

        val enrichedLocator = locator.withPageExtras(
            pageIndex = pageIndex,
            pageCountEstimate = totalPagesEstimate,
            pageCount = pageCount,
            pageCountKnown = (knownPages != null)
        )

        val progression = Progression(
            percent = percent,
            label = "$safeCurrentPage/$pageCount"
        )
        val nav = NavigationAvailability(
            canGoPrev = slice.startChar > 0,
            canGoNext = slice.endChar < totalChars
        )

        val pageId = PageId("txt:${slice.startChar}")
        val metrics = RenderMetrics(
            renderTimeMs = (SystemClock.elapsedRealtime() - startMs),
            cacheHit = cacheHit
        )
        val page = RenderPage(
            id = pageId,
            locator = enrichedLocator,
            content = RenderContent.Text(
                text = slice.text,
                mapping = TxtTextMapping(
                    pageStartChar = slice.startChar,
                    pageEndCharExclusive = slice.endChar
                )
            ),
            links = emptyList(),
            decorations = decorations,
            metrics = metrics
        )

        _state.value = RenderState(
            locator = enrichedLocator,
            progression = progression,
            nav = nav,
            titleInView = null,
            config = config
        )

        renderKey?.let { key ->
            val now = SystemClock.elapsedRealtime()
            val start = slice.startChar
            if (start != lastSavedStartChar && (now - lastSavedAtMs) >= lastPositionStore.minIntervalMs) {
                lastSavedStartChar = start
                lastSavedAtMs = now
                enqueuePersistence { lastPositionStore.save(key, enrichedLocator) }
            }
        }

        return CommitResult(page = page, locator = enrichedLocator)
    }

    private fun Locator.withPageExtras(
        pageIndex: Int,
        pageCountEstimate: Int,
        pageCount: Int,
        pageCountKnown: Boolean
    ): Locator {
        val extras = LinkedHashMap(this.extras)
        extras["pageIndex"] = pageIndex.toString()
        extras["pageCountEstimate"] = pageCountEstimate.toString()
        extras["pageCount"] = pageCount.toString()
        extras["pageCountKnown"] = pageCountKnown.toString()
        return copy(extras = extras)
    }

    private fun parsePageIndex(locator: Locator): Int? {
        val raw = locator.extras["pageIndex"] ?: locator.value
        return raw.toIntOrNull()?.coerceAtLeast(0)
    }

    private fun createSnapshotLocked(): RenderSnapshot? {
        val c = constraints ?: return null
        ensureRenderBucketLocked()
        val key = renderKey ?: return null
        return RenderSnapshot(
            revision = stateRevision,
            startChar = currentStartChar,
            constraints = c,
            config = config,
            renderKey = key
        )
    }

    private fun snapshotMatchesLocked(snapshot: RenderSnapshot, requireStartMatch: Boolean): Boolean {
        if (stateRevision != snapshot.revision) return false
        if (renderKey != snapshot.renderKey) return false
        if (requireStartMatch && currentStartChar != snapshot.startChar) return false
        return true
    }

    private fun ensureRenderBucketLocked() {
        val c = constraints ?: return
        val key = RenderKey.of(
            docId = documentId.value,
            charset = store.charset.name(),
            constraints = c,
            config = config
        )
        if (renderKey != key || pageStarts == null) {
            if (progressiveKey != key) {
                progressiveJob?.cancel()
                progressiveJob = null
                progressiveFrontierStart = 0
                knownPageCount = null
                progressiveKey = key
            }
            renderKey = key
            pageStarts = paginationStore.getOrCreate(key)
        }
        pageStarts?.seedIfEmpty(0, currentStartChar)
        charsPerPageEstimate = pager.estimateCharsPerPage(c, config)
    }

    private fun clearTransientStateLocked() {
        pageCache.clear()
        currentSlice = null
        prefetchJob?.cancel()
        prefetchJob = null
        cancelProgressiveLocked()
        cancelInflightLocked()
        bumpRevisionLocked()
    }

    private fun cancelInflightLocked() {
        inflightSlices.values.forEach { it.cancel() }
        inflightSlices.clear()
    }

    private fun cancelProgressiveLocked(resetState: Boolean = true) {
        progressiveJob?.cancel()
        progressiveJob = null
        if (resetState) {
            progressiveKey = null
            progressiveFrontierStart = 0
            knownPageCount = null
        }
    }

    private fun schedulePrefetch(n: Int) {
        val count = (if (n > 0) n else engineConfig.prefetchAhead).coerceIn(0, 8)
        if (count <= 0) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            runCatching { prefetchNeighbors(count) }
        }
    }

    private suspend fun scheduleProgressivePagination() {
        val key = mutex.withLock {
            val currentKey = renderKey ?: return@withLock null
            if (constraints == null) return@withLock null
            if (knownPageCount != null) return@withLock null

            if (progressiveKey != currentKey) {
                progressiveJob?.cancel()
                progressiveJob = null
                progressiveFrontierStart = 0
                knownPageCount = null
                progressiveKey = currentKey
            }

            if (progressiveJob?.isActive == true) {
                return@withLock null
            }
            currentKey
        } ?: return

        val job = scope.launch {
            runCatching { runProgressivePagination(key) }
        }

        mutex.withLock {
            if (renderKey == key && progressiveKey == key && knownPageCount == null) {
                progressiveJob = job
            } else {
                job.cancel()
            }
        }
    }

    private suspend fun runProgressivePagination(key: RenderKey) {
        val runningJob = coroutineContext[Job]
        try {
            while (true) {
                val seed = mutex.withLock {
                    if (renderKey != key || knownPageCount != null) return@withLock null
                    val currentConstraints = constraints ?: return@withLock null
                    ProgressiveSeed(
                        startChar = progressiveFrontierStart.coerceAtLeast(0),
                        constraints = currentConstraints,
                        config = config
                    )
                } ?: return

                val total = totalChars()
                if (total <= 0) {
                    mutex.withLock {
                        if (renderKey == key) {
                            knownPageCount = pageStarts?.size?.coerceAtLeast(1) ?: 1
                        }
                    }
                    return
                }

                var start = seed.startChar.coerceIn(0, total)
                var madeProgress = false

                repeat(PROGRESSIVE_BATCH_PAGES) {
                    if (start >= total) return@repeat

                    val slice = getOrBuildSlice(
                        startChar = start,
                        constraints = seed.constraints,
                        config = seed.config,
                        renderKey = key,
                        allowCache = true
                    ).first

                    val next = slice.endChar.coerceIn(0, total)
                    val shouldStop = mutex.withLock {
                        if (renderKey != key) {
                            true
                        } else {
                            val starts = pageStarts ?: return@withLock true
                            val newAdds = recordPageBoundaries(starts, slice, total)
                            if (newAdds > 0) {
                                enqueuePersistence {
                                    paginationStore.maybePersist(key, starts, newAdds)
                                }
                            }
                            progressiveFrontierStart = next
                            if (next >= total || next <= start) {
                                knownPageCount = starts.size.coerceAtLeast(1)
                                true
                            } else {
                                false
                            }
                        }
                    }

                    if (shouldStop) return
                    madeProgress = true
                    start = next
                }

                if (!madeProgress) {
                    mutex.withLock {
                        if (renderKey == key) {
                            knownPageCount = pageStarts?.size?.coerceAtLeast(1)
                        }
                    }
                    return
                }

                yield()
            }
        } finally {
            mutex.withLock {
                if (progressiveJob === runningJob) {
                    progressiveJob = null
                }
            }
        }
    }

    private data class ProgressiveSeed(
        val startChar: Int,
        val constraints: LayoutConstraints,
        val config: RenderConfig.ReflowText
    )

    private fun setCurrentStartLocked(destinationStart: Int, pushHistory: Boolean) {
        val safeDestination = destinationStart.coerceAtLeast(0)
        if (safeDestination == currentStartChar) return
        if (pushHistory) {
            navigationHistory.push(currentStartChar)
        }
        currentStartChar = safeDestination
        currentSlice = null
        bumpRevisionLocked()
    }

    private fun recordPageBoundaries(
        starts: PageStartsIndex,
        slice: PageSlice,
        totalChars: Int
    ): Int {
        var additions = 0
        if (starts.addStart(slice.startChar)) additions += 1
        if (slice.endChar in 1 until totalChars && starts.addStart(slice.endChar)) {
            additions += 1
        }
        return additions
    }

    private fun enqueuePersistence(task: suspend () -> Unit) {
        persistenceQueue.trySend(task)
    }

    private fun bumpRevisionLocked() {
        stateRevision += 1
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtLocatorMapper.kt

```kotlin
package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object TxtLocatorExtras {
    const val VERSION = "v"
    const val PROGRESSION = "progression"
    const val SNIPPET = "snippet"
    const val SNIPPET_HASH = "snippetHash"
    const val VERSION_VALUE = "2"
}

internal class TxtLocatorMapper(
    private val store: TxtTextStore,
    private val config: LocatorConfig = LocatorConfig()
) {
    private companion object {
        private val SEARCH_WINDOW_SCALES = intArrayOf(1, 2, 4)
        private const val HEX_DIGITS = "0123456789abcdef"
    }

    private data class SnippetSample(
        val offset: Int,
        val text: String
    )

    private data class SparseSnippetIndex(
        val totalChars: Int,
        val samples: List<SnippetSample>
    )

    @Volatile
    private var sparseIndex: SparseSnippetIndex? = null

    fun locatorForOffsetFast(offset: Int, totalChars: Int): Locator {
        if (totalChars <= 0) return emptyLocator()

        val clamped = offset.coerceIn(0, totalChars - 1)
        val progression = clamped.toDouble() / totalChars.toDouble()
        return locatorWithProgression(clamped.toString(), progression)
    }

    fun locatorForBoundaryOffset(offset: Int, totalChars: Int): Locator {
        if (totalChars <= 0) return emptyLocator()

        val clamped = offset.coerceIn(0, totalChars)
        val progression = clamped.toDouble() / totalChars.toDouble()
        return locatorWithProgression(clamped.toString(), progression)
    }

    private fun locatorWithProgression(value: String, progression: Double): Locator {
        return Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = value,
            extras = mapOf(
                TxtLocatorExtras.VERSION to TxtLocatorExtras.VERSION_VALUE,
                TxtLocatorExtras.PROGRESSION to String.format(Locale.US, "%.6f", progression)
            )
        )
    }

    suspend fun locatorForOffset(offset: Int, totalChars: Int): Locator {
        if (totalChars <= 0) {
            return locatorForOffsetFast(0, 0)
        }

        val clamped = offset.coerceIn(0, totalChars - 1)
        val base = locatorForOffsetFast(clamped, totalChars)
        val snippet = readSnippet(clamped, totalChars)
        val extras = buildMap<String, String> {
            putAll(base.extras)
            if (snippet.isNotBlank()) {
                put(TxtLocatorExtras.SNIPPET, snippet)
                put(TxtLocatorExtras.SNIPPET_HASH, hashSnippet(snippet))
            }
        }

        return base.copy(extras = extras)
    }

    suspend fun offsetForLocator(locator: Locator?, totalChars: Int): Int {
        if (totalChars <= 0) return 0
        if (locator == null || locator.scheme != LocatorSchemes.TXT_OFFSET) return 0

        val parsed = parseOffset(locator.value, totalChars)

        val snippet = locator.extras[TxtLocatorExtras.SNIPPET]?.takeIf { it.isNotBlank() }
        if (snippet != null) {
            resolveBySnippet(parsed, snippet, totalChars)?.let { return it }
        }

        val progression = locator.extras[TxtLocatorExtras.PROGRESSION]?.toDoubleOrNull()
        if (progression != null && progression.isFinite()) {
            val target = (progression.coerceIn(0.0, 1.0) * totalChars).toInt()
            return target.coerceIn(0, totalChars - 1)
        }

        return parsed
    }

    private suspend fun readSnippet(offset: Int, totalChars: Int): String {
        val half = (config.snippetLength / 2).coerceAtLeast(12)
        val start = max(0, offset - half)
        val end = min(totalChars, start + config.snippetLength.coerceAtLeast(24))
        if (end <= start) return ""
        return store.readRange(start, end)
    }

    private suspend fun resolveBySnippet(
        fallbackOffset: Int,
        snippet: String,
        totalChars: Int
    ): Int? {
        val baseWindow = (snippet.length * 1024).coerceIn(
            config.snippetWindowMinChars,
            config.snippetWindowMaxChars
        )
        for (scale in SEARCH_WINDOW_SCALES) {
            val size = (baseWindow * scale).coerceAtMost(config.snippetWindowCapChars)
            val windowStart = (fallbackOffset - size / 2).coerceAtLeast(0)
            val windowEnd = (windowStart + size).coerceAtMost(totalChars)
            if (windowEnd <= windowStart) continue
            val segment = store.readRange(windowStart, windowEnd)
            val nearest = nearestMatch(segment, snippet, fallbackOffset - windowStart)
            if (nearest >= 0) return windowStart + nearest
        }

        searchSparseSamples(snippet, fallbackOffset, totalChars)?.let { return it }

        if (totalChars <= config.smallDocumentFullScanThresholdChars) {
            val full = store.readRange(0, totalChars)
            val nearest = nearestMatch(full, snippet, fallbackOffset)
            if (nearest >= 0) return nearest
        }
        return null
    }

    private suspend fun searchSparseSamples(
        snippet: String,
        fallbackOffset: Int,
        totalChars: Int
    ): Int? {
        val index = ensureSparseIndex(totalChars)
        if (index.samples.isEmpty()) return null

        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        for (sample in index.samples) {
            val local = sample.text.indexOf(snippet)
            if (local < 0) continue
            val candidate = (sample.offset + local).coerceIn(0, totalChars - 1)
            val distance = abs(candidate - fallbackOffset)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    private suspend fun ensureSparseIndex(totalChars: Int): SparseSnippetIndex {
        val cached = sparseIndex
        if (cached != null && cached.totalChars == totalChars) return cached

        val stride = config.sampleStrideChars
            .coerceAtLeast(max(config.snippetLength * 4, 2_048))
            .coerceAtMost(max(totalChars, 2_048))
        val windowChars = config.sampleWindowChars
            .coerceAtLeast(config.snippetLength * 2)
            .coerceIn(128, 4096)
        val limit = config.maxSamples.coerceAtLeast(16)

        val samples = ArrayList<SnippetSample>(min(limit, (totalChars / stride) + 2))
        var offset = 0
        while (offset < totalChars && samples.size < limit) {
            val end = (offset + windowChars).coerceAtMost(totalChars)
            if (end > offset) {
                val text = store.readRange(offset, end)
                if (text.isNotEmpty()) {
                    samples.add(SnippetSample(offset = offset, text = text))
                }
            }
            offset += stride
        }
        if (samples.isEmpty() && totalChars > 0) {
            val end = min(totalChars, windowChars)
            val text = store.readRange(0, end)
            if (text.isNotEmpty()) {
                samples.add(SnippetSample(offset = 0, text = text))
            }
        }

        return SparseSnippetIndex(
            totalChars = totalChars,
            samples = samples
        ).also { sparseIndex = it }
    }

    private fun nearestMatch(haystack: String, needle: String, pivot: Int): Int {
        if (needle.isEmpty() || haystack.length < needle.length) return -1
        var idx = haystack.indexOf(needle)
        var best = -1
        var bestDistance = Int.MAX_VALUE
        while (idx >= 0) {
            val distance = abs(idx - pivot)
            if (distance < bestDistance) {
                bestDistance = distance
                best = idx
            }
            idx = haystack.indexOf(needle, idx + 1)
        }
        return best
    }

    private fun hashSnippet(snippet: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(snippet.toByteArray(Charsets.UTF_8))
        return buildString(16) {
            for (i in 0 until 8) {
                val b = digest[i].toInt() and 0xFF
                append(HEX_DIGITS[b ushr 4])
                append(HEX_DIGITS[b and 0x0F])
            }
        }
    }

    private fun emptyLocator(): Locator {
        return locatorWithProgression(value = "0", progression = 0.0)
    }

    private fun parseOffset(raw: String, totalChars: Int): Int {
        return raw.toIntOrNull()?.coerceIn(0, totalChars - 1) ?: 0
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtNavigationHistory.kt

```kotlin
package com.ireader.engines.txt.internal.controller

internal class TxtNavigationHistory(
    private val maxSize: Int = 128
) {
    private val entries = ArrayDeque<Int>()

    fun isEmpty(): Boolean = entries.isEmpty()

    fun push(startChar: Int) {
        val safeStart = startChar.coerceAtLeast(0)
        if (entries.isNotEmpty() && entries.last() == safeStart) return
        if (entries.size >= maxSize.coerceAtLeast(1)) {
            entries.removeFirst()
        }
        entries.addLast(safeStart)
    }

    fun popOrNull(): Int? {
        if (entries.isEmpty()) return null
        return entries.removeLast()
    }

    fun clear() {
        entries.clear()
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtPageSliceCache.kt

```kotlin
package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.internal.paging.PageSlice
import com.ireader.engines.txt.internal.paging.PaginationCache
import com.ireader.engines.txt.internal.paging.RenderKey

internal class TxtPageSliceCache(
    maxPages: Int
) {
    private val cache = PaginationCache(maxPages = maxPages.coerceAtLeast(4))

    fun clear() {
        cache.clear()
    }

    fun get(startChar: Int, renderKey: RenderKey?): PageSlice? {
        return cache.get(startChar, namespace(renderKey))
    }

    fun put(slice: PageSlice, renderKey: RenderKey?) {
        cache.put(slice, namespace(renderKey))
    }

    private fun namespace(renderKey: RenderKey?): String {
        return renderKey?.toString() ?: "pending"
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/controller/TxtTextMapping.kt

```kotlin
package com.ireader.engines.txt.internal.controller

import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes

internal class TxtTextMapping(
    private val pageStartChar: Int,
    private val pageEndCharExclusive: Int
) : TextMapping {

    override fun locatorAt(charIndex: Int): Locator {
        val globalOffset = (pageStartChar + charIndex).coerceAtLeast(0)
        return Locator(LocatorSchemes.TXT_OFFSET, globalOffset.toString())
    }

    override fun rangeFor(startChar: Int, endChar: Int): LocatorRange {
        return LocatorRange(
            start = locatorAt(startChar),
            end = locatorAt(endChar)
        )
    }

    override fun charRangeFor(range: LocatorRange): IntRange? {
        if (range.start.scheme != LocatorSchemes.TXT_OFFSET || range.end.scheme != LocatorSchemes.TXT_OFFSET) {
            return null
        }
        val start = range.start.value.toIntOrNull() ?: return null
        val end = range.end.value.toIntOrNull() ?: return null
        if (start < pageStartChar || end > pageEndCharExclusive) return null
        val localStart = start - pageStartChar
        val localEnd = end - pageStartChar
        if (localEnd < localStart) return null
        return localStart until localEnd
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtCharsetDetector.kt

```kotlin
package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal object TxtCharsetDetector {

    private const val PROBE_BYTES = 128 * 1024
    private const val MAX_REPLACEMENT_RATIO = 0.01
    private val fallbackCharsets = listOf("GB18030", "GBK", "ISO-8859-1")

    suspend fun detect(
        source: DocumentSource,
        overrideName: String?,
        ioDispatcher: CoroutineDispatcher
    ): Charset = withContext(ioDispatcher) {
        if (!overrideName.isNullOrBlank()) {
            return@withContext Charset.forName(overrideName)
        }

        val probe = readProbeBytes(source)
        val header = probe.copyOf(minOf(probe.size, 4))

        // UTF-8 BOM: EF BB BF
        if (
            header.size >= 3 &&
            header[0] == 0xEF.toByte() &&
            header[1] == 0xBB.toByte() &&
            header[2] == 0xBF.toByte()
        ) {
            return@withContext Charsets.UTF_8
        }

        // UTF-16 LE BOM: FF FE
        if (
            header.size >= 2 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xFE.toByte()
        ) {
            return@withContext Charsets.UTF_16LE
        }

        // UTF-16 BE BOM: FE FF
        if (
            header.size >= 2 &&
            header[0] == 0xFE.toByte() &&
            header[1] == 0xFF.toByte()
        ) {
            return@withContext Charsets.UTF_16BE
        }

        if (looksLikeUtf8(probe)) {
            return@withContext Charsets.UTF_8
        }

        selectBestFallback(probe) ?: Charsets.UTF_8
    }

    private suspend fun readProbeBytes(source: DocumentSource): ByteArray {
        return source.openInputStream().use { input ->
            val out = ByteArray(PROBE_BYTES)
            val read = input.read(out)
            if (read <= 0) ByteArray(0) else out.copyOf(read)
        }
    }

    private fun looksLikeUtf8(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
            ?: return false
        val replacements = decoded.count { it == '\uFFFD' }
        if (replacements == 0) return true
        return replacements.toDouble() / decoded.length.coerceAtLeast(1).toDouble() <= MAX_REPLACEMENT_RATIO
    }

    private fun selectBestFallback(bytes: ByteArray): Charset? {
        if (bytes.isEmpty()) return Charsets.UTF_8
        var bestCharset: Charset? = null
        var bestScore = Int.MAX_VALUE
        for (name in fallbackCharsets) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val text = bytes.toString(charset)
            val replacement = text.count { it == '\uFFFD' }
            val controls = text.count { ch -> ch < ' ' && ch != '\n' && ch != '\r' && ch != '\t' }
            val score = replacement * 10 + controls
            if (score < bestScore) {
                bestScore = score
                bestCharset = charset
            }
        }
        return bestCharset
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtDocument.kt

```kotlin
package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.RenderConfig
import com.ireader.engines.txt.internal.controller.toLocatorConfig
import com.ireader.engines.txt.internal.controller.TxtLocatorMapper
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.provider.InMemoryAnnotationProvider
import com.ireader.engines.txt.internal.provider.TxtOutlineCache
import com.ireader.engines.txt.internal.session.TxtSession
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.internal.storage.TxtTextStoreFactory
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TxtDocument(
    private val source: DocumentSource,
    override val openOptions: OpenOptions,
    private val charset: Charset,
    private val ioDispatcher: CoroutineDispatcher,
    private val config: TxtEngineConfig
) : ReaderDocument {

    override val id: DocumentId = DocumentId("txt:${stableId(source, charset)}")
    override val format: BookFormat = BookFormat.TXT

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = true,
        fixedLayout = false,
        outline = true,
        search = true,
        textExtraction = true,
        annotations = true,
        links = false
    )

    private val storeMutex = Mutex()
    private var store: TxtTextStore? = null
    private var locatorMapper: TxtLocatorMapper? = null
    private val annotationProvider: AnnotationProvider = InMemoryAnnotationProvider()
    private val locatorConfig = config.toLocatorConfig()

    private val paginationStore = TxtPaginationStore(
        config = config,
        docNamespace = id.value,
        charsetName = charset.name()
    )

    private val outlineCache = TxtOutlineCache(
        config = config,
        docNamespace = id.value,
        charsetName = charset.name()
    )

    private val lastPositionStore = TxtLastPositionStore(
        config = config,
        docNamespace = id.value,
        charsetName = charset.name()
    )

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = withContext(ioDispatcher) {
        val title = source.displayName ?: source.uri.lastPathSegment ?: "Untitled"
        val extra = buildMap {
            put("charset", charset.name())
            source.sizeBytes?.let { put("sizeBytes", it.toString()) }
            source.mimeType?.let { put("mimeType", it) }
            put("uri", source.uri.toString())
        }
        ReaderResult.Ok(
            DocumentMetadata(
                title = title,
                author = null,
                language = null,
                identifier = id.value,
                extra = extra
            )
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
        val normalizedConfig = normalizeConfig(initialConfig)
        val textStore = getStore()
        val mapper = getLocatorMapper(textStore)
        val totalChars = textStore.totalChars().coerceAtLeast(0)
        val startChar = mapper.offsetForLocator(initialLocator, totalChars)

        TxtSession.create(
            documentId = id,
            store = textStore,
            paginationStore = paginationStore,
            outlineCache = outlineCache,
            lastPositionStore = lastPositionStore,
            explicitInitial = (initialLocator != null),
            initialStartChar = startChar,
            initialConfig = normalizedConfig,
            ioDispatcher = ioDispatcher,
            engineConfig = config,
            locatorMapper = mapper,
            annotationProvider = annotationProvider
        )
    }

    override fun close() {
        runCatching { store?.close() }
        locatorMapper = null
    }

    private suspend fun getStore(): TxtTextStore = storeMutex.withLock {
        store ?: TxtTextStoreFactory.create(
            source = source,
            charset = charset,
            ioDispatcher = ioDispatcher,
            config = config
        ).also { store = it }
    }

    private suspend fun getLocatorMapper(textStore: TxtTextStore): TxtLocatorMapper = storeMutex.withLock {
        locatorMapper ?: TxtLocatorMapper(
            store = textStore,
            config = locatorConfig
        ).also { locatorMapper = it }
    }

    private fun normalizeConfig(config: RenderConfig): RenderConfig.ReflowText {
        return when (config) {
            is RenderConfig.ReflowText -> config
            else -> RenderConfig.ReflowText()
        }
    }

    private fun stableId(source: DocumentSource, charset: Charset): String {
        val seed = buildString {
            append(source.uri.toString())
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes?.toString().orEmpty())
            append('|')
            append(charset.name())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val b = digest[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtTextNormalizer.kt

```kotlin
package com.ireader.engines.txt.internal.open

internal object TxtTextNormalizer {

    const val TAB_SPACES: Int = 4

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val state = StreamState()
        val out = StringBuilder(input.length)
        appendNormalized(input, state) { out.append(it) }
        return out.toString()
    }

    fun appendNormalized(
        input: CharSequence,
        state: StreamState,
        emit: (Char) -> Unit
    ) {
        for (i in 0 until input.length) {
            emitNormalized(input[i], state, emit)
        }
    }

    private fun emitNormalized(
        ch: Char,
        state: StreamState,
        emit: (Char) -> Unit
    ) {
        if (ch == '\n' && state.lastWasCr) {
            state.lastWasCr = false
            return
        }

        if (ch == '\r') {
            emit('\n')
            state.lastWasCr = true
            state.emittedAny = true
            return
        }

        state.lastWasCr = false

        if (!state.emittedAny && ch == '\uFEFF') {
            return
        }

        if (ch == '\t') {
            repeat(TAB_SPACES) {
                emit(' ')
            }
            state.emittedAny = true
            return
        }

        if (ch < ' ' && ch != '\n') {
            return
        }

        emit(ch)
        state.emittedAny = true
    }

    data class StreamState(
        var lastWasCr: Boolean = false,
        var emittedAny: Boolean = false
    )
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/IntArrayList.kt

```kotlin
package com.ireader.engines.txt.internal.paging

internal class IntArrayList(initialCapacity: Int = 16) {
    private var arr: IntArray = IntArray(initialCapacity.coerceAtLeast(1))
    private var _size: Int = 0

    val size: Int
        get() = _size

    fun clear() {
        _size = 0
    }

    fun get(index: Int): Int {
        check(index in 0 until _size) { "Index out of bounds: $index, size=$_size" }
        return arr[index]
    }

    fun add(value: Int) {
        ensureCapacity(_size + 1)
        arr[_size] = value
        _size += 1
    }

    fun binarySearch(value: Int): Int {
        var lo = 0
        var hi = _size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val m = arr[mid]
            if (m < value) {
                lo = mid + 1
            } else if (m > value) {
                hi = mid - 1
            } else {
                return mid
            }
        }
        return -(lo + 1)
    }

    fun indexOfFloor(value: Int): Int {
        if (_size == 0) return -1
        val idx = binarySearch(value)
        if (idx >= 0) return idx
        val insertion = -idx - 1
        return insertion - 1
    }

    fun addSortedUnique(value: Int): Boolean {
        if (_size == 0) {
            add(value)
            return true
        }
        val idx = binarySearch(value)
        if (idx >= 0) return false

        val insertAt = -idx - 1
        ensureCapacity(_size + 1)
        if (insertAt < _size) {
            System.arraycopy(arr, insertAt, arr, insertAt + 1, _size - insertAt)
        }
        arr[insertAt] = value
        _size += 1
        return true
    }

    fun toIntArrayCopy(): IntArray = arr.copyOf(_size)

    private fun ensureCapacity(capacity: Int) {
        if (capacity <= arr.size) return
        var next = arr.size * 2
        while (next < capacity) next *= 2
        arr = arr.copyOf(next)
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/KeyHash.kt

```kotlin
package com.ireader.engines.txt.internal.paging

import java.security.MessageDigest

internal object KeyHash {
    fun stableName(key: RenderKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toString().toByteArray(Charsets.UTF_8))
        return buildString(20) {
            for (i in 0 until 10) {
                val b = digest[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/PageStartsCodec.kt

```kotlin
package com.ireader.engines.txt.internal.paging

import java.io.ByteArrayOutputStream

internal object PageStartsCodec {

    fun encode(sortedUnique: IntArray): ByteArray {
        val out = ByteArrayOutputStream(sortedUnique.size * 2)
        var prev = 0
        sortedUnique.forEach { value ->
            val delta = (value - prev).coerceAtLeast(0)
            writeVarInt(out, delta)
            prev = value
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): IntArray {
        val values = ArrayList<Int>(256)
        var cursor = 0
        var prev = 0
        while (cursor < bytes.size) {
            val (delta, next) = readVarInt(bytes, cursor)
            cursor = next
            prev += delta
            values.add(prev)
        }
        return values.toIntArray()
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    private fun readVarInt(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            i += 1
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result to i
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/PageStartsIndex.kt

```kotlin
package com.ireader.engines.txt.internal.paging

internal class PageStartsIndex {
    private val lock = Any()
    private val starts = IntArrayList()

    val size: Int
        get() = synchronized(lock) { starts.size }

    fun clear() = synchronized(lock) {
        starts.clear()
    }

    fun isEmpty(): Boolean = synchronized(lock) { starts.size == 0 }

    fun seedIfEmpty(vararg values: Int) = synchronized(lock) {
        if (starts.size > 0) return@synchronized
        values.forEach { value -> addStartLocked(value.coerceAtLeast(0)) }
    }

    fun addStart(v: Int): Boolean = synchronized(lock) {
        addStartLocked(v.coerceAtLeast(0))
    }

    fun floor(value: Int): Int? = synchronized(lock) {
        if (starts.size == 0) return@synchronized null
        val idx = starts.binarySearch(value)
        if (idx >= 0) return@synchronized starts.get(idx)
        val insertion = -idx - 1
        val floorIdx = insertion - 1
        if (floorIdx < 0) null else starts.get(floorIdx)
    }

    fun floorIndexOf(value: Int): Int = synchronized(lock) {
        starts.indexOfFloor(value)
    }

    fun getAtOrNull(index: Int): Int? = synchronized(lock) {
        if (index < 0 || index >= starts.size) null else starts.get(index)
    }

    fun ceiling(value: Int): Int? = synchronized(lock) {
        if (starts.size == 0) return@synchronized null
        val idx = starts.binarySearch(value)
        if (idx >= 0) return@synchronized starts.get(idx)
        val insertion = -idx - 1
        if (insertion >= starts.size) null else starts.get(insertion)
    }

    fun snapshot(): IntArray = synchronized(lock) {
        starts.toIntArrayCopy()
    }

    fun mergeFrom(sortedUnique: IntArray) = synchronized(lock) {
        sortedUnique.forEach { v -> addStartLocked(v.coerceAtLeast(0)) }
    }

    private fun addStartLocked(value: Int): Boolean {
        return starts.addSortedUnique(value)
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/PaginationCache.kt

```kotlin
package com.ireader.engines.txt.internal.paging

internal class PaginationCache(
    private val maxPages: Int = 24
) {
    data class Stats(
        val hits: Long,
        val misses: Long
    )

    private var namespace: String = ""
    private var hitCount: Long = 0L
    private var missCount: Long = 0L

    private val lru = object : LinkedHashMap<Int, PageSlice>(maxPages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageSlice>?): Boolean {
            return size > maxPages
        }
    }

    fun get(startChar: Int, namespace: String): PageSlice? = synchronized(lru) {
        if (this.namespace != namespace) {
            missCount += 1
            return@synchronized null
        }
        val hit = lru[startChar]
        if (hit == null) {
            missCount += 1
        } else {
            hitCount += 1
        }
        hit
    }

    fun put(slice: PageSlice, namespace: String) {
        synchronized(lru) {
            if (this.namespace != namespace) {
                lru.clear()
                this.namespace = namespace
            }
            lru[slice.startChar] = slice
        }
    }

    fun clear() {
        synchronized(lru) {
            lru.clear()
            namespace = ""
        }
    }

    fun stats(): Stats = synchronized(lru) {
        Stats(
            hits = hitCount,
            misses = missCount
        )
    }

    fun resetStats() {
        synchronized(lru) {
            hitCount = 0L
            missCount = 0L
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/RenderKey.kt

```kotlin
package com.ireader.engines.txt.internal.paging

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

internal data class RenderKey(
    val docId: String,
    val charset: String,
    val viewportW: Int,
    val viewportH: Int,
    val densityBits: Int,
    val fontScaleBits: Int,
    val fontSizeBits: Int,
    val lineHeightBits: Int,
    val paragraphBits: Int,
    val paddingBits: Int,
    val hyphenation: Boolean,
    val fontFamily: String?
) {
    companion object {
        fun of(
            docId: String,
            charset: String,
            constraints: LayoutConstraints,
            config: RenderConfig.ReflowText
        ): RenderKey {
            return RenderKey(
                docId = docId,
                charset = charset,
                viewportW = constraints.viewportWidthPx,
                viewportH = constraints.viewportHeightPx,
                densityBits = constraints.density.toBits(),
                fontScaleBits = constraints.fontScale.toBits(),
                fontSizeBits = config.fontSizeSp.toBits(),
                lineHeightBits = config.lineHeightMult.toBits(),
                paragraphBits = config.paragraphSpacingDp.toBits(),
                paddingBits = config.pagePaddingDp.toBits(),
                hyphenation = config.hyphenation,
                fontFamily = config.fontFamilyName
            )
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/TxtLastPositionStore.kt

```kotlin
package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.model.Locator
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TxtLastPositionStore(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val minIntervalMs: Long = config.lastPositionMinIntervalMs.coerceAtLeast(0L)

    suspend fun load(key: RenderKey): Locator? = withContext(ioDispatcher) {
        if (!config.persistLastPosition) return@withContext null
        val file = fileFor(key) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { decodeLocator(file.readBytes()) }.getOrNull()
    }

    suspend fun save(key: RenderKey, locator: Locator) = withContext(ioDispatcher) {
        if (!config.persistLastPosition) return@withContext
        val file = fileFor(key) ?: return@withContext
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(encodeLocator(locator))
            if (file.exists()) file.delete()
            val renamed = tmp.renameTo(file)
            if (!renamed) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun fileFor(key: RenderKey): File? {
        val base = config.cacheDir ?: return null
        val folder = File(
            base,
            "reader-txt-v2/lastpos/${docNamespace.hashCode()}_${charsetName.hashCode()}"
        )
        return File(folder, "${KeyHash.stableName(key)}.pos")
    }

    private fun encodeLocator(locator: Locator): ByteArray {
        val properties = Properties().apply {
            setProperty("scheme", locator.scheme)
            setProperty("value", locator.value)
            setProperty("extras.size", locator.extras.size.toString())
            locator.extras.entries.forEachIndexed { index, entry ->
                setProperty("extras.$index.key", entry.key)
                setProperty("extras.$index.value", entry.value)
            }
        }
        val out = ByteArrayOutputStream()
        properties.store(out, null)
        return out.toByteArray()
    }

    private fun decodeLocator(bytes: ByteArray): Locator? {
        val properties = Properties()
        ByteArrayInputStream(bytes).use { input ->
            properties.load(input)
        }
        val scheme = properties.getProperty("scheme") ?: return null
        val value = properties.getProperty("value") ?: return null
        val size = properties.getProperty("extras.size")?.toIntOrNull() ?: 0
        val extras = buildMap<String, String> {
            for (index in 0 until size) {
                val key = properties.getProperty("extras.$index.key") ?: continue
                val entryValue = properties.getProperty("extras.$index.value") ?: continue
                put(key, entryValue)
            }
        }
        return Locator(scheme = scheme, value = value, extras = extras)
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/TxtPager.kt

```kotlin
package com.ireader.engines.txt.internal.paging

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.engines.txt.internal.storage.TxtTextStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal data class PageSlice(
    val startChar: Int,
    val endChar: Int,
    val text: String
)

internal class TxtPager(
    private val store: TxtTextStore,
    private val chunkSizeChars: Int = 32 * 1024
) {
    private companion object {
        private const val DEFAULT_CHUNK_CACHE_ENTRIES = 10
        private const val MIN_ESTIMATED_CHARS = 256
        private const val MIN_CHUNK_CHARS = 2_000
        private const val MIN_ALIGNMENT_CHARS = 2_048
    }

    private data class ChunkLayoutKey(
        val chunkStart: Int,
        val widthPx: Int,
        val heightPx: Int,
        val densityBits: Int,
        val fontScaleBits: Int,
        val fontSizeBits: Int,
        val lineHeightBits: Int,
        val paddingBits: Int,
        val hyphenation: Boolean,
        val fontFamily: String?
    )

    private data class ChunkLayoutResult(
        val chunkStart: Int,
        val chunkText: String,
        val pageStarts: IntArray
    ) {
        val chunkEnd: Int
            get() = chunkStart + chunkText.length
    }

    private val chunkCache = object : LinkedHashMap<ChunkLayoutKey, ChunkLayoutResult>(
        DEFAULT_CHUNK_CACHE_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChunkLayoutKey, ChunkLayoutResult>?): Boolean {
            return size > DEFAULT_CHUNK_CACHE_ENTRIES
        }
    }

    suspend fun pageAt(
        startChar: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): PageSlice {
        coroutineContext.ensureActive()
        val total = store.totalChars().coerceAtLeast(0)
        if (total == 0) return PageSlice(0, 0, "")

        val start = startChar.coerceIn(0, total - 1)
        val paddingPx = dpToPx(config.pagePaddingDp, constraints.density)
        val contentWidth = max(1, constraints.viewportWidthPx - (paddingPx * 2))
        val contentHeight = max(1, constraints.viewportHeightPx - (paddingPx * 2))

        val estimated = estimateCharsPerPage(constraints, config).coerceAtLeast(MIN_ESTIMATED_CHARS)
        val desiredChunk = max(chunkSizeChars.coerceAtLeast(MIN_CHUNK_CHARS), estimated * 4)
        val alignment = (estimated * 2)
            .coerceIn(MIN_ALIGNMENT_CHARS, chunkSizeChars.coerceAtLeast(MIN_ALIGNMENT_CHARS))
        val chunkStart = alignDown(start, alignment)
        val distanceIntoChunk = (start - chunkStart).coerceAtLeast(0)
        val candidateMax = min(total - chunkStart, desiredChunk + distanceIntoChunk + estimated)
        if (candidateMax <= 0) return PageSlice(start, start, "")

        val key = ChunkLayoutKey(
            chunkStart = chunkStart,
            widthPx = contentWidth,
            heightPx = contentHeight,
            densityBits = constraints.density.toBits(),
            fontScaleBits = constraints.fontScale.toBits(),
            fontSizeBits = config.fontSizeSp.toBits(),
            lineHeightBits = config.lineHeightMult.toBits(),
            paddingBits = config.pagePaddingDp.toBits(),
            hyphenation = config.hyphenation,
            fontFamily = config.fontFamilyName
        )

        val chunk = getOrBuildChunkLayout(
            key = key,
            readLen = candidateMax,
            widthPx = contentWidth,
            heightPx = contentHeight,
            constraints = constraints,
            config = config
        )
        coroutineContext.ensureActive()
        if (chunk.chunkText.isEmpty()) return PageSlice(start, start, "")

        val starts = chunk.pageStarts
        val pageIndex = floorIndex(starts, start).coerceAtLeast(0)
        val pageStart = starts.getOrElse(pageIndex) { start }.coerceIn(0, total)
        val pageEnd = resolvePageEnd(starts, pageIndex, chunk.chunkEnd, total, pageStart)
        if (pageEnd <= pageStart) {
            return buildSingleCharFallback(start = start, total = total)
        }
        val localStart = (pageStart - chunk.chunkStart).coerceIn(0, chunk.chunkText.length)
        val localEnd = (pageEnd - chunk.chunkStart).coerceIn(localStart, chunk.chunkText.length)
        val text = chunk.chunkText.substring(localStart, localEnd)
        if (text.isEmpty()) {
            return buildSingleCharFallback(start = start, total = total)
        }
        return PageSlice(startChar = pageStart, endChar = pageEnd, text = text)
    }

    fun estimateCharsPerPage(
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): Int {
        val paddingPx = dpToPx(config.pagePaddingDp, constraints.density)
        val contentWidth = max(1, constraints.viewportWidthPx - (paddingPx * 2))
        val contentHeight = max(1, constraints.viewportHeightPx - (paddingPx * 2))

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spToPx(config.fontSizeSp, constraints.density, constraints.fontScale)
        }

        val metrics = textPaint.fontMetrics
        val lineHeightPx = max(1f, (metrics.descent - metrics.ascent) * config.lineHeightMult)
        val linesPerPage = max(1, (contentHeight / lineHeightPx).toInt())

        val avgCharWidth = max(1f, textPaint.measureText("中"))
        val charsPerLine = max(4, (contentWidth / avgCharWidth).toInt())

        return (linesPerPage * charsPerLine).coerceIn(200, 50_000)
    }

    private suspend fun getOrBuildChunkLayout(
        key: ChunkLayoutKey,
        readLen: Int,
        widthPx: Int,
        heightPx: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): ChunkLayoutResult {
        val cached = synchronized(chunkCache) { chunkCache[key] }
        if (cached != null) return cached

        val built = buildChunkLayout(
            key = key,
            readLen = readLen,
            widthPx = widthPx,
            heightPx = heightPx,
            constraints = constraints,
            config = config
        )
        synchronized(chunkCache) { chunkCache[key] = built }
        return built
    }

    private suspend fun buildChunkLayout(
        key: ChunkLayoutKey,
        readLen: Int,
        widthPx: Int,
        heightPx: Int,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText
    ): ChunkLayoutResult {
        val chunkText = store.readChars(key.chunkStart, readLen)
        if (chunkText.isEmpty()) {
            return ChunkLayoutResult(
                chunkStart = key.chunkStart,
                chunkText = chunkText,
                pageStarts = intArrayOf(key.chunkStart)
            )
        }

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spToPx(config.fontSizeSp, constraints.density, constraints.fontScale)
        }
        val layout = StaticLayout.Builder
            .obtain(chunkText, 0, chunkText.length, textPaint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, config.lineHeightMult)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .apply {
                if (config.hyphenation) {
                    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                }
            }
            .build()

        val starts = IntArrayList(24)
        starts.addSortedUnique(key.chunkStart)

        var pageStartLine = 0
        for (line in 0 until layout.lineCount) {
            if ((line and 0xFF) == 0) {
                coroutineContext.ensureActive()
            }
            val top = layout.getLineTop(pageStartLine)
            val bottom = layout.getLineBottom(line)
            if (bottom - top > heightPx) {
                val localStart = layout.getLineStart(line).coerceAtLeast(0)
                val absoluteStart = (key.chunkStart + localStart)
                    .coerceIn(key.chunkStart, key.chunkStart + chunkText.length)
                if (!starts.addSortedUnique(absoluteStart)) {
                    starts.addSortedUnique((absoluteStart + 1).coerceAtMost(key.chunkStart + chunkText.length))
                }
                pageStartLine = line
            }
        }

        return ChunkLayoutResult(
            chunkStart = key.chunkStart,
            chunkText = chunkText,
            pageStarts = starts.toIntArrayCopy()
        )
    }

    private fun dpToPx(dp: Float, density: Float): Int = (dp * density).roundToInt()

    private fun spToPx(sp: Float, density: Float, fontScale: Float): Float = sp * density * fontScale

    private fun alignDown(value: Int, quantum: Int): Int {
        if (quantum <= 1) return value.coerceAtLeast(0)
        return ((value.coerceAtLeast(0) / quantum) * quantum).coerceAtLeast(0)
    }

    private suspend fun buildSingleCharFallback(start: Int, total: Int): PageSlice {
        val safeStart = start.coerceIn(0, total - 1)
        val end = (safeStart + 1).coerceAtMost(total)
        val text = store.readRange(safeStart, end)
        return PageSlice(
            startChar = safeStart,
            endChar = end,
            text = text.ifEmpty { " " }
        )
    }

    private fun resolvePageEnd(
        starts: IntArray,
        pageIndex: Int,
        chunkEnd: Int,
        totalChars: Int,
        pageStart: Int
    ): Int {
        val rawEnd = if (pageIndex + 1 < starts.size) {
            starts[pageIndex + 1].coerceIn(pageStart, totalChars)
        } else {
            chunkEnd.coerceIn(pageStart, totalChars)
        }
        return if (rawEnd <= pageStart) {
            min(totalChars, pageStart + 1)
        } else {
            rawEnd
        }
    }

    private fun floorIndex(values: IntArray, target: Int): Int {
        var lo = 0
        var hi = values.lastIndex
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val candidate = values[mid]
            if (candidate <= target) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/paging/TxtPaginationStore.kt

```kotlin
package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.TxtEngineConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TxtPaginationStore(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private data class Bucket(
        val index: PageStartsIndex,
        var newStartsSinceLastWrite: Int = 0
    )

    private val buckets = ConcurrentHashMap<RenderKey, Bucket>()

    fun getOrCreate(key: RenderKey): PageStartsIndex {
        return buckets.getOrPut(key) {
            val index = PageStartsIndex().apply { seedIfEmpty(0) }
            if (config.persistPagination) {
                loadFromDisk(key)?.let { restored -> index.mergeFrom(restored) }
            }
            Bucket(index)
        }.index
    }

    suspend fun maybePersist(key: RenderKey, index: PageStartsIndex, newAdds: Int) {
        if (!config.persistPagination) return
        if (newAdds <= 0) return
        val bucket = buckets[key] ?: return
        val shouldPersist = synchronized(bucket) {
            bucket.newStartsSinceLastWrite += newAdds
            val threshold = config.paginationWriteEveryNewStarts.coerceAtLeast(1)
            if (bucket.newStartsSinceLastWrite >= threshold) {
                bucket.newStartsSinceLastWrite = 0
                true
            } else {
                false
            }
        }
        if (shouldPersist) {
            saveToDisk(key, index)
        }
    }

    suspend fun flush(key: RenderKey, index: PageStartsIndex) {
        if (!config.persistPagination) return
        saveToDisk(key, index)
        buckets[key]?.let { bucket ->
            synchronized(bucket) {
                bucket.newStartsSinceLastWrite = 0
            }
        }
    }

    private fun loadFromDisk(key: RenderKey): IntArray? {
        val file = fileFor(key) ?: return null
        if (!file.exists()) return null
        return runCatching {
            PageStartsCodec.decode(file.readBytes())
        }.getOrNull()
    }

    private suspend fun saveToDisk(key: RenderKey, index: PageStartsIndex) = withContext(ioDispatcher) {
        val file = fileFor(key) ?: return@withContext
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(PageStartsCodec.encode(index.snapshot()))
            if (file.exists()) file.delete()
            val renamed = tmp.renameTo(file)
            if (!renamed) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun fileFor(key: RenderKey): File? {
        val baseDir = config.cacheDir ?: return null
        val folder = File(
            baseDir,
            "reader-txt-v2/pagination/${docNamespace.hashCode()}_${charsetName.hashCode()}"
        )
        return File(folder, "${KeyHash.stableName(key)}.bin")
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/InMemoryAnnotationProvider.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InMemoryAnnotationProvider : AnnotationProvider {

    private data class IndexedAnnotation(
        val start: Int,
        val end: Int,
        val annotation: Annotation
    )

    private val lock = Any()
    private val state = MutableStateFlow<List<Annotation>>(emptyList())
    @Volatile
    private var index: List<IndexedAnnotation> = emptyList()

    override fun observeAll(): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun listAll(): ReaderResult<List<Annotation>> = synchronized(lock) {
        ReaderResult.Ok(state.value)
    }

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        val all = synchronized(lock) { state.value }
        val range = query.range ?: return ReaderResult.Ok(all)

        val queryStart = parseOffset(range.start) ?: return ReaderResult.Ok(emptyList())
        val queryEnd = parseOffset(range.end) ?: return ReaderResult.Ok(emptyList())
        val from = minOf(queryStart, queryEnd)
        val to = maxOf(queryStart, queryEnd)
        if (from >= to) return ReaderResult.Ok(emptyList())

        val indexed = index
        if (indexed.isEmpty()) return ReaderResult.Ok(emptyList())

        var cursor = lowerBoundStart(indexed, from)
        while (cursor > 0 && indexed[cursor - 1].end > from) {
            cursor -= 1
        }

        val result = ArrayList<Annotation>()
        while (cursor < indexed.size) {
            val candidate = indexed[cursor]
            if (candidate.start >= to) break
            if (overlaps(from, to, candidate.start, candidate.end)) {
                result.add(candidate.annotation)
            }
            cursor += 1
        }
        return ReaderResult.Ok(result)
    }

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val anchor = draft.anchor as? AnnotationAnchor.ReflowRange
            ?: return ReaderResult.Err(
                ReaderError.Internal("TXT only supports reflow range annotations")
            )
        if (!isTxtOffset(anchor.range.start) || !isTxtOffset(anchor.range.end)) {
            return ReaderResult.Err(ReaderError.Internal("Invalid txt.offset anchor"))
        }

        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId(UUID.randomUUID().toString()),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        synchronized(lock) {
            val updated = state.value + created
            state.value = updated
            rebuildIndexLocked(updated)
        }
        return ReaderResult.Ok(created)
    }

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val updated = state.value.map { current ->
                if (current.id == annotation.id) {
                    annotation.copy(updatedAtEpochMs = now)
                } else {
                    current
                }
            }
            state.value = updated
            rebuildIndexLocked(updated)
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        synchronized(lock) {
            val updated = state.value.filterNot { it.id == id }
            state.value = updated
            rebuildIndexLocked(updated)
        }
        return ReaderResult.Ok(Unit)
    }

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val result = query(query)
        return when (result) {
            is ReaderResult.Err -> ReaderResult.Err(result.error)
            is ReaderResult.Ok -> {
                val decorations = result.value.mapNotNull { annotation ->
                    val anchor = annotation.anchor as? AnnotationAnchor.ReflowRange ?: return@mapNotNull null
                    Decoration.Reflow(
                        range = anchor.range,
                        style = annotation.style
                    )
                }
                ReaderResult.Ok(decorations)
            }
        }
    }

    private fun parseOffset(locator: Locator): Int? {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return null
        return locator.value.toIntOrNull()
    }

    private fun isTxtOffset(locator: Locator): Boolean = locator.scheme == LocatorSchemes.TXT_OFFSET

    private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        return aStart < bEnd && bStart < aEnd
    }

    private fun lowerBoundStart(values: List<IndexedAnnotation>, target: Int): Int {
        var lo = 0
        var hi = values.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (values[mid].start < target) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return lo
    }

    private fun rebuildIndexLocked(annotations: List<Annotation>) {
        index = annotations.mapNotNull { annotation ->
            val anchor = annotation.anchor as? AnnotationAnchor.ReflowRange ?: return@mapNotNull null
            val start = parseOffset(anchor.range.start) ?: return@mapNotNull null
            val end = parseOffset(anchor.range.end) ?: return@mapNotNull null
            val from = minOf(start, end)
            val to = maxOf(start, end)
            if (from >= to) return@mapNotNull null
            IndexedAnnotation(start = from, end = to, annotation = annotation)
        }.sortedBy { it.start }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtOutlineCache.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.model.OutlineNode
import java.io.File

internal class TxtOutlineCache(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String
) {
    private companion object {
        private const val VERSION_HEADER_PREFIX = "#txt-outline-v"
        private const val CURRENT_VERSION = 2
    }

    // Persisted format is v2-only: level<TAB>offset<TAB>title
    val asTree: Boolean
        get() = config.outlineAsTree

    @Volatile
    private var memory: List<OutlineNode>? = null

    fun getMemory(): List<OutlineNode>? = memory

    fun setMemory(nodes: List<OutlineNode>) {
        memory = nodes
    }

    fun loadFromDisk(): List<OutlineNode>? {
        if (!config.persistOutline) return null
        val file = file() ?: return null
        if (!file.exists()) return null
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: return null
        val startIndex = when {
            lines.isEmpty() -> 0
            lines.first().startsWith(VERSION_HEADER_PREFIX) -> {
                val version = lines.first().removePrefix(VERSION_HEADER_PREFIX).toIntOrNull()
                if (version != CURRENT_VERSION) return null
                1
            }
            else -> 0
        }

        return runCatching {
            val builder = TxtOutlineTreeBuilder(asTree = asTree)
            for (line in lines.drop(startIndex)) {
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val level = parts[0].toIntOrNull() ?: continue
                val offset = parts[1].toIntOrNull() ?: continue
                val title = parts.subList(2, parts.size).joinToString("\t")
                builder.add(level, title, offset)
            }
            builder.build()
        }.getOrNull()
    }

    fun saveToDisk(nodes: List<OutlineNode>) {
        if (!config.persistOutline) return
        val file = file() ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(
                buildString {
                    append(VERSION_HEADER_PREFIX)
                    append(CURRENT_VERSION)
                    append('\n')
                    appendNodes(nodes, level = 1)
                },
                Charsets.UTF_8
            )
            if (file.exists()) file.delete()
            val renamed = tmp.renameTo(file)
            if (!renamed) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun file(): File? {
        val base = config.cacheDir ?: return null
        val folder = File(base, "reader-txt-v2/outline")
        return File(folder, "${docNamespace.hashCode()}_${charsetName.hashCode()}.txt")
    }

    private fun StringBuilder.appendNodes(nodes: List<OutlineNode>, level: Int) {
        nodes.forEach { node ->
            append(level.coerceIn(1, 6))
            append('\t')
            append(node.locator.value)
            append('\t')
            append(node.title.replace('\n', ' '))
            append('\n')
            if (node.children.isNotEmpty()) {
                appendNodes(node.children, level + 1)
            }
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtOutlineProvider.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtOutlineProvider(
    private val store: TxtTextStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val cache: TxtOutlineCache
) : OutlineProvider {

    private val maxNodes = 500
    private val chunkChars = 64 * 1024

    private val chineseChapter = Regex("""^\s*(第.{1,12}[章节回卷部篇])\s*(.*)$""")
    private val englishChapter = Regex("""^\s*(CHAPTER|Chapter)\s+\d+.*$""")
    private val markdownWithLevel = Regex("""^\s*(#{1,6})\s+(.+)$""")
    private val numberedWithLevel = Regex("""^\s*(\d+(?:\.\d+)*)\s*[\.\、\)]\s+(.+)$""")

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> = withContext(ioDispatcher) {
        cache.getMemory()?.let { return@withContext ReaderResult.Ok(it) }

        cache.loadFromDisk()?.let { restored ->
            cache.setMemory(restored)
            return@withContext ReaderResult.Ok(restored)
        }

        runCatching {
            val total = store.totalChars().coerceAtLeast(0)
            if (total == 0) return@runCatching emptyList()

            val builder = TxtOutlineTreeBuilder(asTree = cache.asTree)
            var added = 0
            var offset = 0
            var carry = ""

            while (offset < total && added < maxNodes) {
                val readLen = (total - offset).coerceAtMost(chunkChars)
                val chunk = store.readChars(offset, readLen)
                if (chunk.isEmpty()) break

                val merged = carry + chunk
                val lines = merged.split('\n')
                val completeLineCount = if (merged.endsWith('\n')) lines.size else lines.size - 1
                val baseOffset = (offset - carry.length).coerceAtLeast(0)
                var localOffset = 0

                for (i in 0 until completeLineCount) {
                    val rawLine = lines[i]
                    val cleaned = rawLine.trimEnd('\r')
                    val titleInfo = extractTitleWithLevel(cleaned)
                    if (titleInfo != null) {
                        val lineStart = (baseOffset + localOffset).coerceAtLeast(0)
                        builder.add(
                            level = titleInfo.level,
                            title = titleInfo.title,
                            startChar = lineStart
                        )
                        added += 1
                        if (added >= maxNodes) break
                    }
                    localOffset += rawLine.length + 1
                }

                carry = if (merged.endsWith('\n')) "" else lines.lastOrNull().orEmpty()
                offset += chunk.length
            }

            if (carry.isNotBlank() && added < maxNodes) {
                val titleInfo = extractTitleWithLevel(carry.trimEnd('\r'))
                if (titleInfo != null) {
                    val lineStart = (total - carry.length).coerceAtLeast(0)
                    builder.add(
                        level = titleInfo.level,
                        title = titleInfo.title,
                        startChar = lineStart
                    )
                }
            }

            builder.build()
        }.fold(
            onSuccess = { nodes ->
                cache.setMemory(nodes)
                cache.saveToDisk(nodes)
                ReaderResult.Ok(nodes)
            },
            onFailure = {
                ReaderResult.Ok(emptyList())
            }
        )
    }

    private data class TitleInfo(
        val title: String,
        val level: Int
    )

    private fun extractTitleWithLevel(line: String): TitleInfo? {
        if (line.isBlank()) return null

        markdownWithLevel.matchEntire(line)?.let { match ->
            return TitleInfo(
                title = match.groupValues[2].trim(),
                level = match.groupValues[1].length.coerceIn(1, 6)
            )
        }

        chineseChapter.matchEntire(line)?.let { match ->
            val head = match.groupValues[1].trim()
            val tail = match.groupValues.getOrNull(2)?.trim().orEmpty()
            val title = if (tail.isEmpty()) head else "$head $tail"
            val level = if (head.contains("节")) 2 else 1
            return TitleInfo(title = title, level = level)
        }

        if (englishChapter.matches(line)) {
            return TitleInfo(title = line.trim(), level = 1)
        }

        numberedWithLevel.matchEntire(line)?.let { match ->
            val seg = match.groupValues[1]
            val level = (seg.count { it == '.' } + 1).coerceIn(1, 6)
            return TitleInfo(title = line.trim(), level = level)
        }

        return null
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtOutlineTreeBuilder.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import java.util.ArrayDeque

internal class TxtOutlineTreeBuilder(
    private val asTree: Boolean
) {
    private data class MutableNode(
        val level: Int,
        val title: String,
        val startChar: Int,
        val children: MutableList<MutableNode> = mutableListOf()
    )

    private val roots = mutableListOf<MutableNode>()
    private val stack = ArrayDeque<MutableNode>()

    fun add(level: Int, title: String, startChar: Int) {
        val safeLevel = level.coerceIn(1, 6)
        val node = MutableNode(
            level = safeLevel,
            title = title,
            startChar = startChar.coerceAtLeast(0)
        )

        if (!asTree) {
            roots.add(node)
            return
        }

        while (stack.isNotEmpty() && stack.last().level >= safeLevel) {
            stack.removeLast()
        }

        if (stack.isEmpty()) {
            roots.add(node)
        } else {
            stack.last().children.add(node)
        }
        stack.addLast(node)
    }

    fun build(): List<OutlineNode> = roots.map { it.toOutlineNode() }

    private fun MutableNode.toOutlineNode(): OutlineNode = OutlineNode(
        title = title,
        locator = Locator(LocatorSchemes.TXT_OFFSET, startChar.toString()),
        children = children.map { it.toOutlineNode() }
    )
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtSearchProvider.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.controller.TxtLocatorMapper
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal class TxtSearchProvider(
    private val store: TxtTextStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val locatorMapper: TxtLocatorMapper,
    private val defaultMaxHits: Int = 500
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow

        val chunkSize = 64 * 1024
        val overlap = (query.length + 64).coerceAtMost(8_192)
        val totalChars = withContext(ioDispatcher) { store.totalChars().coerceAtLeast(0) }
        if (totalChars == 0) return@flow

        var cursor = options.startFrom?.let { parseOffset(it) } ?: 0
        cursor = cursor.coerceIn(0, totalChars)
        var emitted = 0
        val maxHits = options.maxHits.takeIf { it > 0 } ?: defaultMaxHits

        while (cursor < totalChars && emitted < maxHits) {
            coroutineContext.ensureActive()

            val readLen = (chunkSize + overlap).coerceAtMost(totalChars - cursor)
            val chunk = withContext(ioDispatcher) { store.readChars(cursor, readLen) }
            if (chunk.isEmpty()) break

            val hasNextChunk = cursor + chunkSize < totalChars
            if (options.caseSensitive && !options.wholeWord) {
                var searchFrom = 0
                while (searchFrom <= chunk.length - query.length && emitted < maxHits) {
                    coroutineContext.ensureActive()
                    val found = chunk.indexOf(query, searchFrom)
                    if (found < 0) break
                    if (hasNextChunk && found >= chunkSize) break

                    val start = cursor + found
                    val end = start + query.length
                    emit(
                        SearchHit(
                            range = LocatorRange(
                                start = locatorMapper.locatorForOffsetFast(start, totalChars),
                                end = locatorMapper.locatorForBoundaryOffset(end.coerceAtMost(totalChars), totalChars)
                            ),
                            excerpt = buildExcerpt(chunk, found, query.length),
                            sectionTitle = null
                        )
                    )
                    emitted += 1
                    searchFrom = found + query.length
                }
            } else {
                var searchFrom = 0
                while (searchFrom <= chunk.length - query.length && emitted < maxHits) {
                    coroutineContext.ensureActive()

                    val found = indexOfMatch(
                        haystack = chunk,
                        needle = query,
                        start = searchFrom,
                        ignoreCase = !options.caseSensitive
                    )
                    if (found < 0) break
                    if (hasNextChunk && found >= chunkSize) break
                    if (options.wholeWord && !isWholeWord(chunk, found, query.length)) {
                        searchFrom = found + query.length
                        continue
                    }

                    val start = cursor + found
                    val end = start + query.length
                    emit(
                        SearchHit(
                            range = LocatorRange(
                                start = locatorMapper.locatorForOffsetFast(start, totalChars),
                                end = locatorMapper.locatorForBoundaryOffset(end.coerceAtMost(totalChars), totalChars)
                            ),
                            excerpt = buildExcerpt(chunk, found, query.length),
                            sectionTitle = null
                        )
                    )
                    emitted += 1
                    searchFrom = found + query.length
                }
            }

            cursor += chunkSize
        }
    }

    private fun parseOffset(locator: Locator): Int {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return 0
        return locator.value.toIntOrNull() ?: 0
    }

    private fun indexOfMatch(
        haystack: String,
        needle: String,
        start: Int,
        ignoreCase: Boolean
    ): Int {
        if (needle.isEmpty()) return -1
        val last = haystack.length - needle.length
        var i = start.coerceAtLeast(0)
        while (i <= last) {
            if (haystack.regionMatches(i, needle, 0, needle.length, ignoreCase = ignoreCase)) {
                return i
            }
            i += 1
        }
        return -1
    }

    private fun isWholeWord(text: String, start: Int, length: Int): Boolean {
        val leftIndex = start - 1
        val rightIndex = start + length
        val leftOk = leftIndex < 0 || !isWordChar(text[leftIndex])
        val rightOk = rightIndex >= text.length || !isWordChar(text[rightIndex])
        return leftOk && rightOk
    }

    private fun isWordChar(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch == '_'
    }

    private fun buildExcerpt(source: String, hitStart: Int, hitLen: Int): String {
        val left = (hitStart - 40).coerceAtLeast(0)
        val right = (hitStart + hitLen + 60).coerceAtMost(source.length)
        val prefix = if (left > 0) "..." else ""
        val suffix = if (right < source.length) "..." else ""
        return prefix + source.substring(left, right).replace('\n', ' ') + suffix
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/provider/TxtTextProvider.kt

```kotlin
package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import kotlin.math.max
import kotlin.math.min

internal class TxtTextProvider(
    private val store: TxtTextStore,
    private val maxRangeChars: Int
) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        val start = parseOffset(range.start)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid start locator"))
        val end = parseOffset(range.end)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid end locator"))

        val from = min(start, end).coerceAtLeast(0)
        val to = max(start, end).coerceAtLeast(from)
        val cappedEnd = min(to, from + maxRangeChars.coerceAtLeast(1))
        return ReaderResult.Ok(store.readRange(from, cappedEnd))
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        val offset = parseOffset(locator)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid locator"))
        val safeMax = maxChars.coerceIn(32, 10_000)
        return ReaderResult.Ok(store.readAround(offset.coerceAtLeast(0), safeMax))
    }

    private fun parseOffset(locator: Locator): Int? {
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return null
        return locator.value.toIntOrNull()
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/session/TxtSession.kt

```kotlin
package com.ireader.engines.txt.internal.session

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.controller.TxtController
import com.ireader.engines.txt.internal.controller.TxtLocatorMapper
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPager
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.provider.TxtOutlineCache
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProvider
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.internal.util.toReaderError
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtSession private constructor(
    override val id: SessionId,
    override val controller: ReaderController,
    override val outline: OutlineProvider?,
    override val search: SearchProvider?,
    override val text: TextProvider?,
    override val annotations: AnnotationProvider?,
    override val resources: ResourceProvider?
) : ReaderSession {

    override fun close() {
        runCatching { controller.close() }
    }

    companion object {
        suspend fun create(
            documentId: DocumentId,
            store: TxtTextStore,
            paginationStore: TxtPaginationStore,
            outlineCache: TxtOutlineCache,
            lastPositionStore: TxtLastPositionStore,
            explicitInitial: Boolean,
            initialStartChar: Int,
            initialConfig: RenderConfig.ReflowText,
            ioDispatcher: CoroutineDispatcher,
            engineConfig: TxtEngineConfig,
            locatorMapper: TxtLocatorMapper,
            annotationProvider: AnnotationProvider
        ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
            try {
                val pager = TxtPager(
                    store = store,
                    chunkSizeChars = engineConfig.chunkSizeChars
                )
                val controller = TxtController(
                    store = store,
                    pager = pager,
                    ioDispatcher = ioDispatcher,
                    annotations = annotationProvider,
                    paginationStore = paginationStore,
                    lastPositionStore = lastPositionStore,
                    explicitInitial = explicitInitial,
                    documentId = documentId,
                    initialStartChar = initialStartChar,
                    locatorMapper = locatorMapper,
                    engineConfig = engineConfig
                ).apply {
                    setConfig(initialConfig)
                }

                ReaderResult.Ok(
                    TxtSession(
                        id = SessionId(UUID.randomUUID().toString()),
                        controller = controller,
                        outline = TxtOutlineProvider(store, ioDispatcher, outlineCache),
                        search = TxtSearchProvider(
                            store = store,
                            ioDispatcher = ioDispatcher,
                            locatorMapper = locatorMapper,
                            defaultMaxHits = engineConfig.maxSearchHitsDefault
                        ),
                        text = TxtTextProvider(
                            store = store,
                            maxRangeChars = engineConfig.maxTextExtractChars
                        ),
                        annotations = annotationProvider,
                        resources = null
                    )
                )
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError())
            }
        }
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/BomUtil.kt

```kotlin
package com.ireader.engines.txt.internal.storage

import java.nio.charset.Charset

internal object BomUtil {

    fun bomSkipBytes(header: ByteArray, charset: Charset): Int {
        // UTF-8 BOM: EF BB BF
        if (
            header.size >= 3 &&
            header[0] == 0xEF.toByte() &&
            header[1] == 0xBB.toByte() &&
            header[2] == 0xBF.toByte()
        ) {
            return 3
        }

        // UTF-16 LE/BE
        if (
            header.size >= 2 &&
            (
                (header[0] == 0xFF.toByte() && header[1] == 0xFE.toByte()) ||
                    (header[0] == 0xFE.toByte() && header[1] == 0xFF.toByte())
                )
        ) {
            return 2
        }

        return 0
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/ChunkIndex.kt

```kotlin
package com.ireader.engines.txt.internal.storage

internal data class ChunkAnchor(
    val charOffset: Int,
    val byteOffset: Long,
    val lastWasCr: Boolean = false,
    val emittedAny: Boolean = false
)

internal class ChunkIndex(
    val anchors: List<ChunkAnchor>,
    val totalChars: Int,
    val startByteOffset: Long
) {
    fun anchorFor(targetChar: Int): ChunkAnchor {
        if (anchors.isEmpty()) return ChunkAnchor(0, startByteOffset)

        val target = targetChar.coerceAtLeast(0)
        var lo = 0
        var hi = anchors.lastIndex
        var best = anchors[0]

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val candidate = anchors[mid]
            if (candidate.charOffset <= target) {
                best = candidate
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/InMemoryTxtTextStore.kt

```kotlin
package com.ireader.engines.txt.internal.storage

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtTextNormalizer
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class InMemoryTxtTextStore(
    private val source: DocumentSource,
    override val charset: Charset,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxBytes: Long = 8L * 1024L * 1024L
) : TxtTextStore {

    @Volatile
    private var cached: String? = null

    override suspend fun totalChars(): Int = ensureLoaded().length

    override suspend fun readRange(startChar: Int, endCharExclusive: Int): String {
        val text = ensureLoaded()
        val start = startChar.coerceIn(0, text.length)
        val end = endCharExclusive.coerceIn(start, text.length)
        return text.substring(start, end)
    }

    override suspend fun readChars(startChar: Int, maxChars: Int): String {
        val text = ensureLoaded()
        val start = startChar.coerceIn(0, text.length)
        val safeMax = maxChars.coerceAtLeast(0)
        val end = (start + safeMax).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    override suspend fun readAround(charOffset: Int, maxChars: Int): String {
        val text = ensureLoaded()
        if (text.isEmpty()) return ""
        val safe = maxChars.coerceAtLeast(1)
        val half = (safe / 2).coerceAtLeast(1)
        val center = charOffset.coerceIn(0, text.length)
        val start = (center - half).coerceAtLeast(0)
        val end = (start + safe).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    override fun close() {
        cached = null
    }

    private suspend fun ensureLoaded(): String = withContext(ioDispatcher) {
        cached?.let { return@withContext it }

        source.sizeBytes?.let { size ->
            if (size > maxBytes) {
                throw IllegalStateException("TXT too large for in-memory store: $size bytes")
            }
        }

        val text = source.openInputStream().use { rawInput ->
            val checkedInput = checkedStream(rawInput)
            decodeAndNormalize(checkedInput)
        }
        cached = text
        text
    }

    private fun checkedStream(input: InputStream): InputStream {
        return object : FilterInputStream(input) {
            private var totalBytes: Long = 0

            override fun read(): Int {
                val value = super.read()
                if (value >= 0) {
                    onBytesRead(1L)
                }
                return value
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val read = super.read(b, off, len)
                if (read > 0) {
                    onBytesRead(read.toLong())
                }
                return read
            }

            private fun onBytesRead(read: Long) {
                totalBytes += read
                if (totalBytes > maxBytes) {
                    throw IllegalStateException("TXT too large for in-memory store: $totalBytes bytes")
                }
            }
        }
    }

    private fun decodeAndNormalize(input: InputStream): String {
        val state = TxtTextNormalizer.StreamState()
        val out = StringBuilder(256 * 1024)
        val buffer = CharArray(16 * 1024)

        InputStreamReader(BufferedInputStream(input, 64 * 1024), charset).use { reader ->
            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break
                TxtTextNormalizer.appendNormalized(
                    input = java.nio.CharBuffer.wrap(buffer, 0, read),
                    state = state
                ) { normalized ->
                    out.append(normalized)
                }
            }
        }

        return out.toString()
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/IndexedTxtTextStore.kt

```kotlin
package com.ireader.engines.txt.internal.storage

import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtTextNormalizer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class IndexedTxtTextStore(
    private val source: DocumentSource,
    private val pfd: ParcelFileDescriptor,
    override val charset: Charset,
    private val ioDispatcher: CoroutineDispatcher,
    private val anchorEveryBytes: Long,
    private val windowCacheChars: Int
) : TxtTextStore {
    private companion object {
        private const val MIN_WINDOW_CACHE_CHARS = 8 * 1024
    }

    private data class CharWindow(
        val startChar: Int,
        val endCharExclusive: Int,
        val text: String
    )

    private val mutex = Mutex()
    private var index: ChunkIndex? = null
    private var charWindowCache: CharWindow? = null

    private val channel: FileChannel by lazy {
        FileInputStream(pfd.fileDescriptor).channel
    }

    override suspend fun totalChars(): Int = withContext(ioDispatcher) {
        ensureIndex().totalChars
    }

    override suspend fun readRange(startChar: Int, endCharExclusive: Int): String = withContext(ioDispatcher) {
        val idx = ensureIndex()
        if (idx.totalChars <= 0) return@withContext ""

        val start = startChar.coerceIn(0, idx.totalChars)
        val end = endCharExclusive.coerceIn(start, idx.totalChars)
        if (start == end) return@withContext ""

        val anchor = idx.anchorFor(start)
        mutex.withLock {
            charWindowCache?.let { window ->
                sliceFromWindow(window, start, end)?.let { return@withLock it }
            }

            val maxWindow = windowCacheChars.coerceAtLeast(MIN_WINDOW_CACHE_CHARS)
            val requestLen = end - start
            if (requestLen > maxWindow) {
                readFromAnchorLocked(
                    anchor = anchor,
                    startChar = start,
                    endChar = end
                )
            } else {
                val window = buildWindowLocked(
                    anchor = anchor,
                    startChar = start,
                    maxWindowChars = maxWindow,
                    totalChars = idx.totalChars
                )
                charWindowCache = window
                sliceFromWindow(window, start, end) ?: window.text
            }
        }
    }

    override suspend fun readChars(startChar: Int, maxChars: Int): String {
        val start = startChar.coerceAtLeast(0)
        val safe = maxChars.coerceAtLeast(0)
        return readRange(start, start + safe)
    }

    override suspend fun readAround(charOffset: Int, maxChars: Int): String = withContext(ioDispatcher) {
        val idx = ensureIndex()
        if (idx.totalChars <= 0) return@withContext ""

        val safe = maxChars.coerceAtLeast(1)
        val half = (safe / 2).coerceAtLeast(1)
        val center = charOffset.coerceIn(0, idx.totalChars)
        val start = (center - half).coerceAtLeast(0)
        val end = (start + safe).coerceAtMost(idx.totalChars)
        readRange(start, end)
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { pfd.close() }
        charWindowCache = null
    }

    private suspend fun ensureIndex(): ChunkIndex {
        index?.let { return it }
        return mutex.withLock {
            index ?: buildIndexLocked().also { index = it }
        }
    }

    private suspend fun buildIndexLocked(): ChunkIndex {
        val header = source.openInputStream().use { input ->
            val buf = ByteArray(4)
            val n = input.read(buf)
            if (n <= 0) ByteArray(0) else buf.copyOf(n)
        }
        val startByteOffset = BomUtil.bomSkipBytes(header, charset).toLong()

        val anchors = ArrayList<ChunkAnchor>(256)
        val streamState = TxtTextNormalizer.StreamState()
        anchors.add(
            ChunkAnchor(
                charOffset = 0,
                byteOffset = startByteOffset,
                lastWasCr = streamState.lastWasCr,
                emittedAny = streamState.emittedAny
            )
        )

        channel.position(startByteOffset)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val byteBuf = ByteBuffer.allocateDirect(256 * 1024)
        val charBuf = CharBuffer.allocate(16 * 1024)

        var decodedChars = 0
        var consumedBytes = 0L
        var lastAnchorBytes = 0L
        var endOfInput = false

        while (!endOfInput) {
            coroutineContext.ensureActive()
            val read = channel.read(byteBuf)
            if (read < 0) {
                endOfInput = true
            }

            byteBuf.flip()
            while (true) {
                charBuf.clear()
                val before = byteBuf.position()
                val result = decoder.decode(byteBuf, charBuf, endOfInput)
                val after = byteBuf.position()
                val consumedNow = (after - before).toLong()
                if (consumedNow > 0) {
                    consumedBytes += consumedNow
                }

                val produced = charBuf.position()
                if (produced > 0) {
                    charBuf.flip()
                    TxtTextNormalizer.appendNormalized(charBuf, streamState) {
                        decodedChars += 1
                    }
                    while (consumedBytes - lastAnchorBytes >= anchorEveryBytes) {
                        anchors.add(
                            ChunkAnchor(
                                charOffset = decodedChars,
                                byteOffset = startByteOffset + consumedBytes,
                                lastWasCr = streamState.lastWasCr,
                                emittedAny = streamState.emittedAny
                            )
                        )
                        lastAnchorBytes += anchorEveryBytes
                    }
                }

                if (result.isOverflow) continue
                if (result.isUnderflow) break
            }
            byteBuf.compact()
        }

        while (true) {
            coroutineContext.ensureActive()
            charBuf.clear()
            val result = decoder.flush(charBuf)
            val produced = charBuf.position()
            if (produced > 0) {
                charBuf.flip()
                TxtTextNormalizer.appendNormalized(charBuf, streamState) {
                    decodedChars += 1
                }
            }
            if (result.isUnderflow) break
        }

        val endByteOffset = channel.size().coerceAtLeast(startByteOffset)
        if (anchors.lastOrNull()?.byteOffset != endByteOffset) {
            anchors.add(
                ChunkAnchor(
                    charOffset = decodedChars,
                    byteOffset = endByteOffset,
                    lastWasCr = streamState.lastWasCr,
                    emittedAny = streamState.emittedAny
                )
            )
        }

        return ChunkIndex(
            anchors = anchors,
            totalChars = decodedChars,
            startByteOffset = startByteOffset
        )
    }

    private suspend fun readFromAnchorLocked(
        anchor: ChunkAnchor,
        startChar: Int,
        endChar: Int
    ): String {
        channel.position(anchor.byteOffset)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val byteBuf = ByteBuffer.allocateDirect(256 * 1024)
        val charBuf = CharBuffer.allocate(16 * 1024)
        val builder = StringBuilder((endChar - startChar).coerceAtMost(64 * 1024))
        val state = TxtTextNormalizer.StreamState(
            lastWasCr = anchor.lastWasCr,
            emittedAny = anchor.emittedAny
        )

        var currentChar = anchor.charOffset
        var endOfInput = false

        while (!endOfInput && currentChar < endChar) {
            coroutineContext.ensureActive()
            val read = channel.read(byteBuf)
            if (read < 0) {
                endOfInput = true
            }

            byteBuf.flip()
            while (true) {
                charBuf.clear()
                val result = decoder.decode(byteBuf, charBuf, endOfInput)
                val produced = charBuf.position()
                if (produced > 0) {
                    charBuf.flip()
                    currentChar = appendNormalizedRange(
                        buffer = charBuf,
                        state = state,
                        currentChar = currentChar,
                        startChar = startChar,
                        endChar = endChar,
                        out = builder
                    )
                }

                if (currentChar >= endChar) break
                if (result.isOverflow) continue
                if (result.isUnderflow) break
            }
            byteBuf.compact()
        }

        if (currentChar < endChar) {
            while (true) {
                coroutineContext.ensureActive()
                charBuf.clear()
                val result = decoder.flush(charBuf)
                val produced = charBuf.position()
                if (produced > 0) {
                    charBuf.flip()
                    currentChar = appendNormalizedRange(
                        buffer = charBuf,
                        state = state,
                        currentChar = currentChar,
                        startChar = startChar,
                        endChar = endChar,
                        out = builder
                    )
                }
                if (result.isUnderflow || currentChar >= endChar) break
            }
        }

        return builder.toString()
    }

    private suspend fun buildWindowLocked(
        anchor: ChunkAnchor,
        startChar: Int,
        maxWindowChars: Int,
        totalChars: Int
    ): CharWindow {
        val windowEnd = (startChar + maxWindowChars).coerceAtMost(totalChars)
        val text = readFromAnchorLocked(
            anchor = anchor,
            startChar = startChar,
            endChar = windowEnd
        )
        return CharWindow(
            startChar = startChar,
            endCharExclusive = startChar + text.length,
            text = text
        )
    }

    private fun appendNormalizedRange(
        buffer: CharBuffer,
        state: TxtTextNormalizer.StreamState,
        currentChar: Int,
        startChar: Int,
        endChar: Int,
        out: StringBuilder
    ): Int {
        var cursor = currentChar
        TxtTextNormalizer.appendNormalized(buffer, state) { normalizedChar ->
            if (cursor >= endChar) return@appendNormalized
            if (cursor >= startChar) {
                out.append(normalizedChar)
            }
            cursor += 1
        }
        return cursor
    }

    private fun sliceFromWindow(
        window: CharWindow,
        startChar: Int,
        endChar: Int
    ): String? {
        if (startChar < window.startChar || endChar > window.endCharExclusive) return null
        val localStart = startChar - window.startChar
        val localEnd = endChar - window.startChar
        if (localStart !in 0..window.text.length) return null
        if (localEnd !in localStart..window.text.length) return null
        return window.text.substring(localStart, localEnd)
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/TxtTextStore.kt

```kotlin
package com.ireader.engines.txt.internal.storage

import java.io.Closeable
import java.nio.charset.Charset

internal interface TxtTextStore : Closeable {
    val charset: Charset

    suspend fun totalChars(): Int

    suspend fun readRange(startChar: Int, endCharExclusive: Int): String

    suspend fun readChars(startChar: Int, maxChars: Int): String

    suspend fun readAround(charOffset: Int, maxChars: Int): String
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/storage/TxtTextStoreFactory.kt

```kotlin
package com.ireader.engines.txt.internal.storage

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.TxtEngineConfig
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineDispatcher

internal object TxtTextStoreFactory {

    suspend fun create(
        source: DocumentSource,
        charset: Charset,
        ioDispatcher: CoroutineDispatcher,
        config: TxtEngineConfig
    ): TxtTextStore {
        val inMemoryThreshold = config.inMemoryThresholdBytes.coerceAtLeast(1L)
        val size = source.sizeBytes
        if (size != null && size <= inMemoryThreshold) {
            return InMemoryTxtTextStore(
                source = source,
                charset = charset,
                ioDispatcher = ioDispatcher,
                maxBytes = inMemoryThreshold
            )
        }

        val pfd = source.openFileDescriptor("r")
        if (pfd != null) {
            val anchorEveryBytes = when {
                size == null -> 1L * 1024L * 1024L
                size <= 32L * 1024L * 1024L -> 512L * 1024L
                size <= 256L * 1024L * 1024L -> 1L * 1024L * 1024L
                else -> 2L * 1024L * 1024L
            }
            return IndexedTxtTextStore(
                source = source,
                pfd = pfd,
                charset = charset,
                ioDispatcher = ioDispatcher,
                anchorEveryBytes = anchorEveryBytes,
                windowCacheChars = config.indexedWindowCacheChars
            )
        }

        // 大文件且不可 seek 直接失败，避免高风险 OOM。
        if (size != null && size > inMemoryThreshold) {
            throw IllegalStateException(
                "TXT too large and source is not seekable: $size bytes"
            )
        }

        return InMemoryTxtTextStore(
            source = source,
            charset = charset,
            ioDispatcher = ioDispatcher,
            maxBytes = inMemoryThreshold
        )
    }
}
```

## engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/util/ReaderErrors.kt

```kotlin
package com.ireader.engines.txt.internal.util

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException

internal fun Throwable.toReaderError(): ReaderError {
    return when (this) {
        is ReaderError -> this
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(message = message, cause = this)
    }
}
```

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ireader"
include(":app")
include(":core:common")
include(":core:common-android")
include(":core:model")
include(":core:files")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:navigation")
include(":core:testing")
include(":core:work")
include(":core:reader:api")
include(":core:reader:runtime")
include(":engines:engine-common")
include(":engines:txt")
include(":engines:epub")
include(":engines:pdf")
include(":feature:library")
include(":feature:reader")
include(":feature:annotations")
include(":feature:search")
include(":feature:settings")
 
```

