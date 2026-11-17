/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public final class FreeFlowStyle {

    public static final String SETTING_KEY = "mist_settings_home_style";
    public static final int STYLE_TABS = 0;
    public static final int STYLE_FREEFLOW = 1;

    public static final String MISTIFY_ACTIVITY = "com.mist.freeflow.FreeFlowMistifyActivity";

    static final Interpolator SPRING = new PathInterpolator(0.38f, 1.21f, 0.22f, 1f);
    static final Interpolator SHEET = new PathInterpolator(0.2f, 0.9f, 0.25f, 1.02f);
    static final Interpolator FADE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private FreeFlowStyle() {
    }

    public static int getStyle(Context context) {
        return Settings.System.getInt(context.getContentResolver(), SETTING_KEY, STYLE_TABS);
    }

    public static boolean isFreeFlow(Context context) {
        return getStyle(context) == STYLE_FREEFLOW;
    }

    public static Intent mistifyIntent(Context context) {
        return new Intent().setClassName(context, MISTIFY_ACTIVITY);
    }

    public static boolean canOpenMistify(Context context) {
        return context.getPackageManager().resolveActivity(mistifyIntent(context),
                PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    static boolean motion() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    static long duration(long ms) {
        return motion() ? ms : 0;
    }

    static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    static int dpi(Context context, float value) {
        return Math.round(dp(context, value));
    }

    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xff) * alpha);
        return (color & 0x00ffffff) | (a << 24);
    }
}
