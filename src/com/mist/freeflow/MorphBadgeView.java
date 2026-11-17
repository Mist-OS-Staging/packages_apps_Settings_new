/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

public class MorphBadgeView extends View {

    static final int CIRCLE = 0;
    static final int SUNNY = 1;
    static final int CLOVER = 2;
    static final int COOKIE = 3;
    static final int SOFT_CLOVER = 4;

    private static final int POINTS = 144;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private int mShape = SUNNY;
    private float mMorph = 1f;
    private float mScale = 1f;
    private float mRotation = 0f;
    private float mIconSize;
    private Drawable mIcon;

    public MorphBadgeView(Context context) {
        this(context, null);
    }

    public MorphBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStyle(Paint.Style.FILL);
        mIconSize = FreeFlowStyle.dp(context, 34);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setShape(int shape) {
        mShape = shape;
        invalidate();
    }

    void setColor(int color) {
        mPaint.setColor(color);
        invalidate();
    }

    void setIcon(Drawable icon, int tint) {
        mIcon = icon == null ? null : icon.mutate();
        if (mIcon != null) {
            mIcon.setTint(tint);
        }
        invalidate();
    }

    void setState(float morph, float scale, float rotation, float iconSizePx) {
        mMorph = morph;
        mScale = scale;
        mRotation = rotation;
        mIconSize = iconSizePx;
        invalidate();
    }

    static float radius(int shape, double angle) {
        switch (shape) {
            case SUNNY:
                return (float) (0.925 + 0.075 * Math.cos(8 * angle));
            case CLOVER:
                return (float) (0.82 - 0.18 * Math.cos(4 * angle));
            case COOKIE:
                return (float) (0.915 + 0.085 * Math.cos(9 * angle));
            case SOFT_CLOVER:
                return (float) (0.88 + 0.12 * Math.cos(4 * angle));
            default:
                return 1f;
        }
    }

    static void buildPath(Path out, int shape, float morph, float cx, float cy, float half) {
        out.rewind();
        for (int i = 0; i < POINTS; i++) {
            double a = 2 * Math.PI * i / POINTS;
            float r = half * (1f + (radius(shape, a) - 1f) * morph);
            float x = cx + (float) (r * Math.cos(a));
            float y = cy + (float) (r * Math.sin(a));
            if (i == 0) {
                out.moveTo(x, y);
            } else {
                out.lineTo(x, y);
            }
        }
        out.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f * mScale;
        canvas.save();
        canvas.rotate(mRotation, cx, cy);
        buildPath(mPath, mShape, mMorph, cx, cy, half);
        canvas.drawPath(mPath, mPaint);
        canvas.restore();
        if (mIcon != null) {
            float w = mIconSize;
            float h = mIconSize;
            int iw = mIcon.getIntrinsicWidth();
            int ih = mIcon.getIntrinsicHeight();
            if (iw > 0 && ih > 0 && iw != ih) {
                float aspect = (float) iw / ih;
                if (aspect > 1f) {
                    w = mIconSize * Math.min(aspect, 1.6f);
                    h = w / aspect;
                } else {
                    h = mIconSize * Math.min(1f / aspect, 1.6f);
                    w = h * aspect;
                }
            }
            int hw = Math.round(w / 2f);
            int hh = Math.round(h / 2f);
            mIcon.setBounds(Math.round(cx) - hw, Math.round(cy) - hh,
                    Math.round(cx) + hw, Math.round(cy) + hh);
            mIcon.draw(canvas);
        }
    }
}
