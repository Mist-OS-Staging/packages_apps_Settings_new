/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.R;

public class HeroCarouselView extends ViewGroup {

    interface Listener {
        void onOpen(int index);

        void onPageChanged(int page);
    }

    private final Card[] mCards = new Card[2];
    private final int mTouchSlop;
    private Listener mListener;
    private int mPage;
    private float mFraction;
    private ValueAnimator mAnimator;
    private float mDownX;
    private float mDownY;
    private boolean mSwiping;

    public HeroCarouselView(Context context) {
        this(context, null);
    }

    public HeroCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        Palette p = new Palette(context);
        mCards[0] = new Card(context, 0, p.tertiaryContainer, p.onTertiaryContainer,
                MorphBadgeView.SUNNY, p.pink, R.drawable.ff_ic_brush, 0xffffffff,
                context.getString(R.string.ff_mistify));
        mCards[1] = new Card(context, 1, p.primaryContainer, p.onPrimaryContainer,
                MorphBadgeView.CLOVER, p.primary, R.drawable.ff_ic_phone, p.onPrimary,
                context.getString(R.string.ff_about_device_desc));
        buildMistify(context, mCards[0], p);
        buildAbout(context, mCards[1], p);
        addView(mCards[0]);
        addView(mCards[1]);
        applyFraction();
        mCards[1].hideText(false);
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    int getPage() {
        return mPage;
    }

    void setPage(int page, boolean animate) {
        if (page == mPage) {
            return;
        }
        mPage = page;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        final float target = page;
        if (animate && FreeFlowStyle.motion()) {
            mAnimator = ValueAnimator.ofFloat(mFraction, target);
            mAnimator.setDuration(700);
            mAnimator.setInterpolator(FreeFlowStyle.SPRING);
            mAnimator.addUpdateListener(a -> {
                mFraction = (float) a.getAnimatedValue();
                applyFraction();
            });
            mAnimator.start();
        } else {
            mFraction = target;
            applyFraction();
        }
        mCards[1 - page].hideText(animate);
        mCards[page].showText(animate);
        if (mListener != null) {
            mListener.onPageChanged(page);
        }
    }

    private void applyFraction() {
        mCards[0].setExpand(1f - FreeFlowStyle.clamp(mFraction, -0.2f, 1.2f));
        mCards[1].setExpand(FreeFlowStyle.clamp(mFraction, -0.2f, 1.2f));
        requestLayout();
    }

