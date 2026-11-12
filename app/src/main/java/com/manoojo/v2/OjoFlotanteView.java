package com.manoojo.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public class OjoFlotanteView extends View {
    private boolean ojoAbierto = false;
    private float apertura = 0.08f;
    private float pulso = 0f;
    private final Paint paintFondo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOjo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPupila = new Paint(Paint.ANTI_ALIAS_FLAG);

    public OjoFlotanteView(Context context) {
        super(context);
        paintFondo.setColor(Color.argb(215, 15, 23, 42));
        paintBorde.setStyle(Paint.Style.STROKE);
        paintBorde.setStrokeWidth(4f);
        paintBorde.setColor(Color.argb(140, 148, 163, 184));
        paintOjo.setStyle(Paint.Style.STROKE);
        paintOjo.setStrokeWidth(5f);
        paintOjo.setStrokeCap(Paint.Cap.ROUND);
        paintOjo.setColor(Color.argb(235, 226, 232, 240));
    }

    public void setOjoAbierto(boolean abierto) {
        this.ojoAbierto = abierto;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radio = Math.min(cx, cy) - 6f;

        float objetivo = ojoAbierto ? 1.0f : 0.06f;
        apertura += (objetivo - apertura) * 0.25f;
        pulso += 0.12f;

        canvas.drawCircle(cx, cy, radio, paintFondo);
        canvas.drawCircle(cx, cy, radio, paintBorde);

        float anchoOjo = radio * 0.62f;
        float altoParpado = radio * 0.42f * apertura;

        Path contorno = new Path();
        contorno.moveTo(cx - anchoOjo, cy);
        contorno.quadTo(cx, cy - altoParpado * 2f, cx + anchoOjo, cy);
        contorno.quadTo(cx, cy + altoParpado * 2f, cx - anchoOjo, cy);
        canvas.drawPath(contorno, paintOjo);

        if (apertura > 0.25f) {
            float rPupila = radio * (0.22f + (float) Math.sin(pulso) * 0.02f) * apertura;
            paintPupila.setShader(new RadialGradient(cx, cy, Math.max(4f, rPupila),
                    Color.argb(245, 186, 230, 253),
                    Color.argb(180, 56, 189, 248),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, rPupila, paintPupila);
        }

        if (Math.abs(objetivo - apertura) > 0.02f || ojoAbierto) {
            postInvalidateDelayed(35);
        }
    }
}
