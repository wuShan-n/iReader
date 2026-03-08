# TXT 阅读时序图

说明：
- 以下内容只依据实际源码梳理，未参考 `docs/` 下文档。
- 范围覆盖 `feature/reader` 打开阅读页、`core/reader/runtime` 分发到 TXT 引擎、`engines/txt` 的最小打开、会话创建、分页渲染、翻页、后台产物补齐、目录、搜索、选区、批注、换行修正。
- 2026-03-08 更新：`feature/reader` 里的会话前置条件与 collector 生命周期已收口到 `ReaderSessionInteractor`；TXT 会话已改为 `TxtSessionFacade + TxtArtifactCoordinator + TxtNavigationService + TxtPaginationService + TxtPageExtrasService`。

主要代码入口：
- `feature/reader/src/main/kotlin/com/ireader/feature/reader/ui/ReaderScreen.kt`
- `feature/reader/src/main/kotlin/com/ireader/feature/reader/presentation/ReaderViewModel.kt`
- `core/data/src/main/kotlin/com/ireader/core/data/reader/ReaderLaunchRepository.kt`
- `core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/DefaultReaderRuntime.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/TxtEngine.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtOpener.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/open/TxtDocument.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/session/TxtSessionFacade.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/artifact/TxtArtifactCoordinator.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/render/TxtController.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/render/PaginationCoordinator.kt`
- `engines/txt/src/main/kotlin/com/ireader/engines/txt/internal/render/TxtPageFitter.kt`

