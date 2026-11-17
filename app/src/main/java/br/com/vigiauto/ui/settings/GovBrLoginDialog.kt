package br.com.vigiauto.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.vigiauto.data.lookup.SinespV1Protocol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Login gov.br do SINESP dentro do app, no mesmo fluxo do aplicativo oficial:
 * o WebView carrega a página de autorização, o usuário digita a própria conta
 * e o redirecionamento traz um `code` de uso único. O código é trocado pelo
 * token fora do WebView; o app nunca vê a senha nem injeta JavaScript.
 */
@Composable
fun GovBrLoginDialog(
    state: GovBrLoginState,
    onCode: (String) -> Unit,
    onResetState: () -> Unit,
    onDismiss: () -> Unit
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    var showManualUrl by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("") }
    var manualUrlError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar login")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Login gov.br",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Site oficial do gov.br",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()

                Box(modifier = Modifier.weight(1f)) {
                    key(reloadKey) {
                        GovBrWebView(
                            onCode = onCode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    when (state) {
                        GovBrLoginState.Idle -> Unit
                        GovBrLoginState.Exchanging -> LoginOverlay {
                            CircularProgressIndicator()
                            Text(
                                text = "Concluindo login…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Aguarde enquanto o SINESP devolve o token.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        is GovBrLoginState.Success -> LoginOverlay {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Login concluído",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = expiryText(state.expiresAtEpochMs),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            state.warning?.let { warning ->
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Text(
                                text = "O token está cifrado no aparelho e já foi ativado no modo Sinesp.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = onDismiss) { Text("Concluir") }
                        }

                        is GovBrLoginState.Error -> LoginOverlay {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Não foi possível concluir o login",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    reloadKey++
                                    onResetState()
                                }
                            ) { Text("Tentar novamente") }
                            TextButton(onClick = onDismiss) { Text("Fechar") }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            manualUrl = ""
                            manualUrlError = null
                            showManualUrl = true
                        },
                        enabled = state !is GovBrLoginState.Exchanging &&
                            state !is GovBrLoginState.Success
                    ) {
                        Text("Já tenho a URL de retorno")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }

    if (showManualUrl) {
        AlertDialog(
            onDismissRequest = { showManualUrl = false },
            title = { Text("Colar URL de retorno") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Copie a URL que termina em .../api/govbr/redirect?code=... " +
                            "e cole abaixo. O código é de uso único e expira rápido.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = {
                            manualUrl = it
                            manualUrlError = null
                        },
                        label = { Text("URL ou code") },
                        minLines = 3,
                        isError = manualUrlError != null,
                        supportingText = manualUrlError?.let { error ->
                            { Text(error) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = SinespV1Protocol.extractCode(manualUrl)
                        if (code == null) {
                            manualUrlError = "Não achei o parâmetro code= na URL."
                        } else {
                            showManualUrl = false
                            onCode(code)
                        }
                    },
                    enabled = manualUrl.isNotBlank()
                ) { Text("Entrar") }
            },
            dismissButton = {
                TextButton(onClick = { showManualUrl = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun LoginOverlay(content: @Composable ColumnScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GovBrWebView(
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    setSupportMultipleWindows(false)
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // O SSO do gov.br pode tratar WebView genérico como
                    // navegador não suportado; o app de referência usa um UA
                    // móvel de Chrome.
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 15; POCO X7 Pro) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    allowFileAccess = false
                    allowContentAccess = false
                }
                val webView = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webView, true)
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return captureRedirect(request?.url?.toString(), view, onCode)
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?
                    ) {
                        if (captureRedirect(url, view, onCode)) return
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        captureRedirect(url, view, onCode)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress / 100f
                    }
                }

                loadUrl(SinespV1Protocol.AUTHORIZE_URL)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    )

    if (progress < 1f) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun captureRedirect(
    url: String?,
    view: WebView?,
    onCode: (String) -> Unit
): Boolean {
    val code = SinespV1Protocol.extractCodeFromRedirect(url) ?: return false
    view?.stopLoading()
    onCode(code)
    return true
}

private fun expiryText(expiresAtEpochMs: Long?): String {
    if (expiresAtEpochMs == null) {
        return "Token recebido e salvo com criptografia no aparelho."
    }
    val formatted = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        .format(Date(expiresAtEpochMs))
    return "Token válido até $formatted."
}
