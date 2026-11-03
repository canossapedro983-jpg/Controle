package br.gastos.app;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;

public class NotifService extends NotificationListenerService {
  static final String TESTE = "Teste Controle de gastos";

  @Override public void onListenerConnected() {
    diagEvento("Leitor conectado");
    try { StatusBarNotification[] ativas = getActiveNotifications(); if (ativas != null) for (StatusBarNotification s : ativas) onNotificationPosted(s); } catch (Exception e) { diagEvento("Erro ao ler notificações ativas: " + e); }
  }
  @Override public void onListenerDisconnected() { diagEvento("Leitor desconectado pelo Android"); }

  // Apagar a notificação da barra NÃO remove a transação pendente.
  @Override public void onNotificationRemoved(StatusBarNotification sbn) { }

  static CharSequence cs(Bundle ex, String k) { try { Object o = ex.get(k); return o instanceof CharSequence ? (CharSequence) o : null; } catch (Exception e) { return null; } }

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    String pkg = null, titulo = "", texto = "";
    try {
      pkg = sbn.getPackageName();
      if (pkg == null) return;
      String pk = pkg.toLowerCase();
      Bundle ex = sbn.getNotification().extras;
      CharSequence t = cs(ex, Notification.EXTRA_TITLE); if (t == null) t = cs(ex, Notification.EXTRA_TITLE_BIG);
      titulo = t == null ? "" : t.toString();
      StringBuilder sb = new StringBuilder();
      CharSequence big = cs(ex, Notification.EXTRA_BIG_TEXT), txt = cs(ex, Notification.EXTRA_TEXT);
      if (big != null) sb.append(big); else if (txt != null) sb.append(txt);
      if (big != null && txt != null && !big.toString().contains(txt)) sb.append(" ").append(txt);
      try { CharSequence[] linhas = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES); if (linhas != null) for (CharSequence l : linhas) sb.append(" ").append(l); } catch (Exception e) { }
      CharSequence sub = cs(ex, Notification.EXTRA_SUB_TEXT); if (sub != null) sb.append(" ").append(sub);
      CharSequence sum = cs(ex, Notification.EXTRA_SUMMARY_TEXT); if (sum != null) sb.append(" ").append(sum);
      CharSequence tick = sbn.getNotification().tickerText; if (sb.length() == 0 && tick != null) sb.append(tick);
      texto = sb.toString().replace(' ', ' ').trim();
      String tudo = (titulo + " " + texto).replace(' ', ' ');
      String low = tudo.toLowerCase();
      boolean teste = pkg.equals(getPackageName()) && titulo.startsWith(TESTE);
      boolean picpay = pk.contains("picpay") || teste;
      boolean gmail = pk.equals("com.google.android.gm");
      boolean carteira = pk.contains("walletnfcrel") || pk.contains("apps.wallet") || pk.contains("nbu.paisa") || pk.equals("com.google.android.gms");
      boolean temValor = tudo.contains("R$") || low.contains("brl") || tudo.matches("(?s).*\\d+,\\d{2}.*");
      if (!picpay && !gmail && !carteira) { if (temValor) diag(pkg, "", "", "Ignorada: app não monitorado"); return; }
      if (gmail) {
        if (!low.contains("pix")) { diag(pkg, titulo, "", "Ignorada: e-mail sem Pix"); return; }
      } else if (!temValor) { diag(pkg, titulo, texto, "Ignorada: sem valor em R$"); return; }
      if (pk.equals("com.google.android.gms") && !(low.contains("pag") || low.contains("compra") || low.contains("carteira") || low.contains("wallet") || tudo.contains("R$"))) { diag(pkg, titulo, texto, "Ignorada: Google Play sem pagamento"); return; }
      SharedPreferences p = getSharedPreferences("gastos", MODE_PRIVATE);
      synchronized (NotifService.class) {
        JSONArray arr = new JSONArray(p.getString("notifs", "[]"));
        String chave = (titulo + "|" + texto).trim();
        for (int i = 0; i < arr.length(); i++) if (chave.equals(arr.getJSONObject(i).optString("chave"))) { diag(pkg, titulo, texto, "Já estava na fila"); return; }
        JSONArray vistas = new JSONArray(p.getString("notifsVistas", "[]"));
        for (int i = 0; i < vistas.length(); i++) if (chave.equals(vistas.optString(i))) { diag(pkg, titulo, texto, "Já tratada antes"); return; }
        JSONObject o = new JSONObject();
        o.put("id", sbn.getPostTime() + "_" + Math.abs(chave.hashCode()));
        o.put("ts", sbn.getPostTime());
        o.put("titulo", teste ? "PicPay" : titulo);
        o.put("texto", texto);
        o.put("chave", chave);
        o.put("pkg", teste ? "com.picpay.teste" : pkg);
        arr.put(o);
        p.edit().putString("notifs", arr.toString()).commit();
      }
      diag(pkg, titulo, texto, "Guardada para Aprovar");
    } catch (Exception e) { diag(pkg == null ? "?" : pkg, titulo, texto, "Erro: " + e); }
  }

  void diagEvento(String s) { diag("—", "", "", s); }
  void diag(String pkg, String titulo, String texto, String res) {
    try {
      SharedPreferences p = getSharedPreferences("gastos", MODE_PRIVATE);
      synchronized (NotifService.class) {
        JSONArray a = new JSONArray(p.getString("diag", "[]")), b = new JSONArray();
        JSONObject o = new JSONObject();
        o.put("ts", System.currentTimeMillis()); o.put("pkg", pkg);
        o.put("titulo", titulo.length() > 60 ? titulo.substring(0, 60) : titulo);
        o.put("texto", texto.length() > 120 ? texto.substring(0, 120) : texto);
        o.put("res", res);
        b.put(o); for (int i = 0; i < a.length() && b.length() < 25; i++) b.put(a.get(i));
        p.edit().putString("diag", b.toString()).apply();
      }
    } catch (Exception e) { }
  }
}
