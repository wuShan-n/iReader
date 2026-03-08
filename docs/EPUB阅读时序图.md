# EPUB 阅读时序图

本文基于当前仓库中已经落地的 EPUB 阅读实现整理，覆盖 `feature/reader`、`core/reader/runtime`、`engines/epub` 三层在“阅读期”的真实调用链。

## 文档边界

- 只覆盖阅读期链路，不包含导入、索引、书架扫描。
- 只画仓库内已经验证过的调用路径，不补推测性的后台任务。
- EPUB 当前使用 Readium Navigator 作为嵌入式阅读内核，Compose 层拿到的是 `RenderContent.Embedded`，不是 HTML 页面快照。

## 关键代码锚点

- `feature/reader/src/main/kotlin/com/ireader/feature/reader/ui/ReaderScreen.kt`
- `feature/reader/src/main/kotlin/com/ireader/feature/reader/presentation/ReaderViewModel.kt`
- `core/data/src/main/kotlin/com/ireader/core/data/reader/ReaderLaunchRepository.kt`
- `core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/DefaultReaderRuntime.kt`
- `engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/open/EpubOpener.kt`
- `engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/controller/EpubController.kt`
- `engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/session/EpubSession.kt`

## 1. 打开阅读页到首屏可读

这一段描述从 `ReaderScreen` 进入阅读页，到 `ReaderViewModel` 拿到 `ReaderHandle` 的完整打开链路。

几个关键事实：

