package com.manoojo.v2;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class LectorNotificacionesService extends NotificationListenerService {
    public interface NotificacionCallback {
        void alLlegarMensaje(String app, String remitente, String texto);
    }

    public static NotificacionCallback listenerEnVivo;
    public static String ultimaNotificacionResumen = "No hay notificaciones recientes";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName()) || pkg.equals("android") || pkg.equals("com.android.systemui")) {
            return;
        }

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        CharSequence titulo = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence texto = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (titulo == null && texto == null) return;

        String rem = titulo != null ? titulo.toString().trim() : "Aviso";
        String msg = texto != null ? texto.toString().trim() : "";
        if (msg.isEmpty()) return;

        String nombreApp = pkg.contains("whatsapp") ? "WhatsApp"
                : pkg.contains("facebook") ? "Facebook"
                : pkg.contains("instagram") ? "Instagram"
                : pkg.contains("tiktok") ? "TikTok"
                : "una aplicación";

        ultimaNotificacionResumen = "Mensaje en " + nombreApp + " de " + rem + ": " + msg;

        if (listenerEnVivo != null) {
            listenerEnVivo.alLlegarMensaje(nombreApp, rem, msg);
        }
    }
}
