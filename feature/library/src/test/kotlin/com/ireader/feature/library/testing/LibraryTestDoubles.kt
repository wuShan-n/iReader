package com.ireader.feature.library.testing

import com.ireader.core.data.book.BookMaintenanceScheduler
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.LibraryBookRow
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.files.importing.ImportJobState
import com.ireader.core.files.importing.ImportManager
import com.ireader.core.files.importing.ImportRequest
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

typealias MethodHandler = (args: Array<Any?>?) -> Any?

@Suppress("UNCHECKED_CAST")
fun <T> proxyInterface(
    clazz: Class<T>,
    handler: (method: Method, args: Array<Any?>?) -> Any?
): T {
    return Proxy.newProxyInstance(
        clazz.classLoader,
        arrayOf(clazz)
    ) { _, method, args ->
        handler(method, args)
    } as T
}

fun fakeBookDao(overrides: Map<String, MethodHandler> = emptyMap()): BookDao {
    return proxyInterface(BookDao::class.java) { method, args ->
        overrides[method.name]?.invoke(args) ?: when (method.name) {
            "upsert" -> 1L
            "findByFingerprint", "getById", "getByDocumentId" -> null
            "listAll" -> emptyList<BookEntity>()
            "deleteById" -> Unit
            "observeById" -> flowOf(null)
            "observeMissing" -> flowOf(emptyList<BookEntity>())
            "observeLibrary" -> flowOf(emptyList<LibraryBookRow>())
            "updateIndexState",
            "updateLastOpened",
            "updateFavorite",
            "updateReadingStatus",
            "updateSource",
            "updateMetadata" -> Unit
            else -> error("Unexpected BookDao call: ${method.name}")
        }
    }
}

fun fakeCollectionDao(overrides: Map<String, MethodHandler> = emptyMap()): CollectionDao {
    return proxyInterface(CollectionDao::class.java) { method, args ->
        overrides[method.name]?.invoke(args) ?: when (method.name) {
            "upsert" -> 1L
            "getById", "getByName" -> null
            "observeAll" -> flowOf(emptyList<CollectionEntity>())
            "deleteById" -> Unit
            else -> error("Unexpected CollectionDao call: ${method.name}")
        }
    }
}

fun fakeBookCollectionDao(overrides: Map<String, MethodHandler> = emptyMap()): BookCollectionDao {
    return proxyInterface(BookCollectionDao::class.java) { method, args ->
        overrides[method.name]?.invoke(args) ?: when (method.name) {
            "insert" -> 1L
            "delete", "deleteAllForBook" -> Unit
            "listCollectionIdsForBook" -> emptyList<Long>()
            else -> error("Unexpected BookCollectionDao call: ${method.name}")
        }
    }
}

fun fakeProgressDao(overrides: Map<String, MethodHandler> = emptyMap()): ProgressDao {
    return proxyInterface(ProgressDao::class.java) { method, args ->
        overrides[method.name]?.invoke(args) ?: when (method.name) {
            "upsert", "deleteByBookId" -> Unit
            "getByBookId" -> null
            "observeByBookId" -> flowOf(null)
            else -> error("Unexpected ProgressDao call: ${method.name}")
        }
    }
}

class FakeBookMaintenanceScheduler : BookMaintenanceScheduler {
    val reindexRequests: MutableList<List<Long>> = mutableListOf()
    var missingCheckCount: Int = 0

    override fun enqueueReindex(bookIds: List<Long>) {
        reindexRequests += bookIds
    }

    override fun enqueueMissingCheck() {
        missingCheckCount += 1
    }
}

class FakeImportManager : ImportManager {
    var enqueueError: Throwable? = null
    var nextJobId: String = "job-1"
    var lastRequest: ImportRequest? = null
    val flows: MutableMap<String, Flow<ImportJobState>> = mutableMapOf()
    val cancelCalls: MutableList<String> = mutableListOf()

    override suspend fun enqueue(request: ImportRequest): String {
        enqueueError?.let { throw it }
        lastRequest = request
        return nextJobId
    }

    override fun observe(jobId: String): Flow<ImportJobState> {
        return flows[jobId] ?: emptyFlow()
    }

    override suspend fun cancel(jobId: String) {
        cancelCalls += jobId
    }
}

class MutableImportJobFlow(initial: ImportJobState) {
    private val state = MutableStateFlow(initial)
    val flow: Flow<ImportJobState> = state

    fun emit(value: ImportJobState) {
        state.value = value
    }
}