    private void buildMistify(Context context, Card card, Palette p) {
        card.text.addView(label(context, R.string.ff_customise, p.onTertiaryContainer));
        TextView title = Ui.line(context, 34, 800, p.onTertiaryContainer);
        Ui.width(title, 125);
        title.setText(R.string.ff_mistify);
        card.text.addView(title, wrap(0));
        LinearLayout dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        int size = FreeFlowStyle.dpi(context, 20);
        float full = size / 2f;
        float[][] radii = {
                {full, full, full, full, full, full, full, full},
                {7, 7, 7, 7, 7, 7, 7, 7},
                {full, full, full, full, full, full, 5, 5},
                {full, full, full, full, full, full, full, full}};
        int[] colors = {p.purple, p.pink, p.orange, p.green};
        card.dots = new View[4];
        for (int i = 0; i < 4; i++) {
            View dot = new View(context);
            GradientDrawable d = new GradientDrawable();
            float[] r = radii[i].clone();
            if (i == 1 || i == 2) {
                for (int k = 0; k < r.length; k++) {
                    if (r[k] < full) {
                        r[k] = FreeFlowStyle.dp(context, r[k]);
                    }
                }
            }
            d.setCornerRadii(r);
            d.setColor(colors[i]);
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) {
                lp.setMarginStart(FreeFlowStyle.dpi(context, 6));
            }
            dots.addView(dot, lp);
            card.dots[i] = dot;
        }
        card.text.addView(dots, wrap(2));
    }

    private void buildAbout(Context context, Card card, Palette p) {
        card.text.addView(label(context, R.string.ff_about_device, p.onPrimaryContainer));
        TextView model = Ui.text(context, 24, 800, p.onPrimaryContainer);
        Ui.width(model, 112);
        model.setMaxLines(2);
        model.setText(SystemProperties.get("ro.product.model", Build.MODEL));
        card.text.addView(model, wrap(0));
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        TextView rom = chip(context, context.getString(R.string.ff_rom_name), p.onPrimary);
        rom.setBackground(Ui.round(p.primary, FreeFlowStyle.dp(context, 999)));
        chips.addView(rom);
        TextView version = chip(context,
                context.getString(R.string.ff_android_version, Build.VERSION.RELEASE),
                p.onPrimaryContainer);
        GradientDrawable outline = Ui.round(0, FreeFlowStyle.dp(context, 999));
        outline.setStroke(FreeFlowStyle.dpi(context, 1.5f),
                FreeFlowStyle.withAlpha(p.onPrimaryContainer, 0.4f));
        version.setBackground(outline);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(FreeFlowStyle.dpi(context, 6));
        chips.addView(version, lp);
        card.text.addView(chips, wrap(0));
    }

    private static TextView label(Context context, int res, int color) {
        TextView tv = Ui.line(context, 11, 800, color);
        tv.setLetterSpacing(0.08f);
        tv.setText(res);
        int h = FreeFlowStyle.dpi(context, 10);
        int v = FreeFlowStyle.dpi(context, 5);
        tv.setPadding(h, v, h, v);
        tv.setBackground(Ui.round(FreeFlowStyle.withAlpha(color, 0.12f),
                FreeFlowStyle.dp(context, 10)));
        return tv;
    }

    private static TextView chip(Context context, String text, int color) {
        TextView tv = Ui.line(context, 12, 700, color);
        tv.setText(text);
        int h = FreeFlowStyle.dpi(context, 10);
        int v = FreeFlowStyle.dpi(context, 4);
        tv.setPadding(h, v, h, v);
        return tv;
    }

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = FreeFlowStyle.dpi(getContext(), 8 + topDp);
        return lp;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = FreeFlowStyle.dpi(getContext(), 172);
        int inner = width - getPaddingLeft() - getPaddingRight();
        int gap = FreeFlowStyle.dpi(getContext(), 8);
        int slim = FreeFlowStyle.dpi(getContext(), 64);
        int large = Math.max(slim, inner - gap - slim);
        int w0 = Math.round(FreeFlowStyle.lerp(large, slim, mFraction));
        w0 = Math.max(slim / 2, Math.min(inner - gap - slim / 2, w0));
        int w1 = inner - gap - w0;
        int textWidth = Math.max(FreeFlowStyle.dpi(getContext(), 150),
                large - FreeFlowStyle.dpi(getContext(), 136));
        int[] widths = {w0, w1};
        for (int i = 0; i < 2; i++) {
            mCards[i].setTextWidth(textWidth);
            mCards[i].measure(MeasureSpec.makeMeasureSpec(widths[i], MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int gap = FreeFlowStyle.dpi(getContext(), 8);
        int x = getPaddingLeft();
        int h = getMeasuredHeight();
        for (Card card : mCards) {
            int w = card.getMeasuredWidth();
            card.layout(x, 0, x + w, h);
            x += w + gap;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mSwiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                checkSwipe(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mSwiping = false;
                break;
        }
        return mSwiping;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mSwiping = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                checkSwipe(ev);
                return true;
            case MotionEvent.ACTION_UP:
                float dx = ev.getX() - mDownX;
                if (mSwiping && Math.abs(dx) > FreeFlowStyle.dp(getContext(), 36)) {
                    setPage(dx < 0 ? 1 : 0, true);
                }
                mSwiping = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mSwiping = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private void checkSwipe(MotionEvent ev) {
        float dx = ev.getX() - mDownX;
        float dy = ev.getY() - mDownY;
        if (!mSwiping && Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy)) {
            mSwiping = true;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
    }

    private final class Card extends FrameLayout {

        final LinearLayout text;
        final MorphBadgeView badge;
        final GradientDrawable bg;
        final GradientDrawable mask;
        View[] dots;

        Card(Context context, int index, int color, int onColor, int shape, int badgeColor,
                int iconRes, int iconTint, String description) {
            super(context);
            bg = Ui.round(color, FreeFlowStyle.dp(context, 44));
            mask = Ui.round(0xffffffff, FreeFlowStyle.dp(context, 44));
            setBackground(bg);
            setForeground(Ui.ripple(FreeFlowStyle.withAlpha(onColor, 0.12f), mask));
            setClipToOutline(true);
            setContentDescription(description);
            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> {
                if (mPage != index) {
                    setPage(index, true);
                }
                if (mListener != null) {
                    mListener.onOpen(index);
                }
            });

            text = new LinearLayout(context);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            LayoutParams tlp = new LayoutParams(FreeFlowStyle.dpi(context, 160),
                    LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
            tlp.setMarginStart(FreeFlowStyle.dpi(context, 24));
            addView(text, tlp);

            badge = new MorphBadgeView(context);
            badge.setShape(shape);
            badge.setColor(badgeColor);
            badge.setIcon(context.getDrawable(iconRes), iconTint);
            int size = FreeFlowStyle.dpi(context, 88);
            LayoutParams blp = new LayoutParams(size, size, Gravity.CENTER_VERTICAL | Gravity.END);
            blp.setMarginEnd(FreeFlowStyle.dpi(context, 16));
            addView(badge, blp);
        }

        void setTextWidth(int width) {
            LayoutParams lp = (LayoutParams) text.getLayoutParams();
            if (lp.width != width) {
                lp.width = width;
                text.setLayoutParams(lp);
            }
        }

        void setExpand(float e) {
            Context c = getContext();
            float radius = FreeFlowStyle.dp(c, FreeFlowStyle.lerp(32, 44, e));
            bg.setCornerRadius(radius);
            mask.setCornerRadius(radius);
            float morph = FreeFlowStyle.clamp(e, 0f, 1f);
            badge.setState(morph, FreeFlowStyle.lerp(52f / 88f, 1f, e),
                    FreeFlowStyle.lerp(-135f, 0f, e),
                    FreeFlowStyle.dp(c, FreeFlowStyle.lerp(22, 34, morph)));
            badge.setTranslationX(FreeFlowStyle.dp(c, FreeFlowStyle.lerp(28, 0, e)));
            invalidateOutline();
        }

        void hideText(boolean animate) {
            float shift = -FreeFlowStyle.dp(getContext(), 28);
            text.animate().cancel();
            if (animate && FreeFlowStyle.motion()) {
                text.animate().alpha(0f).translationX(shift).setStartDelay(0)
                        .setDuration(250).setInterpolator(FreeFlowStyle.FADE).start();
            } else {
                text.setAlpha(0f);
                text.setTranslationX(shift);
            }
            if (dots != null) {
                for (View dot : dots) {
                    dot.animate().cancel();
                    dot.setAlpha(0f);
                    dot.setScaleX(0.5f);
                    dot.setScaleY(0.5f);
                    dot.setTranslationY(FreeFlowStyle.dp(getContext(), 10));
                }
            }
        }

        void showText(boolean animate) {
            text.animate().cancel();
            boolean motion = animate && FreeFlowStyle.motion();
            if (motion) {
                text.animate().alpha(1f).translationX(0f).setStartDelay(180)
                        .setDuration(400).setInterpolator(FreeFlowStyle.FADE).start();
            } else {
                text.setAlpha(1f);
                text.setTranslationX(0f);
            }
            if (dots != null) {
                for (int i = 0; i < dots.length; i++) {
                    View dot = dots[i];
                    dot.animate().cancel();
                    if (motion) {
                        dot.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                                .setStartDelay(220 + i * 60L).setDuration(350)
                                .setInterpolator(FreeFlowStyle.SPRING).start();
                    } else {
                        dot.setAlpha(1f);
                        dot.setScaleX(1f);
                        dot.setScaleY(1f);
                        dot.setTranslationY(0f);
                    }
                }
            }
        }
    }
}