## 1. 打开 TXT 阅读会话

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant Screen as ReaderScreen
    participant VM as ReaderViewModel
    participant Session as ReaderSessionInteractor
    participant BookRepo as BookRepo
    participant SourceResolver as BookSourceResolver
    participant ProgressRepo as ProgressRepo
    participant LocatorCodec as LocatorCodec
    participant LaunchRepo as ReaderLaunchRepository
    participant Settings as ReaderSettingsStore
    participant Runtime as DefaultReaderRuntime
    participant Detector as DefaultBookFormatDetector
    participant Registry as EngineRegistry
    participant Engine as TxtEngine
    participant Opener as TxtOpener
    participant Encoding as EncodingDetector
    participant Source as DocumentSource
    participant Document as TxtDocument
    participant BlockIndex as TxtBlockIndex
    participant TxtLocator as TxtStableLocatorCodec
    participant BreakIndex as SoftBreakIndex
    participant TextProjectionEngine as TextProjectionEngine
    participant BlockStore as BlockStore
    participant Controller as TxtController
    participant TxtSession as TxtSessionFacade

    User->>Screen: 进入阅读页
    Screen->>VM: dispatch(Start(bookId, locatorArg))
    Screen->>VM: dispatch(TextLayouterFactoryChanged(factory))
    Note over Screen,VM: LayoutChanged(constraints) 由 ReaderScaffold 在视口可用后异步上送

    VM->>VM: closeSession()
    VM->>Session: closeCurrent(saveProgress)
    VM->>BookRepo: getRecordById(bookId)
    BookRepo-->>VM: BookRecord?

    alt 书籍不存在
        VM-->>Screen: 更新错误状态并返回
    else 书籍存在
        VM->>SourceResolver: resolve(book)
        SourceResolver-->>VM: DocumentSource?

        alt 文件源缺失
            VM->>BookRepo: setIndexState(MISSING, "File not found")
            VM-->>Screen: 更新错误状态并返回
        else 文件源可用
            opt 路由未显式提供 locator
                VM->>ProgressRepo: getByBookId(bookId)
                ProgressRepo-->>VM: locatorJson?
                VM->>LocatorCodec: decode(locatorJson)
                LocatorCodec-->>VM: historyLocator?
            end

            VM->>LaunchRepo: openBook(bookId, locatorArg, password)
            LaunchRepo->>Settings: getOpenSettingsSnapshot()
            Settings-->>LaunchRepo: open settings snapshot
            LaunchRepo->>Runtime: openSession(source, options, initialLocator, initialConfig=reflowConfig+appearance)

            Runtime->>Detector: detect(source, hintFormat=TXT)
            Note right of Detector: hint 存在时直接返回 TXT
            Detector-->>Runtime: ReaderResult.Ok(TXT)
            Runtime->>Registry: engineFor(TXT)
            Registry-->>Runtime: TxtEngine
            Runtime->>Engine: open(source, options)
            Engine->>Opener: openMinimal(source, options)

            Opener->>Opener: computeSampleHash(source)
            Opener->>Opener: computeDocumentId(source, sampleHash)
            Opener->>Opener: buildFiles(documentId)
            Opener->>Encoding: detect(source, options.textEncoding)
            Encoding-->>Opener: charset

            alt text.store/meta 命中且校验通过
                Opener->>Opener: tryLoadCached(files, source, sampleHash, charset)
                Opener->>Opener: refreshArtifactManifest()
            else 缓存缺失或失效
                Opener->>Source: openInputStream()
                Source-->>Opener: InputStream
                Opener->>Opener: writeUtf16Content()<br/>规范化字符/换行并统计 hardWrapLikely、typicalLineLength
                Opener->>Opener: 写入 text.store / meta.json
                Opener->>Opener: clearDerivedArtifacts()
                Opener->>Opener: write initial manifest.json
            end

            Opener-->>Engine: TxtOpenResult(documentId, files, meta)
            Engine->>Document: createDocument(openResult, source, options)
            Document-->>Runtime: ReaderDocument(TxtDocument)

            Runtime->>Document: createSession(initialLocator, sanitized ReflowText)
            Document->>BlockIndex: openIfValid(files.blockIdx, meta) or minimal(meta)
            Document->>TxtLocator: parseOffset(initialLocator)
            Document->>BreakIndex: openIfValid(files.breakMap, meta, BALANCED)
            BreakIndex-->>Document: breakIndex?
            Document->>TextProjectionEngine: new(store, files, meta, breakIndex)
            Document->>BlockStore: new(store, blockIndex, projectionEngine)
            Document->>Controller: new(initialOffset, initialConfig, blockIndex, projectionEngine, blockStore, ...)
            Document->>TxtSession: create(controller, files, meta, blockIndex, projectionEngine, blockStore, ...)
            TxtSession-->>Document: ReaderSession(TxtSessionFacade)

            Runtime-->>LaunchRepo: ReaderHandle
            LaunchRepo-->>VM: ReaderHandle

            VM->>Session: attach(bookId, handle, openEpoch)
            VM->>VM: startSessionCollectors(handle, bookId)
            VM->>Controller: setTextLayouterFactory(factory) [若 factory 已到位]

            alt LayoutConstraints 已到位
                VM->>Controller: setLayoutConstraints(constraints)
                VM->>VM: requestRenderIfReady(immediate=true)
            else LayoutConstraints 尚未到位
                Note over VM: 等待 LayoutChanged 后才允许真正 render
            end
        end
    end
