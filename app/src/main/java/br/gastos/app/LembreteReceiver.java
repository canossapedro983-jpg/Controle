package br.gastos.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;

public class LembreteReceiver extends BroadcastReceiver {
  static final String CANAL = "lembrete";

  @Override public void onReceive(Context ctx, Intent it) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(it.getAction())) { reagendar(ctx); return; }
    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CANAL, "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT));
    int pend = 0;
    try { pend = new org.json.JSONArray(ctx.getSharedPreferences("gastos", Context.MODE_PRIVATE).getString("notifs", "[]")).length(); } catch (Exception e) { }
    Intent abrir = new Intent(ctx, MainActivity.class);
    abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pi = PendingIntent.getActivity(ctx, 1, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CANAL) : new Notification.Builder(ctx);
    b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Controle de gastos")
      .setContentText(pend > 0 ? pend + " transação(ões) esperando na aba Aprovar." : "Hora de conferir seus gastos de hoje.")
      .setContentIntent(pi).setAutoCancel(true);
    nm.notify(77, b.build());
    reagendar(ctx);
  }

  static void reagendar(Context ctx) {
    SharedPreferences p = ctx.getSharedPreferences("gastos", Context.MODE_PRIVATE);
    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    Intent i = new Intent(ctx, LembreteReceiver.class);
    PendingIntent pi = PendingIntent.getBroadcast(ctx, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    am.cancel(pi);
    if (!p.getBoolean("lembrete", false)) return;
    Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR_OF_DAY, p.getInt("lembreteH", 20)); c.set(Calendar.MINUTE, p.getInt("lembreteM", 0)); c.set(Calendar.SECOND, 0);
    if (c.getTimeInMillis() <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_MONTH, 1);
    if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
    else am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
  }
}
