package br.gastos.app;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;

public class NotifService extends NotificationListenerService {
  // Ao conectar, guarda também as notificações que já estão na barra (caso o serviço tenha reiniciado).
  @Override public void onListenerConnected() {
    try { StatusBarNotification[] ativas = getActiveNotifications(); if (ativas != null) for (StatusBarNotification s : ativas) onNotificationPosted(s); } catch (Exception e) { }
  }

  // Apagar a notificação da barra NÃO remove a transação pendente: ela só sai do app ao confirmar ou excluir na aba Aprovar.
  @Override public void onNotificationRemoved(StatusBarNotification sbn) { }

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    try {
      String pkg = sbn.getPackageName();
      if (pkg == null) return;
      String pk = pkg.toLowerCase();
      boolean picpay = pk.contains("picpay");
      boolean gmail = pk.equals("com.google.android.gm");
      boolean carteira = pk.contains("walletnfcrel") || pk.equals("com.google.android.apps.wallet") || pk.contains("google.android.apps.nbu.paisa");
      if (!picpay && !gmail && !carteira) return;
      Bundle ex = sbn.getNotification().extras;
      String titulo = String.valueOf(ex.getCharSequence(Notification.EXTRA_TITLE, ""));
      StringBuilder sb = new StringBuilder();
      CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
      CharSequence txt = ex.getCharSequence(Notification.EXTRA_TEXT);
      if (big != null) sb.append(big); else if (txt != null) sb.append(txt);
      if (big != null && txt != null && !big.toString().contains(txt)) sb.append(" ").append(txt);
      CharSequence[] linhas = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
      if (linhas != null) for (CharSequence l : linhas) sb.append(" ").append(l);
      CharSequence sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT);
      if (sub != null) sb.append(" ").append(sub);
      String texto = sb.toString().trim();
      String tudo = (titulo + " " + texto);
      if (gmail) {
        String low = tudo.toLowerCase();
        if (!low.contains("pix")) return;
        if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0 && linhas == null) return;
      } else if (!tudo.contains("R$")) return;
      SharedPreferences p = getSharedPreferences("gastos", MODE_PRIVATE);
      synchronized (NotifService.class) {
        JSONArray arr = new JSONArray(p.getString("notifs", "[]"));
        String chave = (titulo + "|" + texto).trim();
        for (int i = 0; i < arr.length(); i++) if (chave.equals(arr.getJSONObject(i).optString("chave"))) return;
        JSONArray vistas = new JSONArray(p.getString("notifsVistas", "[]"));
        for (int i = 0; i < vistas.length(); i++) if (chave.equals(vistas.optString(i))) return;
        JSONObject o = new JSONObject();
        o.put("id", sbn.getPostTime() + "_" + Math.abs(chave.hashCode()));
        o.put("ts", sbn.getPostTime());
        o.put("titulo", titulo);
        o.put("texto", texto);
        o.put("chave", chave);
        o.put("pkg", pkg);
        arr.put(o);
        p.edit().putString("notifs", arr.toString()).commit();
      }
    } catch (Exception e) { }
  }
}