- 当前书籍格式会作为 `hintFormat` 直接传给 `DefaultBookFormatDetector`，对于书架内已知 EPUB 不会再做内容 sniff。
- EPUB 不会像 TXT 那样提前构造 `initialConfig`，而是在 `DefaultReaderRuntime.openSession()` 内根据 `DocumentCapabilities` 决定是 `fixedConfig` 还是 `reflowConfig`。
- `EpubSession.create()` 同时组装 controller 和各类 provider，后续目录、搜索、文本、资源、批注都从同一个 `ReaderHandle` 暴露出去。

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant Screen as ReaderScreen
    participant VM as ReaderViewModel
    participant BookRepo as BookRepo
    participant Source as BookSourceResolver
    participant Progress as ProgressRepo
    participant UC as ReaderLaunchRepository
    participant Settings as ReaderSettingsStore
    participant Runtime as DefaultReaderRuntime
    participant Detector as DefaultBookFormatDetector
    participant Registry as EngineRegistry
    participant Engine as EpubEngine
    participant Opener as EpubOpener
    participant Asset as Readium AssetRetriever
    participant PubOpen as Readium PublicationOpener
    participant Doc as EpubDocument
    participant Session as EpubSession

    User->>Screen: 打开阅读页(bookId, locatorArg?)
    Screen->>VM: dispatch(Start)
    VM->>VM: closeSession()<br/>nextOpenEpoch()<br/>重置 page/error/renderGate
    VM->>BookRepo: getRecordById(bookId)
    alt 书籍不存在
        BookRepo-->>VM: null
        VM-->>Screen: 显示错误(Book not found)
    else 书籍存在
        BookRepo-->>VM: BookRecord
        VM->>Source: resolve(book)
        alt 源文件缺失
            Source-->>VM: null
            VM->>BookRepo: setIndexState(MISSING, "File not found")
            VM-->>Screen: 显示错误(MissingSource)
        else 源文件可用
            Source-->>VM: DocumentSource
            alt 路由参数带 locatorArg
                VM->>VM: locatorCodec.decode(locatorArg)
            else 使用历史进度
                VM->>Progress: getByBookId(bookId)
                Progress-->>VM: locatorJson?
                VM->>VM: locatorCodec.decode(locatorJson)
            end
            VM->>UC: openBook(bookId, locatorArg, password)
            UC->>Settings: getOpenSettingsSnapshot()
            Settings-->>UC: displayPrefs + reflowConfig + fixedConfig
            Note over UC,Runtime: EPUB 不预先传入 initialConfig。<br/>Runtime 在拿到 capabilities 后才选择 fixed/reflow 配置。
            UC->>Runtime: openSession(source, options, initialLocator, initialConfig=null, resolveInitialConfig)
            Runtime->>Detector: detect(source, hint=EPUB)
            Detector-->>Runtime: Ok(EPUB)
            Note over Detector,Runtime: 当前 hintFormat 非空时直接返回，<br/>不会再按 mime/后缀/魔数重复探测。
            Runtime->>Registry: engineFor(EPUB)
            Registry-->>Runtime: EpubEngine
            Runtime->>Engine: open(source, options)
            Engine->>Opener: open(source, options)
            Opener->>Opener: uri.toAbsoluteUrl()<br/>mediaType = source.mimeType ?: EPUB
            Opener->>Asset: retrieve(url, mediaType)
            alt 资源读取失败
                Asset-->>Opener: error
                Opener-->>Engine: Err(Io)
                Engine-->>Runtime: Err
                Runtime-->>UC: Err
                UC-->>VM: Err
                VM-->>Screen: 显示打开失败
            else 资源读取成功
                Asset-->>Opener: Asset
                Opener->>PubOpen: open(asset, credentials=password, allowUserInteraction=true)
                alt Publication 打开失败
                    PubOpen-->>Opener: error
                    Opener-->>Engine: Err(CorruptOrInvalid)
                    Engine-->>Runtime: Err
                    Runtime-->>UC: Err
                    UC-->>VM: Err
                    Note over Opener,VM: ReaderViewModel 具备 InvalidPassword 处理逻辑，<br/>但当前 EPUB opener 这里仍映射为 CorruptOrInvalid。
                    VM-->>Screen: 显示打开失败
                else Publication 打开成功
                    PubOpen-->>Opener: Publication
                    Opener->>Opener: isRestricted?<br/>isFixedLayoutPublication()<br/>build DocumentCapabilities
                    alt DRM / 受限
                        Opener-->>Engine: Err(DrmRestricted)
                        Engine-->>Runtime: Err
                        Runtime-->>UC: Err
                        UC-->>VM: Err
                        VM-->>Screen: 显示 DRM 错误
                    else 可创建文档
                        Opener-->>Engine: Ok(EpubDocument)
                        Engine-->>Runtime: Ok(document)
                        Runtime->>Runtime: config = resolveInitialConfig(capabilities)<br/>RenderConfigSanitizer.sanitize(config, capabilities)
                        Runtime->>Doc: createSession(initialLocator, sanitizedConfig)
                        Doc->>Session: EpubSession.create(...)
                        Session->>Session: buildParts()<br/>EpubController / Outline / Search / Text / Resources / Selection / Annotation
                        Session-->>Doc: ReaderSession
                        Doc-->>Runtime: Ok(session)
                        Runtime-->>UC: Ok(ReaderHandle)
                        UC-->>VM: Ok(handle)
                        VM->>VM: session.attach(bookId, handle)<br/>renderGate.attachSession(handle, openEpoch)<br/>pendingTouchLastOpenedBookId = bookId
                        VM-->>Screen: 写入 handle/resources/capabilities/currentConfig
                    end
                end
            end
        end
    end
