/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

final class Ui {

    private static Typeface sBase;
    private static String sBaseFamily;

    private Ui() {
    }

    static Typeface face(Context context, int weight) {
        String family = context.getString(
                com.android.internal.R.string.config_headlineFontFamily);
        if (TextUtils.isEmpty(family)) {
            family = "sans-serif";
        }
        if (sBase == null || !family.equals(sBaseFamily)) {
            sBase = Typeface.create(family, Typeface.NORMAL);
            sBaseFamily = family;
        }
        return Typeface.create(sBase, weight, false);
    }

    static TextView text(Context context, float sp, int weight, int color) {
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(face(context, weight));
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    static TextView line(Context context, float sp, int weight, int color) {
        TextView tv = text(context, sp, weight, color);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    static void width(TextView tv, int axis) {
        tv.setFontVariationSettings("'wdth' " + axis);
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static RippleDrawable ripple(int color, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(color), null, mask);
    }

    static Drawable copy(Drawable d) {
        if (d == null) {
            return null;
        }
        Drawable.ConstantState state = d.getConstantState();
        return state != null ? state.newDrawable().mutate() : d;
    }

    static Drawable glyph(Drawable d) {
        if (d instanceof LayerDrawable && ((LayerDrawable) d).getNumberOfLayers() > 1) {
            LayerDrawable layers = (LayerDrawable) d;
            return copy(layers.getDrawable(layers.getNumberOfLayers() - 1));
        }
        return copy(d);
    }

    static ImageView circleIcon(Context context, Drawable icon, int size, boolean expressive,
            int fallbackBg, int fallbackFg) {
        ImageView iv = new ImageView(context);
        iv.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = copy(icon);
        if (expressive || d == null) {
            iv.setImageDrawable(d);
        } else {
            iv.setBackground(oval(fallbackBg));
            d.setTint(fallbackFg);
            iv.setImageDrawable(d);
            int pad = Math.round(size * 0.24f);
            iv.setPadding(pad, pad, pad, pad);
        }
        return iv;
    }
}
