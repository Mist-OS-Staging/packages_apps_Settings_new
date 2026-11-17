/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.overlay.FeatureFactory;

import java.util.ArrayList;
import java.util.List;

public class FreeFlowMistifyActivity extends FragmentActivity {

    private static final String[][] GROUPS = {
            {"monet_settings", "themes"},
            {"lock_screen", "status_bar", "quick_settings", "power_menu"},
            {"miscellaneous", "spoofing"}};

    private Palette mPalette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.freeflow_mistify);
        mPalette = new Palette(this);

        View root = findViewById(R.id.ff_mfy_root);
        View bar = findViewById(R.id.ff_mfy_bar);
        View scroll = findViewById(R.id.ff_mfy_scroll);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, 0, i.right, 0);
            bar.setPadding(bar.getPaddingLeft(), i.top, bar.getPaddingRight(), 0);
            scroll.setPadding(scroll.getPaddingLeft(), FreeFlowStyle.dpi(this, 68) + i.top,
                    scroll.getPaddingRight(), FreeFlowStyle.dpi(this, 40) + i.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        findViewById(R.id.ff_mfy_back).setOnClickListener(v -> finish());
        FeatureFactory.getFeatureFactory().getSearchFeatureProvider()
                .initSearchToolbar(this, findViewById(R.id.ff_mfy_search),
                        SettingsEnums.SETTINGS_HOMEPAGE);

        MorphBadgeView badge = findViewById(R.id.ff_mfy_badge);
        badge.setShape(MorphBadgeView.SUNNY);
        badge.setColor(mPalette.pink);
        badge.setIcon(getDrawable(R.drawable.ff_ic_brush), 0xffffffff);
        badge.setState(1f, 1f, 0f, FreeFlowStyle.dp(this, 42));

        MorphBadgeView bg = findViewById(R.id.ff_mfy_bg_shape);
        bg.setShape(MorphBadgeView.CLOVER);
        bg.setColor(FreeFlowStyle.withAlpha(mPalette.onTertiaryContainer, 0.12f));

        buildDots(findViewById(R.id.ff_mfy_dots));
        List<View> rows = buildList(findViewById(R.id.ff_mfy_list));

        if (savedInstanceState == null && FreeFlowStyle.motion()) {
            animateIn(findViewById(R.id.ff_mfy_hero), findViewById(R.id.ff_mfy_title), badge,
                    bg, rows);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.ff_hold, R.anim.ff_slide_out_end);
    }

    private void buildDots(LinearLayout dots) {
        int size = FreeFlowStyle.dpi(this, 20);
        float full = size / 2f;
        float r7 = FreeFlowStyle.dp(this, 7);
        float r5 = FreeFlowStyle.dp(this, 5);
        float[][] radii = {
                {full, full, full, full, full, full, full, full},
                {r7, r7, r7, r7, r7, r7, r7, r7},
                {full, full, full, full, full, full, r5, r5},
                {full, full, full, full, full, full, full, full}};
        int[] colors = {mPalette.purple, mPalette.pink, mPalette.orange, mPalette.green};
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadii(radii[i]);
            d.setColor(colors[i]);
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) {
                lp.setMarginStart(FreeFlowStyle.dpi(this, 6));
            }
            dots.addView(dot, lp);
        }
    }

    private int colorFor(String key) {
        switch (key) {
            case "monet_settings":
            case "themes":
                return mPalette.blue;
            case "lock_screen":
                return mPalette.orange;
            case "status_bar":
            case "quick_settings":
                return mPalette.pink;
            case "power_menu":
                return mPalette.amber;
            case "miscellaneous":
                return mPalette.red;
            case "spoofing":
                return mPalette.green;
            default:
                return mPalette.primary;
        }
    }

    private List<Preference> loadEntries() {
        List<Preference> out = new ArrayList<>();
        PreferenceManager pm = new PreferenceManager(this);
        PreferenceScreen screen = pm.inflateFromResource(this, R.xml.mist_settings, null);
        collect(screen, out);
        return out;
    }

    private static void collect(PreferenceGroup group, List<Preference> out) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                collect((PreferenceGroup) p, out);
            } else if (p.isVisible() && p.getKey() != null) {
                out.add(p);
            }
        }
    }

    private List<View> buildList(LinearLayout list) {
        List<Preference> entries = loadEntries();
        List<List<Preference>> groups = new ArrayList<>();
        for (String[] keys : GROUPS) {
            List<Preference> g = new ArrayList<>();
            for (String key : keys) {
                for (Preference p : entries) {
                    if (key.equals(p.getKey())) {
                        g.add(p);
                    }
                }
            }
            groups.add(g);
        }
        List<Preference> rest = new ArrayList<>();
        for (Preference p : entries) {
            boolean known = false;
            for (List<Preference> g : groups) {
                known |= g.contains(p);
            }
            if (!known) {
                rest.add(p);
            }
        }
        groups.get(groups.size() - 1).addAll(rest);

        List<View> rows = new ArrayList<>();
        for (List<Preference> g : groups) {
            if (g.isEmpty()) {
                continue;
            }
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < g.size(); i++) {
                View row = row(g.get(i), i, g.size());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    lp.topMargin = FreeFlowStyle.dpi(this, 3);
                }
                group.addView(row, lp);
                rows.add(row);
            }
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.topMargin = FreeFlowStyle.dpi(this, 16);
            list.addView(group, glp);
        }
        return rows;
    }

    private View row(Preference pref, int index, int count) {
        Context c = this;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(FreeFlowStyle.dpi(c, 80));
        int pad = FreeFlowStyle.dpi(c, 16);
        row.setPadding(pad, FreeFlowStyle.dpi(c, 12), pad, FreeFlowStyle.dpi(c, 12));
        row.setBackground(FreeFlowHome.groupShape(c, index, count, mPalette.surfaceBright));
        row.setForeground(Ui.ripple(FreeFlowStyle.withAlpha(mPalette.onSurface, 0.12f),
                FreeFlowHome.groupShape(c, index, count, 0xffffffff)));
        row.setClickable(true);
        row.setFocusable(true);

        int size = FreeFlowStyle.dpi(c, 44);
        FrameLayout circle = new FrameLayout(c);
        circle.setBackground(Ui.oval(colorFor(pref.getKey())));
        ImageView icon = new ImageView(c);
        Drawable d = Ui.glyph(pref.getIcon());
        if (d != null) {
            d.setTint(0xffffffff);
            icon.setImageDrawable(d);
        }
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = FreeFlowStyle.dpi(c, 22);
        circle.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        row.addView(circle, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.line(c, 18, 500, mPalette.onSurface);
        title.setText(pref.getTitle());
        texts.addView(title);
        if (!TextUtils.isEmpty(pref.getSummary())) {
            TextView sub = Ui.text(c, 14, 400, mPalette.onSurfaceVariant);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setLineSpacing(0f, 1.15f);
            sub.setText(pref.getSummary());
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = FreeFlowStyle.dpi(c, 3);
            texts.addView(sub, slp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(pad);
        row.addView(texts, tlp);

        row.setOnClickListener(v -> open(pref));
        return row;
    }

    private void open(Preference pref) {
        if (pref.getFragment() != null) {
            new SubSettingLauncher(this)
                    .setDestination(pref.getFragment())
                    .setArguments(pref.getExtras())
                    .setSourceMetricsCategory(MetricsEvent.MIST)
                    .setTitleRes(-1)
                    .launch();
        } else if (pref.getIntent() != null) {
            startActivity(pref.getIntent());
        }
    }

    private void animateIn(View hero, View title, View badge, View bg, List<View> rows) {
        hero.setScaleX(0.88f);
        hero.setScaleY(0.88f);
        hero.setTranslationY(FreeFlowStyle.dp(this, 20));
        hero.setAlpha(0f);
        hero.animate().scaleX(1f).scaleY(1f).translationY(0f).alpha(1f).setStartDelay(80)
                .setDuration(700).setInterpolator(FreeFlowStyle.SPRING).start();
        badge.setScaleX(0.4f);
        badge.setScaleY(0.4f);
        badge.setRotation(-90f);
        badge.setAlpha(0f);
        badge.animate().scaleX(1f).scaleY(1f).rotation(0f).alpha(1f).setStartDelay(200)
                .setDuration(1000).setInterpolator(FreeFlowStyle.SPRING).start();
        bg.setScaleX(0.4f);
        bg.setScaleY(0.4f);
        bg.setRotation(-90f);
        bg.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(100)
                .setDuration(1200).setInterpolator(FreeFlowStyle.SPRING).start();
        rise(title, 220);
        for (int i = 0; i < rows.size(); i++) {
            rise(rows.get(i), 250 + i * 45L);
        }
    }

    private void rise(View v, long delay) {
        v.setTranslationY(FreeFlowStyle.dp(this, 16));
        v.setAlpha(0f);
        v.animate().translationY(0f).alpha(1f).setStartDelay(delay).setDuration(500)
                .setInterpolator(FreeFlowStyle.SPRING).start();
    }
}
