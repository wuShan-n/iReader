先给结论：我最推荐的不是继续把目录拆得更细，而是把 **Reader 做成真正的平台层**：`core/reader/api + core/reader/runtime + engines/*`，再把 TXT 从“一个大 Controller 驱动全部功能”重构成“**产物流水线 + 会话门面 + 双层 locator**”的架构。Android 官方对多模块本来就不主张唯一标准答案，而是建议按稳定边界拆分；同时推荐明确分层、single source of truth、UDF 和 state holder。([Android Developers][1])

你这套提案里，我会保留的部分很多，差不多有 80%：

* `app` 作为唯一组装者
* `core/reader/api` 与 `core/reader/runtime` 分离
* `engines/*` 不被 feature 直接依赖
* `feature/*` 内部按 navigation / ui / presentation / domain / di 分层
* `benchmark` 和 `baselineprofile` 保留

真正要改的是边界，不是目录名。

## 1. 先把依赖规则再收紧一层

我会把 `feature/*` 的依赖收得更狠：**只依赖** `core/data`、`core/model`、`core/navigation`、`core/designsystem`、`core/reader/runtime`。不要让 feature 直接碰 `core/database`、`core/datastore`、`core/files`，因为这些本质上都是 data source；官方 data layer 指南明确说，其他层不应直接依赖 data source，入口应该是 repository。([Android Developers][2])

我会把规则定成这样：

```text
feature/* 
  -> core/data
  -> core/model
  -> core/navigation
  -> core/designsystem
  -> core/reader/runtime

core/data
  -> core/database
  -> core/datastore
  -> core/files
  -> core/model
  -> core/common

core/reader/runtime
  -> core/reader/api
  -> core/model
  -> core/common

engines/*
  -> core/reader/api
  -> core/model
  -> core/files
  -> core/common
  -> engines/engine-common

app
  -> feature/*
  -> core/reader/runtime
  -> engines/*
```

这条很关键，因为它能直接解决你现在 `feature/reader` 里“既要懂业务，又要懂引擎打开，会话关闭，渲染前置条件，搜索取消，patch 后失效”等一堆粘连问题。

## 2. 模型不要全塞进 `core/model`

这里我会和你现在的迁移计划稍微唱反调。

放进 `core/model` 的，应该是**业务上稳定、跨层共识明确**的东西，比如：

* `BookFormat`
* `DocumentId`
* `Book/Metadata`
* `StableLocator`
* `Outline`
* `Progression`
* 用户侧的 `Bookmark / Highlight / Note`

但 `DocumentCapabilities` 这类东西，我不会放进 `core/model`，而是留在 `core/reader/api`。因为它描述的是**引擎/会话能力**，不是业务领域事实。`RenderConfig`、`RenderPage`、`TextMapping`、`TileProvider`、`ReaderSession` 这些更不用说，全部都应该留在 `reader/api`。

`Annotation.kt` 我也建议拆成两层：

* `core/model`：持久化的用户批注实体
* `core/reader/api`：渲染期 anchor / decoration / resolved selection

否则你会把“用户业务数据”和“引擎渲染锚点”绑死在一起，后面 EPUB/PDF/TXT 的锚定策略很难演进。

另外，别同时维护两套结果语义：`core/common.Result` 和 `ReaderResult`。我的建议是：**内部实现尽量直接用异常 / Flow catch / Kotlin Result，到了 runtime 边界再统一映射成 `ReaderError`**。官方 data layer 指南本身也把异常和 `catch` 视作 Kotlin 协程/Flow 的默认错误处理路径之一。([Android Developers][2])

## 3. `feature/reader` 以后只面对 `ReaderRuntime`

这是这次重构里最值钱的一刀。

我建议让 `feature/reader` 不再直接碰 `ReaderDocument / ReaderSession / ReaderController / provider`。这些都应该缩到 `runtime` 后面。对 feature 暴露的只剩一个门面，例如：

