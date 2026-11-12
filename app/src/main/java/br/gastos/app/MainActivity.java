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
  static final String URL_ONLINE = "https://canossapedro983-jpg.github.io/Controle/";
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
    if (atual == this) atual = null;
    try { cm.unregisterNetworkCallback(redeCb); } catch (Exception e) { }
    super.onDestroy();
  }

  static volatile MainActivity atual;
  void avisarPagina() { runOnUiThread(() -> { if (web != null) web.evaluateJavascript("try{window.subirNotifs&&subirNotifs();window.atualizaAprovar&&atualizaAprovar()}catch(e){}", null); }); }
  @Override protected void onResume() {
    super.onResume(); atual = this;
    try { if (Build.VERSION.SDK_INT >= 24) android.service.notification.NotificationListenerService.requestRebind(new android.content.ComponentName(this, NotifService.class)); } catch (Exception e) { }
    avisarPagina();
  }

  @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    if (req == 1 && arquivoCb != null) {
      arquivoCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
      arquivoCb = null;
    }
  }

  @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

  void responderBio(boolean ok) { runOnUiThread(() -> web.evaluateJavascript("window.bioResultado&&bioResultado(" + ok + ")", null)); }

  class Ponte {
    @JavascriptInterface public String getNotifs() { return prefs.getString("notifs", "[]"); }
    @JavascriptInterface public void removerNotif(String id) {
      synchronized (NotifService.class) {
        try {
          org.json.JSONArray a = new org.json.JSONArray(prefs.getString("notifs", "[]")), b = new org.json.JSONArray();
          org.json.JSONArray v = new org.json.JSONArray(prefs.getString("notifsVistas", "[]"));
          for (int i = 0; i < a.length(); i++) { org.json.JSONObject o = a.getJSONObject(i); if (id.equals(o.optString("id"))) v.put(o.optString("chave")); else b.put(o); }
          org.json.JSONArray v2 = new org.json.JSONArray(); for (int i = Math.max(0, v.length() - 300); i < v.length(); i++) v2.put(v.get(i));
          prefs.edit().putString("notifs", b.toString()).putString("notifsVistas", v2.toString()).commit();
        } catch (Exception e) { }
      }
    }
    @JavascriptInterface public boolean notifPermitida() {
      String s = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
      return s != null && s.contains(getPackageName());
    }
    @JavascriptInterface public void abrirPermissaoNotif() {
      runOnUiThread(() -> { try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); } catch (Exception e) { } });
    }
    @JavascriptInterface public void lembrete(boolean ligado, int h, int m) {
      prefs.edit().putBoolean("lembrete", ligado).putInt("lembreteH", h).putInt("lembreteM", m).apply();
      if (ligado && Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
        runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 5));
      LembreteReceiver.reagendar(MainActivity.this);
    }
    @JavascriptInterface public boolean temBiometria() {
      if (Build.VERSION.SDK_INT < 29) return false;
      try {
        android.hardware.biometrics.BiometricManager bm = (android.hardware.biometrics.BiometricManager) getSystemService(Context.BIOMETRIC_SERVICE);
        return bm != null && bm.canAuthenticate() == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
      } catch (Exception e) { return false; }
    }
    @JavascriptInterface public void biometria() {
      if (Build.VERSION.SDK_INT < 29) { responderBio(false); return; }
      runOnUiThread(() -> {
        try {
          android.hardware.biometrics.BiometricPrompt.Builder b = new android.hardware.biometrics.BiometricPrompt.Builder(MainActivity.this)
            .setTitle("Controle de gastos").setSubtitle("Desbloqueie para ver seus gastos");
          b.setNegativeButton("Usar PIN", getMainExecutor(), (d, w) -> responderBio(false));
          b.build().authenticate(new android.os.CancellationSignal(), getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) { responderBio(true); }
            @Override public void onAuthenticationError(int c, CharSequence s) { responderBio(false); }
          });
        } catch (Exception e) { responderBio(false); }
      });
    }
    @JavascriptInterface public boolean bateriaOk() {
      try { return ((android.os.PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()); } catch (Exception e) { return true; }
    }
    @JavascriptInterface public void pedirBateria() {
      runOnUiThread(() -> {
        try { startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception e2) { } }
      });
    }
    @JavascriptInterface public String getDiag() { return prefs.getString("diag", "[]"); }
    @JavascriptInterface public void limparDiag() { prefs.edit().putString("diag", "[]").apply(); }
    @JavascriptInterface public void reconectarNotif() {
      try {
        android.content.ComponentName cn = new android.content.ComponentName(MainActivity.this, NotifService.class);
        android.content.pm.PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
        if (Build.VERSION.SDK_INT >= 24) android.service.notification.NotificationListenerService.requestRebind(cn);
      } catch (Exception e) { }
    }
    @JavascriptInterface public boolean notifTeste() {
      try {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
          runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 6)); return false;
        }
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new android.app.NotificationChannel("teste", "Teste de leitura", android.app.NotificationManager.IMPORTANCE_DEFAULT));
        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(MainActivity.this, "teste") : new android.app.Notification.Builder(MainActivity.this);
        b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(NotifService.TESTE + " " + (System.currentTimeMillis() % 1000)).setContentText("Compra de R$ 1,23 aprovada em LOJA TESTE").setAutoCancel(true);
        nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
        return true;
      } catch (Exception e) { return false; }
    }
    @JavascriptInterface public boolean modoOffline() { return offline; }
    @JavascriptInterface public String getFila() { return prefs.getString("fila", "{}"); }
    @JavascriptInterface public void setFila(String s) { prefs.edit().putString("fila", s).commit(); }
    @JavascriptInterface public String getCache() { return prefs.getString("cache", ""); }
    @JavascriptInterface public void salvarCache(String s) { prefs.edit().putString("cache", s).apply(); }
    @JavascriptInterface public void irOnline() { runOnUiThread(() -> { if (temInternet()) abrirOnline(); }); }

    @JavascriptInterface public boolean salvarBase64(String nome, String b64, String mime) {
      try { return gravar(nome, android.util.Base64.decode(b64, android.util.Base64.DEFAULT), mime); } catch (Exception e) { return false; }
    }
    @JavascriptInterface public boolean salvar(String nome, String texto) {
      String mime = nome.endsWith(".html") ? "text/html" : nome.endsWith(".json") ? "application/json" : "text/csv";
      return gravar(nome, texto.getBytes(StandardCharsets.UTF_8), mime);
    }
    boolean gravar(String nome, byte[] bytes, String mime) {
      try {
        OutputStream out;
        if (Build.VERSION.SDK_INT >= 29) {
          ContentValues cv = new ContentValues();
          cv.put(MediaStore.Downloads.DISPLAY_NAME, nome);
          cv.put(MediaStore.Downloads.MIME_TYPE, mime);
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
