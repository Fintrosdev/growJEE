package dev.fintros.growjee;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_PICKER = 41;
    private static final String HOME = "https://fintrosdev.github.io/growJEE/";
    private FrameLayout root;
    private WebView web;
    private ValueCallback<Uri[]> uploadCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(7, 9, 16));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(7, 9, 16));
        root = new FrameLayout(this);
        web = makeWebView();
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (state == null) web.loadUrl(HOME); else web.restoreState(state);
    }

    private WebView makeWebView() {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (scheme.equals("https") || scheme.equals("http") || scheme.equals("about") || scheme.equals("blob")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "No app can open that link.", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("application/pdf");
                try { startActivityForResult(Intent.createChooser(pick, "Choose a PDF"), FILE_PICKER); }
                catch (ActivityNotFoundException e) { uploadCallback = null; callback.onReceiveValue(null); return false; }
                return true;
            }
            @Override public boolean onCreateWindow(WebView source, boolean dialog, boolean userGesture, android.os.Message resultMsg) {
                WebView popup = makeWebView();
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        web.loadUrl(request.getUrl().toString());
                        root.removeView(popup);
                        popup.destroy();
                        return true;
                    }
                    @Override public void onPageFinished(WebView v, String url) {
                        if (!url.equals("about:blank")) { web.loadUrl(url); root.removeView(popup); popup.destroy(); }
                    }
                });
                root.addView(popup, new FrameLayout.LayoutParams(1, 1));
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        return view;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICKER && uploadCallback != null) {
            Uri[] resultUris = result == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
            uploadCallback.onReceiveValue(resultUris);
            uploadCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) { web.saveState(state); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (uploadCallback != null) uploadCallback.onReceiveValue(null); if (web != null) web.destroy(); super.onDestroy(); }
}
