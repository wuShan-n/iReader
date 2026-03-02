package com.ireader.engines.epub.internal.readium

import androidx.fragment.app.Fragment
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.ReaderNavigatorAdapter
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

internal class ReadiumNavigatorAdapter(
    private val publication: Publication,
    private val navigatorFactory: EpubNavigatorFactory,
    initialLocator: org.readium.r2.shared.publication.Locator?,
    initialPreferences: EpubPreferences,
    ioDispatcher: CoroutineDispatcher
) : ReaderNavigatorAdapter {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var locatorJob: Job? = null

    private var navigator: EpubNavigatorFragment? = null
    private var pendingInitialLocator: org.readium.r2.shared.publication.Locator? = initialLocator
    private var currentPreferences: EpubPreferences = initialPreferences

    private val _locatorFlow = MutableStateFlow(initialLocator?.let(ReadiumLocatorMapper::toModel))
    override val locatorFlow: StateFlow<Locator?> = _locatorFlow

    @OptIn(ExperimentalReadiumApi::class)
    override fun createFragment(): Fragment {
        navigator?.let { return it }

        val listener = object : EpubNavigatorFragment.Listener {
            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                // External handling stays in app layer.
            }
        }

        val paginationListener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(
                pageIndex: Int,
                totalPages: Int,
                locator: org.readium.r2.shared.publication.Locator
            ) {
                _locatorFlow.value = ReadiumLocatorMapper.toModel(locator)
            }
        }

        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = pendingInitialLocator,
            initialPreferences = currentPreferences,
            listener = listener,
            paginationListener = paginationListener
        )

        val created = fragmentFactory.instantiate(
            checkNotNull(EpubNavigatorFragment::class.java.classLoader),
            EpubNavigatorFragment::class.java.name
        ) as EpubNavigatorFragment
        navigator = created

        locatorJob?.cancel()
        locatorJob = scope.launch {
            created.currentLocator.collect { locator ->
                _locatorFlow.value = ReadiumLocatorMapper.toModel(locator)
            }
        }

        return created
    }

    override suspend fun goTo(locator: Locator): ReaderResult<Unit> {
        val target = ReadiumLocatorMapper.toReadium(publication, locator)
            ?: return ReaderResult.Err(
                ReaderError.CorruptOrInvalid("Invalid EPUB locator: ${locator.value}")
            )

        val currentNavigator = navigator
        if (currentNavigator == null) {
            pendingInitialLocator = target
            _locatorFlow.value = ReadiumLocatorMapper.toModel(target)
            return ReaderResult.Ok(Unit)
        }

        return if (currentNavigator.go(target, animated = false)) {
            ReaderResult.Ok(Unit)
        } else {
            ReaderResult.Err(ReaderError.Internal("Failed to navigate to locator"))
        }
    }

    override suspend fun submitConfig(config: RenderConfig.ReflowText): ReaderResult<Unit> {
        val delta = ReadiumPreferencesMapper.fromRenderConfig(config)
        currentPreferences += delta
        navigator?.submitPreferences(currentPreferences)
        return ReaderResult.Ok(Unit)
    }

    override suspend fun next(): ReaderResult<Unit> {
        val currentNavigator = navigator
            ?: return ReaderResult.Err(ReaderError.Internal("Navigator is not attached"))
        return if (currentNavigator.goForward(animated = true)) {
            ReaderResult.Ok(Unit)
        } else {
            ReaderResult.Err(ReaderError.Internal("Cannot go to next page"))
        }
    }

    override suspend fun prev(): ReaderResult<Unit> {
        val currentNavigator = navigator
            ?: return ReaderResult.Err(ReaderError.Internal("Navigator is not attached"))
        return if (currentNavigator.goBackward(animated = true)) {
            ReaderResult.Ok(Unit)
        } else {
            ReaderResult.Err(ReaderError.Internal("Cannot go to previous page"))
        }
    }

    override fun close() {
        locatorJob?.cancel()
        scope.cancel()
    }
}
