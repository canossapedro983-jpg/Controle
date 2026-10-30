package br.gastos.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> arquivoCb;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setDatabaseEnabled(true);
    s.setSupportMultipleWindows(false);
    s.setJavaScriptCanOpenWindowsAutomatically(true);
    web.addJavascriptInterface(new Ponte(), "Android");
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
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
    s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
    web.setWebViewClient(new WebViewClient());
    web.loadUrl("https://claude.ai/artifact/9RgL67QPKizYzvmnLLhWus");
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
