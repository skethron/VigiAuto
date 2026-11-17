package br.com.vigiauto.data.lookup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import br.com.vigiauto.domain.model.NmtStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "NmtWebView"
private const val NMT_URL = "https://ottcsmt.prefeitura.sp.gov.br/CMUV/Publico/"
private const val QUERY_TIMEOUT_MS = 75_000L
private const val POLL_INTERVAL_MS = 800L
private const val MAX_SUBMIT_RETRIES = 40

/**
 * Executa a consulta OTTC/NMT em um WebView sem nenhuma janela visível.
 *
 * O WebView é criado no processo do app, mas nunca é anexado a uma Activity
 * ou a uma janela. O site oficial continua executando o reCAPTCHA invisível
 * normalmente; o app apenas preenche a placa, aciona o botão de pesquisa e lê
 * o bloco de resposta. Não há tela, progresso, toast ou qualquer retorno
 * visual para o usuário.
 *
 * Esta é a alternativa possível neste APK enquanto não existe um backend
 * próprio. A especificação recomenda mover a automação para um servidor; a
 * interface do worker foi isolada para permitir essa troca sem tocar na UI.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class NmtWebViewSession(
    private val context: Context,
    private val plate: String
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val submitted = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)
    private val submitRetries = AtomicInteger(0)

    private var deferred: CompletableDeferred<NmtPageResult>? = null
    private var webView: WebView? = null
    private var pollRunnable: Runnable? = null

    suspend fun query(): NmtPageResult {
        val result = CompletableDeferred<NmtPageResult>()
        deferred = result

        val created = runCatching {
            withContext(Dispatchers.Main.immediate) { createWebView() }
        }.onFailure {
            Log.e(TAG, "Falha ao criar WebView oculto", it)
        }.getOrNull()

        if (created == null) {
            withContext(NonCancellable + Dispatchers.Main.immediate) { destroyWebView() }
            return NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "webview_create")
        }

        return try {
            withTimeoutOrNull(QUERY_TIMEOUT_MS) { result.await() }
                ?: NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "timeout")
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                destroyWebView()
            }
        }
    }

    private fun createWebView(): WebView {
        val view = WebView(context)
        // Registra a instância antes da configuração: se qualquer etapa falhar,
        // o finally de query() consegue destruir o WebView parcial.
        webView = view
        view.apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.INVISIBLE
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                saveFormData = false
                setGeolocationEnabled(false)
            }
            // O CookieManager é compartilhado com o WebView do login gov.br.
            // Não limpamos cookies globais; o app não lê nem grava cookies.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                // O site não deve abrir diálogo; se abrir, responde sem UI.
                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onJsPrompt(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    defaultValue: String?,
                    result: JsPromptResult?
                ): Boolean {
                    result?.confirm("")
                    return true
                }
            }
            webViewClient = createClient()
            // O WebView não é anexado a uma janela, mas recebe dimensões
            // virtuais para o layout e o reCAPTCHA terem um viewport válido.
            measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 1080, 1920)
            loadUrl(NMT_URL)
            resumeTimers()
        }
        // Fallback: mesmo que onPageFinished não seja entregue para um
        // WebView fora da hierarquia, tenta preencher o formulário depois
        // do carregamento inicial. O script só envia quando os campos
        // existem e o reCAPTCHA está pronto.
        //
        // Precisa usar o handler da sessão, fora de view.apply: dentro do
        // apply, `handler` resolveria para View.getHandler(), que é nulo
        // enquanto o WebView não está anexado a uma janela.
        mainHandler.postDelayed({
            if (!completed.get() && !submitted.get()) submitForm()
        }, 5_000L)
        return view
    }

    private fun createClient(): WebViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            if (url?.contains("/CMUV/Publico") != true || completed.get()) return
            if (!submitted.get()) {
                submitForm()
            } else {
                schedulePoll(250L)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                finish(NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "network"))
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                finish(
                    NmtPageResult(
                        NmtStatus.UNAVAILABLE,
                        errorCode = "http_${errorResponse.statusCode}"
                    )
                )
            }
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            finish(NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "renderer_gone"))
            return true
        }
    }

    private fun submitForm() {
        if (completed.get() || !submitted.compareAndSet(false, true)) return
        val view = webView ?: return

        runCatching {
            view.evaluateJavascript(submitScript(plate)) { raw ->
                if (completed.get()) return@evaluateJavascript
                val value = decodeJsString(raw)
                val retry = submitRetries.incrementAndGet()
                if ((value == "wait" || value.isEmpty()) && retry <= MAX_SUBMIT_RETRIES) {
                    submitted.set(false)
                    mainHandler.postDelayed({ submitForm() }, 500L)
                } else {
                    schedulePoll(POLL_INTERVAL_MS)
                }
            }
        }.onFailure {
            finish(NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "submit_js"))
        }
    }

    private fun schedulePoll(delayMs: Long) {
        if (completed.get()) return
        val runnable = pollRunnable ?: Runnable { pollResult() }.also { pollRunnable = it }
        mainHandler.removeCallbacks(runnable)
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun pollResult() {
        if (completed.get()) return
        val view = webView ?: return
        runCatching {
            view.evaluateJavascript(READ_RESULT_JS) { raw ->
                if (completed.get()) return@evaluateJavascript
                val state = decodeState(raw)
                if (state == null) {
                    schedulePoll(POLL_INTERVAL_MS)
                } else {
                    finish(NmtPageParser.parse(state.html))
                }
            }
        }.onFailure {
            finish(NmtPageResult(NmtStatus.UNAVAILABLE, errorCode = "read_js"))
        }
    }

    private fun finish(result: NmtPageResult) {
        if (!completed.compareAndSet(false, true)) return
        mainHandler.removeCallbacksAndMessages(null)
        deferred?.complete(result)
    }

    private fun destroyWebView() {
        mainHandler.removeCallbacksAndMessages(null)
        val view = webView ?: return
        webView = null
        runCatching { view.stopLoading() }
        runCatching { view.loadUrl("about:blank") }
        runCatching { view.clearHistory() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }

    private data class DomState(val html: String)

    private fun decodeState(raw: String?): DomState? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val value = JSONTokener(raw).nextValue()
            val json = when (value) {
                is String -> JSONObject(value)
                is JSONObject -> value
                else -> return null
            }
            val html = json.optString("html")
            if (html.isBlank()) null else DomState(html)
        }.getOrNull()
    }

    private fun decodeJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching {
            when (val value = JSONTokener(raw).nextValue()) {
                is String -> value
                null -> ""
                else -> value.toString()
            }
        }.getOrDefault(raw.trim('"'))
    }

    private fun submitScript(plate: String): String {
        val quotedPlate = JSONObject.quote(plate)
        return """
            (function() {
              var plate = $quotedPlate;
              var clicked = false;
              var tab = document.querySelector('a[href="#tabplaca"]');
              if (tab) tab.click();
              var section = document.getElementById('paginaBusca');
              if (section) {
                section.value = 'V';
                section.dispatchEvent(new Event('change', { bubbles: true }));
              }
              var input = document.getElementById('placa');
              var button = document.getElementById('btn_veiculo');
              if (!input || !button) return 'wait';
              input.focus();
              input.value = plate;
              input.dispatchEvent(new Event('input', { bubbles: true }));
              input.dispatchEvent(new Event('change', { bubbles: true }));
              function clickButton() {
                if (clicked) return;
                clicked = true;
                try { button.click(); } catch (e) {}
              }
              var attempts = 0;
              function waitForCaptcha() {
                if (window.grecaptcha && typeof window.grecaptcha.execute === 'function') {
                  if (typeof window.grecaptcha.ready === 'function') {
                    window.grecaptcha.ready(clickButton);
                    setTimeout(clickButton, 12000);
                  } else {
                    clickButton();
                  }
                  return;
                }
                if (attempts++ < 40) {
                  setTimeout(waitForCaptcha, 500);
                } else {
                  clickButton();
                }
              }
              waitForCaptcha();
              return 'ok';
            })();
        """.trimIndent()
    }

    companion object {
        /**
         * Lê somente o bloco que o site tornou visível. O HTML completo, com
         * `__VIEWSTATE` e token, permanece dentro do WebView e não é
         * persistido nem enviado para fora.
         */
        private const val READ_RESULT_JS = """
            (function() {
              function visible(el) {
                if (!el || el.hidden) return false;
                var style = el.style;
                if (style && (style.display === 'none' || style.visibility === 'hidden')) return false;
                return ((el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().length > 0);
              }
              var result = document.getElementById('retornoMsg');
              if (visible(result)) return JSON.stringify({ html: result.outerHTML });
              var error = document.getElementById('errorMsg');
              if (visible(error)) return JSON.stringify({ html: error.outerHTML });
              return '';
            })();
        """
    }
}
