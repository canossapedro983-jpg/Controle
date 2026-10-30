package br.gastos.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  static final String URL_ONLINE = "https://claude.ai/artifact/9RgL67QPKizYzvmnLLhWus";
  static final String URL_OFFLINE = "file:///android_asset/index.html";
  private WebView web;
  private ValueCallback<Uri[]> arquivoCb;
  private SharedPreferences prefs;
  private volatile boolean offline = false;
  private ConnectivityManager cm;
  private ConnectivityManager.NetworkCallback redeCb;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("gastos", MODE_PRIVATE);
    cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true);
    s.setJavaScriptCanOpenWindowsAutomatically(true);
    s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
    web.addJavascriptInterface(new Ponte(), "Android");
    web.setWebViewClient(new WebViewClient() {
      @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
        if (req.isForMainFrame() && !offline) abrirOffline();
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (arquivoCb != null) arquivoCb.onReceiveValue(null);
        arquivoCb = cb;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Escolha o extrato"), 1);
        return true;
      }
    });
    setContentView(web);
    if (temInternet()) abrirOnline(); else abrirOffline();

    redeCb = new ConnectivityManager.NetworkCallback() {
      @Override public void onAvailable(Network n) {
        runOnUiThread(() -> { if (offline) web.evaluateJavascript("window.voltouOnline&&voltouOnline()", null); });
      }
    };
    try { cm.registerDefaultNetworkCallback(redeCb); } catch (Exception e) { }
  }

  private boolean temInternet() {
    try {
      Network n = cm.getActiveNetwork();
      if (n == null) return false;
      NetworkCapabilities c = cm.getNetworkCapabilities(n);
      return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    } catch (Exception e) { return true; }
  }

  private void abrirOnline() { offline = false; web.loadUrl(URL_ONLINE); }
  private void abrirOffline() { offline = true; web.loadUrl(URL_OFFLINE); }

  @Override protected void onDestroy() {
    try { cm.unregisterNetworkCallback(redeCb); } catch (Exception e) { }
    super.onDestroy();
  }

  @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    if (req == 1 && arquivoCb != null) {
      arquivoCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
      arquivoCb = null;
    }
  }

  @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

  class Ponte {
    @JavascriptInterface public boolean modoOffline() { return offline; }
    @JavascriptInterface public String getFila() { return prefs.getString("fila", "{}"); }
    @JavascriptInterface public void setFila(String s) { prefs.edit().putString("fila", s).commit(); }
    @JavascriptInterface public String getCache() { return prefs.getString("cache", ""); }
    @JavascriptInterface public void salvarCache(String s) { prefs.edit().putString("cache", s).apply(); }
    @JavascriptInterface public void irOnline() { runOnUiThread(() -> { if (temInternet()) abrirOnline(); }); }

    @JavascriptInterface public boolean salvar(String nome, String texto) {
      try {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        OutputStream out;
        if (Build.VERSION.SDK_INT >= 29) {
          ContentValues cv = new ContentValues();
          cv.put(MediaStore.Downloads.DISPLAY_NAME, nome);
          cv.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
          Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
          out = getContentResolver().openOutputStream(u);
        } else {
          File d = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
          out = new FileOutputStream(new File(d, nome));
        }
        out.write(bytes); out.close();
        return true;
      } catch (Exception e) { return false; }
    }
  }
}
