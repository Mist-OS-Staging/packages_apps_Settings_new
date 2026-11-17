/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class SheetFrame extends LinearLayout {

    private int mTopOffset;

    public SheetFrame(Context context) {
        this(context, null);
    }

    public SheetFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTopOffset = FreeFlowStyle.dpi(context, 52);
    }

    void setTopOffset(int offset) {
        if (mTopOffset != offset) {
            mTopOffset = offset;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        int height = Math.max(0, parentHeight - mTopOffset);
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