```

关键点：
- `ReaderScreen` 的 `Start`、`TextLayouterFactoryChanged`、`LayoutChanged` 是三条独立输入，现在由 `ReaderSessionInteractor` 统一维护会话、布局、排版器三者的前置条件。
- `TxtOpener.openMinimal()` 只保证 `text.store`、`meta.json`、`manifest.json` 足够打开阅读，会话可先建立，`block.idx` 和 `break.map` 可以稍后后台补齐。
- `ReaderLaunchRepository` 对 TXT 会直接使用 `reflowConfig.withReaderAppearance(displayPrefs)` 作为初始配置。

## 2. 首次渲染与翻页

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant Screen as ReaderScaffold
    participant VM as ReaderViewModel
    participant Gate as ReaderSessionInteractor
    participant RenderQ as RenderCoordinator
    participant Controller as TxtController
    participant Pagination as PaginationCoordinator
    participant Cache as TxtPageCache
    participant Checkpoint as TxtLayoutCheckpointStore
    participant Fitter as TxtPageFitter
    participant BlockStore as BlockStore
    participant TextProjectionEngine as TextProjectionEngine
    participant Store as Utf16TextStore
    participant Layouter as TextLayouter
    participant Adjuster as TxtPageEndAdjuster
    participant LinkDetector as LinkDetector
    participant Annotation as AnnotationProvider
    participant UI as Reader UI

    Screen->>VM: dispatch(LayoutChanged(constraints))
    VM->>Gate: updateLayout(constraints)
    VM->>Controller: setLayoutConstraints(constraints)
    VM->>RenderQ: requestImmediateRender(LAYOUT) 或 requestRender(...)
    RenderQ->>VM: onRender()
    VM->>Gate: snapshotIfReady()

    alt handle/layout/factory 任一缺失
        Gate-->>VM: null
        VM-->>UI: 暂不渲染
    else 条件齐备
        Gate-->>VM: ReaderViewportSnapshot
        VM->>Controller: render(RenderPolicy.Default)

        Controller->>Pagination: pageAt(currentStart, allowCache=true)
        alt 页缓存命中
            Pagination->>Cache: get(startOffset)
            Cache-->>Pagination: TxtPageSlice
        else 页缓存未命中
            Pagination->>Fitter: fitPage(startOffset, config, constraints)
            loop 最多 8 个 paragraph batch
                Fitter->>BlockStore: readParagraphs(startAnchor, codeUnitBudget)
                BlockStore->>Store: readString/readBlock()
                BlockStore->>TextProjectionEngine: stateAt()/projectRange()
                TextProjectionEngine-->>BlockStore: 逻辑段落投射结果
                BlockStore-->>Fitter: LogicalParagraph batch
                Fitter->>Layouter: measure(candidateText, TextLayoutInput)
                alt 当前批次仍能放入页面
                    Fitter->>Fitter: appendProjection()
                else 当前段落溢出
                    Fitter->>Fitter: refineParagraphFit() 二分查找可容纳字符数
                    Fitter->>Fitter: appendProjection(partial)
                    Fitter->>Adjuster: adjust(rawWindow, measuredEnd)
                end
            end
            Fitter-->>Pagination: TxtPageSlice(start, end, text, projectedBoundaryToRawOffsets)
            Pagination->>Checkpoint: record(startOffset)
            Pagination->>Cache: put(slice)
        end

        Controller->>LinkDetector: detect(pageText, pageStartOffset, mapping)
        opt 当前会话支持批注且已有 annotations
            Controller->>Annotation: decorationsFor(AnnotationQuery(range))
            Annotation-->>Controller: decorations
        end

        Controller-->>VM: RenderPage(Text + mapping + links + decorations + metrics)
        Controller->>Controller: emit ReaderEvent.Rendered(pageId, metrics)
        Controller->>Controller: emit ReaderEvent.PageChanged(locator)
        VM->>VM: commitPageIfCurrent()
        VM->>UI: replacePage(nextPage)

        opt RenderPolicy.prefetchNeighbors > 0
            Controller->>Pagination: prefetchAround(currentStart, count)
        end
    end

    User->>VM: Next / Prev / GoTo / GoToProgress
    VM->>Controller: next(policy) / prev(policy) / goTo(locator, policy) / goToProgress(percent, policy)
    Controller->>Pagination: pageAt() / previousStart() / startForProgress()
    Pagination-->>Controller: TxtPageSlice
    Controller-->>VM: RenderPage
    VM->>UI: replacePage(nextPage)
```

关键点：
- `RenderCoordinator` 会把普通请求做 `24ms debounce`，立即请求走 `immediateRequests`。
- TXT 的 `document.capabilities.fixedLayout == false`，所以 `ReaderViewModel` 中仅对固定版式使用的 DRAFT/FINAL 二段渲染分支不会进入 TXT 主链路。
- `TxtController.next/prev` 仍要求 layouter 已绑定，但具体前置条件与 viewport 绑定缓存已经从 `ReaderViewModel` 下沉到 `ReaderSessionInteractor`。

