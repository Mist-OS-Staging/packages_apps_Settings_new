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

    private static final int BADGE_DP = 124;

    private final Card[] mCards = new Card[2];
    private final int mTouchSlop;
    private Listener mListener;
    private int mPage;
    private float mFraction;
    private ValueAnimator mAnimator;
    private float mDownX;
    private float mDownY;
    private boolean mSwiping;
    private float mStartFraction;
    private android.view.VelocityTracker mVelocity;

    public HeroCarouselView(Context context) {
        this(context, null);
    }

    public HeroCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        Palette p = new Palette(context);
        mCards[0] = new Card(context, 0, p.primaryContainer, p.onPrimaryContainer,
                MorphBadgeView.COOKIE, p.primary, R.drawable.logo_single, p.onPrimary,
                context.getString(R.string.ff_mistify));
        mCards[1] = new Card(context, 1, p.tertiaryContainer, p.onTertiaryContainer,
                MorphBadgeView.SOFT_CLOVER, p.tertiary, R.drawable.ff_ic_phone, p.onTertiary,
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
        animateTo(page, animate);
        mCards[1 - page].hideText(animate);
        mCards[page].showText(animate);
        if (mListener != null) {
            mListener.onPageChanged(page);
        }
    }

    private void animateTo(float target, boolean animate) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (animate && FreeFlowStyle.motion()) {
            float distance = Math.abs(target - mFraction);
            mAnimator = ValueAnimator.ofFloat(mFraction, target);
            mAnimator.setDuration(Math.round(FreeFlowStyle.lerp(320, 700,
                    FreeFlowStyle.clamp(distance, 0f, 1f))));
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
    }

    private float travel() {
        int inner = getWidth() - getPaddingLeft() - getPaddingRight();
        int gap = FreeFlowStyle.dpi(getContext(), 8);
        int slim = FreeFlowStyle.dpi(getContext(), 64);
        return Math.max(1, inner - gap - 2 * slim);
    }

    private void drag(MotionEvent ev) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        float f = mStartFraction - (ev.getX() - mDownX) / travel();
        if (f < 0f) {
            f *= 0.3f;
        } else if (f > 1f) {
            f = 1f + (f - 1f) * 0.3f;
        }
        mFraction = f;
        applyFraction();
    }

    private void settle(float velocityX) {
        float fling = FreeFlowStyle.dp(getContext(), 600);
        int target;
        if (Math.abs(velocityX) > fling) {
            target = velocityX < 0 ? 1 : 0;
        } else {
            target = mFraction > 0.5f ? 1 : 0;
        }
        if (target != mPage) {
            setPage(target, true);
        } else {
            animateTo(target, true);
        }
    }

    private void applyFraction() {
        mCards[0].setExpand(1f - FreeFlowStyle.clamp(mFraction, -0.2f, 1.2f));
        mCards[1].setExpand(FreeFlowStyle.clamp(mFraction, -0.2f, 1.2f));
        requestLayout();
    }

    private void buildMistify(Context context, Card card, Palette p) {
        card.text.addView(label(context, context.getString(R.string.ff_hero_customise),
                p.onPrimaryContainer));
        card.text.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView title = Ui.line(context, 30, 500, p.onPrimaryContainer);
        title.setText(R.string.ff_mistify);
        card.text.addView(title);
        card.text.addView(sub(context, context.getString(R.string.ff_hero_mistify_desc),
                p.onPrimaryContainer), wrap(4));
    }

    private void buildAbout(Context context, Card card, Palette p) {
        card.text.addView(label(context, context.getString(R.string.ff_hero_about),
                p.onTertiaryContainer));
        card.text.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView model = Ui.text(context, 26, 500, p.onTertiaryContainer);
        model.setMaxLines(2);
        model.setEllipsize(android.text.TextUtils.TruncateAt.END);
        model.setLineSpacing(0f, 1.05f);
        model.setText(SystemProperties.get("ro.product.model", Build.MODEL));
        card.text.addView(model);
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        TextView rom = chip(context, context.getString(R.string.ff_rom_name), p.onTertiary);
        rom.setBackground(Ui.round(p.tertiary, FreeFlowStyle.dp(context, 999)));
        chips.addView(rom);
        TextView version = chip(context,
                context.getString(R.string.ff_android_version, Build.VERSION.RELEASE),
                p.onTertiaryContainer);
        GradientDrawable outline = Ui.round(0, FreeFlowStyle.dp(context, 999));
        outline.setStroke(FreeFlowStyle.dpi(context, 1.5f),
                FreeFlowStyle.withAlpha(p.onTertiaryContainer, 0.45f));
        version.setBackground(outline);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        clp.setMarginStart(FreeFlowStyle.dpi(context, 6));
        chips.addView(version, clp);
        card.text.addView(chips, wrap(8));
    }

    private static TextView label(Context context, String text, int color) {
        TextView tv = Ui.line(context, 14, 500, FreeFlowStyle.withAlpha(color, 0.85f));
        tv.setText(text);
        return tv;
    }

    private static TextView chip(Context context, String text, int color) {
        TextView tv = Ui.line(context, 12, 500, color);
        tv.setText(text);
        int h = FreeFlowStyle.dpi(context, 10);
        int v = FreeFlowStyle.dpi(context, 4);
        tv.setPadding(h, v, h, v);
        return tv;
    }

    private static TextView sub(Context context, String text, int color) {
        TextView tv = Ui.text(context, 14, 400, FreeFlowStyle.withAlpha(color, 0.85f));
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setLineSpacing(0f, 1.1f);
        tv.setText(text);
        return tv;
    }

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = FreeFlowStyle.dpi(getContext(), topDp);
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
        int textWidth = Math.max(FreeFlowStyle.dpi(getContext(), 114),
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
                recycleVelocity();
                track(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                track(ev);
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
                recycleVelocity();
                track(ev);
                return true;
            case MotionEvent.ACTION_MOVE:
                track(ev);
                checkSwipe(ev);
                if (mSwiping) {
                    drag(ev);
                }
                return true;
            case MotionEvent.ACTION_UP:
                track(ev);
                if (mSwiping) {
                    mVelocity.computeCurrentVelocity(1000);
                    settle(mVelocity.getXVelocity());
                }
                mSwiping = false;
                recycleVelocity();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mSwiping) {
                    settle(0f);
                }
                mSwiping = false;
                recycleVelocity();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private void track(MotionEvent ev) {
        if (mVelocity == null) {
            mVelocity = android.view.VelocityTracker.obtain();
        }
        mVelocity.addMovement(ev);
    }

    private void recycleVelocity() {
        if (mVelocity != null) {
            mVelocity.recycle();
            mVelocity = null;
        }
    }

    private void checkSwipe(MotionEvent ev) {
        float dx = ev.getX() - mDownX;
        float dy = ev.getY() - mDownY;
        if (!mSwiping && Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy)) {
            mSwiping = true;
            mDownX = ev.getX();
            mStartFraction = mFraction;
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
        private float mExpand = 1f;

        Card(Context context, int index, int color, int onColor, int shape, int badgeColor,
                int iconRes, int iconTint, String description) {
            super(context);
            bg = Ui.round(color, FreeFlowStyle.dp(context, 28));
            mask = Ui.round(0xffffffff, FreeFlowStyle.dp(context, 28));
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
            LayoutParams tlp = new LayoutParams(FreeFlowStyle.dpi(context, 114),
                    LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.START);
            int inset = FreeFlowStyle.dpi(context, 20);
            tlp.setMargins(inset, inset, inset, inset);
            addView(text, tlp);

            badge = new MorphBadgeView(context);
            badge.setShape(shape);
            badge.setColor(badgeColor);
            badge.setIcon(context.getDrawable(iconRes), iconTint);
            int size = FreeFlowStyle.dpi(context, BADGE_DP);
            addView(badge, new LayoutParams(size, size, Gravity.TOP | Gravity.START));
        }

        void setTextWidth(int width) {
            LayoutParams lp = (LayoutParams) text.getLayoutParams();
            if (lp.width != width) {
                lp.width = width;
                text.setLayoutParams(lp);
            }
        }

        void setExpand(float e) {
            mExpand = e;
            Context c = getContext();
            float morph = FreeFlowStyle.clamp(e, 0f, 1f);
            float radius = FreeFlowStyle.dp(c, FreeFlowStyle.lerp(24, 28, morph));
            bg.setCornerRadius(radius);
            mask.setCornerRadius(radius);
            badge.setState(morph, FreeFlowStyle.lerp(48f / BADGE_DP, 1f, e),
                    FreeFlowStyle.lerp(-135f, 0f, e),
                    FreeFlowStyle.dp(c, FreeFlowStyle.lerp(22, 40, morph)));
            placeBadge();
            invalidateOutline();
        }

        private void placeBadge() {
            Context c = getContext();
            float e = mExpand;
            float half = FreeFlowStyle.dp(c, BADGE_DP) / 2f;
            float w = getWidth();
            float cx = FreeFlowStyle.lerp(w / 2f, w - FreeFlowStyle.dp(c, 44), e);
            float cy = FreeFlowStyle.dp(c, FreeFlowStyle.lerp(86, 78, e));
            badge.setTranslationX(cx - half);
            badge.setTranslationY(cy - half);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            placeBadge();
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