```

## 2. 渲染前置条件与嵌入式绑定

EPUB 的首屏不是一次“把页面渲染成位图/文本页”就结束。当前实现先通过一次 `render()` 产出 `RenderContent.Embedded`，让 Compose 切换到 `ReaderSurface`，再由 `bindSurface()` 把 Readium `EpubNavigatorFragment` 真正挂上去。

几个关键事实：

- `RenderPrereqGate` 只在 `session + layout + layouter` 同时齐备时才允许触发渲染。
- `setTextLayouterFactory()` 对 EPUB 当前是 no-op，但它仍参与统一的渲染门槛判断。
- `setLayoutConstraints()` 对 EPUB 的主要价值是点击视口和承载面尺寸同步，而不是像 TXT 那样直接影响排版分页。

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant Screen as ReaderScreen
    participant Scaffold as ReaderScaffold
    participant VM as ReaderViewModel
    participant Gate as RenderPrereqGate
    participant Render as RenderCoordinator
    participant Ctrl as EpubController
    participant UI as ReaderUiState
    participant Page as PageRenderer
    participant Surface as ReaderSurface
    participant FM as FragmentManager
    participant Factory as EpubNavigatorFactory
    participant Nav as EpubNavigatorFragment
    participant Decor as EpubDecorationsHost
    participant BookRepo as BookRepo

    par Compose 注入排版环境
        Screen->>VM: dispatch(TextLayouterFactoryChanged(factory))
        VM->>Gate: updateLayouter(factory)
        VM->>Ctrl: setTextLayouterFactory(factory)
        Ctrl-->>VM: Ok(Unit)
    and Compose 上报阅读视口
        Scaffold->>VM: dispatch(LayoutChanged(constraints))
        VM->>Gate: updateLayout(constraints)
        VM->>Ctrl: setLayoutConstraints(constraints)
        Ctrl-->>VM: Ok(Unit)
    end

    Note over Gate: 只有 session + layout + layouter 都就绪，<br/>requestRenderIfReady() 才会真正放行。

    VM->>Render: requestImmediateRender(OPEN/LAYOUT)
    Render->>VM: onRender()
    VM->>Gate: snapshotIfReady()
    Gate-->>VM: session + openEpoch + layout + layouter
    VM->>Ctrl: render(RenderPolicy.Default)
    Ctrl->>Ctrl: currentEmbeddedPage()
    Ctrl-->>VM: RenderPage(content = Embedded)
    VM->>VM: nextRenderToken()<br/>commitPageIfCurrent(openEpoch, renderToken)
    VM->>BookRepo: touchLastOpened(bookId)
    VM->>UI: page = RenderContent.Embedded
    UI->>Page: 进入 Embedded 分支
    Page->>Surface: ReaderSurface(controller)

    Note over VM,Page: 这里的 render() 主要作用是让 Compose 切入 Embedded 模式。<br/>真正的 EPUB 内容显示发生在后续 bindSurface()。

    Surface->>Ctrl: bindSurface(DefaultFragmentRenderSurface)
    Ctrl->>FM: findFragmentByTag("epub-navigator-{sessionId}")
    alt 复用已有 Fragment
        FM-->>Ctrl: existing EpubNavigatorFragment
    else 首次绑定
        Ctrl->>Factory: createFragmentFactory(initialLocator=pendingLocator, initialPreferences)
        Factory-->>Ctrl: FragmentFactory
        Ctrl->>FM: replace(containerId, fragment, tag).commitNow()
        FM-->>Ctrl: EpubNavigatorFragment
    end
    Ctrl->>Nav: addInputListener(backgroundTapListener)
    Ctrl->>Decor: bind(fragment)
    Ctrl->>Nav: submitPreferences(config.toEpubPreferences())
    Ctrl->>Ctrl: collectCurrentLocator(fragment.currentLocator)
    opt 复用 Fragment 且存在 pendingLocator
        Ctrl->>Nav: go(pendingLocator, animated=false)
    end
    Nav-->>Ctrl: currentLocator Flow 持续回推
    Ctrl->>Ctrl: 更新 RenderState(locator/progression/title/nav)
    Ctrl-->>VM: ReaderEvent.PageChanged(locator)
    VM->>UI: 同步 renderState/currentConfig/title

    opt 阅读设置发生变化
        VM->>VM: observeEffectiveConfig()<br/>normalizeEpubEffectiveReflowConfig()
        VM->>Ctrl: setConfig(effectiveConfig)
        Ctrl->>Nav: submitPreferences(newPreferences)
        VM->>Render: requestRender(SETTINGS/CONFIG)
    end
```

补充说明：

- `ReaderSurface` 只有在宿主 `Activity` 是 `FragmentActivity` 时才能绑定 EPUB 导航器，否则只会显示“不支持 EPUB 导航器渲染”文本。
- `ReaderSurface` 销毁时会调用 `controller.unbindSurface()`；当前 `EpubController` 会把当前位置写回 `pendingLocator`，然后解除 decoration 绑定并移除 fragment。
- `ReaderViewModel.closeSession()` 最终会经由 `SessionCoordinator.closeCurrent()` 保存当前进度，再关闭 `ReaderHandle -> ReaderSession -> ReaderDocument`。

## 3. 翻页、跳转与进度保存