## 3. 首次可见后后台补齐产物

```mermaid
sequenceDiagram
    autonumber
    participant TxtSession as TxtSessionFacade
    participant Artifact as TxtArtifactCoordinator
    participant Controller as TxtController
    participant Manifest as TxtArtifactManifest
    participant BlockIndex as TxtBlockIndex
    participant Store as Utf16TextStore
    participant BreakBuilder as SoftBreakIndexBuilder
    participant BreakIndex as SoftBreakIndex
    participant TextProjectionEngine as TextProjectionEngine

    Note over TxtSession: init 中启动 sessionScope 协程
    TxtSession->>Controller: 监听 events.filterIsInstance<Rendered>().first()
    Controller-->>TxtSession: ReaderEvent.Rendered
    TxtSession->>Artifact: warmupProviders()
    TxtSession->>Artifact: ensureBackgroundArtifactsReady()

    Artifact->>Manifest: readIfValid(files.manifestJson, meta)
    alt manifest 缺失或无效
        Manifest-->>Artifact: initial(meta)
    else manifest 可用
        Manifest-->>Artifact: manifest
    end

    alt block.idx 未就绪
        Artifact->>Store: reopen Utf16TextStore(files.textStore)
        Artifact->>BlockIndex: buildIfNeeded(file, lockFile, store, meta)
        BlockIndex-->>Artifact: block.idx built
        Artifact->>Manifest: markBlockIndexReady(version=1)
        Artifact->>Manifest: write manifest.json
    else block.idx 已就绪
        Note over Artifact: 跳过 block index 构建
    end

    alt break.map 未就绪
        Artifact->>BreakBuilder: buildIfNeeded(files, meta, BALANCED)
        Artifact->>BreakIndex: openIfValid(files.breakMap, meta, BALANCED)
        BreakIndex-->>Artifact: builtIndex?
        alt break.map 打开成功
            Artifact->>TextProjectionEngine: attachIndex(builtIndex)
            Artifact->>Manifest: markBreakMapReady(version=7)
            Artifact->>Manifest: write manifest.json
        else break.map 仍不可用
            Note over Artifact: manifest 保持 breakMapReady=false
        end
    else projectionEngine 已有 indexed breaks
        opt manifest 还没标记 ready
            Artifact->>Manifest: markBreakMapReady(version=7)
            Artifact->>Manifest: write manifest.json
        end
    end
```

关键点：
- 这条后台链路是在首个 `Rendered` 事件之后才启动，不阻塞首屏。
- 当前实现由 `TxtSessionFacade` 在首个 `Rendered` 后驱动 `TxtArtifactCoordinator`；搜索索引允许后台 warmup，目录仍保持按需构建。

## 4. 目录与搜索

### 4.1 目录加载

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant VM as ReaderViewModel
    participant Outline as TxtOutlineProvider
    participant BlockStore as BlockStore
    participant TextProjectionEngine as TextProjectionEngine
    participant Detector as ChapterDetector
    participant UI as Reader UI

    User->>VM: OpenToc / SetMenuTab(Toc)
    VM->>VM: loadTocIfNeeded(force?)
    VM->>Outline: getOutline()

    alt persistOutline=true 且 outline.idx 命中
        Outline->>Outline: loadFromCache()
        Outline-->>VM: OutlineNode list
    else 走实时检测
        loop 最多 64 个 batch
            Outline->>BlockStore: readParagraphs(cursor, OUTLINE_SCAN_BUDGET=64000)
            BlockStore->>TextProjectionEngine: projectRange()
            TextProjectionEngine-->>BlockStore: paragraph.displayText
            Outline->>Detector: isChapterTitle(title)
            Detector-->>Outline: true/false
            Outline->>Outline: confidenceFor(title, paragraphText)
        end
        opt persistOutline=true
            Outline->>Outline: saveToCache(outline.idx)
        end
        Outline-->>VM: OutlineNode list
    end

    VM->>VM: flattenOutlineIterative()
    VM->>UI: TocState(items, isLoading=false)
