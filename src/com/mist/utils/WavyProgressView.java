/*
 * SPDX-FileCopyrightText: 2026 Project Mist OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class WavyProgressView extends View {

    private final Paint mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mWave = new Path();

    private final float mStroke;
    private final float mAmplitude;
    private final float mWavelength;
    private final float mGap;

    private int mProgress;
    private int mMax = 100;
    private float mPhase;
    private ValueAnimator mPhaseAnimator;

    public WavyProgressView(Context context) {
        this(context, null);
    }

    public WavyProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WavyProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final float d = getResources().getDisplayMetrics().density;
        mStroke = 4f * d;
        mAmplitude = 3f * d;
        mWavelength = 24f * d;
        mGap = 6f * d;

        mIndicatorPaint.setStyle(Paint.Style.STROKE);
        mIndicatorPaint.setStrokeCap(Paint.Cap.ROUND);
        mIndicatorPaint.setStrokeJoin(Paint.Join.ROUND);
        mIndicatorPaint.setStrokeWidth(mStroke);

        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        mTrackPaint.setStrokeWidth(mStroke);

        mDotPaint.setStyle(Paint.Style.FILL);

        setIndicatorColor(context.getColor(
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorPrimary));
        setTrackColor(context.getColor(
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorSurfaceContainerHighest));
    }

    public void setIndicatorColor(int color) {
        mIndicatorPaint.setColor(color);
        mDotPaint.setColor(color);
        invalidate();
    }

    public void setTrackColor(int color) {
        mTrackPaint.setColor(color);
        invalidate();
    }

    public void setMax(int max) {
        mMax = Math.max(1, max);
        invalidate();
    }

    public int getMax() {
        return mMax;
    }

    public void setProgress(int progress) {
        mProgress = Math.max(0, Math.min(mMax, progress));
        invalidate();
    }

    public int getProgress() {
        return mProgress;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int h = (int) Math.ceil(mStroke + mAmplitude * 2 + 2);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mPhaseAnimator == null) {
            mPhaseAnimator = ValueAnimator.ofFloat(0f, 1f);
            mPhaseAnimator.setDuration(2400);
            mPhaseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mPhaseAnimator.setInterpolator(new LinearInterpolator());
            mPhaseAnimator.addUpdateListener(a -> {
                mPhase = (float) a.getAnimatedValue();
                if (mProgress > 0) invalidate();
            });
        }
        mPhaseAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mPhaseAnimator != null) mPhaseAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float half = mStroke / 2f;
        final float left = getPaddingLeft() + half;
        final float right = getWidth() - getPaddingRight() - half;
        final float cy = getHeight() / 2f;
        if (right <= left) return;

        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final float span = right - left;
        final float filled = span * mProgress / (float) mMax;

        if (rtl) {
            canvas.save();
            canvas.scale(-1f, 1f, getWidth() / 2f, cy);
        }

        final float trackStart = left + (mProgress > 0 ? filled + mGap : 0);
        if (trackStart < right) {
            canvas.drawLine(trackStart, cy, right, cy, mTrackPaint);
        }
        canvas.drawCircle(right, cy, half, mDotPaint);

        if (mProgress > 0) {
            mWave.reset();
            final float end = left + filled;
            final float shift = mPhase * mWavelength;
            final float step = Math.max(1f, mWavelength / 24f);
            boolean first = true;
            for (float x = left; ; x += step) {
                final float px = Math.min(x, end);
                final double t = (px - left + shift) / mWavelength * Math.PI * 2;
                final float ramp = Math.min(1f, (px - left) / mWavelength);
                final float py = cy + (float) Math.sin(t) * mAmplitude * ramp;
                if (first) {
                    mWave.moveTo(px, py);
                    first = false;
                } else {
                    mWave.lineTo(px, py);
                }
                if (px >= end) break;
            }
            canvas.drawPath(mWave, mIndicatorPaint);
        }

        if (rtl) canvas.restore();
    }
}
