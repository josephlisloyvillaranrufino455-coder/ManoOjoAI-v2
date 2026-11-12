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

    private void mostrarPunteroTransparente(float x, float y) {
        if (wmPuntero == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                View circulo = new View(this);
                GradientDrawable forma = new GradientDrawable();
                forma.setShape(GradientDrawable.OVAL);
                forma.setColor(Color.argb(95, 0, 210, 255));
                forma.setStroke(4, Color.argb(180, 255, 255, 255));
                circulo.setBackground(forma);

                int tipo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

                int tamano = 85;
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        tamano, tamano, tipo,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);

                params.gravity = Gravity.TOP | Gravity.START;
                params.x = (int) (x - tamano / 2f);
                params.y = (int) (y - tamano / 2f);

                wmPuntero.addView(circulo, params);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { wmPuntero.removeView(circulo); } catch (Exception ignored) {}
                }, 400);
            } catch (Exception ignored) {}
        });
    }
    public boolean ejecutarGesto(float x1, float y1, float x2, float y2, long duracionMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        mostrarPunteroTransparente(x1, y1);
        if (x1 != x2 || y1 != y2) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> mostrarPunteroTransparente(x2, y2), duracionMs / 2);
        }
        Path camino = new Path();
        camino.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) {
            camino.lineTo(x2, y2);
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
            mostrarPunteroTransparente(inicios[i][0], inicios[i][1]);
            Path p = new Path();
            p.moveTo(inicios[i][0], inicios[i][1]);
            if (fines != null && i < fines.length) {
                p.lineTo(fines[i][0], fines[i][1]);
            }
            builder.addStroke(new GestureDescription.StrokeDescription(p, 0, duracionMs));
        }
        return dispatchGesture(builder.build(), null, null);
    }

    public boolean tocarRapido(float x, float y) {
        return ejecutarGesto(x, y, x, y, 25);
    }

    public boolean mantenerPresionado(float x, float y) {
        return ejecutarGesto(x, y, x, y, 750);
    }

    public void gestoSalirInicio() {
        ejecutarGesto(610, 2680, 610, 1450, 180);
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void gestoRecientes() {
        ejecutarGesto(610, 2680, 610, 1350, 480);
        new Handler(Looper.getMainLooper()).postDelayed(() -> performGlobalAction(GLOBAL_ACTION_RECENTS), 500);
    }

    public void gestoAtras() {
        ejecutarGesto(20, 1400, 480, 1400, 160);
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void gestoNotificaciones() {
        ejecutarGesto(280, 20, 280, 1250, 200);
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public void gestoCentroControl() {
        ejecutarGesto(980, 20, 980, 1250, 200);
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public void deslizarArriba() { ejecutarGesto(610, 1950, 610, 650, 200); }
    public void deslizarAbajo() { ejecutarGesto(610, 650, 610, 1950, 200); }
    public void deslizarIzquierda() { ejecutarGesto(1050, 1350, 170, 1350, 200); }
    public void deslizarDerecha() { ejecutarGesto(170, 1350, 1050, 1350, 200); }

    public void acercarZoom() {
        float[][] ini = {{540, 1350}, {680, 1350}};
        float[][] fin = {{200, 1350}, {1020, 1350}};
        ejecutarMultiDedos(ini, fin, 220);
    }

    public void alejarZoom() {
        float[][] ini = {{200, 1350}, {1020, 1350}};
        float[][] fin = {{540, 1350}, {680, 1350}};
        ejecutarMultiDedos(ini, fin, 220);
    }

    public void accionMultiDedos(int numDedos, String direccion) {
        int n = Math.max(1, Math.min(numDedos, 10));
        float[][] ini = new float[n][2];
        float[][] fin = new float[n][2];
        for (int i = 0; i < n; i++) {
            float x = 150 + (i * (920f / Math.max(1, n - 1)));
            float y = 1350;
            ini[i][0] = x; ini[i][1] = y;
            fin[i][0] = x; fin[i][1] = y;
            if ("ARRIBA".equals(direccion)) fin[i][1] = 650;
            else if ("ABAJO".equals(direccion)) fin[i][1] = 1950;
            else if ("IZQUIERDA".equals(direccion)) fin[i][0] = Math.max(80, x - 450);
            else if ("DERECHA".equals(direccion)) fin[i][0] = Math.min(1140, x + 450);
        }
        ejecutarMultiDedos(ini, fin, 200);
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
                    mostrarPunteroTransparente(rx, ry);
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
                if (limpia.equals(nombreBoton) || limpia.endsWith(" " + nombreBoton) || limpia.startsWith(nombreBoton + " ")) {
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
