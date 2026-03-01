下面这套是**在你现有 API/Runtime 不改动或极少改动**的前提下，我认为最稳、最好扩展、性能也最可控的 **TXT 引擎实现方案**（分页/定位/搜索/复制/标注都能自然落地）。

---

## 0. TXT 引擎的“最推荐”关键决策

### ① Locator 方案：`txt.offset` 用“全书 UTF-16 char offset（Kotlin Char 下标）”

* 你现在 `Locator.value: String`，TXT 最合适就是存 **“全书字符偏移”**（UTF-16 code unit index）。
* 好处：`TextMapping`、标注（`LocatorRange`）、复制/分享都顺滑；无需额外 scheme。
* 代价：必须**固定 charset**（由 `OpenOptions.textEncoding` + BOM 探测确定），否则 offset 会变。

> 内部实现可以用 byte offset 做 IO 优化，但对外 locator 统一用 char offset。

### ② 读取策略：**构建“稀疏索引”实现准随机访问（charOffset → byteOffset）**

为了支持：

* goTo(locator) / getText(range) / getTextAround / search startFrom
* 不把整本 TXT 一次性读入内存（大文件）

推荐在 open 时（或首次需要时）做一个 **ChunkIndex**：

* 每 N 字符（比如 16K 或 32K chars）记录一次：`charOffset -> byteOffset`
* 同时得到 `totalChars`
* 之后提取任意 char range：先二分找最近 anchor，再从该 byteOffset 解码到目标区间

### ③ 分页策略：**StaticLayout 驱动“按视口/字体”分页，分页结果做 LRU + 可选持久化**

* TXT 只能是 reflow：`RenderContent.Text`
* 用 `StaticLayout`（framework）测量能放下多少字符，得到 `pageStartChar` / `pageEndChar`
* 分页缓存 key：`(docId + encoding + constraints + RenderConfig.ReflowText)`
* 缓存内容：`pageStarts: IntArray`（每页起始 char offset）+ `pageTextCache (LRU)`

> 这能让 next/prev 极快；goToProgress 二分 pageStarts 或按 totalChars 估算后修正。

---

## 1) engines/txt 推荐目录与职责

```text
engines/txt/
├── TxtEngine.kt
└── internal/
    ├── open/
    │   ├── TxtDocument.kt
    │   ├── TxtCharsetDetector.kt         # BOM/heuristic + options.textEncoding
    │   └── TxtIdFactory.kt               # DocumentId 生成策略
    ├── storage/
    │   ├── TxtTextStore.kt               # char范围读取 / around / totalChars
    │   ├── ChunkIndex.kt                 # 稀疏索引（char<->byte）
    │   └── PfdChannel.kt                 # ParcelFileDescriptor -> FileChannel 封装
    ├── paging/
    │   ├── TxtPager.kt                   # StaticLayout 计算“页能容纳多少字符”
    │   ├── PaginationCache.kt            # pageStarts + LRU pageText
    │   └── RenderConfigKey.kt            # config/constraints 的 hash key
    ├── controller/
    │   ├── TxtController.kt              # ReaderController 实现（state/events/nav）
    │   └── TxtTextMapping.kt             # TextMapping：pageCharIndex <-> global char offset
    └── provider/
        ├── TxtTextProvider.kt
        ├── TxtSearchProvider.kt
        └── TxtOutlineProvider.kt         # 可选：按章节/标题规则生成
```

---

## 2) TxtEngine / TxtDocument / TxtSession：能力与对象关系

### 2.1 Capabilities（建议这样开）

TXT 的建议默认能力：

```kotlin
DocumentCapabilities(
  reflowable = true,
  fixedLayout = false,
  outline = false,          // 先关，后面可按规则生成章节目录再打开
  search = true,
  textExtraction = true,    // 复制/分享/导出
  annotations = true,       // 只要你有 AnnotationRepo 或实现内存版也行
  links = false             // 可后续做 URL detect
)
```

### 2.2 TxtEngine.open：只做三件事（不要把重活堆这里）

1. 选 charset（OpenOptions + BOM）
2. 创建 TextStore（拿到 totalChars/索引可以 lazy）
3. 返回 TxtDocument

