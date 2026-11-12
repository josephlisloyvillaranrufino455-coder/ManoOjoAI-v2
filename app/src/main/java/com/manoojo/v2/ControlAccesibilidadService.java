package com.manoojo.v2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;

public class ControlAccesibilidadService extends AccessibilityService {
    public static ControlAccesibilidadService instancia;
    public static String paqueteActivo = "";
    private WindowManager wmPuntero;
    private String mapaPantallaEnVivo = "";

    public interface CapturaCallback {
        void alObtenerImagen(String base64Jpeg);
    }

    public interface GrabacionCallback {
        void alGrabarBoton(String nombreBoton, float x, float y);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instancia = this;
        wmPuntero = (WindowManager) getSystemService(WINDOW_SERVICE);
        actualizarMapaEnVivo();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            String nuevoPkg = event.getPackageName().toString();
            if (!nuevoPkg.equals(getPackageName()) && !nuevoPkg.equals("com.android.systemui")) {
                paqueteActivo = nuevoPkg;
            }
        }
        actualizarMapaEnVivo();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instancia = null;
    }

        private int anchoPantalla() { return getResources().getDisplayMetrics().widthPixels; }
    private int altoPantalla() { return getResources().getDisplayMetrics().heightPixels; }


    public void actualizarMapaEnVivo() {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return;
        StringBuilder sb = new StringBuilder();
        escanearNodosConCoordenadas(raiz, sb);
        mapaPantallaEnVivo = sb.toString().trim();
    }

    public String obtenerMapaPantalla() {
        actualizarMapaEnVivo();
        return mapaPantallaEnVivo.isEmpty() ? "Pantalla grafica sin texto visible" : mapaPantallaEnVivo;
    }

    private void escanearNodosConCoordenadas(AccessibilityNodeInfo nodo, StringBuilder sb) {
        if (nodo == null || sb.length() > 1600) return;
        Rect r = new Rect();
        nodo.getBoundsInScreen(r);
        String txt = nodo.getText() != null ? nodo.getText().toString().trim() : "";
        String desc = nodo.getContentDescription() != null ? nodo.getContentDescription().toString().trim() : "";
        String id = nodo.getViewIdResourceName() != null ? nodo.getViewIdResourceName() : "";

        if (!r.isEmpty() && (!txt.isEmpty() || !desc.isEmpty() || nodo.isEditable())) {
            String tipo = nodo.isEditable() ? "CAJA" : (nodo.isClickable() ? "BOTON" : "TEXTO");
            String etiqueta = !txt.isEmpty() ? txt : (!desc.isEmpty() ? desc : id);
            sb.append("[").append(tipo).append(": ").append(etiqueta)
              .append(" | x=").append(r.centerX()).append(", y=").append(r.centerY()).append("] ");
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            escanearNodosConCoordenadas(nodo.getChild(i), sb);
        }
    }

    private void animarDedoVisual(float x1, float y1, float x2, float y2, long duracionMs, boolean esPresion) {
        if (wmPuntero == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                View dedo = new View(this);
                GradientDrawable forma = new GradientDrawable();
                forma.setShape(GradientDrawable.OVAL);
                forma.setColor(esPresion ? Color.argb(145, 56, 189, 248) : Color.argb(110, 0, 210, 255));
                forma.setStroke(5, Color.argb(220, 255, 255, 255));
                dedo.setBackground(forma);

                int tipo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

                int tam = esPresion ? 96 : 82;
                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                        tam, tam, tipo,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                p.gravity = Gravity.TOP | Gravity.START;
                p.x = (int) (x1 - tam / 2f);
                p.y = (int) (y1 - tam / 2f);
                wmPuntero.addView(dedo, p);

                if (x1 == x2 && y1 == y2) {
                    dedo.animate().scaleX(1.25f).scaleY(1.25f).alpha(0f).setDuration(Math.max(260, duracionMs + 150))
                            .withEndAction(() -> { try { wmPuntero.removeView(dedo); } catch (Exception ignored) {} }).start();
                } else {
                    int pasos = 14;
                    long intervalo = Math.max(16, duracionMs / pasos);
                    Handler h = new Handler(Looper.getMainLooper());
                    for (int i = 1; i <= pasos; i++) {
                        final float t = i / (float) pasos;
                        final float ease = t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
                        h.postDelayed(() -> {
                            try {
                                p.x = (int) (x1 + (x2 - x1) * ease - tam / 2f);
                                p.y = (int) (y1 + (y2 - y1) * ease - tam / 2f);
                                wmPuntero.updateViewLayout(dedo, p);
                            } catch (Exception ignored) {}
                        }, i * intervalo);
                    }
                    h.postDelayed(() -> {
                        try { wmPuntero.removeView(dedo); } catch (Exception ignored) {}
                    }, duracionMs + 120);
                }
            } catch (Exception ignored) {}
        });
    }

    public boolean ejecutarGesto(float x1, float y1, float x2, float y2, long duracionMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        boolean esToque = (x1 == x2 && y1 == y2);
        animarDedoVisual(x1, y1, x2, y2, duracionMs, esToque);

        Path camino = new Path();
        camino.moveTo(x1, y1);
        if (!esToque) {
            float ctrlX = (x1 + x2) / 2f + ((y2 - y1) * 0.03f);
            float ctrlY = (y1 + y2) / 2f + ((x2 - x1) * 0.03f);
            camino.quadTo(ctrlX, ctrlY, x2, y2);
        }

        GestureDescription gesto = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(camino, 0, duracionMs))
                .build();
        return dispatchGesture(gesto, null, null);
    }

    public boolean ejecutarMultiDedos(float[][] inicios, float[][] fines, long duracionMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        int cantidad = Math.min(inicios.length, 10);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (int i = 0; i < cantidad; i++) {
            float fx = (fines != null && i < fines.length) ? fines[i] : inicios[i];
            float fy = (fines != null && i < fines.length) ? fines[i] : inicios[i];
            animarDedoVisual(inicios[i], inicios[i], fx, fy, duracionMs, false);
            Path p = new Path();
            p.moveTo(inicios[i], inicios[i]);
            if (fines != null && i < fines.length) {
                p.lineTo(fx, fy);
            }
            builder.addStroke(new GestureDescription.StrokeDescription(p, 0, duracionMs));
        }
        return dispatchGesture(builder.build(), null, null);
    }

    public boolean tocarRapido(float x, float y) {
        return ejecutarGesto(x, y, x, y, 80);
    }

    public boolean mantenerPresionado(float x, float y) {
        return ejecutarGesto(x, y, x, y, 850);
    }
        public void gestoSalirInicio() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.50f, h * 0.96f, w * 0.50f, h * 0.55f, 260);
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void gestoRecientes() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.50f, h * 0.96f, w * 0.50f, h * 0.50f, 520);
        new Handler(Looper.getMainLooper()).postDelayed(() -> performGlobalAction(GLOBAL_ACTION_RECENTS), 540);
    }

    public void gestoAtras() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(15f, h * 0.52f, w * 0.45f, h * 0.52f, 240);
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void gestoNotificaciones() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.25f, 25f, w * 0.25f, h * 0.55f, 320);
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public void gestoCentroControl() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.80f, 25f, w * 0.80f, h * 0.55f, 320);
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public void deslizarArriba() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.50f, h * 0.76f, w * 0.50f, h * 0.20f, 360);
    }

    public void deslizarAbajo() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.50f, h * 0.24f, w * 0.50f, h * 0.80f, 360);
    }

    public void deslizarIzquierda() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.88f, h * 0.50f, w * 0.12f, h * 0.50f, 380);
    }

    public void deslizarDerecha() {
        float w = anchoPantalla(), h = altoPantalla();
        ejecutarGesto(w * 0.12f, h * 0.50f, w * 0.88f, h * 0.50f, 380);
    }

    public void acercarZoom() {
        float w = anchoPantalla(), h = altoPantalla();
        GestureDescription.Builder b = new GestureDescription.Builder();
        Path p1 = new Path(); p1.moveTo(w * 0.44f, h * 0.50f); p1.lineTo(w * 0.15f, h * 0.50f);
        Path p2 = new Path(); p2.moveTo(w * 0.56f, h * 0.50f); p2.lineTo(w * 0.85f, h * 0.50f);
        animarDedoVisual(w * 0.44f, h * 0.50f, w * 0.15f, h * 0.50f, 320, false);
        animarDedoVisual(w * 0.56f, h * 0.50f, w * 0.85f, h * 0.50f, 320, false);
        b.addStroke(new GestureDescription.StrokeDescription(p1, 0, 320));
        b.addStroke(new GestureDescription.StrokeDescription(p2, 0, 320));
        dispatchGesture(b.build(), null, null);
    }

    public void alejarZoom() {
        float w = anchoPantalla(), h = altoPantalla();
        GestureDescription.Builder b = new GestureDescription.Builder();
        Path p1 = new Path(); p1.moveTo(w * 0.15f, h * 0.50f); p1.lineTo(w * 0.44f, h * 0.50f);
        Path p2 = new Path(); p2.moveTo(w * 0.85f, h * 0.50f); p2.lineTo(w * 0.56f, h * 0.50f);
        animarDedoVisual(w * 0.15f, h * 0.50f, w * 0.44f, h * 0.50f, 320, false);
        animarDedoVisual(w * 0.85f, h * 0.50f, w * 0.56f, h * 0.50f, 320, false);
        b.addStroke(new GestureDescription.StrokeDescription(p1, 0, 320));
        b.addStroke(new GestureDescription.StrokeDescription(p2, 0, 320));
        dispatchGesture(b.build(), null, null);
    }

    public void accionMultiDedos(int numDedos, String direccion) {
        float w = anchoPantalla(), h = altoPantalla();
        int n = Math.max(1, Math.min(numDedos, 10));
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (int i = 0; i < n; i++) {
            float x1 = (w * 0.15f) + (i * ((w * 0.70f) / Math.max(1, n - 1)));
            float y1 = h * 0.50f;
            float x2 = x1, y2 = y1;
            if ("ARRIBA".equals(direccion)) { y1 = h * 0.76f; y2 = h * 0.20f; }
            else if ("ABAJO".equals(direccion)) { y1 = h * 0.24f; y2 = h * 0.80f; }
            else if ("IZQUIERDA".equals(direccion)) x2 = Math.max(50f, x1 - (w * 0.55f));
            else if ("DERECHA".equals(direccion)) x2 = Math.min(w - 50f, x1 + (w * 0.55f));
            animarDedoVisual(x1, y1, x2, y2, 380, false);
            Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, 380));
        }
        dispatchGesture(b.build(), null, null);
    }


    public boolean tocarElementoOIcono(String objetivo) {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return false;
        String buscado = objetivo.toLowerCase(Locale.ROOT).trim();

        String[] sinonimos = new String[]{buscado};
        if (buscado.contains("buscar") || buscado.contains("lupa")) {
            sinonimos = new String[]{buscado, "buscar", "search", "lupa", "explorar", "ir"};
        } else if (buscado.contains("enviar") || buscado.contains("mandar")) {
            sinonimos = new String[]{buscado, "enviar", "send", "mandar"};
        } else if (buscado.contains("borrar") || buscado.contains("eliminar")) {
            sinonimos = new String[]{buscado, "eliminar", "delete", "borrar", "papelera"};
        } else if (buscado.contains("opciones") || buscado.contains("puntitos") || buscado.contains("menu")) {
            sinonimos = new String[]{buscado, "más opciones", "more options", "menú", "menu"};
        }

        for (String palabra : sinonimos) {
            AccessibilityNodeInfo nodo = buscarNodoInteligente(raiz, palabra);
            if (nodo != null) {
                Rect r = new Rect();
                nodo.getBoundsInScreen(r);
                if (!r.isEmpty()) return tocarRapido(r.centerX(), r.centerY());
                return nodo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    public boolean mantenerElemento(String objetivo) {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return false;
        AccessibilityNodeInfo nodo = buscarNodoInteligente(raiz, objetivo.toLowerCase(Locale.ROOT).trim());
        if (nodo != null) {
            Rect r = new Rect();
            nodo.getBoundsInScreen(r);
            if (!r.isEmpty()) return mantenerPresionado(r.centerX(), r.centerY());
            return nodo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        }
        return false;
    }

    private AccessibilityNodeInfo buscarNodoInteligente(AccessibilityNodeInfo nodo, String buscado) {
        if (nodo == null) return null;
        if (nodo.getText() != null && nodo.getText().toString().toLowerCase(Locale.ROOT).contains(buscado)) return nodo;
        if (nodo.getContentDescription() != null && nodo.getContentDescription().toString().toLowerCase(Locale.ROOT).contains(buscado)) return nodo;
        if (nodo.getViewIdResourceName() != null && nodo.getViewIdResourceName().toLowerCase(Locale.ROOT).contains(buscado)) return nodo;

        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo hallado = buscarNodoInteligente(nodo.getChild(i), buscado);
            if (hallado != null) return hallado;
        }
        return null;
    }

    public boolean escribirTextoEnCampo(String texto) {
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) return false;
        AccessibilityNodeInfo foco = raiz.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (foco == null) foco = buscarCampoEditable(raiz);

        if (foco != null) {
            Rect r = new Rect();
            foco.getBoundsInScreen(r);
            if (!r.isEmpty()) tocarRapido(r.centerX(), r.centerY());
            final AccessibilityNodeInfo campoFinal = foco;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
                campoFinal.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }, 220);
            return true;
        }
        return false;
    }

    private AccessibilityNodeInfo buscarCampoEditable(AccessibilityNodeInfo nodo) {
        if (nodo == null) return null;
        if (nodo.isEditable()) return nodo;
        for (int i = 0; i < nodo.getChildCount(); i++) {
            AccessibilityNodeInfo enc = buscarCampoEditable(nodo.getChild(i));
            if (enc != null) return enc;
        }
        return null;
    }

    public void capturarPantallaEnVivo(CapturaCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback.alObtenerImagen(null);
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    try {
                        Bitmap hw = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if (hw != null) {
                            Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                            Bitmap escalado = Bitmap.createScaledBitmap(sw, 540, 1200, true);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            escalado.compress(Bitmap.CompressFormat.JPEG, 65, baos);
                            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                            result.getHardwareBuffer().close();
                            callback.alObtenerImagen(b64);
                            return;
                        }
                    } catch (Exception ignored) {}
                    callback.alObtenerImagen(null);
                }

                @Override
                public void onFailure(int errorCode) {
                    callback.alObtenerImagen(null);
                }
            });
        } catch (Exception e) {
            callback.alObtenerImagen(null);
        }
    }

    public void iniciarGrabacionBoton(String nombreBoton, GrabacionCallback callback) {
        if (wmPuntero == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            View capa = new View(this);
            capa.setBackgroundColor(Color.argb(45, 0, 180, 255));
            int tipo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    tipo, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            capa.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float rx = event.getRawX();
                                        float ry = event.getRawY();
                    try { wmPuntero.removeView(capa); } catch (Exception ignored) {}
                    guardarBotonJuego(paqueteActivo, nombreBoton, rx, ry);
                    animarDedoVisual(rx, ry, rx, ry, 100, true);
                    callback.alGrabarBoton(nombreBoton, rx, ry);
                    return true;
                }
                return false;
            });
            wmPuntero.addView(capa, p);
        });
    }

    public void guardarBotonJuego(String pkg, String nombre, float x, float y) {
        if (pkg == null || pkg.isEmpty()) pkg = "general";
        SharedPreferences prefs = getSharedPreferences("BotonesJuegosV2", Context.MODE_PRIVATE);
        prefs.edit().putString(pkg + "::" + nombre.toLowerCase(Locale.ROOT).trim(), x + "," + y).apply();
    }

    public boolean ejecutarBotonJuegoSiExiste(String fraseUsuario) {
        if (paqueteActivo == null || paqueteActivo.isEmpty()) return false;
        SharedPreferences prefs = getSharedPreferences("BotonesJuegosV2", Context.MODE_PRIVATE);
        String limpia = fraseUsuario.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String clave = entry.getKey();
            if (clave.startsWith(paqueteActivo + "::")) {
                String nombreBoton = clave.substring((paqueteActivo + "::").length());
                if (limpia.equals(nombreBoton) || limpia.endsWith(" " + nombreBoton) || limpia.endsWith(" " + nombreBoton + "es") || limpia.startsWith(nombreBoton + " ")) {
                    String[] xy = String.valueOf(entry.getValue()).split(",");
                    if (xy.length == 2) {
                        tocarRapido(Float.parseFloat(xy[0]), Float.parseFloat(xy[1]));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String listarBotonesDeJuegoActual() {
        if (paqueteActivo == null || paqueteActivo.isEmpty()) return "";
        SharedPreferences prefs = getSharedPreferences("BotonesJuegosV2", Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (String clave : prefs.getAll().keySet()) {
            if (clave.startsWith(paqueteActivo + "::")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(clave.substring((paqueteActivo + "::").length()));
            }
        }
        return sb.toString();
    }
}
