spackage com.manoojo.v2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CerebroAI {
    private static final String URL_GEMINI =
            "https://generativelanguage" + ".googleapis" + ".com/v1beta/models/"
            + "gemini-3-flash-preview:generateContent";


    private static final List<String> historial = new ArrayList<>();

    public interface CerebroCallback {
        void alDecidir(List<String[]> accionesEncadenadas, String respuestaHablada);
    }

    public static void procesar(Context context, String ordenUsuario, String mapaPantalla, String imagenBase64, CerebroCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences("ManoOjoV2Prefs", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "").replaceAll("\\s+", "");

        if (apiKey.isEmpty()) {
            responderSoloVoz(callback, "Por favor, pega tu clave de mente en la pantalla inicial para poder ayudarte.");
            return;
        }

        String ordenMin = ordenUsuario.toLowerCase();
        if (ordenMin.contains("olvida la charla") || ordenMin.contains("limpia el historial")) {
            historial.clear();
            responderSoloVoz(callback, "De acuerdo, he limpiado nuestra conversación reciente.");
            return;
        }

        String recuerdos = prefs.getString("recuerdos_especiales", "");

        new Thread(() -> {
            try {
                StringBuilder memoriaTexto = new StringBuilder();
                for (String turno : historial) memoriaTexto.append(turno).append("\n");

                String prompt = "Eres Mano y Ojo AI v2, compañero inteligente, culto, respetuoso y muy humano en Android.\n"
                        + "PERSONALIDAD: Hablas como una persona real, educada y cercana, sin sonar robótico ni usar asteriscos.\n"
                        + "RECUERDOS GUARDADOS DEL USUARIO:\n" + (recuerdos.isEmpty() ? "Ninguno aún." : recuerdos) + "\n"
                        + "REGLAS DE ACCION (si debes actuar en el teléfono, pon las etiquetas PRIMERO, puedes encadenar varias seguidas):\n"
                        + "- Salir a inicio: [ACCION:INICIO] | Atras: [ACCION:ATRAS] | Recientes: [ACCION:RECIENTES]\n"
                        + "- Notificaciones: [ACCION:NOTIFICACIONES] | Centro control: [ACCION:CONTROL]\n"
                        + "- Deslizar: [ACCION:SIGUIENTE] (subir/bajar feed) | [ACCION:ANTERIOR] | [ACCION:IZQUIERDA] | [ACCION:DERECHA]\n"
                        + "- Zoom: [ACCION:ACERCAR] | [ACCION:ALEJAR] | Multi-dedos (1-10): [ACCION:DEDOS:cantidad:ARRIBA/ABAJO/IZQUIERDA/DERECHA]\n"
                        + "- Abrir app exacta: [ACCION:ABRIR:nombre_exacto]\n"
                        + "- Tocar boton/icono (lupa, enviar, etc.): [ACCION:TOCAR:nombre_o_icono] | Tocar coordenada: [ACCION:COORD:x,y]\n"
                        + "- Mantener presionado: [ACCION:MANTENER:texto_o_elemento]\n"
                        + "- Escribir texto: [ACCION:ESCRIBIR:texto]\n"
                        + "- Linterna: [ACCION:LINTERNA:ON/OFF] | Leer pantalla: [ACCION:LEER]\n"
                        + "- Guardar recuerdo importante (cumpleaños, fechas, datos): [GUARDAR_RECUERDO:dato_resumido]\n"
                        + "CRITERIO INTELIGENTE:\n"
                        + "1. Si pide algo abierto como 'quiero ver videos', sugiere apps disponibles y pregunta cuál prefiere.\n"
                        + "2. En WhatsApp, usa el mapa de coordenadas para tocar la caja Mensaje, escribir y tocar Enviar, o mantener un mensaje y tocar Eliminar.\n"
                        + "3. Si menciona una fecha especial o dato importante, pregunta con amabilidad si desea que lo guardes y se lo recuerdes. Si confirma, usa [GUARDAR_RECUERDO:...].\n"
                        + "4. Después de las etiquetas, escribe una respuesta breve, natural y respetuosa en español.\n"
                        + "HISTORIAL RECIENTE:\n" + memoriaTexto
                        + "PANTALLA ACTIVA EN VIVO:\n" + mapaPantalla + "\n"
                        + "USUARIO DICE: " + ordenUsuario;

                JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
                if (imagenBase64 != null && !imagenBase64.isEmpty()) {
                    JSONObject inlineData = new JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", imagenBase64);
                    parts.put(new JSONObject().put("inline_data", inlineData));
                }

                JSONObject cont = new JSONObject().put("parts", parts);
                JSONObject genConfig = new JSONObject().put("maxOutputTokens", 140);
                JSONObject cuerpo = new JSONObject()
                        .put("contents", new JSONArray().put(cont))
                        .put("generationConfig", genConfig);

                HttpURLConnection con = (HttpURLConnection) new URL(URL_GEMINI).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestProperty("x-goog-api-key", apiKey);
                con.setDoOutput(true);
                con.setConnectTimeout(18000);
                con.setReadTimeout(18000);

                try (OutputStream os = con.getOutputStream()) {
                    os.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
                }

                                int code = con.getResponseCode();
                if (code == 503 || code == 429) {
                    Thread.sleep(1200);
                    con = (HttpURLConnection) new URL(URL_GEMINI).openConnection();
                    con.setRequestMethod("POST");
                    con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    con.setRequestProperty("x-goog-api-key", apiKey);
                    con.setDoOutput(true);
                    con.setConnectTimeout(18000);
                    con.setReadTimeout(18000);
                    try (OutputStream os2 = con.getOutputStream()) {
                        os2.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    code = con.getResponseCode();
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? con.getInputStream() : con.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code == 200) {
                    String txt = new JSONObject(sb.toString()).getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text").replace("*", "").trim();
                    guardarEnMemoria(ordenUsuario, txt);
                    interpretarRespuesta(context, txt, callback);
                } else if (code == 503) {
                    responderSoloVoz(callback, "El servidor de inteligencia está un poco ocupado ahora mismo, por favor repíteme la orden en unos segundos.");
                } else {
                    responderSoloVoz(callback, "Disculpa, tuve un inconveniente con el servidor código " + code);
                }

            } catch (Exception e) {
                responderSoloVoz(callback, "Disculpa, parece que la señal de internet tardó demasiado en responder.");
            }
        }).start();
    }
    private static synchronized void guardarEnMemoria(String usuario, String asistente) {
        historial.add("Usuario: " + usuario);
        historial.add("Asistente: " + asistente);
        while (historial.size() > 12) historial.remove(0);
    }

    private static void responderSoloVoz(CerebroCallback callback, String mensaje) {
        new Handler(Looper.getMainLooper()).post(() -> callback.alDecidir(new ArrayList<>(), mensaje));
    }

    private static void interpretarRespuesta(Context context, String texto, CerebroCallback callback) {
        List<String[]> acciones = new ArrayList<>();
        StringBuilder hablada = new StringBuilder();
        int idx = 0;

        while (idx < texto.length()) {
            int iniAcc = texto.indexOf("[ACCION:", idx);
            int iniRec = texto.indexOf("[GUARDAR_RECUERDO:", idx);

            int proximo = -1;
            boolean esRecuerdo = false;
            if (iniAcc != -1 && (iniRec == -1 || iniAcc < iniRec)) {
                proximo = iniAcc;
            } else if (iniRec != -1) {
                proximo = iniRec;
                esRecuerdo = true;
            }

            if (proximo == -1) {
                hablada.append(texto.substring(idx));
                break;
            }

            hablada.append(texto.substring(idx, proximo)).append(" ");
            int fin = texto.indexOf("]", proximo);
            if (fin == -1) break;

            if (esRecuerdo) {
                String dato = texto.substring(proximo + 18, fin).trim();
                guardarRecuerdoPermanente(context, dato);
            } else {
                String contenido = texto.substring(proximo + 8, fin);
                String[] partes = contenido.split(":", 2);
                String tipo = partes[0].trim().toUpperCase();
                String param = partes.length > 1 ? partes[1].trim() : "";
                acciones.add(new String[]{tipo, param});
            }
            idx = fin + 1;
        }

        String vozFinal = hablada.toString().replaceAll("\\s+", " ").trim();
        new Handler(Looper.getMainLooper()).post(() -> callback.alDecidir(acciones, vozFinal));
    }

    private static void guardarRecuerdoPermanente(Context context, String nuevoDato) {
        if (nuevoDato.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("ManoOjoV2Prefs", Context.MODE_PRIVATE);
        String actuales = prefs.getString("recuerdos_especiales", "");
        String actualizado = actuales.isEmpty() ? "- " + nuevoDato : actuales + "\n- " + nuevoDato;
        prefs.edit().putString("recuerdos_especiales", actualizado).apply();
    }
        public static void guardarRecuerdoDirecto(Context context, String dato) {
        try {
            if (dato == null || dato.trim().isEmpty()) return;
            SharedPreferences prefs = context.getSharedPreferences("MemoriaPermanenteV2", Context.MODE_PRIVATE);
            String actuales = prefs.getString("recuerdos_especiales", "");
            String fecha = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
            String entrada = "- [" + fecha + "] " + dato.trim();
            String actualizado = actuales.isEmpty() ? entrada : actuales + "\n" + entrada;
            if (actualizado.length() > 4000) actualizado = actualizado.substring(actualizado.length() - 4000);
            prefs.edit().putString("recuerdos_especiales", actualizado).apply();
        } catch (Exception ignored) {}
    }

}