```kotlin
class TxtEngine(
  private val io: CoroutineDispatcher = Dispatchers.IO,
  // 可选：注入 AnnotationRepo/ProgressRepo 等
) : ReaderEngine {

  override val supportedFormats = setOf(BookFormat.TXT)

  override suspend fun open(source: DocumentSource, options: OpenOptions): ReaderResult<ReaderDocument> =
    withContext(io) {
      runCatching {
        val charset = TxtCharsetDetector.detect(source, options.textEncoding)
        ReaderResult.Ok(TxtDocument(source, options, charset, io))
      }.getOrElse { ReaderResult.Err(it.toReaderError()) }
    }
}
```

### 2.3 TxtDocument.createSession：组装 controller + providers

* controller 依赖 TextStore + pager + cache
* providers（search/text/annotations）复用同一个 TextStore

---

## 3) TxtTextStore：TXT 引擎的“地基”

建议对 controller/provider 暴露的最小接口：

```kotlin
internal interface TxtTextStore : Closeable {
  val charset: Charset
  suspend fun totalChars(): Int                      // 全书 char 总数（UTF-16）
  suspend fun readChars(startChar: Int, maxChars: Int): String
  suspend fun readRange(startChar: Int, endChar: Int): String
  suspend fun readAround(charOffset: Int, maxChars: Int): String
}
```

### 3.1 稀疏索引 ChunkIndex（推荐参数）

* `CHUNK_CHARS = 16_384`（或 32K）
* index entry: `(charOffsetAnchor, byteOffsetAnchor)`
* 构建方式：顺序解码全书一次（IO 线程），每到 chunk 边界记录 anchor
  同时统计 `totalChars`

> 这一步对大文件可能 100ms~几秒不等，但这是 TXT 引擎要做“像样体验”的必要成本。你也可以 lazy：首次 goTo/search/textProvider 才 build。

### 3.2 读取任意 range 的流程

1. 二分 index 找 `anchorChar <= startChar` 的最近点
2. seek 到 anchor byteOffset（优先用 `openFileDescriptor` -> FileChannel.position）
3. 解码推进到 startChar，再读到 endChar

> 对 content:// Uri，`ParcelFileDescriptor` + `FileChannel` 通常可以 seek；如果不行则 fallback 顺序流，但会慢一些（仍可用）。

---

## 4) 分页：StaticLayout 计算“本页能放多少字符”

### 4.1 分页输入

* LayoutConstraints：viewportW/H + density + fontScale
* RenderConfig.ReflowText：fontSizeSp/lineHeightMult/pagePaddingDp/…

### 4.2 分页输出（最关键）

对给定 `startChar`：

* 先从 TextStore 读一段候选文本，比如 `maxChars = 20_000`（够覆盖一页）
* 用 `StaticLayout` 生成排版（width=contentWidthPx）
* 通过 `layout.getLineForVertical(contentHeightPx)` 找到最后一行
* 再取 `layout.getLineEnd(lastLine)` 得到 `countCharsFit`
* `endChar = startChar + countCharsFit`

> 这样每页是“真实按屏幕排版”出来的，翻页不会漂。

### 4.3 TxtPager 建议接口

```kotlin
internal data class PageSlice(
  val startChar: Int,
  val endChar: Int,
  val text: String
)

internal class TxtPager(...) {
  suspend fun pageAt(startChar: Int, constraints: LayoutConstraints, config: RenderConfig.ReflowText): PageSlice
}
```

---

## 5) TxtController：把分页缓存/导航/状态流做扎实

### 5.1 Controller 内部状态（推荐）

* `var constraints: LayoutConstraints?`
* `var config: RenderConfig.ReflowText`
* `var currentStartChar: Int`
* `pageStarts: MutableList<Int>`（已知页起始点，从 0 开始递增）
* `pageTextLru: LruCache<Int, PageSlice>`（key=startChar）
* 一个 `Mutex`：保证 next/prev/goTo/render 串行

### 5.2 RenderState 的计算（推荐）

* locator：`Locator(TXT_OFFSET, currentStartChar.toString())`
* progression.percent：`currentStartChar / totalChars`
* nav.canGoPrev：`currentStartChar > 0`
* nav.canGoNext：`currentEndChar < totalChars`

