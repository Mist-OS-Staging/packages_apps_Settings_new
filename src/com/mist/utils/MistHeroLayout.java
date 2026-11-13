/*
 * SPDX-FileCopyrightText: 2026 Project Mist OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class MistHeroLayout extends FrameLayout {

    private float mHeightRatio = 0.58f;

    public MistHeroLayout(Context context) {
        super(context);
    }

    public MistHeroLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MistHeroLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setHeightRatio(float ratio) {
        if (ratio > 0 && ratio != mHeightRatio) {
            mHeightRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = Math.round(width * mHeightRatio);
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