* `open(bookId, locator)`
* `bindViewport(layout, textLayoutBridge)`
* `updateAppearance(settings)`
* `next / prev / goTo / search / select / addAnnotation`
* `state: StateFlow<ReaderUiState>`
* `events: Flow<ReaderEvent>`
* `close()`

也就是说，`ReaderViewModel` 以后是**屏幕状态持有者**，不是“半个引擎编排器”。这也更符合官方 UI layer / state holder 的职责边界。([Android Developers][3])

DI 方面我会直接用 Hilt + multibinding，把 `Set<ReaderEngine>` 注入 `EngineRegistry`。Hilt 目前仍然是 Jetpack 推荐的 Android DI 方案。([Android Developers][4])

## 4. TXT 不要再以 `TxtController` 为中心

你现在真正该重构的，不是“把 `TxtController` 拆几个文件”，而是把 TXT 变成下面这套结构：

```text
engines/txt/internal/
├── artifact/          # text.store / meta / manifest / block.idx / break.map / search.idx / outline.idx
├── canonical/         # 编码探测、标准化、Utf16TextStore、BlockIndex
├── projection/        # BreakResolver、patch overlay、raw/display offset mapping
├── location/          # StableLocator <-> RenderLocator 解析与恢复
├── pagination/        # TxtPageFitter、PaginationCoordinator、page cache、checkpoint
├── navigation/        # next/prev/goTo/goToProgress
├── session/           # TxtSessionFacade、lifecycle、event bridge
├── outline/
├── search/
├── selection/
└── annotation/
```

其中 6 个核心角色是：

1. `TxtArtifactManager`
   只负责产物生命周期：生成、校验、版本、失效、manifest。

2. `TextProjectionEngine`
   负责 raw text -> display text 的投射，以及 raw/display offset 映射。
   **搜索、目录、选区、批注都统一建立在它上面。**

3. `TxtPaginationService`
   只做分页、缓存、checkpoint，不做 session 协调。

4. `TxtNavigator`
   只做 next / prev / goto / progression。

5. `TxtFeatureServices`
   outline/search/selection/annotation/link detection 各自独立。

6. `TxtSessionFacade`
   只负责生命周期、事件流、warmup、关闭。

你现在的 `TxtController` 实际上同时扮演了 2~6，已经过胖了。

## 5. 非 MVP 版本一定要做“双层 locator”

这是我最强烈建议你做的升级。

不要再把“持久化定位”和“渲染期定位”混成一个东西。应该分成：

* `StableLocator`：落库、跨版本恢复、跨 patch 恢复
* `RenderLocator`：会话内高性能分页、跳转、二分定位

`StableLocator` 我建议至少包含：

* `block/paragraph anchor`
* `intra-block offset`
* `optional text quote/context`
* `progression fallback`
* `source/content fingerprint`

这样将来即使：

* 规范化策略变了
* break patch 变了
* `block.idx` 版本升级了
* 用户重新导入同一本书

进度和批注仍然有恢复空间。

你现在的 `TxtAnchorLocatorCodec` 这种偏绝对 offset 的做法，短期性能很好，但长期兼容性会越来越贵。

## 6. 产物要分三层，而不是一锅炖

我建议把 TXT 产物分成三层：

* **Canonical persistent**
  `text.store`、`meta.json`、`block.idx`

* **Projection persistent**
  `break.map`、`patches.json`、`projectionVersion`

* **View-dependent ephemeral**
  `page cache`、`layout checkpoints`

最重要的一条规则是：

**`outline.idx` 和 `search.idx` 不应该只依赖 sourceVersion，而应该依赖 `projectionVersion`。**

因为你现在已经明确了：搜索、目录都基于 `BreakResolver.projectRange()` 之后的显示文本。那 patch 一变，搜索/目录天然就该失效并异步重建；这个方向是对的，但要把它设计成正式依赖图，而不是散落在几个 invalidate 调用里。