```

### 4.2 文内搜索

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant VM as ReaderViewModel
    participant Search as TxtSearchProviderPro
    participant Bloom as TrigramBloomIndex
    participant TextProjectionEngine as TextProjectionEngine
    participant Matcher as KmpMatcher
    participant Locator as TxtStableLocatorCodec
    participant Acc as SearchResultAccumulator
    participant UI as Reader UI

    User->>VM: ExecuteSearch(query)
    VM->>VM: cancelActiveSearch()
    VM->>Search: search(query, SearchOptions(maxHits=300))

    Search->>Bloom: openIfValid(files.searchIdx, meta)
    alt bloom 命中且 query.length >= 3
        loop candidate blocks
            Search->>Bloom: mayContainAll(blockId, trigramHashes)
            alt 可能命中
                Search->>TextProjectionEngine: projectRange(rangeStart, rangeEnd)
                Search->>Matcher: forEachMatch(displayText)
                Matcher-->>Search: matchIndex*
                Search->>Locator: rangeForOffsets(globalStart, globalEnd)
                Search-->>VM: emit SearchHit(range, excerpt)
            end
        end
    else bloom 不可用或 query 太短
        opt projectionEngine.hasIndexedBreaks() 且 query.length >= 3
            Search->>Search: scheduleBloomBuild() 后台构建 search.idx
        end
        loop blocks from startBlock
            Search->>TextProjectionEngine: projectRange(rangeStart, rangeEnd)
            Search->>Matcher: forEachMatch(displayText)
            Search->>Locator: rangeForOffsets(globalStart, globalEnd)
            Search-->>VM: emit SearchHit(range, excerpt)
        end
    end

    VM->>Acc: add(batch)
    Acc-->>VM: 分批回推结果
    VM->>UI: SearchState(results += batch, isSearching=false after flow end)
```

关键点：
- 搜索不是直接扫原始 `text.store`，而是先经过 `TextProjectionEngine.projectRange()`，因此搜索文本与页面显示文本保持一致。
- `TxtSearchProviderPro` 在 bloom index 不可用时会退化为全块扫描，不会阻塞功能可用性。

## 5. 选区、批注、换行修正

### 5.1 选区

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant VM as ReaderViewModel
    participant Selection as TxtSelectionManager
    participant Locator as TxtStableLocatorCodec
    participant TextProjectionEngine as TextProjectionEngine

    User->>VM: SelectionStart(locator)
    VM->>Selection: start(locator)
    Selection->>Locator: parseOffset(locator)
    Selection->>Selection: buildSelection(anchorOffset, anchorOffset)
    Selection-->>VM: ReaderResult.Ok(Unit)

    User->>VM: SelectionUpdate(locator)
    VM->>Selection: update(locator)
    Selection->>Locator: parseOffset(locator)
    Selection->>TextProjectionEngine: projectRange(startOffset, cappedEndOffset)
    Selection-->>VM: ReaderResult.Ok(Unit)

    User->>VM: SelectionFinish / ClearSelection
    VM->>Selection: finish() / clear()
```

### 5.2 创建批注

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant VM as ReaderViewModel
    participant Selection as TxtSelectionManager
    participant DraftFactory as ReaderAnnotationDraftFactory
    participant Annotation as AnnotationProvider
    participant Controller as TxtController

    User->>VM: CreateAnnotation
    VM->>Selection: currentSelection()
    Selection-->>VM: Selection?
    VM->>DraftFactory: create(selection or fallbackLocator=currentPage.locator)
    DraftFactory-->>VM: AnnotationDraft
    VM->>Annotation: create(draft)

    alt 创建成功
        VM->>Selection: clearSelection()
        VM->>Selection: clear()
        VM->>Controller: invalidate(CONTENT_CHANGED)
        VM->>Controller: render() 当前页
        VM-->>User: Snackbar("已添加批注")
    else 创建失败
        VM-->>User: Snackbar("创建批注失败")
    end
```

