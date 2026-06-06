package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ArrowOverlayView extends View {

    public static class Arrow {
        public final float sx, sy, ex, ey;
        public final int color;
        public final float widthPx;
        public Arrow(float sx, float sy, float ex, float ey, int color, float widthPx) {
            this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey;
            this.color = color; this.widthPx = widthPx;
        }
    }

    private final List<Arrow> arrows = new ArrayList<>();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  headPath = new Path();

    public ArrowOverlayView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeCap(Paint.Cap.ROUND);
        shadow.setColor(0x80000000);
        head.setStyle(Paint.Style.FILL);
    }

    public void setArrows(List<Arrow> a) {
        arrows.clear();
        if (a != null) arrows.addAll(a);
        postInvalidate();
    }

    @Override protected void onDraw(Canvas c) {
        for (Arrow a : arrows) {
            shadow.setStrokeWidth(a.widthPx + 6f);
            stroke.setStrokeWidth(a.widthPx);
            stroke.setColor(a.color);
            head.setColor(a.color);

            c.drawLine(a.sx, a.sy, a.ex, a.ey, shadow);
            c.drawLine(a.sx, a.sy, a.ex, a.ey, stroke);

            double ang = Math.atan2(a.ey - a.sy, a.ex - a.sx);
            float headLen = a.widthPx * 2.5f;
            float wing = (float) (Math.PI / 7);
            float lx = a.ex - (float) (Math.cos(ang - wing) * headLen);
            float ly = a.ey - (float) (Math.sin(ang - wing) * headLen);
            float rx = a.ex - (float) (Math.cos(ang + wing) * headLen);
            float ry = a.ey - (float) (Math.sin(ang + wing) * headLen);
            headPath.reset();
            headPath.moveTo(a.ex, a.ey);
            headPath.lineTo(lx, ly);
            headPath.lineTo(rx, ry);
            headPath.close();
            c.drawPath(headPath, head);
        }
    }
}