这一段描述阅读期最频繁的导航链路，包括点击翻页区、显式 `Next/Prev`、按进度跳转、按 locator 跳转，以及进度持久化。

几个关键事实：

- EPUB 的实际翻页动作是 `EpubNavigatorFragment.goForward()/goBackward()/go()`。
- `ReaderViewModel` 用 `openEpoch + activeRenderToken` 保护页面提交，避免旧 render 结果覆盖当前会话。
- 进度保存不是每次立即写库，而是从 `controller.state` 取 `locator + progression` 后做 800ms debounce。

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant Nav as EpubNavigatorFragment
    participant Ctrl as EpubController
    participant VM as ReaderViewModel
    participant Render as RenderCoordinator
    participant Pub as Publication
    participant Save as ReaderLaunchRepository
    participant Progress as ProgressRepo

    alt 阅读区域背景点击
        User->>Nav: 点击阅读区域
        Nav->>Ctrl: InputListener.onTap(TapEvent)
        Ctrl-->>VM: ReaderEvent.BackgroundTap(x, y, viewport)
        VM->>VM: handleTap()<br/>根据点击区域决定 PREV / NEXT / CENTER
    else 工具栏或音量键翻页
        User->>VM: dispatch(Next / Prev)
    else 目录 / 搜索 / 进度条跳转
        User->>VM: dispatch(GoTo(locator) / GoToProgress(percent))
    end

    alt 按 locator 跳转
        VM->>Render: withNavigationLock { controller.goTo(locator, policy) }
        Ctrl->>Ctrl: 校验 locator scheme
        alt surface 尚未绑定
            Ctrl->>Ctrl: pendingLocator = locator<br/>state.locator = locator
            Ctrl->>Ctrl: render(policy) 返回 Embedded 占位页
        else surface 已绑定
            Ctrl->>Nav: go(locator, animated=false)
            Ctrl->>Ctrl: awaitLocatorChange(previous)
            Ctrl->>Ctrl: render(policy)
        end
    else 按百分比跳转
        VM->>Render: withNavigationLock { controller.goToProgress(percent, policy) }
        Ctrl->>Pub: locateProgression(percent)
        Pub-->>Ctrl: target Locator
        Ctrl->>Nav: go(target, animated=false)
        Ctrl->>Ctrl: awaitLocatorChange(previous)
        Ctrl->>Ctrl: render(policy)
    else 前后翻页
        VM->>Render: withNavigationLock { controller.next/prev(policy) }
        Ctrl->>Nav: goForward() / goBackward()
        opt 不是明显边界页
            Ctrl->>Ctrl: awaitLocatorChange(previous)
        end
        Ctrl->>Ctrl: render(policy)
    end

    Nav-->>Ctrl: currentLocator Flow 更新
    Ctrl->>Ctrl: 更新 RenderState.locator / progression / nav / title
    Ctrl-->>VM: ReaderEvent.PageChanged(locator)
    VM->>VM: nextRenderToken()<br/>commitPageIfCurrent(openEpoch, renderToken)

    Note over VM: commitPageIfCurrent() 只有在 sessionHandle、openEpoch、activeRenderToken<br/>同时匹配时才真正 replacePage，避免旧 render 覆盖当前页面。

    par Compose 页面更新
        VM->>VM: replacePage(RenderContent.Embedded, direction?)
    and 进度持久化
        VM->>Save: debounce 800ms 后保存(locator, progression)
        Save->>Progress: upsert(bookId, locatorJson, progression, anchors)
        Progress-->>Save: done
    end