注：
- 当前实现会在批注创建成功后 `invalidate(CONTENT_CHANGED)`，并立即重绘当前页，确保 decoration 同步到 UI。

### 5.3 TXT 换行修正

```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant VM as ReaderViewModel
    participant PatchSupport as TxtSessionFacade(TextBreakPatchSupport)
    participant Controller as TxtController
    participant Locator as TxtStableLocatorCodec
    participant TextProjectionEngine as TextProjectionEngine
    participant Pagination as PaginationCoordinator
    participant Outline as TxtOutlineProvider
    participant Search as TxtSearchProviderPro
    participant UI as Reader UI

    User->>VM: ApplyTextBreakPatch(direction, state)
    VM->>VM: cancelActiveSearch() / cancelFinalRender()
    VM->>PatchSupport: applyBreakPatch(currentPage.locator, direction, state)
    PatchSupport->>Controller: applyBreakPatch(locator, direction, state)
    Controller->>Locator: parseOffset(locator)
    Controller->>Controller: findNearestNewlineOffset()
    Controller->>TextProjectionEngine: patch(newlineOffset, BreakMapState)
    Controller->>Pagination: invalidateProjectedContent()
    Controller->>Controller: renderSnapshotLocked(RenderPolicy.Default)
    Controller-->>PatchSupport: RenderPage
    PatchSupport->>Outline: invalidate()
    PatchSupport->>Search: invalidate()
    PatchSupport-->>VM: RenderPage
    VM->>VM: commitPageIfCurrent()
    opt 当前菜单停留在 TOC
        VM->>VM: loadTocIfNeeded(force=true)
    end
    VM->>UI: replacePage(nextPage) + Snackbar

    User->>VM: ClearTextBreakPatches
    VM->>PatchSupport: clearBreakPatches()
    PatchSupport->>Controller: clearBreakPatches()
    Controller->>TextProjectionEngine: clearPatches()
    Controller->>Pagination: invalidateProjectedContent()
    Controller-->>VM: RenderPage
    VM->>UI: replacePage(nextPage) + Snackbar
```

关键点：
- 换行修正真正修改的是 `TextProjectionEngine` 的 patch 层，不会回写原始 `text.store`。
- 一旦 patch 变化，`TxtSessionFacade` 会通过 `TxtArtifactCoordinator` 让目录缓存和搜索缓存失效，因为章节识别与搜索都依赖投射后的显示文本。

## 6. 汇总

实际代码中的 TXT 阅读主链路可以概括为：

1. `ReaderScreen` 先后把 `Start`、`TextLayouterFactoryChanged`、`LayoutChanged` 送入 `ReaderViewModel`。
2. `ReaderViewModel.open()` 负责拿书、找源、恢复历史定位，并通过 `ReaderLaunchRepository -> DefaultReaderRuntime -> TxtEngine -> TxtOpener` 打开最小可读文档。
3. `TxtDocument.createSession()` 在会话阶段组装 `TxtController + TextProjectionEngine + BlockStore + TxtSessionFacade`。
4. `ReaderSessionInteractor` 等待会话、布局、排版器三者都准备好后，`RenderCoordinator` 才调用 `TxtController.render()`。
5. `TxtController` 现在把导航、分页后台任务、页面 links/decorations 拆给 `TxtNavigationService + TxtPaginationService + TxtPageExtrasService`，自身主要负责 controller API 与 `RenderPage` 组装。
6. 首个 `Rendered` 事件之后，`TxtSessionFacade` 通过 `TxtArtifactCoordinator` 后台补齐 `block.idx` 与 `break.map`，后续目录、搜索、换行修正、选区都建立在这些产物和 `TextProjectionEngine` 的投射能力之上。
