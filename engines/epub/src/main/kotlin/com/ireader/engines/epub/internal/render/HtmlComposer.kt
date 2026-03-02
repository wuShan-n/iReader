package com.ireader.engines.epub.internal.render

import com.ireader.engines.epub.internal.cache.SimpleLruCache
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import java.io.File

internal object HtmlComposer {

    private data class TemplateKey(
        val spineIndex: Int,
        val sig: Int,
        val viewportWidth: Int,
        val viewportHeight: Int
    )

    private val templateCache = SimpleLruCache<TemplateKey, String>(maxSize = 24)

    fun compose(
        container: EpubContainer,
        spineIndex: Int,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints?,
        pageIndex: Int,
        baseRelPath: String,
        anchorId: String?,
        sig: Int,
        spineIndexForMetrics: Int
    ): String {
        val width = constraints?.viewportWidthPx ?: 0
        val height = constraints?.viewportHeightPx ?: 0

        val key = TemplateKey(
            spineIndex = spineIndex,
            sig = sig,
            viewportWidth = width,
            viewportHeight = height
        )

        val template = templateCache.getOrPut(key) {
            val chapterFile = File(container.rootDir, container.spinePath(spineIndex))
            val raw = runCatching { chapterFile.readText() }.getOrDefault("")
            val body = extractBody(raw) ?: raw
            val css = buildCss(config, constraints)
            val baseHref = container.spineUri(spineIndex).toString()

            """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <base href="$baseHref"/>
                <style>$css</style>
              </head>
              <body>
                <div class="reader-content">$body</div>
                <script>
                  (function(){
                    const VW = __VW__;
                    const PAGE = __PAGE__;
                    const BASE_REL = "__BASE_REL__";
                    const ANCHOR = "__ANCHOR__";
                    const SPINE = __SPINE__;
                    const SIG = __SIG__;

                    function normalizePath(path){
                      const parts = [];
                      path.split('/').forEach(seg => {
                        if(!seg || seg === '.') return;
                        if(seg === '..'){ if(parts.length) parts.pop(); return; }
                        parts.push(seg);
                      });
                      return parts.join('/');
                    }

                    function resolveFrom(baseRel, href){
                      const raw = (href || '').split('#')[0] || '';
                      const baseDir = baseRel.includes('/') ? baseRel.substring(0, baseRel.lastIndexOf('/')) : '';
                      const joined = raw ? (baseDir ? (baseDir + '/' + raw) : raw) : baseRel;
                      return normalizePath(joined);
                    }

                    function isAbsoluteScheme(h){
                      return /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(h);
                    }

                    function goToPage(){
                      if(!VW || VW <= 0) return;
                      const scroller = document.scrollingElement || document.documentElement;
                      scroller.scrollLeft = Math.max(0, PAGE) * VW;
                    }

                    function goToAnchor(){
                      if(!ANCHOR) return;
                      const target = document.getElementById(ANCHOR) || document.querySelector('[name="' + ANCHOR + '"]');
                      if(target && target.scrollIntoView){
                        target.scrollIntoView();
                      }
                    }

                    function reportMetrics(){
                      if(!VW || VW <= 0) return null;
                      const scroller = document.scrollingElement || document.documentElement;
                      const scrollWidth = scroller.scrollWidth || 0;
                      const pages = Math.max(1, Math.ceil(scrollWidth / VW));
                      const page = Math.max(0, Math.round((scroller.scrollLeft || 0) / VW));
                      window.location.href = 'reader://metrics?spine=' + SPINE + '&sig=' + SIG + '&pages=' + pages + '&page=' + page;
                      return { pages: pages, page: page };
                    }

                    function clamp01(value){
                      return Math.max(0, Math.min(1, value));
                    }

                    function round3(value){
                      return Math.round(value * 1000) / 1000;
                    }

                    function reportLinkBounds(page){
                      try{
                        const links = [];
                        const nodes = document.querySelectorAll('a[href]');
                        const maxCount = 40;
                        const vh = Math.max(1, window.innerHeight || 1);
                        for(let i = 0; i < nodes.length && links.length < maxCount; i++){
                          const anchor = nodes[i];
                          const rect = anchor.getBoundingClientRect();
                          if(rect.right <= 0 || rect.bottom <= 0 || rect.left >= VW || rect.top >= vh){
                            continue;
                          }

                          const href = anchor.getAttribute('href') || '';
                          if(!href) continue;

                          let key = '';
                          if(isAbsoluteScheme(href)){
                            key = 'E|' + href;
                          }else{
                            const fragment = href.includes('#') ? href.split('#')[1] : '';
                            const resolved = resolveFrom(BASE_REL, href);
                            const value = 'href:' + resolved + (fragment ? ('#' + fragment) : '');
                            key = 'I|' + value;
                          }

                          const left = clamp01(rect.left / VW);
                          const top = clamp01(rect.top / vh);
                          const right = clamp01(rect.right / VW);
                          const bottom = clamp01(rect.bottom / vh);
                          if(right <= left || bottom <= top) continue;

                          links.push({
                            k: key,
                            l: round3(left),
                            t: round3(top),
                            r: round3(right),
                            b: round3(bottom)
                          });
                        }

                        if(links.length === 0) return;
                        const data = encodeURIComponent(JSON.stringify(links));
                        window.location.href =
                          'reader://linkbounds?spine=' + SPINE + '&sig=' + SIG + '&page=' + page + '&data=' + data;
                      }catch(e){}
                    }

                    let reportTimer = 0;
                    let lastReportedPage = -1;

                    function reportAll(){
                      const metrics = reportMetrics();
                      if(!metrics) return;
                      if(metrics.page !== lastReportedPage){
                        lastReportedPage = metrics.page;
                        reportLinkBounds(metrics.page);
                      }
                    }

                    function debounceReport(){
                      if(reportTimer) clearTimeout(reportTimer);
                      reportTimer = setTimeout(reportAll, 120);
                    }

                    document.addEventListener('click', function(event){
                      const anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
                      if(!anchor) return;

                      const href = anchor.getAttribute('href') || '';
                      if(!href) return;

                      event.preventDefault();
                      if(isAbsoluteScheme(href)){
                        window.location.href = 'reader://external?url=' + encodeURIComponent(href);
                        return;
                      }

                      const fragment = href.includes('#') ? href.split('#')[1] : '';
                      const resolved = resolveFrom(BASE_REL, href);
                      const value = 'href:' + resolved + (fragment ? ('#' + fragment) : '');
                      window.location.href = 'reader://goto?scheme=epub.cfi&value=' + encodeURIComponent(value);
                    }, true);

                    window.addEventListener('scroll', function(){
                      debounceReport();
                    }, {passive:true});

                    window.addEventListener('load', function(){
                      goToPage();
                      goToAnchor();
                      setTimeout(reportAll, 0);
                    });
                  })();
                </script>
              </body>
            </html>
            """.trimIndent()
        }

        return template
            .replace("__VW__", width.toString())
            .replace("__PAGE__", pageIndex.toString())
            .replace("__BASE_REL__", escapeJs(baseRelPath))
            .replace("__ANCHOR__", escapeJs(anchorId.orEmpty()))
            .replace("__SPINE__", spineIndexForMetrics.toString())
            .replace("__SIG__", sig.toString())
    }

