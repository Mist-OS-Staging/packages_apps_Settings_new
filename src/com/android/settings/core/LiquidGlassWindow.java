/*
 * Copyright (C) 2026 MistOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;

import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.util.mist.glass.LiquidGlassSettings;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.slider.Slider;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Liquid glass for Settings windows (Mistify > Themes > Liquid glass):
 * <ul>
 *   <li>the wallpaper shows through a blurred, tinted (frosted) window, like iOS; preference
 *       cards keep their own surfaces so every row stays readable, only the page chrome (window,
 *       app bar, list containers) is made see-through;</li>
 *   <li>every slider and seek bar becomes an iOS slider: thin track, round white thumb.</li>
 * </ul>
 * Toggles are handled by the SettingsLib switch style itself.
 */
public final class LiquidGlassWindow {

    // Frosted window tint floor, kept high because page titles and summaries sit right on it.
    private static final float MIN_ALPHA_LIGHT = 0.62f;
    private static final float MIN_ALPHA_DARK = 0.55f;

    private static final int IOS_BLUE_LIGHT = 0xFF0088FF;
    private static final int IOS_BLUE_DARK = 0xFF0091FF;

    // Sliders already turned into glass ones; RecyclerView reuses them, so style each once.
    private static final Set<View> sStyled = Collections.newSetFromMap(new WeakHashMap<>());

    private LiquidGlassWindow() {
    }

    /** Applies Liquid glass to the activity when it is on for Settings. */
    public static void apply(Activity activity) {
        final LiquidGlassSettings.Config config = LiquidGlassSettings.read(activity);
        if (!config.isEnabledFor(LiquidGlassSettings.AREA_SETTINGS)) {
            return;
        }
        final Window window = activity.getWindow();
        final boolean dark = LiquidGlassSettings.isNightMode(activity);

        // Smooth mode, or no window blur (battery saver, blur off): the wallpaper would
        // show unblurred behind the text, so keep the normal window and only restyle controls.
        final boolean frost = !config.isLite()
                && activity.getWindowManager().isCrossWindowBlurEnabled();
        if (frost) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            final int blur = Math.round(config.blurRadiusPx(activity.getResources()));
            window.setBackgroundBlurRadius(Math.max(blur, 1));

            final float alpha = Math.max(config.tintAlpha(dark),
                    dark ? MIN_ALPHA_DARK : MIN_ALPHA_LIGHT);
            final int base = dark ? 0x1C1C1E : 0xF2F2F7;
            window.setBackgroundDrawable(
                    new ColorDrawable((Math.round(alpha * 255f) << 24) | base));
        }

        final View decor = window.getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> applyToTree(decor, config, dark, frost, false));
    }

    private static void applyToTree(View view, LiquidGlassSettings.Config config, boolean dark,
            boolean frost, boolean insideList) {
        // Runs on every layout pass: skip hidden subtrees, they get styled when they show up.
        if (view.getVisibility() == View.GONE) {
            return;
        }
        if (frost && !insideList) {
            clearChromeBackground(view);
        }
        if (view instanceof Slider) {
            styleSlider((Slider) view, config, dark);
            return;
        }
        if (view instanceof SeekBar) {
            styleSeekBar((SeekBar) view, config, dark);
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        final boolean list = insideList || view instanceof RecyclerView;
        final ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            applyToTree(group.getChildAt(i), config, dark, frost, list);
        }
    }

    /** Clears an opaque page background so the frosted window shows through. */
    private static void clearChromeBackground(View view) {
        final Drawable background = view.getBackground();
        if (background instanceof MaterialShapeDrawable) {
            final MaterialShapeDrawable shape = (MaterialShapeDrawable) background;
            if (shape.getFillColor() == null
                    || shape.getFillColor().getDefaultColor() != Color.TRANSPARENT) {
                shape.setFillColor(ColorStateList.valueOf(Color.TRANSPARENT));
            }
        } else if (background instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) background).getColor()) == 255) {
            view.setBackground(null);
        }
    }

    private static int activeColor(LiquidGlassSettings.Config config, boolean dark, int accent) {
        return config.toggleColor == 1 ? (dark ? IOS_BLUE_DARK : IOS_BLUE_LIGHT) : accent;
    }

    private static int trackColor(boolean dark) {
        return dark ? 0x5C787880 : 0x33787878;
    }

    private static int hairline(boolean dark) {
        return dark ? 0x26FFFFFF : 0x1F000000;
    }

    private static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Material slider as an iOS slider. */
    private static void styleSlider(Slider slider, LiquidGlassSettings.Config config,
            boolean dark) {
        if (!sStyled.add(slider)) return;
        final Context context = slider.getContext();
        final ColorStateList accent = slider.getTrackActiveTintList();
        final int active = activeColor(config, dark,
                accent != null ? accent.getDefaultColor() : IOS_BLUE_LIGHT);

        slider.setTrackHeight(dp(context, 6));
        slider.setTrackActiveTintList(ColorStateList.valueOf(active));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(trackColor(dark)));
        slider.setTrackInsideCornerSize(dp(context, 3));
        slider.setThumbTrackGapSize(0);
        slider.setTrackStopIndicatorSize(0);
        slider.setTickActiveTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        slider.setTickInactiveTintList(ColorStateList.valueOf(Color.TRANSPARENT));

        slider.setThumbWidth(dp(context, 28));
        slider.setThumbHeight(dp(context, 28));
        slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        slider.setThumbStrokeColor(ColorStateList.valueOf(hairline(dark)));
        slider.setThumbStrokeWidth(Math.max(1, dp(context, 0.5f)));
        slider.setThumbElevation(dp(context, 3));
        slider.setHaloRadius(0);
    }

    /** Platform seek bar as an iOS slider. */
    private static void styleSeekBar(SeekBar seekBar, LiquidGlassSettings.Config config,
            boolean dark) {
        if (!sStyled.add(seekBar)) return;
        final Context context = seekBar.getContext();
        final ColorStateList accent = seekBar.getProgressTintList();
        final int active = activeColor(config, dark,
                accent != null ? accent.getDefaultColor() : IOS_BLUE_LIGHT);
        final int height = dp(context, 6);

        final GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(height / 2f);
        track.setColor(trackColor(dark));
        final GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(height / 2f);
        fill.setColor(active);
        final LayerDrawable progress = new LayerDrawable(new Drawable[] {
                track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        progress.setId(0, android.R.id.background);
        progress.setId(1, android.R.id.progress);
        progress.setLayerHeight(0, height);
        progress.setLayerHeight(1, height);
        progress.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        progress.setLayerGravity(1, Gravity.CENTER_VERTICAL);

        final GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setStroke(Math.max(1, dp(context, 0.5f)), hairline(dark));
        thumb.setSize(dp(context, 28), dp(context, 28));

        // Drop the tints that would recolour the new drawables.
        seekBar.setProgressTintList(null);
        seekBar.setProgressBackgroundTintList(null);
        seekBar.setThumbTintList(null);
        seekBar.setProgressDrawable(progress);
        seekBar.setThumb(thumb);
        seekBar.setSplitTrack(false);
        seekBar.setMinHeight(height);
        seekBar.setMaxHeight(height);
    }
}
