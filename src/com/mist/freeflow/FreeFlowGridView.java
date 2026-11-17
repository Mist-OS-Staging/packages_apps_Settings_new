/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.NestedScrollView;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FreeFlowGridView extends ViewGroup implements FreeFlowCardView.Callbacks {

    interface Listener {
        void onCardClick(String key);

        void onRequestEdit();

        void onLayoutChanged();

        void onAddCard();
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_RESIZE = 2;
    private static final long HOLD_MS = 420;

    private static final int[][] SIZES = {
            {1, 1}, {2, 1}, {1, 2}, {1, 3}, {2, 2}, {1, 4}, {2, 3}, {2, 4}};

    private final Palette mPalette;
    private final boolean mExpressive;
    private final int mTouchSlop;
    private final float mGap;
    private final float mRow;
    private float[] mTops;
    private final float mListCardHeight;
    private final Map<String, FreeFlowCardView> mViews = new LinkedHashMap<>();
    private final Map<String, CardData> mData = new HashMap<>();
    private final Map<String, PointF> mFlip = new HashMap<>();
    private final Paint mDropFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDropStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGhostFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGhostStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final TextView mAddTile;

    private List<CardLayout.Spec> mOrder = new ArrayList<>();
    private Listener mListener;
    private boolean mEditing;
    private int[] mAddAt;

    private int mMode = MODE_NONE;
    private String mTouchKey;
    private float mDownRawX;
    private float mDownRawY;
    private int mScrollAtDown;
    private float mLastRawX;
    private float mLastRawY;
    private boolean mHoldPending;
    private int[] mDrop;
    private RectF mGhost;
    private int[] mResizeOrigin;
    private float mAutoScroll;
    private NestedScrollView mScroller;

    private final Runnable mHold = () -> {
        mHoldPending = false;
        if (mTouchKey == null || mMode != MODE_NONE) {
            return;
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        FreeFlowCardView v = mViews.get(mTouchKey);
        if (v != null) {
            v.setPressedVisual(false);
        }
        if (mListener != null) {
            mListener.onRequestEdit();
        }
        beginMove();
    };

    private final Runnable mAutoScrollTick = new Runnable() {
        @Override
        public void run() {
            if (mMode != MODE_MOVE || mAutoScroll == 0 || mScroller == null) {
                return;
            }
            int before = mScroller.getScrollY();
            mScroller.scrollBy(0, Math.round(mAutoScroll));
            if (mScroller.getScrollY() != before) {
                updateMove();
            }
            postOnAnimation(this);
        }
    };

    public FreeFlowGridView(Context context) {
        this(context, null);
    }

    public FreeFlowGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        mPalette = new Palette(context);
        mExpressive = com.android.settingslib.widget.SettingsThemeHelper
                .isExpressiveTheme(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mGap = FreeFlowStyle.dp(context, 10);
        mRow = FreeFlowStyle.dp(context, 57);
        mListCardHeight = FreeFlowStyle.dp(context, 84);

        mDropFill.setColor(FreeFlowStyle.withAlpha(mPalette.primary, 0.16f));
        mDropStroke.setStyle(Paint.Style.STROKE);
        mDropStroke.setStrokeWidth(FreeFlowStyle.dp(context, 2));
        mDropStroke.setColor(mPalette.primary);
        mGhostFill.setColor(FreeFlowStyle.withAlpha(mPalette.primary, 0.10f));
        mGhostStroke.setStyle(Paint.Style.STROKE);
        mGhostStroke.setStrokeWidth(FreeFlowStyle.dp(context, 2));
        mGhostStroke.setColor(mPalette.primary);
        mGhostStroke.setPathEffect(new DashPathEffect(new float[] {
                FreeFlowStyle.dp(context, 6), FreeFlowStyle.dp(context, 4)}, 0));

        mAddTile = Ui.text(context, 15, 600, mPalette.onSurface);
        mAddTile.setGravity(Gravity.CENTER);
        mAddTile.setText(R.string.ff_add_card);
        android.graphics.drawable.Drawable plus = context.getDrawable(R.drawable.ff_ic_add);
        if (plus != null) {
            plus.setTint(mPalette.onSurface);
        }
        mAddTile.setCompoundDrawablesRelativeWithIntrinsicBounds(null, plus, null, null);
        mAddTile.setCompoundDrawablePadding(FreeFlowStyle.dpi(context, 6));
        GradientDrawable dashed = Ui.round(0, FreeFlowStyle.dp(context, 28));
        dashed.setStroke(FreeFlowStyle.dpi(context, 2), mPalette.outline,
                FreeFlowStyle.dp(context, 6), FreeFlowStyle.dp(context, 4));
        mAddTile.setBackground(dashed);
        mAddTile.setForeground(Ui.ripple(FreeFlowStyle.withAlpha(mPalette.onSurface, 0.12f),
                Ui.round(0xffffffff, FreeFlowStyle.dp(context, 28))));
        mAddTile.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onAddCard();
            }
        });
        mAddTile.setVisibility(GONE);
        addView(mAddTile);
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    List<CardLayout.Spec> getOrder() {
        return CardLayout.copy(mOrder);
    }

    boolean isEditing() {
        return mEditing;
    }

    void setOrder(List<CardLayout.Spec> order, boolean animate) {
        if (animate) {
            snapshot();
        }
        mOrder = CardLayout.copy(order);
        syncViews();
        requestLayout();
    }

    void setData(Map<String, CardData> data) {
        mData.clear();
        mData.putAll(data);
        syncViews();
        requestLayout();
    }

    void updateData(String key, CardData data) {
        mData.put(key, data);
        FreeFlowCardView v = mViews.get(key);
        CardLayout.Spec s = CardLayout.find(mOrder, key);
        if (v != null && s != null && data != null) {
            v.bind(s, data, cardWidth(s));
        } else {
            syncViews();
            requestLayout();
        }
    }

    void setEditing(boolean editing) {
        if (mEditing == editing) {
            return;
        }
        mEditing = editing;
        if (!editing) {
            cancelTouch();
        }
        for (FreeFlowCardView v : mViews.values()) {
            v.setEditing(editing);
            updateAccessibility(v);
        }
        requestLayout();
    }

    private boolean isShown(CardLayout.Spec s) {
        return mData.get(s.key) != null;
    }

    private void syncViews() {
        List<String> keep = new ArrayList<>();
        for (CardLayout.Spec s : mOrder) {
            if (isShown(s)) {
                keep.add(s.key);
            }
        }
        for (String key : new ArrayList<>(mViews.keySet())) {
            if (!keep.contains(key)) {
                removeView(mViews.remove(key));
            }
        }
        for (CardLayout.Spec s : mOrder) {
            CardData d = mData.get(s.key);
            if (d == null) {
                continue;
            }
            FreeFlowCardView v = mViews.get(s.key);
            if (v == null) {
                v = new FreeFlowCardView(getContext(), s.key, mPalette, mExpressive, this);
                mViews.put(s.key, v);
                addView(v);
                v.setEditing(mEditing);
            }
            v.bind(s, d, cardWidth(s));
            updateAccessibility(v);
        }
    }

    private float colWidth() {
        return (getWidth() - mGap) / 2f;
    }

    private int cardWidth(CardLayout.Spec s) {
        float w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        float col = (w - mGap) / 2f;
        return Math.round(s.colSpan == 2 ? w : col);
    }

    private int rows() {
        int bottom = 0;
        for (CardLayout.Spec s : mOrder) {
            if (isShown(s)) {
                bottom = Math.max(bottom, s.bottom());
            }
        }
        if (mDrop != null) {
            bottom = Math.max(bottom, mDrop[1] + mDrop[3]);
        }
        if (mAddAt != null) {
            bottom = Math.max(bottom, mAddAt[1] + 2);
        }
        if (bottom == 0 && !mEditing) {
            return 0;
        }
        return Math.max(1, bottom + (mEditing ? 4 : 0));
    }

    static boolean isListCard(CardLayout.Spec s, CardData d) {
        return s.colSpan == 2 && s.rowSpan == 2 && d != null
                && d.kind != CardData.BATTERY && d.kind != CardData.STORAGE;
    }

    private void buildTops() {
        if (mEditing) {
            mTops = null;
            return;
        }
        int bottom = 0;
        for (CardLayout.Spec s : mOrder) {
            if (isShown(s)) {
                bottom = Math.max(bottom, s.bottom());
            }
        }
        boolean[] used = new boolean[bottom];
        boolean[] full = new boolean[bottom];
        boolean[] list = new boolean[bottom];
        java.util.Arrays.fill(list, true);
        for (CardLayout.Spec s : mOrder) {
            if (!isShown(s)) {
                continue;
            }
            boolean listCard = isListCard(s, mData.get(s.key));
            for (int r = s.row; r < s.bottom() && r < bottom; r++) {
                used[r] = true;
                if (s.colSpan == 2) {
                    full[r] = true;
                }
                if (!listCard) {
                    list[r] = false;
                }
            }
        }
        float compact = (mListCardHeight - mGap) / 2f;
        mTops = new float[bottom + 1];
        float y = 0f;
        for (int r = 0; r < bottom; r++) {
            mTops[r] = y;
            if (used[r]) {
                y += (full[r] && list[r] ? compact : mRow) + mGap;
            }
        }
        mTops[bottom] = y;
    }

    private float rowTop(int row) {
        if (mTops != null && row >= 0 && row < mTops.length) {
            return mTops[row];
        }
        return row * (mRow + mGap);
    }

    private int cardHeight(CardLayout.Spec s) {
        if (mTops != null && s.bottom() < mTops.length) {
            return Math.round(mTops[s.bottom()] - mTops[s.row] - mGap);
        }
        return Math.round(s.rowSpan * (mRow + mGap) - mGap);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        mAddAt = mEditing && mOrder.size() < CardLayout.MAX_CARDS
                ? CardLayout.firstFree(mOrder, 1, 2) : null;
        buildTops();
        int height;
        if (mTops != null) {
            float last = mTops[mTops.length - 1];
            height = last <= 0f ? 0 : Math.round(last - mGap);
        } else {
            int rows = rows();
            height = rows == 0 ? 0 : Math.round(rows * (mRow + mGap) - mGap);
        }
        float col = (width - mGap) / 2f;
        for (CardLayout.Spec s : mOrder) {
            FreeFlowCardView v = mViews.get(s.key);
            if (v == null) {
                continue;
            }
            int w = Math.round(s.colSpan == 2 ? width : col);
            v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cardHeight(s), MeasureSpec.EXACTLY));
        }
        mAddTile.setVisibility(mAddAt != null ? VISIBLE : GONE);
        if (mAddAt != null) {
            mAddTile.measure(MeasureSpec.makeMeasureSpec(Math.round(col), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(2 * mRow + mGap),
                            MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float col = colWidth();
        for (CardLayout.Spec s : mOrder) {
            FreeFlowCardView v = mViews.get(s.key);
            if (v == null) {
                continue;
            }
            int left = Math.round(s.col * (col + mGap));
            int top = Math.round(rowTop(s.row));
            v.layout(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
        }
        if (mAddAt != null) {
            int left = Math.round(mAddAt[0] * (col + mGap));
            int top = Math.round(mAddAt[1] * (mRow + mGap));
            mAddTile.layout(left, top, left + mAddTile.getMeasuredWidth(),
                    top + mAddTile.getMeasuredHeight());
        }
        runFlip();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            post(this::syncViews);
        }
    }

    private void snapshot() {
        mFlip.clear();
        for (FreeFlowCardView v : mViews.values()) {
            mFlip.put(v.key, new PointF(v.getLeft() + v.getTranslationX(),
                    v.getTop() + v.getTranslationY()));
        }
    }

    private void runFlip() {
        if (mFlip.isEmpty()) {
            return;
        }
        for (Map.Entry<String, PointF> e : mFlip.entrySet()) {
            FreeFlowCardView v = mViews.get(e.getKey());
            if (v == null || e.getKey().equals(mMode == MODE_MOVE ? mTouchKey : null)) {
                continue;
            }
            float dx = e.getValue().x - v.getLeft();
            float dy = e.getValue().y - v.getTop();
            v.animate().cancel();
            if (Math.abs(dx) + Math.abs(dy) > 1 && FreeFlowStyle.motion()) {
                v.setTranslationX(dx);
                v.setTranslationY(dy);
                v.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                        .translationZ(0f).setDuration(420)
                        .setInterpolator(FreeFlowStyle.SPRING).start();
            } else {
                v.setTranslationX(0f);
                v.setTranslationY(0f);
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setTranslationZ(0f);
            }
        }
        mFlip.clear();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrop != null) {
            float col = colWidth();
            float left = mDrop[0] * (col + mGap);
            float top = mDrop[1] * (mRow + mGap);
            float w = mDrop[2] == 2 ? getWidth() : col;
            float h = mDrop[3] * (mRow + mGap) - mGap;
            mRect.set(left, top, left + w, top + h);
            float radius = mDrop[3] == 1 ? h / 2f : FreeFlowStyle.dp(getContext(), 28);
            canvas.drawRoundRect(mRect, radius, radius, mDropFill);
            float inset = mDropStroke.getStrokeWidth() / 2f;
            mRect.inset(inset, inset);
            canvas.drawRoundRect(mRect, radius, radius, mDropStroke);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mGhost != null) {
            float radius = FreeFlowStyle.dp(getContext(), 28);
            canvas.drawRoundRect(mGhost, radius, radius, mGhostFill);
            canvas.drawRoundRect(mGhost, radius, radius, mGhostStroke);
        }
    }

    private FreeFlowCardView cardAt(float x, float y) {
        FreeFlowCardView hit = null;
        for (FreeFlowCardView v : mViews.values()) {
            if (x >= v.getLeft() && x <= v.getRight() && y >= v.getTop() && y <= v.getBottom()) {
                hit = v;
            }
        }
        return hit;
    }

    private NestedScrollView scroller() {
        if (mScroller == null) {
            ViewParent p = getParent();
            while (p != null && !(p instanceof NestedScrollView)) {
                p = p.getParent();
            }
            mScroller = (NestedScrollView) p;
        }
        return mScroller;
    }

    private int scrollY() {
        NestedScrollView s = scroller();
        return s != null ? s.getScrollY() : 0;
    }

    private void disallowParent(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private String removeAt(float x, float y) {
        if (!mEditing) {
            return null;
        }
        float offset = FreeFlowStyle.dp(getContext(), 7);
        float reach = FreeFlowStyle.dp(getContext(), 24);
        for (FreeFlowCardView v : mViews.values()) {
            float cx = v.getLeft() + offset;
            float cy = v.getTop() + offset;
            if (Math.hypot(x - cx, y - cy) <= reach) {
                return v.key;
            }
        }
        return null;
    }

    private String mRemoveKey;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mRemoveKey != null || (ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && (mRemoveKey = removeAt(ev.getX(), ev.getY())) != null)) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_UP
                    && removeAt(ev.getX(), ev.getY()) != null) {
                String key = mRemoveKey;
                mRemoveKey = null;
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                onRemove(key);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mRemoveKey = null;
            }
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                FreeFlowCardView v = cardAt(ev.getX(), ev.getY());
                if (v == null) {
                    return false;
                }
                mTouchKey = v.key;
                mDownRawX = mLastRawX = ev.getRawX();
                mDownRawY = mLastRawY = ev.getRawY();
                mScrollAtDown = scrollY();
                mMode = MODE_NONE;
                if (mEditing) {
                    disallowParent(true);
                    if (v.isInResizeHandle(ev.getX() - v.getLeft(), ev.getY() - v.getTop())) {
                        beginResize(v);
                    }
                } else {
                    v.setPressedVisual(true);
                    mHoldPending = true;
                    postDelayed(mHold, HOLD_MS);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                mLastRawX = ev.getRawX();
                mLastRawY = ev.getRawY();
                if (mTouchKey == null) {
                    return false;
                }
                float dist = Math.abs(mLastRawX - mDownRawX) + Math.abs(mLastRawY - mDownRawY);
                if (mMode == MODE_NONE) {
                    if (mEditing) {
                        if (dist > FreeFlowStyle.dp(getContext(), 6)) {
                            beginMove();
                        }
                    } else if (dist > mTouchSlop) {
                        cancelHold();
                    }
                } else if (mMode == MODE_MOVE) {
                    updateAutoScroll();
                    updateMove();
                } else if (mMode == MODE_RESIZE) {
                    updateResize(ev.getX(), ev.getY());
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (mMode == MODE_NONE && mHoldPending && mTouchKey != null) {
                    String key = mTouchKey;
                    cancelHold();
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    if (mListener != null) {
                        mListener.onCardClick(key);
                    }
                } else {
                    finishGesture();
                }
                mTouchKey = null;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mMode != MODE_NONE) {
                    finishGesture();
                } else {
                    cancelHold();
                }
                mTouchKey = null;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private void cancelHold() {
        removeCallbacks(mHold);
        mHoldPending = false;
        if (mTouchKey != null) {
            FreeFlowCardView v = mViews.get(mTouchKey);
            if (v != null) {
                v.setPressedVisual(false);
            }
        }
    }

    private void cancelTouch() {
        cancelHold();
        if (mMode != MODE_NONE) {
            finishGesture();
        }
        mTouchKey = null;
    }

    private void beginMove() {
        FreeFlowCardView v = mTouchKey != null ? mViews.get(mTouchKey) : null;
        if (v == null) {
            return;
        }
        mMode = MODE_MOVE;
        disallowParent(true);
        v.animate().cancel();
        v.bringToFront();
        v.setDragging(true);
        updateMove();
    }

    private void updateMove() {
        FreeFlowCardView v = mViews.get(mTouchKey);
        CardLayout.Spec me = CardLayout.find(mOrder, mTouchKey);
        if (v == null || me == null) {
            return;
        }
        float dx = mLastRawX - mDownRawX;
        float dy = mLastRawY - mDownRawY + (scrollY() - mScrollAtDown);
        v.setTranslationX(dx);
        v.setTranslationY(dy);
        float col = colWidth();
        int c = Math.round((v.getLeft() + dx) / (col + mGap));
        int r = Math.round((v.getTop() + dy) / (mRow + mGap));
        c = Math.max(0, Math.min(CardLayout.COLS - me.colSpan, c));
        r = Math.max(0, Math.min(40, r));
        int[] drop = {c, r, me.colSpan, me.rowSpan};
        if (mDrop == null || mDrop[0] != c || mDrop[1] != r) {
            int oldRows = rows();
            mDrop = drop;
            if (rows() != oldRows) {
                requestLayout();
            }
        }
        invalidate();
    }

    private void updateAutoScroll() {
        NestedScrollView s = scroller();
        if (s == null) {
            return;
        }
        int[] loc = new int[2];
        s.getLocationOnScreen(loc);
        float y = mLastRawY - loc[1];
        float edge = FreeFlowStyle.dp(getContext(), 90);
        float top = edge + FreeFlowStyle.dp(getContext(), 64);
        float max = FreeFlowStyle.dp(getContext(), 18);
        float v = 0;
        if (y > s.getHeight() - edge) {
            v = Math.min(max, (y - (s.getHeight() - edge)) / 4f);
        } else if (y < top) {
            v = -Math.min(max, (top - y) / 4f);
        }
        boolean was = mAutoScroll != 0;
        mAutoScroll = v;
        if (v != 0 && !was) {
            postOnAnimation(mAutoScrollTick);
        }
    }

    private void beginResize(FreeFlowCardView v) {
        CardLayout.Spec me = CardLayout.find(mOrder, v.key);
        if (me == null) {
            return;
        }
        mMode = MODE_RESIZE;
        mResizeOrigin = new int[] {v.getLeft(), v.getTop(), me.col};
        mGhost = new RectF(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        invalidate();
    }

    private void updateResize(float x, float y) {
        CardLayout.Spec me = CardLayout.find(mOrder, mTouchKey);
        if (me == null || mResizeOrigin == null) {
            return;
        }
        float min = FreeFlowStyle.dp(getContext(), 60);
        float w = Math.max(min, x - mResizeOrigin[0]);
        float h = Math.max(min, y - mResizeOrigin[1]);
        float col = colWidth();
        int c = w > (col + (2 * col + mGap)) / 2f ? 2 : 1;
        int r = Math.max(1, Math.min(CardLayout.MAX_ROW_SPAN,
                Math.round((h + mGap) / (mRow + mGap))));
        int newCol = c == 2 ? 0 : mResizeOrigin[2];
        mGhost = new RectF(mResizeOrigin[0], mResizeOrigin[1], mResizeOrigin[0] + w,
                mResizeOrigin[1] + h);
        if (me.colSpan != c || me.rowSpan != r || me.col != newCol) {
            snapshot();
            me.colSpan = c;
            me.rowSpan = r;
            me.col = newCol;
            mResizeOrigin[0] = Math.round(newCol * (col + mGap));
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            rebind(me.key);
            requestLayout();
        }
        invalidate();
    }

    private void rebind(String key) {
        FreeFlowCardView v = mViews.get(key);
        CardLayout.Spec s = CardLayout.find(mOrder, key);
        CardData d = mData.get(key);
        if (v != null && s != null && d != null) {
            v.bind(s, d, cardWidth(s));
            updateAccessibility(v);
        }
    }

    private void finishGesture() {
        removeCallbacks(mAutoScrollTick);
        mAutoScroll = 0;
        int mode = mMode;
        String key = mTouchKey;
        CardLayout.Spec me = key != null ? CardLayout.find(mOrder, key) : null;
        FreeFlowCardView v = key != null ? mViews.get(key) : null;
        if (mode == MODE_MOVE && me != null && v != null) {
            snapshot();
            mFlip.put(key, new PointF(v.getLeft() + v.getTranslationX(),
                    v.getTop() + v.getTranslationY()));
            int[] drop = mDrop;
            mMode = MODE_NONE;
            v.setDragging(false);
            if (drop != null) {
                mOrder = CardLayout.place(mOrder, key, drop[0], drop[1], me.colSpan,
                        me.rowSpan, new int[] {me.col, me.row});
            }
        } else if (mode == MODE_RESIZE && me != null) {
            snapshot();
            mOrder = CardLayout.place(mOrder, key, me.col, me.row, me.colSpan, me.rowSpan,
                    null);
        }
        mMode = MODE_NONE;
        mDrop = null;
        mGhost = null;
        mResizeOrigin = null;
        disallowParent(false);
        if (mode != MODE_NONE) {
            for (CardLayout.Spec s : mOrder) {
                rebind(s.key);
            }
            requestLayout();
            invalidate();
            if (mListener != null) {
                mListener.onLayoutChanged();
            }
        }
    }

    @Override
    public void onRemove(String key) {
        snapshot();
        List<CardLayout.Spec> o = CardLayout.copy(mOrder);
        o.remove(CardLayout.find(o, key));
        mOrder = o;
        syncViews();
        requestLayout();
        if (mListener != null) {
            mListener.onLayoutChanged();
        }
    }

    void addCard(String key) {
        if (mOrder.size() >= CardLayout.MAX_CARDS || CardLayout.find(mOrder, key) != null) {
            return;
        }
        snapshot();
        int[] at = CardLayout.firstFree(mOrder, 1, 2);
        List<CardLayout.Spec> o = CardLayout.copy(mOrder);
        o.add(new CardLayout.Spec(key, at[0], at[1], 1, 2));
        mOrder = o;
        syncViews();
        requestLayout();
        if (mListener != null) {
            mListener.onLayoutChanged();
        }
    }

    private void moveBy(String key, int dc, int dr) {
        CardLayout.Spec me = CardLayout.find(mOrder, key);
        if (me == null) {
            return;
        }
        int c = Math.max(0, Math.min(CardLayout.COLS - me.colSpan, me.col + dc));
        int r = Math.max(0, me.row + dr);
        if (c == me.col && r == me.row) {
            return;
        }
        snapshot();
        mOrder = CardLayout.place(mOrder, key, c, r, me.colSpan, me.rowSpan,
                new int[] {me.col, me.row});
        afterAccessibilityChange(key);
    }

    private void resizeBy(String key, int step) {
        CardLayout.Spec me = CardLayout.find(mOrder, key);
        if (me == null) {
            return;
        }
        int index = 0;
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i][0] == me.colSpan && SIZES[i][1] == me.rowSpan) {
                index = i;
            }
        }
        int next = Math.max(0, Math.min(SIZES.length - 1, index + step));
        if (next == index) {
            return;
        }
        int c = SIZES[next][0];
        int r = SIZES[next][1];
        snapshot();
        mOrder = CardLayout.place(mOrder, key, c == 2 ? 0 : me.col, me.row, c, r, null);
        afterAccessibilityChange(key);
    }

    private void afterAccessibilityChange(String key) {
        for (CardLayout.Spec s : mOrder) {
            rebind(s.key);
        }
        requestLayout();
        FreeFlowCardView v = mViews.get(key);
        if (v != null) {
            CardLayout.Spec s = CardLayout.find(mOrder, key);
            if (s != null) {
                v.announceForAccessibility(FreeFlowCardView.sizeName(getContext(),
                        s.colSpan, s.rowSpan));
            }
        }
        if (mListener != null) {
            mListener.onLayoutChanged();
        }
    }

    private final Map<String, int[]> mA11yIds = new HashMap<>();

    private void updateAccessibility(FreeFlowCardView v) {
        int[] old = mA11yIds.remove(v.key);
        if (old != null) {
            for (int id : old) {
                ViewCompat.removeAccessibilityAction(v, id);
            }
        }
        final String key = v.key;
        ViewCompat.replaceAccessibilityAction(v,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK, null,
                mEditing ? null : (view, args) -> {
                    if (mListener != null) {
                        mListener.onCardClick(key);
                    }
                    return true;
                });
        ViewCompat.replaceAccessibilityAction(v,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                getContext().getString(R.string.ff_edit_shortcuts),
                mEditing ? null : (view, args) -> {
                    if (mListener != null) {
                        mListener.onRequestEdit();
                    }
                    return true;
                });
        if (!mEditing) {
            return;
        }
        Context c = getContext();
        int[] ids = {
                ViewCompat.addAccessibilityAction(v, c.getString(R.string.ff_move_up),
                        (view, args) -> {
                            moveBy(key, 0, -1);
                            return true;
                        }),
                ViewCompat.addAccessibilityAction(v, c.getString(R.string.ff_move_down),
                        (view, args) -> {
                            moveBy(key, 0, 1);
                            return true;
                        }),
                ViewCompat.addAccessibilityAction(v, c.getString(R.string.ff_move_left),
                        (view, args) -> {
                            moveBy(key, -1, 0);
                            return true;
                        }),
                ViewCompat.addAccessibilityAction(v, c.getString(R.string.ff_move_right),
                        (view, args) -> {
                            moveBy(key, 1, 0);
                            return true;
                        }),
                ViewCompat.addAccessibilityAction(v, c.getString(R.string.ff_make_larger),
                        (view, args) -> {
                            resizeBy(key, 1);
                            return true;
                        }),
                ViewCompat.addAccessibilityAction(v, c.getString(R.string.ff_make_smaller),
                        (view, args) -> {
                            resizeBy(key, -1);
                            return true;
                        })};
        mA11yIds.put(key, ids);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mHold);
        removeCallbacks(mAutoScrollTick);
    }
}