```

补充说明：

- `handleTap()` 还会处理防误触区域、中心区显示/隐藏 chrome、翻页后短时间内中心点击撤销等交互；这些都发生在 `ReaderViewModel` 内部，真正触发翻页时才进入上图中的 `controller.next/prev()`。
- `prefetchNeighbors()` 在当前 `EpubController` 中是 no-op，因此 EPUB 现在没有额外的邻页预热链路。

## 4. 目录加载与目录跳转

目录不是在打开会话时预热出来的，而是在用户真正打开目录面板后懒加载。

几个关键事实：

- `ReaderViewModel.loadTocIfNeeded()` 会先检查 `sessionHandle.outline` 是否存在。
- `EpubOutlineProvider` 直接读取 `Publication.tableOfContents`，再把 `Link` 递归映射成应用侧 `OutlineNode`。
- 目录点击后不会走特殊捷径，而是重新回到统一的 `openLocator() -> GoTo(locator)` 导航链。

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant UI as ReaderScaffold
    participant VM as ReaderViewModel
    participant Handle as ReaderHandle
    participant Outline as EpubOutlineProvider
    participant Pub as Publication
    participant State as ReaderUiState

    User->>UI: 打开菜单 / 目录
    UI->>VM: dispatch(OpenMenu / OpenToc / SetMenuTab(Toc))
    VM->>VM: loadTocIfNeeded(force=false)
    VM->>Handle: outline
    alt OutlineProvider 不可用
        Handle-->>VM: null
        VM->>State: toc.error = "Outline is not available"
    else OutlineProvider 可用
        Handle-->>VM: EpubOutlineProvider
        VM->>State: toc.isLoading = true
        VM->>Outline: getOutline()
        Outline->>Pub: tableOfContents
        loop 遍历 TOC Link 树
            Outline->>Pub: locatorFromLink(link)
            Pub-->>Outline: Readium Locator
            Outline->>Outline: toAppLocator()<br/>递归 children -> OutlineNode
        end
        Outline-->>VM: List<OutlineNode>
        VM->>VM: flattenOutlineIterative()
        VM->>State: toc.items = 扁平目录列表
    end

    User->>UI: 点击目录项
    UI->>VM: openLocator(locatorEncoded)
    VM->>VM: locatorCodec.decode(encoded)
    VM->>VM: dispatch(GoTo(locator))
    VM->>VM: 复用统一导航链路(controller.goTo)
```

补充说明：

- 当 `force = false` 且当前 `toc.items` 已有内容时，`loadTocIfNeeded()` 会直接返回，不重复请求 `OutlineProvider`。
- 目录项在 UI 层会额外保留 `href / position / progression / confidence` 等字段，来源于 locator extras。

## 5. 全文搜索与结果跳转

搜索链路使用 `Flow<SearchHit>` 增量返回结果，`ReaderViewModel` 再用 `SearchResultAccumulator` 做小批量 UI 合并。

几个关键事实：

- 当前 EPUB 搜索能力来自 Readium `Publication.search()`。
- 结果上限由 `ReaderViewModel.executeSearch()` 固定为 `maxHits = 300`。
- 点击搜索结果后，仍然回到统一的 `openLocator() -> GoTo(locator)` 跳转链。

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant UI as SearchSheet
    participant VM as ReaderViewModel
    participant Handle as ReaderHandle
    participant Search as EpubSearchProvider
    participant Pub as Publication SearchService
    participant State as ReaderUiState

    User->>UI: 输入 query 并点击搜索
    UI->>VM: dispatch(SearchQueryChanged / ExecuteSearch)
    VM->>VM: query = trim()
    alt query 为空
        VM->>State: 清空 search.results / error
    else SearchProvider 不可用
        VM->>Handle: search
        Handle-->>VM: null
        VM->>State: search.error = "Search is not available"
    else 可执行搜索
        VM->>Handle: search
        Handle-->>VM: EpubSearchProvider
        VM->>VM: cancelActiveSearch()<br/>searchGeneration++
        VM->>State: isSearching = true<br/>results = []
        VM->>Search: search(query, SearchOptions(maxHits=300))
        Search->>Search: normalizeQuery()
        Search->>Pub: publication.search(query, options)
        Pub-->>Search: iterator
        loop iterator.next() 直到 maxHits 或结束
            Search->>Pub: next()
            Pub-->>Search: page.locators
            loop 遍历每个 locator
                Search->>Search: toAppLocator()<br/>isAtOrAfter(startFrom)?<br/>build excerpt
                Search-->>VM: emit SearchHit
                VM->>VM: SearchResultAccumulator.add(hit)
                opt 达到批量刷新阈值
                    VM->>State: results += batch
                end
            end
        end
        Search->>Pub: iterator.close()
        VM->>VM: accumulator.flush()
        VM->>State: isSearching = false
    end

    User->>UI: 点击某条结果
    UI->>VM: openLocator(locatorEncoded)
    VM->>VM: dispatch(GoTo(locator))
    VM->>VM: 复用统一导航链路(controller.goTo)