    private fun buildCss(config: RenderConfig.ReflowText, constraints: LayoutConstraints?): String {
        val density = constraints?.density ?: 1f
        val fontScale = constraints?.fontScale ?: 1f
        val width = constraints?.viewportWidthPx ?: 0
        val height = constraints?.viewportHeightPx ?: 0

        val fontPx = config.fontSizeSp * density * fontScale
        val paddingPx = config.pagePaddingDp * density

        val columnsCss = if (width > 0 && height > 0) {
            """
            html, body {
              width: ${width}px;
              height: ${height}px;
              overflow: hidden;
            }
            .reader-content {
              min-height: ${height}px;
              -webkit-column-width: ${width}px;
              column-width: ${width}px;
              -webkit-column-gap: 0;
              column-gap: 0;
              column-fill: auto;
            }
            """.trimIndent()
        } else {
            ""
        }

        return """
            body {
              margin: 0;
              padding: ${paddingPx}px;
              font-size: ${fontPx}px;
              line-height: ${config.lineHeightMult};
              word-break: break-word;
              overflow-wrap: anywhere;
            }
            p { margin: 0 0 ${config.paragraphSpacingDp * density}px 0; }
            img { max-width: 100%; height: auto; }
            a { color: inherit; text-decoration: underline; }
            $columnsCss
        """.trimIndent()
    }

    private fun extractBody(html: String): String? {
        val lower = html.lowercase()
        val bodyStart = lower.indexOf("<body")
        if (bodyStart < 0) return null

        val bodyContentStart = lower.indexOf('>', bodyStart)
        if (bodyContentStart < 0) return null

        val bodyEnd = lower.lastIndexOf("</body>")
        if (bodyEnd < 0 || bodyEnd <= bodyContentStart) return null

        return html.substring(bodyContentStart + 1, bodyEnd)
    }

    private fun escapeJs(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
