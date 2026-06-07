package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ArrowOverlayView extends View {

    public static class Arrow {
        public final float sx, sy, ex, ey;
        public final int color;
        public final float widthPx;
        public final String label;
        public Arrow(float sx, float sy, float ex, float ey,
                     int color, float widthPx, String label) {
            this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey;
            this.color = color; this.widthPx = widthPx; this.label = label;
        }
    }

    private final List<Arrow> arrows = new ArrayList<>();
    private Rect boardBounds;
    private int boardFrameColor = 0x6600C853;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  headPath = new Path();

    public ArrowOverlayView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeCap(Paint.Cap.ROUND);
        shadow.setColor(0x99000000);
        head.setStyle(Paint.Style.FILL);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(5f);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(30f);
        label.setFakeBoldText(true);
        labelBg.setStyle(Paint.Style.FILL);
        labelBg.setColor(0xCC000000);
    }

    public void setArrows(List<Arrow> a) {
        arrows.clear();
        if (a != null) arrows.addAll(a);
        postInvalidate();
    }

    public void setBoardBounds(Rect r) {
        boardBounds = r;
        postInvalidate();
    }

    public void setBoardFrameColor(int c) {
        boardFrameColor = c;
        postInvalidate();
    }

    public void clearArrowsOnly() {
        arrows.clear();
        postInvalidate();
    }

    public void clearAll() {
        arrows.clear();
        boardBounds = null;
        postInvalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (boardBounds != null) {
            frame.setColor(boardFrameColor);
            c.drawRect(boardBounds, frame);
        }
        for (Arrow a : arrows) {
            shadow.setStrokeWidth(a.widthPx + 6f);
            stroke.setStrokeWidth(a.widthPx);
            stroke.setColor(a.color);
            head.setColor(a.color);

            c.drawLine(a.sx, a.sy, a.ex, a.ey, shadow);
            c.drawLine(a.sx, a.sy, a.ex, a.ey, stroke);

            double ang = Math.atan2(a.ey - a.sy, a.ex - a.sx);
            float headLen = a.widthPx * 2.6f;
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

            if (a.label != null) {
                float tx = (a.sx + a.ex) / 2f;
                float ty = (a.sy + a.ey) / 2f;
                float w = label.measureText(a.label) + 18f;
                c.drawRoundRect(tx - w / 2, ty - 24, tx + w / 2, ty + 14, 10f, 10f, labelBg);
                c.drawText(a.label, tx - (w - 18f) / 2f, ty + 8f, label);
            }
        }
    }
}
