// 주가 추이를 Canvas로 직접 그리는 스파크라인 뷰
package com.stockwidget.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

public class SparklineView extends View {

    private double[] data;
    private int lineColor;
    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SparklineView(Context context) {
        super(context);
        innerPaint.setColor(0xFFFFFFFF);
        innerPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(double[] data, int lineColor) {
        this.data = data;
        this.lineColor = lineColor;

        linePaint.setColor(lineColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3.5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setColor(lineColor);
        dotPaint.setStyle(Paint.Style.FILL);

        // 선 아래 그라디언트 (반투명)
        int colorAlpha60 = (lineColor & 0x00FFFFFF) | 0x3C000000; // 24% 투명도
        int colorAlpha00 = (lineColor & 0x00FFFFFF);               // 0% 투명도
        fillPaint.setStyle(Paint.Style.FILL);
        // Shader는 onDraw에서 높이를 알고 설정

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.length < 2) return;

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // 역순 정렬 (API가 최신→오래된 순으로 반환)
        double[] pts = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            pts[i] = data[data.length - 1 - i];
        }

        double min = pts[0], max = pts[0];
        for (double v : pts) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double range = max - min;
        if (range == 0) range = 1;

        float padTop = h * 0.10f;
        float padBot = h * 0.10f;
        float drawH  = h - padTop - padBot;

        float stepX = (float) w / (pts.length - 1);

        // x, y 좌표 계산
        float[] xs = new float[pts.length];
        float[] ys = new float[pts.length];
        for (int i = 0; i < pts.length; i++) {
            xs[i] = i * stepX;
            ys[i] = padTop + (float) ((max - pts[i]) / range) * drawH;
        }

        // 그라디언트 채우기
        int colorAlpha = (lineColor & 0x00FFFFFF) | 0x3C000000;
        int colorZero  = lineColor & 0x00FFFFFF;
        LinearGradient gradient = new LinearGradient(
                0, padTop, 0, h, colorAlpha, colorZero, Shader.TileMode.CLAMP);
        fillPaint.setShader(gradient);

        Path fillPath = new Path();
        fillPath.moveTo(xs[0], h);
        fillPath.lineTo(xs[0], ys[0]);
        for (int i = 1; i < pts.length; i++) {
            float cx = (xs[i - 1] + xs[i]) / 2f;
            fillPath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
        }
        fillPath.lineTo(xs[pts.length - 1], h);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        // 선
        Path linePath = new Path();
        linePath.moveTo(xs[0], ys[0]);
        for (int i = 1; i < pts.length; i++) {
            float cx = (xs[i - 1] + xs[i]) / 2f;
            linePath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
        }
        canvas.drawPath(linePath, linePaint);

        // 마지막 점 강조
        float lastX = xs[pts.length - 1];
        float lastY = ys[pts.length - 1];
        canvas.drawCircle(lastX, lastY, 4.5f, dotPaint);
        canvas.drawCircle(lastX, lastY, 2f, innerPaint);
    }
}