## 7. 你当前流程里有 4 个地方要立刻修

这几个不必等大重构，直接改：

* 批注创建成功后，**不要只 invalidate cache，要立即 rerender 当前页**
* `TxtSession.create()` 里把 `outline/search warmup` 接上，不要只留实现不调用
* `next/prev` 不该因为 layouter factory 为空而失败；应该在 `bindViewport` 前根本不暴露可导航状态
* `RenderPrereqGate` / `RenderCoordinator` 可以保留思路，但应尽量下沉到 runtime handle，而不是留在 feature 作为长期业务逻辑

## 8. WorkManager、Room migration、Baseline Profile 都值得上

会话内的首屏后 warmup，用普通协程 scope 就够了；**只有那些要跨进程、跨重启保证完成的任务**，例如批量导入、全文索引、备份，才交给 WorkManager。官方对 WorkManager 的定位就是持久性、可延期、跨重启的后台任务。([Android Developers][5])

你这次一旦改 progress / annotation / locator 存储结构，就一定会碰到 Room migration。官方文档强调 schema 变化时要保住设备上已有用户数据，所以迁移测试必须一起补上。([Android Developers][6])

性能侧我建议把 `benchmark` 和 `baselineprofile` 从“可选”提升到“默认做”。阅读器对首开、首屏、翻页抖动很敏感，而 Baseline Profiles 官方给出的收益量级就是**首启代码执行可提升约 30%**。([Android Developers][7])

另外我会新增一个你目录里还缺的模块：

```text
core/reader/testkit/
├── EngineContractSuite
├── LocatorRoundTripSuite
├── SearchProjectionSuite
├── AnnotationAnchorRecoverySuite
└── fixtures/
```

这个模块是非 MVP 方案的核心资产。TXT、EPUB、PDF 都跑同一套 contract tests，后续替换引擎不会靠人工回归。

## 9. 我的落地顺序

我会这样拆阶段，不会一上来全量翻修：

1. **冻结边界**
   先定依赖规则、模型归属、runtime 对 feature 的唯一入口。

2. **引入 `ReaderHandle` 门面**
   让 `feature/reader` 脱离 `ReaderSession/Controller`。

3. **拆 TXT 内部大类**
   先把 `TxtController` 拆成 `artifact / projection / pagination / session facade`。

4. **上双层 locator，并双写迁移**
   一段时间内同时写老 locator 和新 locator，验证恢复质量后再切。

5. **补全产物依赖图**
   `projectionVersion`、patch、search、outline、warmup 全部串起来。

6. **补测试和性能体系**
   engine contract tests、golden corpus、macrobenchmark、baseline profile 一起落。

最后给你一句最直接的判断：**你的根目录方案基本是对的，但真正的“非 MVP 重构”不在模块数量，而在于把 TXT 从控制器式实现升级成平台式实现。**

下一轮我可以直接把 `core/reader/runtime` 的接口骨架，以及 `engines/txt` 的目标类图和关键 Kotlin 签名写出来。

[1]: https://developer.android.com/topic/modularization/patterns "Common modularization patterns  |  App architecture  |  Android Developers"
[2]: https://developer.android.com/topic/architecture/data-layer "Data layer  |  App architecture  |  Android Developers"
[3]: https://developer.android.com/topic/architecture/ui-layer/stateholders?utm_source=chatgpt.com "State holders and UI state | App architecture | Android Developers"
[4]: https://developer.android.com/training/dependency-injection "Dependency injection in Android  |  App architecture  |  Android Developers"
[5]: https://developer.android.com/develop/background-work/background-tasks/persistent "Task scheduling  |  Background work  |  Android Developers"
[6]: https://developer.android.com/training/data-storage/room/migrating-db-versions "Migrate your Room database  |  App data and files  |  Android Developers"
[7]: https://developer.android.com/topic/performance/baselineprofiles/overview "Baseline Profiles overview  |  App quality  |  Android Developers"
