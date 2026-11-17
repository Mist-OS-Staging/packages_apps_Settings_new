/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class FreeFlowSheetBehavior<V extends View> extends BottomSheetBehavior<V> {

    public FreeFlowSheetBehavior() {
        super();
    }

    public FreeFlowSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child,
            @NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            View list = findList(child);
            boolean inList = list != null && list.isShown()
                    && parent.isPointInChildBounds(list, (int) event.getX(), (int) event.getY());
            setDraggable(!inList || !list.canScrollVertically(-1));
        }
        return super.onInterceptTouchEvent(parent, child, event);
    }

    static View findList(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (view.isNestedScrollingEnabled() && view != null
                && (view instanceof androidx.recyclerview.widget.RecyclerView
                        || view instanceof androidx.core.widget.NestedScrollView)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findList(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