```

补充说明：

- ViewModel 实际收集的是 `provider.search(...).asReaderResult()`，因此搜索迭代过程中的 provider 异常会被转成 `ReaderResult.Err` 并映射到 `search.error`。
- 重新发起搜索时，旧搜索会先经过 `cancelActiveSearch()` 取消，再递增 `searchGeneration`；后续只有 generation 匹配的结果允许写回 UI。

## 6. 选区、批注与装饰同步

EPUB 批注的关键点不在 `SelectionController`，而在于 Readium Navigator 内部选区 + `AnnotationStore.observe()` 回流后的 decoration 同步。

几个关键事实：

- EPUB 当前没有 app-side `SelectionController`；`ReaderViewModel.SelectionStart/Update/Finish` 这套入口主要服务 TXT/PDF，EPUB 批注是通过 `selection.currentSelection()` 按需读取现有选区。
- `ReaderViewModel.createAnnotation()` 会优先用真实选区生成 `LocatorRange`，拿不到选区时才回退到当前页面 locator。
- 批注高亮的真正刷新主链是 `AnnotationStore.observe() -> EpubAnnotationProvider -> EpubDecorationsHost -> EpubNavigatorFragment.applyDecorations()`。

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant Nav as EpubNavigatorFragment
    participant VM as ReaderViewModel
    participant Sel as EpubSelectionProvider
    participant Draft as ReaderAnnotationDraftFactory
    participant Ann as EpubAnnotationProvider
    participant Store as AnnotationStore
    participant Decor as EpubDecorationsHost
    participant Ctrl as EpubController

    Note over Nav,VM: EPUB 当前没有 app-side SelectionController。<br/>选区保存在 Readium Navigator 内部，由 currentSelection() 按需读取。
    Note over Ann,Store: EpubAnnotationProvider 在 session 创建时就启动 observeJob，<br/>持续监听 store.observe(documentId)。

    User->>Nav: 在 WebView / Readium 中长按选择文本
    User->>VM: dispatch(CreateAnnotation)
    alt 文档不支持批注
        VM->>VM: sessionHandle.annotations == null
        VM-->>User: Snackbar("当前文档不支持批注")
    else 支持批注
        VM->>Sel: currentSelection()
        Sel->>Nav: currentSelection()
        alt 读取选区失败
            Nav-->>Sel: error
            Sel-->>VM: Err
            VM->>VM: 记录提示<br/>selection = null
        else 返回选区或空选区
            Nav-->>Sel: Selection? (locator/start/end/rects/text)
            Sel-->>VM: Ok(selection?)
        end

        VM->>Draft: create(selection, fallbackLocator = renderState.locator)
        alt 既无有效选区也无 fallbackLocator
            Draft-->>VM: Err
            VM-->>User: Snackbar("无法创建批注：缺少定位信息")
        else 生成 AnnotationDraft
            Draft-->>VM: Ok(draft)
            VM->>Ann: create(draft)
            Ann->>Store: create(documentId, draft)
            Store-->>Ann: Annotation
            Ann-->>VM: Ok(annotation)
            VM->>Sel: clearSelection()
            Sel->>Nav: clearSelection()
            VM->>Ctrl: invalidate(CONTENT_CHANGED)
            Note over Ctrl: 当前 EPUB controller.invalidate() 直接返回 Ok(Unit)。<br/>高亮刷新主链依赖下面的 store.observe() 回流。
            par 用户提示
                VM-->>User: Snackbar("已添加批注")
            and decoration 回流
                Store-->>Ann: observe(documentId) 推送最新注释列表
                Ann->>Ann: Annotation -> Readium Decoration<br/>diff renderedDecorations
                Ann->>Decor: applyAll(group -> decorations)
                alt navigator 已绑定
                    Decor->>Nav: applyDecorations(decorations, group)
                else navigator 尚未绑定
                    Decor->>Decor: 写入 pending，等待下次 bind()
                end
            end
        end
    end
```

补充说明：

