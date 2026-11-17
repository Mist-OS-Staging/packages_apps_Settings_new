/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.R;

final class FreeFlowCardView extends FrameLayout {

    interface Callbacks {
        void onRemove(String key);
    }

    final String key;
    private final Palette mPalette;
    private final boolean mExpressive;
    private final Callbacks mCallbacks;
    private final GradientDrawable mBg;
    private final GradientDrawable mOutline;
    private final LinearLayout mContent;
    private final BadgeView mRemove;
    private final BadgeView mResize;

    private CardLayout.Spec mSpec;
    private CardData mData;
    private int mCardWidth;
    private boolean mEditing;
    private boolean mDragging;
    private float mRadiusDp = 28f;
    private ValueAnimator mPressAnim;

    FreeFlowCardView(Context context, String key, Palette palette, boolean expressive,
            Callbacks callbacks) {
        super(context);
        this.key = key;
        mPalette = palette;
        mExpressive = expressive;
        mCallbacks = callbacks;
        setClipChildren(false);
        setClipToPadding(false);

        mBg = Ui.round(palette.surfaceBright, FreeFlowStyle.dp(context, 28));
        setBackground(mBg);
        mOutline = Ui.round(0, FreeFlowStyle.dp(context, 28));
        mOutline.setStroke(FreeFlowStyle.dpi(context, 2),
                FreeFlowStyle.withAlpha(palette.primary, 0.45f));

        mContent = new LinearLayout(context);
        addView(mContent, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        mRemove = new BadgeView(context, BadgeView.REMOVE);
        mRemove.setOnClickListener(v -> mCallbacks.onRemove(key));
        int s = FreeFlowStyle.dpi(context, 48);
        LayoutParams rlp = new LayoutParams(s, s, Gravity.TOP | Gravity.START);
        rlp.leftMargin = -FreeFlowStyle.dpi(context, 17);
        rlp.topMargin = -FreeFlowStyle.dpi(context, 17);
        addView(mRemove, rlp);

        mResize = new BadgeView(context, BadgeView.RESIZE);
        addView(mResize, new LayoutParams(s, s, Gravity.BOTTOM | Gravity.END));

        mRemove.setVisibility(GONE);
        mResize.setVisibility(GONE);
    }

    CardLayout.Spec getSpec() {
        return mSpec;
    }

    CharSequence getTitle() {
        return mData != null ? mData.title : null;
    }

    boolean isPill() {
        return mSpec != null && mSpec.rowSpan == 1;
    }

    void bind(CardLayout.Spec spec, CardData data, int cardWidth) {
        mSpec = spec.copy();
        mData = data;
        mCardWidth = cardWidth;
        build();
        updateDecorations(false);
    }

    void setEditing(boolean editing) {
        if (mEditing == editing) {
            return;
        }
        mEditing = editing;
        if (mSpec != null) {
            build();
        }
        updateDecorations(editing);
    }

    void setDragging(boolean dragging) {
        mDragging = dragging;
        float scale = dragging ? 1.05f : 1f;
        animate().scaleX(scale).scaleY(scale)
                .translationZ(dragging ? FreeFlowStyle.dp(getContext(), 12) : 0f)
                .setDuration(FreeFlowStyle.duration(250)).setInterpolator(FreeFlowStyle.SPRING)
                .start();
        if (!isPill()) {
            setRadius(dragging ? 36f : 28f);
        }
        setForeground(mEditing && !dragging ? mOutline : null);
    }

    void setPressedVisual(boolean pressed) {
        animate().scaleX(pressed ? 0.96f : 1f).scaleY(pressed ? 0.96f : 1f)
                .setDuration(FreeFlowStyle.duration(450)).setInterpolator(FreeFlowStyle.SPRING)
                .start();
        if (isPill()) {
            return;
        }
        if (mPressAnim != null) {
            mPressAnim.cancel();
        }
        float target = pressed ? 14f : 28f;
        if (!FreeFlowStyle.motion()) {
            setRadius(target);
            return;
        }
        mPressAnim = ValueAnimator.ofFloat(mRadiusDp, target);
        mPressAnim.setDuration(450);
        mPressAnim.setInterpolator(FreeFlowStyle.SPRING);
        mPressAnim.addUpdateListener(a -> setRadius((float) a.getAnimatedValue()));
        mPressAnim.start();
    }

    boolean isInResizeHandle(float x, float y) {
        if (!mEditing || mResize.getVisibility() != VISIBLE) {
            return false;
        }
        return x >= mResize.getLeft() && x <= mResize.getRight()
                && y >= mResize.getTop() && y <= mResize.getBottom();
    }

    private void setRadius(float dp) {
        mRadiusDp = dp;
        float px = FreeFlowStyle.dp(getContext(), dp);
        mBg.setCornerRadius(px);
        mOutline.setCornerRadius(px);
        invalidateOutline();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (isPill()) {
            mBg.setCornerRadius(h / 2f);
            mOutline.setCornerRadius(h / 2f);
        } else {
            setRadius(mDragging ? 36f : 28f);
        }
    }

    private void updateDecorations(boolean pop) {
        setForeground(mEditing && !mDragging ? mOutline : null);
        int vis = mEditing ? VISIBLE : GONE;
        mRemove.setVisibility(vis);
        mResize.setVisibility(vis);
        if (mSpec == null) {
            return;
        }
        Context c = getContext();
        CharSequence title = getTitle() != null ? getTitle() : "";
        mRemove.setContentDescription(c.getString(R.string.ff_remove_card, title));
        mResize.setContentDescription(c.getString(R.string.ff_resize_card, title));
        mResize.setStateDescription(sizeName(c, mSpec.colSpan, mSpec.rowSpan));
        mResize.setPill(isPill());
        LayoutParams lp = (LayoutParams) mResize.getLayoutParams();
        int w = isPill() ? FreeFlowStyle.dpi(c, 40) : FreeFlowStyle.dpi(c, 48);
        int h = isPill() ? LayoutParams.MATCH_PARENT : FreeFlowStyle.dpi(c, 48);
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            mResize.setLayoutParams(lp);
        }
        if (pop && mEditing && FreeFlowStyle.motion()) {
            for (View v : new View[] {mRemove, mResize}) {
                v.setScaleX(0.6f);
                v.setScaleY(0.6f);
                v.setAlpha(0f);
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350)
                        .setInterpolator(FreeFlowStyle.SPRING).start();
            }
        }
    }

    static String sizeName(Context c, int colSpan, int rowSpan) {
        int res;
        switch (rowSpan) {
            case 1:
                res = R.string.ff_size_pill;
                break;
            case 2:
                res = R.string.ff_size_small;
                break;
            case 3:
                res = R.string.ff_size_medium;
                break;
            default:
                res = R.string.ff_size_tall;
                break;
        }
        String base = c.getString(res);
        return colSpan == 2 ? c.getString(R.string.ff_size_wide, base) : base;
    }

    private void build() {
        mContent.removeAllViews();
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setGravity(Gravity.NO_GRAVITY);
        if (mSpec == null || mData == null) {
            return;
        }
        Context c = getContext();
        boolean pill = mSpec.rowSpan == 1;
        boolean wide = mSpec.colSpan == 2;
        boolean big = mSpec.rowSpan >= 3;
        int kind = mData.kind;
        setContentDescription(mData.title);
        if (pill) {
            mContent.setPaddingRelative(FreeFlowStyle.dpi(c, 10), 0,
                    FreeFlowStyle.dpi(c, mEditing ? 40 : 18), 0);
            buildPill(c, wide);
            return;
        }
        int pad = FreeFlowStyle.dpi(c, 16);
        mContent.setPaddingRelative(FreeFlowStyle.dpi(c, 18), pad, pad, pad);
        if (kind == CardData.BATTERY) {
            buildBattery(c, wide, big);
        } else if (kind == CardData.STORAGE) {
            buildStorage(c, wide, big);
        } else if (kind == CardData.ACCOUNT && wide) {
            buildAccount(c);
        } else if (wide && mSpec.rowSpan == 2) {
            buildRow(c);
        } else {
            buildColumn(c, big);
        }
    }

    private View icon(Context c, int sizeDp) {
        int size = FreeFlowStyle.dpi(c, sizeDp);
        View v;
        if (mData.kind == CardData.ACCOUNT) {
            v = avatar(c, size);
        } else {
            v = Ui.circleIcon(c, mData.icon, size, mExpressive, mPalette.primary,
                    mPalette.onPrimary);
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }

    private View avatar(Context c, int size) {
        if (mData.icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(Ui.copy(mData.icon));
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            iv.setClipToOutline(true);
            return iv;
        }
        TextView tv = Ui.text(c, size * 0.36f / c.getResources().getDisplayMetrics().density,
                700, mPalette.onPrimary);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Ui.oval(mPalette.primary));
        tv.setText(initials(mData.title));
        tv.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return tv;
    }

    private static String initials(CharSequence name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : name.toString().trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.appendCodePoint(part.codePointAt(0));
            }
        }
        return sb.toString().toUpperCase();
    }

    private String pillValue(boolean wide) {
        if (mData.kind == CardData.BATTERY) {
            if (mData.level < 0) {
                return null;
            }
            String pct = mData.level + "%";
            if (!wide) {
                return pct;
            }
            StringBuilder sb = new StringBuilder(pct);
            if (mData.temperature != null) {
                sb.append(" · ").append(mData.temperature);
            }
            if (mData.status != null) {
                sb.append(" · ").append(mData.status);
            }
            return sb.toString();
        }
        if (mData.kind == CardData.STORAGE) {
            if (mData.used == null) {
                return null;
            }
            return wide && mData.total != null
                    ? getContext().getString(R.string.ff_storage_pair, mData.used, mData.total)
                    : mData.used;
        }
        if (wide && mData.summary != null) {
            return mData.summary.toString();
        }
        return null;
    }

    private void buildPill(Context c, boolean wide) {
        mContent.setOrientation(LinearLayout.HORIZONTAL);
        mContent.setGravity(Gravity.CENTER_VERTICAL);
        mContent.addView(icon(c, 38));
        boolean meter = mData.kind == CardData.BATTERY || mData.kind == CardData.STORAGE;
        String value = pillValue(wide);
        TextView title = Ui.line(c, 16, 600, mPalette.onSurface);
        title.setText(!wide && meter && value != null ? value : mData.title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(FreeFlowStyle.dpi(c, 12));
        if (wide && !meter && value != null) {
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(title);
            TextView sub = Ui.line(c, 13, 400, mPalette.onSurfaceVariant);
            sub.setText(value);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            slp.topMargin = FreeFlowStyle.dpi(c, 1);
            texts.addView(sub, slp);
            mContent.addView(texts, tlp);
            return;
        }
        mContent.addView(title, tlp);
        if (wide && value != null) {
            TextView v = meter ? Ui.line(c, 17, 700, mPalette.onSurface)
                    : Ui.line(c, 13, 400, mPalette.onSurfaceVariant);
            if (meter) {
                Ui.width(v, 112);
            }
            v.setText(value);
            v.setMaxWidth(Math.round(mCardWidth * 0.45f));
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            vlp.setMarginStart(FreeFlowStyle.dpi(c, 8));
            mContent.addView(v, vlp);
        }
    }

    private void buildColumn(Context c, boolean big) {
        mContent.addView(icon(c, big ? 60 : 48));
        mContent.addView(new View(c), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView title = Ui.text(c, big ? 19 : 16, 600, mPalette.onSurface);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0f, 1.05f);
        title.setText(mData.title);
        mContent.addView(title);
        if (!TextUtils.isEmpty(mData.summary)) {
            TextView sub = Ui.text(c, 13, 400, mPalette.onSurfaceVariant);
            sub.setMaxLines(big ? 4 : 1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setText(mData.summary);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = FreeFlowStyle.dpi(c, 2);
            mContent.addView(sub, lp);
        }
    }

    private void buildRow(Context c) {
        mContent.setOrientation(LinearLayout.HORIZONTAL);
        mContent.setGravity(Gravity.CENTER_VERTICAL);
        mContent.setPaddingRelative(mContent.getPaddingStart() + FreeFlowStyle.dpi(c, 4),
                mContent.getPaddingTop(), mContent.getPaddingEnd(), mContent.getPaddingBottom());
        mContent.addView(icon(c, 48));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.line(c, 17, 600, mPalette.onSurface);
        title.setText(mData.title);
        texts.addView(title);
        if (!TextUtils.isEmpty(mData.summary)) {
            TextView sub = Ui.text(c, 14, 400, mPalette.onSurfaceVariant);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setText(mData.summary);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = FreeFlowStyle.dpi(c, 3);
            texts.addView(sub, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(FreeFlowStyle.dpi(c, 18));
        mContent.addView(texts, lp);
    }

    private void buildAccount(Context c) {
        mContent.setOrientation(LinearLayout.HORIZONTAL);
        mContent.setGravity(Gravity.CENTER_VERTICAL);
        mContent.setPaddingRelative(mContent.getPaddingStart() + FreeFlowStyle.dpi(c, 4),
                mContent.getPaddingTop(), mContent.getPaddingEnd(), mContent.getPaddingBottom());
        mContent.addView(icon(c, 56));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.line(c, 20, 400, mPalette.onSurface);
        title.setText(mData.title);
        texts.addView(title);
        if (!TextUtils.isEmpty(mData.summary)) {
            TextView sub = Ui.text(c, 15, 400, mPalette.onSurfaceVariant);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setText(mData.summary);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = FreeFlowStyle.dpi(c, 3);
            texts.addView(sub, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(FreeFlowStyle.dpi(c, 18));
        mContent.addView(texts, lp);
    }

    private float numberSize(boolean wide, boolean big) {
        if (big) {
            return wide ? (mSpec.rowSpan == 4 ? 64 : 48) : (mSpec.rowSpan == 4 ? 44 : 34);
        }
        return wide ? 32 : 26;
    }

    private LinearLayout meterTop(Context c, String number, boolean wide, boolean big) {
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(icon(c, 36));
        TextView num = Ui.line(c, numberSize(wide, big), 700, mPalette.onSurface);
        Ui.width(num, 115);
        num.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        num.setText(number != null ? number : "");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(FreeFlowStyle.dpi(c, 8));
        top.addView(num, lp);
        return top;
    }

    private View bar(Context c, float fraction, int color) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        int h = FreeFlowStyle.dpi(c, 10);
        float r = h / 2f;
        float f = FreeFlowStyle.clamp(fraction < 0 ? 0 : fraction, 0f, 1f);
        View fill = new View(c);
        fill.setBackground(Ui.round(color, r));
        View track = new View(c);
        track.setBackground(Ui.round(mPalette.surfaceContainerHighest, r));
        if (f > 0f) {
            bar.addView(fill, new LinearLayout.LayoutParams(0, h, f));
        }
        if (f < 1f) {
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, h, 1f - f);
            if (f > 0f) {
                tlp.setMarginStart(FreeFlowStyle.dpi(c, 4));
            }
            bar.addView(track, tlp);
        }
        bar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return bar;
    }

    private TextView chip(Context c, String text) {
        TextView tv = Ui.line(c, 12, 700, mPalette.onSurface);
        tv.setText(text);
        int h = FreeFlowStyle.dpi(c, 9);
        int v = FreeFlowStyle.dpi(c, 3);
        tv.setPadding(h, v, h, v);
        tv.setBackground(Ui.round(mPalette.surfaceContainerHighest,
                FreeFlowStyle.dp(c, 999)));
        return tv;
    }

    private void spacer(Context c) {
        mContent.addView(new View(c), new LinearLayout.LayoutParams(0, 0, 1f));
    }

    private void buildBattery(Context c, boolean wide, boolean big) {
        String pct = mData.level >= 0 ? mData.level + "%" : "";
        mContent.addView(meterTop(c, pct, wide, big));
        spacer(c);
        mContent.addView(bar(c, mData.level / 100f, mPalette.green));
        spacer(c);
        LinearLayout chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        int gap = FreeFlowStyle.dpi(c, 6);
        if (mData.temperature != null) {
            chips.addView(chip(c, mData.temperature));
        }
        if (mData.status != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(chips.getChildCount() > 0 ? gap : 0);
            chips.addView(chip(c, mData.status), lp);
        }
        if (wide) {
            TextView label = Ui.line(c, 13, 400, mPalette.onSurfaceVariant);
            label.setText(mData.title);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(gap);
            chips.addView(label, lp);
        }
        mContent.addView(chips);
    }

    private void buildStorage(Context c, boolean wide, boolean big) {
        String number = mData.used;
        if (wide && mData.used != null && mData.total != null) {
            number = c.getString(R.string.ff_storage_pair, mData.used, mData.total);
        }
        mContent.addView(meterTop(c, number, wide, big));
        spacer(c);
        mContent.addView(bar(c, mData.usedFraction, mPalette.purple));
        spacer(c);
        TextView line = Ui.line(c, 13, 400, mPalette.onSurfaceVariant);
        if (wide && mData.free != null) {
            line.setText(c.getString(R.string.ff_storage_free, mData.free));
        } else if (mData.total != null) {
            line.setText(c.getString(R.string.ff_storage_of, mData.total));
        } else {
            line.setText(mData.title);
        }
        mContent.addView(line);
    }

    static final class BadgeView extends View {

        static final int REMOVE = 0;
        static final int RESIZE = 1;

        private final int mMode;
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final int mOnSurface;
        private final int mRing;
        private boolean mPill;

        BadgeView(Context context, int mode) {
            super(context);
            mMode = mode;
            Palette p = new Palette(context);
            mOnSurface = p.onSurface;
            mRing = p.surfaceContainerLow;
            mFill.setColor(p.onSurface);
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setStrokeCap(Paint.Cap.ROUND);
            if (mode == REMOVE) {
                mStroke.setColor(p.surfaceContainerLow);
                setClickable(true);
                setFocusable(true);
            } else {
                mStroke.setColor(p.primary);
                setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        }

        void setPill(boolean pill) {
            if (mPill != pill) {
                mPill = pill;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context c = getContext();
            if (mMode == REMOVE) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = FreeFlowStyle.dp(c, 15);
                mFill.setColor(mRing);
                canvas.drawCircle(cx, cy, r, mFill);
                mFill.setColor(mOnSurface);
                canvas.drawCircle(cx, cy, r - FreeFlowStyle.dp(c, 3), mFill);
                mStroke.setStrokeWidth(FreeFlowStyle.dp(c, 2.4f));
                float a = FreeFlowStyle.dp(c, 4.5f);
                canvas.drawLine(cx - a, cy - a, cx + a, cy + a, mStroke);
                canvas.drawLine(cx + a, cy - a, cx - a, cy + a, mStroke);
                return;
            }
            float box = FreeFlowStyle.dp(c, mPill ? 22 : 30);
            float margin = FreeFlowStyle.dp(c, mPill ? 8 : 6);
            float right = getWidth() - margin;
            float bottom = getHeight() - margin;
            float unit = box / 30f;
            mStroke.setStrokeWidth(5 * unit);
            float radius = 16 * unit;
            float cx = right - box + 8 * unit;
            float cy = bottom - box + 8 * unit;
            mRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(mRect, 0, 90, false, mStroke);
        }
    }
}
