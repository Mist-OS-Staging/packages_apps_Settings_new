/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;

final class Palette {

    final int primary;
    final int onPrimary;
    final int primaryContainer;
    final int onPrimaryContainer;
    final int tertiary;
    final int onTertiary;
    final int tertiaryContainer;
    final int onTertiaryContainer;
    final int surfaceBright;
    final int surfaceContainerLow;
    final int surfaceContainer;
    final int surfaceContainerHigh;
    final int surfaceContainerHighest;
    final int onSurface;
    final int onSurfaceVariant;
    final int outline;
    final int outlineVariant;
    final int background;

    final int blue;
    final int pink;
    final int orange;
    final int amber;
    final int red;
    final int green;
    final int purple;

    Palette(Context context) {
        primary = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorPrimary);
        onPrimary = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOnPrimary);
        primaryContainer = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorPrimaryContainer);
        onPrimaryContainer = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOnPrimaryContainer);
        tertiary = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorTertiary);
        onTertiary = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOnTertiary);
        tertiaryContainer = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorTertiaryContainer);
        onTertiaryContainer = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOnTertiaryContainer);
        surfaceBright = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorSurfaceBright);
        surfaceContainerLow = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorSurfaceContainerLow);
        surfaceContainer = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorSurfaceContainer);
        surfaceContainerHigh = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorSurfaceContainerHigh);
        surfaceContainerHighest = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorSurfaceContainerHighest);
        onSurface = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOnSurface);
        onSurfaceVariant = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOnSurfaceVariant);
        outline = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOutline);
        outlineVariant = c(context, com.android.settingslib.widget.theme.R.color
                .settingslib_materialColorOutlineVariant);
        android.content.res.TypedArray a = context.obtainStyledAttributes(
                new int[] {android.R.attr.colorBackground});
        background = a.getColor(0, surfaceContainerLow);
        a.recycle();

        blue = c(context, com.android.settings.R.color.homepage_blue_bg);
        pink = c(context, com.android.settings.R.color.homepage_pink_bg);
        orange = c(context, com.android.settings.R.color.homepage_orange_bg);
        amber = c(context, com.android.settings.R.color.homepage_yellow_bg);
        red = c(context, com.android.settings.R.color.homepage_red_bg);
        green = c(context, com.android.settings.R.color.homepage_green_bg);
        purple = c(context, com.android.settings.R.color.homepage_purple_bg);
    }

    private static int c(Context context, int id) {
        return context.getColor(id);
    }
}