label 可以先给百分比：`"12%"`；等你做出 pageCount 再给 `"12/320"`。

### 5.3 TextMapping（必须做）

TXT 的 mapping 很简单：本页 text 的 charIndex → 全局 charOffset：

```kotlin
internal class TxtTextMapping(
  private val pageStartChar: Int
) : TextMapping {
  override fun locatorAt(charIndex: Int): Locator =
    Locator(LocatorSchemes.TXT_OFFSET, (pageStartChar + charIndex).toString())

  override fun rangeFor(startChar: Int, endChar: Int): LocatorRange =
    LocatorRange(locatorAt(startChar), locatorAt(endChar))

  override fun charRangeFor(range: LocatorRange): IntRange? {
    if (range.start.scheme != LocatorSchemes.TXT_OFFSET) return null
    val s = range.start.value.toIntOrNull() ?: return null
    val e = range.end.value.toIntOrNull() ?: return null
    return (s - pageStartChar) until (e - pageStartChar)
  }
}
```

这会直接打通：**选中高亮/标注锚点/复制范围**。

---

## 6) Providers：TXT 的 Text/Search 推荐实现

### 6.1 TxtTextProvider（强烈建议实现）

* `getText(range)`：用 TextStore.readRange
* `getTextAround(locator)`：parse offset 后 readAround

注意：如果 range 很大要做上限保护（比如最多 200_000 chars），避免 UI 卡死。

### 6.2 TxtSearchProvider：流式搜索（不会爆内存）

建议做“chunk + overlap”的顺序扫描：

* chunkChars：比如 64K
* overlap：`query.length + 64`，防止命中跨边界漏掉
* `startFrom`：从 startChar 开始
* 每次找到 match：emit `SearchHit(range, excerpt, sectionTitle=null)`
* 达到 maxHits 停止
* 支持 caseSensitive / wholeWord（wholeWord 只做简单边界：前后不是 letter/digit/underscore）

**excerpt**：match 前后各取 40~80 字符即可；必要时可以调用 `TextStore.readAround()` 生成更准确片段。

---

## 7) 编码探测：最低成本的“够用方案”（TXT 必须有）

推荐优先级：

1. `OpenOptions.textEncoding`（用户选择优先）
2. BOM：UTF-8 / UTF-16LE / UTF-16BE
3. 无 BOM：默认 UTF-8
4. （可选）如果 UTF-8 解码替换字符比例过高，再 fallback GBK（面向中文场景很实用）

> 不建议一上来就上大而全的 charset detector，先把“可配置 + BOM + 常用 fallback”做稳。

---

## 8) 你现在这套 API 下，TXT 引擎的“最小可跑闭环”清单

**必须实现：**

* TxtEngine.open
* TxtDocument（metadata/title 可用 displayName；author/language 可空）
* TxtSession（controller + search/text）
* TxtController（setLayoutConstraints / render / next / prev / goTo / goToProgress）
* TxtPager（StaticLayout）
* TxtTextStore（至少顺序读取 + totalChars；最好有 ChunkIndex）

**可延后：**

* OutlineProvider（章节规则）
* AnnotationProvider（接 core/data repo）
* prefetchNeighbors（先 no-op 或简单把 next/prev page 放入 LRU）

---

## 9) 一句“最推荐”的实现总结

> **对外 locator 统一用 charOffset（txt.offset），对内用 ChunkIndex 做 char↔byte 的稀疏映射；分页用 StaticLayout 算本页 endChar；Controller 维护 pageStarts + LRU；Search 走 chunk+overlap 流式扫描；TextProvider 直接基于 TextStore。**
> 这套能在你当前契约下把 TXT 的体验做到“接近成熟阅读器”，且后续接 EPUB 的 reflow 模型也能复用一大半（pager/controller/caching/search 都可迁移到 engine-common）。

---

如果你愿意，我可以按你现有包名/模块约束，直接把 **TxtEngine/TxtDocument/TxtSession/TxtController/TxtPager/TxtTextStore(含 ChunkIndex)** 的骨架代码完整写出来（可编译级别），你只需要补 DI/注册即可跑起来。
