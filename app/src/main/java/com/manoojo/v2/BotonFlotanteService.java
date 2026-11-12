package com.manoojo.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BotonFlotanteService extends Service {
    public static BotonFlotanteService instancia;
    public WindowManager.LayoutParams paramsOjo;

    private WindowManager windowManager;
    private OjoFlotanteView ojoView;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private TextToSpeech voz;
    private AudioManager audioManager;
    private final Handler manejador = new Handler(Looper.getMainLooper());
    private boolean activo = true;
    private boolean escuchando = false;
    private boolean modoMusica = false;
    private String esperandoGrabacionBoton = null;
    private String ultimoJuegoAnunciado = "";
    private BroadcastReceiver receptorPantalla;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instancia = this;

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        iniciarNotificacionActiva();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ojoView = new OjoFlotanteView(this);

        int tipoVentana = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                130, 130, tipoVentana,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 260;
                paramsOjo = params;

        ojoView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long tiempoToque;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        tiempoToque = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - tiempoToque < 250) {
                            modoMusica = false;
                            if (voz != null && voz.isSpeaking()) voz.stop();
                            programarEscucha(80);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(ojoView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(ojoView, params);
        configurarVozCulta();
        configurarOidoRapido();
        registrarEventosSistema();
        programarEscucha(1200);
    }

    private void registrarEventosSistema() {
        receptorPantalla = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    modoMusica = false;
                    hablarCulto("Bienvenido de nuevo. ¿En qué puedo ayudarle hoy?");
                }
            }
        };
        IntentFilter filtro = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receptorPantalla, filtro, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receptorPantalla, filtro);
        }

        LectorNotificacionesService.listenerEnVivo = (app, remitente, texto) -> {
            if (!modoMusica && (voz == null || !voz.isSpeaking())) {
                hablarCulto("Tiene un mensaje nuevo en " + app + " de " + remitente + ". ¿Desea que lo lea?");
            }
        };
    }

    private void iniciarNotificacionActiva() {
        String canalId = "mano_ojo_v2_canal";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    canalId, "Mano y Ojo AI v2", NotificationManager.IMPORTANCE_LOW);
            canal.setSound(null, null);
            canal.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(canal);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, canalId)
                : new Notification.Builder(this);
        Notification notif = builder.setContentTitle("Mano y Ojo AI v2 activo")
                .setContentText("Visión y voz en tiempo real")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, notif);
        }
    }

    private void configurarVozCulta() {
        voz = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale esp = new Locale("es", "ES");
                voz.setLanguage(esp);
                try {
                    for (Voice v : voz.getVoices()) {
                        if (v.getLocale().getLanguage().equals("es") && v.getQuality() >= Voice.QUALITY_HIGH) {
                            voz.setVoice(v);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                voz.setPitch(0.96f);
                voz.setSpeechRate(0.94f);
                voz.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) { programarEscucha(500); }
                    @Override public void onError(String utteranceId) { programarEscucha(500); }
                });
                hablarCulto("Bienvenido. Estoy listo, ¿en qué puedo asistirle?");
            }
        });
    }

    private void hablarCulto(String texto) {
        if (voz != null && texto != null && !texto.isEmpty()) {
            detenerEscuchaActual();
            voz.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "voz_v2");
        }
    }
    private void configurarOidoRapido() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 340L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 300L);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                escuchando = true;
                ojoView.setOjoAbierto(true);
                verificarRecordatorioJuego();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                escuchando = false;
                ojoView.setOjoAbierto(false);
            }

            @Override
            public void onError(int error) {
                escuchando = false;
                ojoView.setOjoAbierto(false);
                programarEscucha(900);
            }

            @Override
            public void onResults(Bundle results) {
                escuchando = false;
                ojoView.setOjoAbierto(false);
                ArrayList<String> palabras = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (palabras != null && !palabras.isEmpty()) {
                    for (String cand : palabras) {
                        if (procesarOrdenUsuario(cand.toLowerCase(Locale.ROOT))) return;
                    }
                }
                programarEscucha(600);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> parciales = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (parciales == null || parciales.isEmpty()) return;
                String txt = parciales.get(0).toLowerCase(Locale.ROOT).trim();

                if (txt.contains("ok perfecto") || txt.contains("perfecto")) {
                    if (voz != null) voz.stop();
                    return;
                }

                ControlAccesibilidadService srv = ControlAccesibilidadService.instancia;
                if (srv != null && srv.ejecutarBotonJuegoSiExiste(txt)) {
                    detenerEscuchaActual();
                    programarEscucha(450);
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void verificarRecordatorioJuego() {
        ControlAccesibilidadService srv = ControlAccesibilidadService.instancia;
        if (srv == null) return;
        String pkg = ControlAccesibilidadService.paqueteActivo;
        if (pkg != null && !pkg.isEmpty() && !pkg.equals(ultimoJuegoAnunciado)) {
            ultimoJuegoAnunciado = pkg;
            String botones = srv.listarBotonesDeJuegoActual();
            if (!botones.isEmpty()) {
                hablarCulto("Tiene guardados los botones: " + botones + ". Solo dígalos cuando quiera que los presione.");
            }
        }
    }

    private void detenerEscuchaActual() {
        manejador.removeCallbacksAndMessages(null);
        if (speechRecognizer != null && escuchando) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
            escuchando = false;
            ojoView.setOjoAbierto(false);
        }
    }

    private void programarEscucha(long retrasoMs) {
        if (!activo || modoMusica) return;
        manejador.removeCallbacksAndMessages(null);
        manejador.postDelayed(() -> {
            if (activo && !modoMusica && speechRecognizer != null && !escuchando) {
                try { speechRecognizer.startListening(speechIntent); }
                catch (Exception ignored) { programarEscucha(1200); }
            }
        }, retrasoMs);
    }

    private boolean abrirAplicacionExacta(String nombreBuscado) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String limpio = nombreBuscado.toLowerCase(Locale.ROOT).trim();
        ApplicationInfo coincidenciaParcial = null;

        for (ApplicationInfo app : apps) {
            String etiqueta = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT).trim();
            if (etiqueta.equals(limpio)) {
                Intent lanzar = pm.getLaunchIntentForPackage(app.packageName);
                if (lanzar != null) {
                    lanzar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(lanzar);
                    return true;
                }
            }
            if (coincidenciaParcial == null && etiqueta.contains(limpio)) {
                coincidenciaParcial = app;
            }
        }

        if (coincidenciaParcial != null) {
            Intent lanzar = pm.getLaunchIntentForPackage(coincidenciaParcial.packageName);
            if (lanzar != null) {
                lanzar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(lanzar);
                return true;
            }
        }
        return false;
    }

    private boolean procesarOrdenUsuario(String orden) {
        ControlAccesibilidadService srv = ControlAccesibilidadService.instancia;
        if (srv == null) return false;

        if (orden.contains("ok perfecto") || orden.contains("perfecto")) {
            if (voz != null) voz.stop();
            programarEscucha(500);
            return true;
        }

        if (orden.length() < 2) return false;

        if (srv.ejecutarBotonJuegoSiExiste(orden)) {
            programarEscucha(400);
            return true;
        }

        if (orden.contains("ayudame a guardar") || orden.contains("ayúdame a guardar") || orden.contains("como grabo")) {
            hablarCulto("Con gusto. Diga: graba botón, seguido del nombre que quiera darle, por ejemplo: graba botón pared. Luego toque ese lugar en la pantalla.");
            return true;
        }

        if (orden.startsWith("graba boton ") || orden.startsWith("graba botón ") || orden.startsWith("aprende boton ") || orden.startsWith("aprende botón ")) {
            String nombre = orden.replaceFirst("^(graba|aprende) bot[oó]n\\s+", "").trim();
            if (!nombre.isEmpty()) {
                srv.iniciarGrabacionBoton(nombre, (btn, x, y) ->
                        hablarCulto("Perfecto, ya quedó guardado el botón " + btn + ". Cada vez que quiera que lo presione aquí, solo diga " + btn + "."));
                hablarCulto("Listo, toque ahora mismo en la pantalla donde está el botón " + nombre + ".");
                return true;
            }
        }

        if (orden.contains("que botones tengo") || orden.contains("qué botones tengo") || orden.contains("que botones sabes")) {
            String lista = srv.listarBotonesDeJuegoActual();
            hablarCulto(lista.isEmpty() ? "Aún no tiene botones guardados en esta aplicación. Si desea crear uno, diga: graba botón, y el nombre."
                    : "En esta aplicación tiene guardados los botones: " + lista);
            return true;
        }

        if (orden.contains("bateria") || orden.contains("batería")) {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int nivel = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
            hablarCulto(nivel >= 0 ? "Actualmente tiene un " + nivel + " por ciento de batería." : "No pude verificar el nivel de batería.");
            return true;
        }

        if (orden.contains("que hora es") || orden.contains("qué hora es")) {
            String hora = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            hablarCulto("Son las " + hora + ".");
            return true;
        }

        if (orden.contains("lee el mensaje") || orden.contains("lee la notificacion") || orden.contains("lee la notificación")) {
            hablarCulto(LectorNotificacionesService.ultimaNotificacionResumen);
            return true;
        }

        if (orden.contains("reproduce") || orden.contains("pon musica") || orden.contains("pon música")) {
            modoMusica = true;
        }
               if (orden.contains("desliza hacia arriba") || orden.contains("desliza arriba") || orden.equals("sube") || orden.equals("siguiente")) {
            srv.deslizarArriba();
            programarEscucha(400);
            return true;
        }
        if (orden.contains("desliza hacia abajo") || orden.contains("desliza abajo") || orden.equals("baja") || orden.equals("anterior")) {
            srv.deslizarAbajo();
            programarEscucha(400);
            return true;
        }
        if (orden.contains("desliza hacia la izquierda") || orden.contains("desliza izquierda") || orden.contains("a la izquierda")) {
            srv.deslizarIzquierda();
            programarEscucha(400);
            return true;
        }
        if (orden.contains("desliza hacia la derecha") || orden.contains("desliza derecha") || orden.contains("a la derecha")) {
            srv.deslizarDerecha();
            programarEscucha(400);
            return true;
        }
        if (orden.startsWith("presiona ") || orden.startsWith("toca ") || orden.startsWith("pulsa ")) {
            String objetivo = orden.replaceFirst("^(presiona|toca|pulsa)\\s+", "").trim();
            if (!objetivo.isEmpty() && srv.tocarElementoOIcono(objetivo)) {
                programarEscucha(400);
                return true;
            }
        }
        if (orden.startsWith("mantén presionado ") || orden.startsWith("manten presionado ")) {
            String objetivo = orden.replaceFirst("^mant[eé]n presionado\\s+", "").trim();
            if (!objetivo.isEmpty() && srv.mantenerElemento(objetivo)) {
                programarEscucha(400);
                return true;
            }
        }
        if (orden.contains("partanse en cuatro") || orden.contains("pártanse en cuatro")
                || orden.contains("partete en cuatro") || orden.contains("pártete en cuatro")
                || orden.contains("dividete en cuatro") || orden.contains("divídete en cuatro")) {
            partirOjitoEnCuatro();
            programarEscucha(400);
            return true;
        }

        if (orden.equals("presiona aquí") || orden.equals("presiona aqui") || orden.equals("toca aquí") || orden.equals("toca aqui")) {
            if (paramsOjo != null) {
                srv.tocarRapido(paramsOjo.x + 65f, paramsOjo.y + 65f);
                programarEscucha(400);
                return true;
            }
        }

        if (orden.startsWith("memorizate ") || orden.startsWith("memorízate ") || orden.startsWith("recuerda esto") || orden.startsWith("guarda esto")) {
            String detalle = orden.replaceFirst("^(memor[ií]zate|recuerda|guarda)\\s*", "").trim();

            if (detalle.contains("gesto")) {
                String nombreGesto = detalle.replace("este gesto", "").replace("el gesto", "").replace("gesto", "").trim();
                if (nombreGesto.isEmpty()) nombreGesto = "gesto personalizado";
                final String nombreFinal = nombreGesto;
                hablarCulto("Con gusto. Realiza ahora mismo el gesto en la pantalla para memorizarlo.");
                srv.grabarGestoCompletoUsuario(nombreFinal, () -> {
                    hablarCulto("Listo, ya memoricé el gesto " + nombreFinal + ".");
                });
                return true;
            }

            if (detalle.contains("imagen") || detalle.contains("pantalla") || detalle.contains("foto")) {
                hablarCulto("Observando y memorizando esta imagen.");
                srv.capturarPantallaEnVivo(b64 -> {
                    CerebroAI.procesar(this,
                            "Analiza y memoriza todos los detalles, nombres, números y contexto de esta imagen para recordármelos en el futuro. Confirma brevemente qué memorizaste.",
                            "Captura visual solicitada por el usuario", b64,
                            (acciones,vozTexto) -> hablarCulto(vozTexto));
                });
                return true;
            }

            if (detalle.contains("letra") || detalle.contains("texto") || detalle.contains("frase")) {
                String contenido = detalle.replaceFirst("^(esta letra|este texto|esta frase|la letra|el texto)\\s*:?\\s*", "").trim();
                String textoAGuardar = contenido.isEmpty() ? srv.obtenerMapaPantalla() : contenido;
                CerebroAI.guardarRecuerdoDirecto(this, "Texto/Letra memorizada: " + textoAGuardar);
                hablarCulto("Perfecto, ya dejé esa letra bien guardada en mi memoria.");
                return true;
            }

            String contextoPantalla = srv.obtenerMapaPantalla();
            String extra = detalle.replace("esto", "").replace("todo", "").trim();
            String paqueteMemo = (!extra.isEmpty() ? "Nota: " + extra + " | " : "") + "Pantalla: " + contextoPantalla;
            CerebroAI.guardarRecuerdoDirecto(this, paqueteMemo);
            hablarCulto("Listo, ya lo memoricé todo con éxito.");
            return true;
        }

        if (srv.reproducirGestoAprendido(orden)) {
            programarEscucha(400);
            return true;
        }

        String mapa = srv.obtenerMapaPantalla();
        boolean esJuego = mapa.contains("sin texto visible") || orden.contains("ves en pantalla") || orden.contains("que ves") || orden.contains("qué ves");

        if (esJuego) {
            srv.capturarPantallaEnVivo(imgBase64 -> enviarACerebro(orden, mapa, imgBase64));
        } else {
            enviarACerebro(orden, mapa, null);
        }
        return true;
    }

    private void enviarACerebro(String orden, String mapa, String imgBase64) {
        CerebroAI.procesar(this, orden, mapa, imgBase64, (acciones, respuestaHablada) -> {
            ejecutarSecuenciaAcciones(acciones, 0, () -> {
                if (respuestaHablada != null && !respuestaHablada.isEmpty()) {
                    hablarCulto(respuestaHablada);
                } else {
                    programarEscucha(500);
                }
            });
        });
    }

    private void ejecutarSecuenciaAcciones(List<String[]> acciones, int indice, Runnable alTerminar) {
        ControlAccesibilidadService srv = ControlAccesibilidadService.instancia;
        if (srv == null || indice >= acciones.size()) {
            alTerminar.run();
            return;
        }

        String[] paso = acciones.get(indice);
        String tipo = paso[0];
        String param = paso.length > 1 ? paso[1] : "";

        switch (tipo) {
            case "INICIO": srv.gestoSalirInicio(); break;
            case "ATRAS": srv.gestoAtras(); break;
            case "RECIENTES": srv.gestoRecientes(); break;
            case "NOTIFICACIONES": srv.gestoNotificaciones(); break;
            case "CONTROL": srv.gestoCentroControl(); break;
            case "SIGUIENTE": srv.deslizarArriba(); break;
            case "ANTERIOR": srv.deslizarAbajo(); break;
            case "IZQUIERDA": srv.deslizarIzquierda(); break;
            case "DERECHA": srv.deslizarDerecha(); break;
            case "ACERCAR": srv.acercarZoom(); break;
            case "ALEJAR": srv.alejarZoom(); break;
            case "DEDOS":
                String[] dp = param.split(":", 2);
                int n = 5;
                try { n = Integer.parseInt(dp[0].trim()); } catch (Exception ignored) {}
                srv.accionMultiDedos(n, dp.length > 1 ? dp[1].trim().toUpperCase() : "ARRIBA");
                break;
            case "ABRIR": abrirAplicacionExacta(param); break;
            case "TOCAR": srv.tocarElementoOIcono(param); break;
            case "COORD":
                String[] xy = param.split(",");
                if (xy.length == 2) {
                    try { srv.tocarRapido(Float.parseFloat(xy[0].trim()), Float.parseFloat(xy[1].trim())); } catch (Exception ignored) {}
                }
                break;
            case "MANTENER": srv.mantenerElemento(param); break;
            case "ESCRIBIR": srv.escribirTextoEnCampo(param); break;
            case "LINTERNA":
                try {
                    CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
                    if (cm != null && cm.getCameraIdList().length > 0) {
                        cm.setTorchMode(cm.getCameraIdList()[0], param.equalsIgnoreCase("ON"));
                    }
                } catch (Exception ignored) {}
                break;
            case "LEER":
                hablarCulto(srv.obtenerMapaPantalla());
                return;
        }

        manejador.postDelayed(() -> ejecutarSecuenciaAcciones(acciones, indice + 1, alTerminar), 450);
    }
    public static void tocarConCuerpoOjito(float x, float y, Runnable toqueReal) {
        if (instancia == null || instancia.ojoView == null || instancia.windowManager == null || instancia.paramsOjo == null) {
            if (toqueReal != null) toqueReal.run();
            return;
        }
        instancia.manejador.post(() -> {
            try {
                final int origX = instancia.paramsOjo.x;
                final int origY = instancia.paramsOjo.y;
                final int mitad = 65;

                instancia.paramsOjo.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                instancia.paramsOjo.x = (int) (x - mitad);
                instancia.paramsOjo.y = (int) (y - mitad);
                instancia.windowManager.updateViewLayout(instancia.ojoView, instancia.paramsOjo);

                instancia.ojoView.animate().scaleX(0.82f).scaleY(0.82f).setDuration(85).withEndAction(() -> {
                    if (toqueReal != null) toqueReal.run();
                    instancia.ojoView.animate().scaleX(1f).scaleY(1f).setDuration(105).start();
                }).start();

                instancia.manejador.postDelayed(() -> {
                    try {
                        instancia.paramsOjo.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        instancia.paramsOjo.x = origX;
                        instancia.paramsOjo.y = origY;
                        instancia.windowManager.updateViewLayout(instancia.ojoView, instancia.paramsOjo);
                    } catch (Exception ignored) {}
                }, 420);
            } catch (Exception e) {
                if (toqueReal != null) toqueReal.run();
            }
        });
    }

    public void partirOjitoEnCuatro() {
        if (ojoView == null || windowManager == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;

        ojoView.animate().alpha(0.25f).scaleX(0.7f).scaleY(0.7f).setDuration(220).start();
        int tipoVentana = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final List<OjoFlotanteView> fragmentos = new ArrayList<>();
        float d1x = w * 0.28f, d1y = h * 0.35f;
        float d2x = w * 0.72f, d2y = h * 0.35f;
        float d3x = w * 0.28f, d3y = h * 0.65f;
        float d4x = w * 0.72f, d4y = h * 0.65f;

        for (int i = 0; i < 4; i++) {
            float dx = (i == 0 || i == 2) ? d1x : d2x;
            float dy = (i == 0 || i == 1) ? d1y : d3y;
            OjoFlotanteView mini = new OjoFlotanteView(this);
            mini.setOjoAbierto(true);
            WindowManager.LayoutParams mp = new WindowManager.LayoutParams(
                    84, 84, tipoVentana,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            mp.gravity = Gravity.TOP | Gravity.START;
            mp.x = (int) (dx - 42);
            mp.y = (int) (dy - 42);
            windowManager.addView(mini, mp);
            mini.setScaleX(0.2f);
            mini.setScaleY(0.2f);
            mini.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
            fragmentos.add(mini);
        }

        manejador.postDelayed(() -> {
            for (OjoFlotanteView mini : fragmentos) {
                mini.animate().scaleX(0.1f).scaleY(0.1f).alpha(0f).setDuration(240).withEndAction(() -> {
                    try { windowManager.removeView(mini); } catch (Exception ignored) {}
                }).start();
            }
            ojoView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).start();
        }, 2500);
    }
    @Override
    public void onDestroy() {
        activo = false;
        detenerEscuchaActual();
        super.onDestroy();
        if (receptorPantalla != null) unregisterReceiver(receptorPantalla);
        if (ojoView != null) windowManager.removeView(ojoView);
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (voz != null) { voz.stop(); voz.shutdown(); }
    }
}
