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
      if (!picpay && !gmail) return;
      Bundle ex = sbn.getNotification().extras;
      String titulo = String.valueOf(ex.getCharSequence(Notification.EXTRA_TITLE, ""));
      CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
      String texto = String.valueOf(big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT, ""));
      String tudo = (titulo + " " + texto);
      if (!tudo.contains("R$")) return;
      if (gmail && !tudo.toLowerCase().contains("pix")) return;
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
