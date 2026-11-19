package cl.silbom.tecnico;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * SILBOM Android: WebView local + lector NFC NDEF de texto real.
 *
 * Importante: una etiqueta solo identifica una máquina. El backend verifica
 * la existencia, los permisos y cualquier operación LOTO posterior.
 */
public final class MainActivity extends Activity {

    private static final String APP_URL = "file:///android_asset/index.html";
    private static final Pattern MACHINE_CODE = Pattern.compile("^EQ-[A-Z]{2,4}-[0-9]{2}$");

    private WebView webView;
    private NfcAdapter nfcAdapter;
    private volatile boolean scanRequested;

    private final class SilbomNfcBridge {
        @JavascriptInterface
        public void startScan() {
            runOnUiThread(() -> beginScan());
        }

        @JavascriptInterface
        public void cancelScan() {
            runOnUiThread(() -> stopScan());
        }
    }

    private final NfcAdapter.ReaderCallback nfcReader = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {
            synchronized (MainActivity.this) {
                if (!scanRequested) return; // Ignora lecturas múltiples del mismo sticker.
                scanRequested = false;
            }
            String result = null;
            String error = null;
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                error = "no_ndef";
            } else {
                try {
                    ndef.connect();
                    NdefMessage message = ndef.getNdefMessage();
                    if (message == null || message.getRecords().length == 0) {
                        error = "no_text";
                    } else {
                        int textCount = 0;
                        for (NdefRecord record : message.getRecords()) {
                            if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN
                                    && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
                                textCount++;
                                result = NfcTextDecoder.decodeText(record.getPayload());
                            }
                        }
                        if (textCount != 1) {
                            error = textCount == 0 ? "no_text" : "multiple_text";
                        } else if (result == null || !MACHINE_CODE.matcher(result).matches()) {
                            error = "invalid_code";
                        }
                    }
                } catch (Exception exception) {
                    error = "read_failed";
                } finally {
                    try {
                        ndef.close();
                    } catch (IOException ignored) {
                        // El tag puede alejarse mientras se finaliza la lectura.
                    }
                }
            }
            final String code = result;
            final String finalError = error;
            runOnUiThread(() -> {
                disableReader();
                if (finalError != null) {
                    sendJsEvent("onError", finalError);
                } else {
                    sendJsEvent("onRead", code);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFF22272B);
            getWindow().setNavigationBarColor(0xFF22272B);
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true); // Assets HTML empaquetados en la APK.
        settings.setAllowContentAccess(false);
        // Compatibilidad con la WebView existente; requiere auditoría antes de producción.
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        settings.setUserAgentString(settings.getUserAgentString() + " SilbomNativeApp/1.1");
        webView.addJavascriptInterface(new SilbomNfcBridge(), "SilbomAndroidNfc");

        // Impide cargar sitios remotos dentro de una WebView que tiene un puente nativo.
        webView.setWebViewClient(new WebViewClient() {
            private boolean handleNavigation(Uri uri) {
                if (APP_URL.equals(uri.toString())) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                        // URL externa sin aplicación capaz de abrirla.
                    }
                }
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl(APP_URL);
    }

    private void beginScan() {
        if (nfcAdapter == null) {
            sendJsEvent("onError", "nfc_unavailable");
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            sendJsEvent("onError", "nfc_disabled");
            return;
        }
        synchronized (this) {
            scanRequested = true;
        }
        try {
            nfcAdapter.enableReaderMode(this, nfcReader,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V,
                    null);
        } catch (Exception exception) {
            scanRequested = false;
            sendJsEvent("onError", "scan_failed");
        }
    }

    private void disableReader() {
        if (nfcAdapter == null) return;
        try {
            nfcAdapter.disableReaderMode(this);
        } catch (Exception ignored) {
            // No era posible dejar activo el lector; no se ejecuta ninguna acción.
        }
    }

    private void stopScan() {
        scanRequested = false;
        disableReader();
        sendJsEvent("onError", "cancelled");
    }

    private void sendJsEvent(String method, String value) {
        if (webView == null || !APP_URL.equals(webView.getUrl())) return;
        webView.evaluateJavascript(
                "window.SilbomNfc && window.SilbomNfc." + method
                        + "(" + JSONObject.quote(value) + ");", null);
    }

    @Override
    protected void onPause() {
        scanRequested = false;
        disableReader();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scanRequested = false;
        disableReader();
        if (webView != null) {
            webView.removeJavascriptInterface("SilbomAndroidNfc");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