- `ReaderAnnotationDraftFactory` 对 EPUB 最常见的输出是 `AnnotationAnchor.ReflowRange`。
- 如果 `selectedText` 可用，批注默认内容会带上当前选中文字；否则只写锚点，不强制带正文内容。

## 7. 资源访问与文本提取能力

这部分不是当前阅读主页面的显式主链，但它们已经随 `ReaderHandle` 就绪，后续任何需要直接读取 EPUB 资源或抽取正文片段的功能都要经过这里。

几个关键事实：

- `EpubResourceProvider` 处理的是应用层显式资源访问，不等同于 Readium Navigator 内部自己的资源解析过程。
- `EpubTextProvider` 优先复用 locator 自带的高亮/上下文文本，取不到时再回退到 `Publication.content()`。

```mermaid
sequenceDiagram
    autonumber
    actor Caller as 上层调用者
    participant Handle as ReaderHandle
    participant Res as EpubResourceProvider
    participant Text as EpubTextProvider
    participant Pub as Publication

    Caller->>Handle: resources / text

    par 资源访问
        Caller->>Res: openResource(path) / getMimeType(path)
        Res->>Res: parseHref(path)
        alt href 无效
            Res-->>Caller: Err(CorruptOrInvalid) / Ok(null)
        else href 合法
            alt openResource(path)
                Res->>Pub: get(href)
                alt 资源不存在
                    Pub-->>Res: null
                    Res-->>Caller: Err(NotFound)
                else 资源存在
                    Pub-->>Res: Resource
                    Res-->>Caller: InputStream
                end
            else getMimeType(path)
                Res->>Pub: linkWithHref(href)
                Pub-->>Res: Link?
                Res-->>Caller: mimeType?
            end
        end
    and 文本提取
        Caller->>Text: getText(range) / getTextAround(locator, maxChars)
        Text->>Text: toReadiumLocatorOrNull()
        alt locator scheme 不受支持
            Text-->>Caller: Err(CorruptOrInvalid)
        else locator 可用
            opt locator 已携带 highlight / before / after
                Text-->>Caller: 直接返回现成文本
            end
            opt 需要从 Publication 内容服务读取
                Text->>Pub: content(locator)
                alt content service 不可用
                    Pub-->>Text: null
                    Text-->>Caller: Err(Internal)
                else content 可用
                    loop iterator.nextOrNull() 直到 maxChars / endPosition
                        Text->>Pub: next TextualElement
                        Pub-->>Text: TextualElement
                    end
                    Text-->>Caller: 聚合后的字符串
                end
            end
        end
    end

    Note over Caller,Handle: 当前 feature/reader 主页面没有直接调用这两条链路。<br/>它们属于 EPUB session 的能力面，供后续资源桥接、摘录、分享等功能复用。
```

## 实现事实与边界

- EPUB 当前使用 `Readium EpubNavigatorFragment` 作为最终显示层；Compose 负责壳层状态、手势转发、菜单与面板。
- `render()` 对 EPUB 返回的是 `RenderContent.Embedded` 占位页，不是排版后的位图或文本页。
- `setTextLayouterFactory()`、`setLayoutConstraints()` 仍然经过统一渲染门槛，但 EPUB 当前对它们的使用重点分别是接口对齐和视口信息同步。
- `prefetchNeighbors()`、`invalidate()` 在当前 `EpubController` 中都没有实质性的渲染工作；其中批注高亮刷新主要依靠 `EpubDecorationsHost`。
- 阅读设置变化会先经过 `ReaderViewModel.normalizeEpubEffectiveReflowConfig()`，再被映射成 `EpubPreferences` 提交给 Readium。
- `ReaderSurface` 卸载时会触发 `unbindSurface()`，`EpubController` 会把当前位置存回 `pendingLocator`，为下一次重绑恢复位置。
- `ReaderViewModel` 虽然具备密码弹窗分支，但当前 `EpubOpener` 并没有把 Readium 打开失败细分为 `ReaderError.InvalidPassword`。
- 导航器内部超链接在当前仓库里没有经过 `ReaderIntent.ActivateLink` 这条显式 feature 层桥接，因此本文只覆盖已经在代码中验证过的 EPUB 导航入口。
