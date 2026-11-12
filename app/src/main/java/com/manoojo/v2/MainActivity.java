package com.manoojo.v2;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private EditText campoClave;
    private TextView estadoClave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 64, 48, 64);

        TextView titulo = new TextView(this);
        titulo.setText("Mano y Ojo AI v2.0");
        titulo.setTextSize(26);
        titulo.setTextColor(Color.WHITE);
        titulo.setGravity(Gravity.CENTER);
        layout.addView(titulo);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("Visión 100% en vivo, 10 dedos rápidos y memoria inteligente");
        subtitulo.setTextSize(14);
        subtitulo.setTextColor(Color.parseColor("#94A3B8"));
        subtitulo.setGravity(Gravity.CENTER);
        subtitulo.setPadding(0, 8, 0, 40);
        layout.addView(subtitulo);

        SharedPreferences prefs = getSharedPreferences("ManoOjoV2Prefs", MODE_PRIVATE);
        String claveGuardada = prefs.getString("api_key", "");

        estadoClave = new TextView(this);
        estadoClave.setText(claveGuardada.isEmpty() ? "Estado de mente: Falta pegar tu clave" : "Estado de mente: Clave activa y lista");
        estadoClave.setTextColor(claveGuardada.isEmpty() ? Color.parseColor("#F87171") : Color.parseColor("#34D399"));
        estadoClave.setTextSize(14);
        estadoClave.setPadding(0, 0, 0, 12);
        layout.addView(estadoClave);

        campoClave = new EditText(this);
        campoClave.setHint("Pega aquí tu clave de Google AI Studio (AIza...)");
        campoClave.setText(claveGuardada);
        campoClave.setTextColor(Color.WHITE);
        campoClave.setHintTextColor(Color.parseColor("#64748B"));
        campoClave.setBackgroundColor(Color.parseColor("#1E293B"));
        campoClave.setPadding(32, 28, 32, 28);
        campoClave.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        layout.addView(campoClave);

        layout.addView(crearBoton("0. Guardar Clave de Mente", "#2563EB", v -> {
            String limpia = campoClave.getText().toString().replaceAll("\\s+", "");
            prefs.edit().putString("api_key", limpia).apply();
            estadoClave.setText(limpia.isEmpty() ? "Estado de mente: Clave vacía" : "Estado de mente: Clave guardada con éxito");
            estadoClave.setTextColor(limpia.isEmpty() ? Color.parseColor("#F87171") : Color.parseColor("#34D399"));
            Toast.makeText(this, "Clave guardada en memoria segura", Toast.LENGTH_SHORT).show();
        }));

        layout.addView(crearBoton("1. Permiso de Ojo Flotante", "#334155", v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "Permiso de ventana flotante ya está activo", Toast.LENGTH_SHORT).show();
            }
        }));

        layout.addView(crearBoton("2. Permiso de 10 Dedos y Visión", "#334155", v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }));

        layout.addView(crearBoton("3. Permiso de Leer Notificaciones", "#334155", v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }));

        layout.addView(crearBoton("4. Encender Mano y Ojo AI v2", "#10B981", v -> {
            solicitarPermisosBase();
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Activa primero el Permiso 1 (Ojo Flotante)", Toast.LENGTH_LONG).show();
                return;
            }
            Intent servicio = new Intent(this, BotonFlotanteService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(servicio);
            } else {
                startService(servicio);
            }
            Toast.makeText(this, "Asistente encendido", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        }));

        scroll.addView(layout);
        setContentView(scroll);
        solicitarPermisosBase();
    }

    private Button crearBoton(String texto, String colorHex, android.view.View.OnClickListener accion) {
        Button btn = new Button(this);
        btn.setText(texto);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(colorHex));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 24, 0, 0);
        btn.setLayoutParams(lp);
        btn.setPadding(24, 28, 24, 28);
        btn.setOnClickListener(accion);
        return btn;
    }

    private void solicitarPermisosBase() {
        java.util.List<String> faltantes = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            faltantes.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            faltantes.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            faltantes.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!faltantes.isEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toArray(new String[]), 101);
        }
    }
}
