/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.biometrics.fingerprint;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.fingerprint.FingerprintSensorProperties;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.RotationUtils;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams;

/**
 * View corresponding with udfps_enroll_view.xml
 */
public class UdfpsEnrollView extends FrameLayout implements UdfpsEnrollHelper.Listener {
    private static final String TAG = "UdfpsEnrollView";
    @NonNull
    private final UdfpsEnrollDrawable mFingerprintDrawable;
    @NonNull
    private final UdfpsEnrollProgressBarDrawable mFingerprintProgressDrawable;
    @NonNull
    private final Handler mHandler;

    @NonNull
    private ImageView mFingerprintProgressView;

    private int mProgressBarRadius;
    // Upper bound on mProgressBarRadius, so the ring can be fitted to the space around the sensor.
    private int mMaxProgressBarRadius = Integer.MAX_VALUE;
    // Parent position that the current margins were computed from.
    private final int[] mParentLocation = new int[2];
    private final int[] mTmpLocation = new int[2];

    // sensorRect may be bigger than the sensor. True sensor dimensions are defined in
    // overlayParams.sensorBounds
    private Rect mSensorRect;
    private UdfpsOverlayParams mOverlayParams;

    public UdfpsEnrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mFingerprintDrawable = new UdfpsEnrollDrawable(mContext, attrs);
        mFingerprintProgressDrawable = new UdfpsEnrollProgressBarDrawable(context, attrs);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onFinishInflate() {
        ImageView fingerprintView = findViewById(R.id.udfps_enroll_animation_fp_view);
        fingerprintView.setImageDrawable(mFingerprintDrawable);
        mFingerprintProgressView = findViewById(R.id.udfps_enroll_animation_fp_progress_view);
        mFingerprintProgressView.setImageDrawable(mFingerprintProgressDrawable);
    }

    // Implements UdfpsEnrollHelper.Listener
    @Override
    public void onEnrollmentProgress(int remaining, int totalSteps) {
        mHandler.post(() -> {
            mFingerprintProgressDrawable.onEnrollmentProgress(remaining, totalSteps);
            mFingerprintDrawable.onEnrollmentProgress(remaining, totalSteps);
        });
    }

    @Override
    public void onEnrollmentHelp(int remaining, int totalSteps) {
        mHandler.post(() -> mFingerprintProgressDrawable.onEnrollmentHelp(remaining, totalSteps));
    }

    @Override
    public void onAcquired(boolean animateIfLastStepGood) {
        mHandler.post(() -> {
            onFingerUp();
            if (animateIfLastStepGood) mFingerprintProgressDrawable.onLastStepAcquired();
        });
    }

    @Override
    public void onPointerDown(int sensorId) {
        onFingerDown();
    }

    @Override
    public void onPointerUp(int sensorId) {
        onFingerUp();
    }

    public UdfpsOverlayParams getOverlayParams() {
        return mOverlayParams;
    }

    /**
     * Set UdfpsOverlayParams
     */
    public void setOverlayParams(UdfpsOverlayParams params) {
        mOverlayParams = params;

        post(() -> {
            mProgressBarRadius =
                    (int) (mOverlayParams.getScaleFactor() * getContext().getResources().getInteger(
                            R.integer.config_udfpsEnrollProgressBar));
            mSensorRect = new Rect(mOverlayParams.getSensorBounds());

            onSensorRectUpdated();
        });
    }

    /**
     * Set UdfpsEnrollHelper
     */
    public void setEnrollHelper(UdfpsEnrollHelper enrollHelper) {
        mFingerprintDrawable.setEnrollHelper(enrollHelper);
        enrollHelper.setListener(this);
    }

    /**
     * Adjust progress bar radius only for decreasing.
     * @param decreasePadding the decrease padding
     */
    void setDecreasePadding(int decreasePadding) {
        mProgressBarRadius -= decreasePadding;
        onSensorRectUpdated();
    }

    /**
     * Caps how far the progress ring may extend beyond the sensor. The cap only ever goes down,
     * so repeated or out-of-order calls are harmless.
     */
    void capProgressBarRadius(int maxRadius) {
        if (maxRadius >= mMaxProgressBarRadius) {
            return;
        }
        mMaxProgressBarRadius = maxRadius;
        if (mSensorRect != null) {
            onSensorRectUpdated();
        }
    }

    /** Returns the current cap set by {@link #capProgressBarRadius}. */
    int getMaxProgressBarRadius() {
        return mMaxProgressBarRadius;
    }

    /** Returns how far the progress ring currently extends beyond the sensor. */
    int getProgressBarRadius() {
        return getPaddingX();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(mParentMovedListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(mParentMovedListener);
        super.onDetachedFromWindow();
    }

    // updateDimensions() places this view with margins measured from the parent's position on
    // screen, so redo it whenever the parent moves (e.g. the header or hint animation changes
    // size), or the ring and icon drift off the sensor.
    private final ViewTreeObserver.OnGlobalLayoutListener mParentMovedListener = () -> {
        if (mSensorRect == null || !(getParent() instanceof View)) {
            return;
        }
        ((View) getParent()).getLocationOnScreen(mTmpLocation);
        if (mTmpLocation[0] != mParentLocation[0] || mTmpLocation[1] != mParentLocation[1]) {
            post(() -> {
                if (mSensorRect != null) {
                    onSensorRectUpdated();
                }
            });
        }
    };

    private void onSensorRectUpdated() {
        updateDimensions();

        // Updates sensor rect in relation to the overlay view
        mSensorRect.set(getPaddingX(), getPaddingY(),
                (mOverlayParams.getSensorBounds().width() + getPaddingX()),
                (mOverlayParams.getSensorBounds().height() + getPaddingY()));
        mFingerprintDrawable.onSensorRectUpdated(new RectF(mSensorRect));
    }

    private void updateDimensions() {
        // Original sensorBounds assume portrait mode.
        final Rect rotatedBounds = new Rect(mOverlayParams.getSensorBounds());
        int rotation = mOverlayParams.getRotation();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            RotationUtils.rotateBounds(
                    rotatedBounds,
                    mOverlayParams.getNaturalDisplayWidth(),
                    mOverlayParams.getNaturalDisplayHeight(),
                    rotation
            );
        }

        // Use parent view's and rotatedBound's absolute coordinates to decide the margins of
        // UdfpsEnrollView, so that its center keeps consistent with sensor rect's.
        ViewGroup parentView = (ViewGroup) getParent();
        MarginLayoutParams marginLayoutParams = (MarginLayoutParams) getLayoutParams();
        FrameLayout.LayoutParams params = (LayoutParams) getLayoutParams();
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            final int[] coords = parentView.getLocationOnScreen();
            mParentLocation[0] = coords[0];
            mParentLocation[1] = coords[1];
            final int parentLeft = coords[0];
            final int parentTop = coords[1];
            final int parentRight = parentLeft + parentView.getWidth();
            params.gravity = Gravity.RIGHT | Gravity.TOP;
            final int rightMargin = parentRight - rotatedBounds.right - getPaddingX();
            final int topMargin = rotatedBounds.top - parentTop - getPaddingY();
            if (marginLayoutParams.rightMargin == rightMargin
                    && marginLayoutParams.topMargin == topMargin) {
                return;
            }
            marginLayoutParams.rightMargin = rightMargin;
            marginLayoutParams.topMargin = topMargin;
            setLayoutParams(params);
        } else {
            final int[] coords = parentView.getLocationOnScreen();
            mParentLocation[0] = coords[0];
            mParentLocation[1] = coords[1];
            final int parentLeft = coords[0];
            final int parentTop = coords[1];
            final int parentRight = parentLeft + parentView.getWidth();
            final int parentBottom = parentTop + parentView.getHeight();
            if (rotation == Surface.ROTATION_90) {
                params.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                marginLayoutParams.rightMargin = parentRight - rotatedBounds.right - getPaddingX();
                marginLayoutParams.bottomMargin =
                        parentBottom - rotatedBounds.bottom - getPaddingY();
            } else if (rotation == Surface.ROTATION_270) {
                params.gravity = Gravity.LEFT | Gravity.BOTTOM;
                marginLayoutParams.leftMargin = rotatedBounds.left - parentLeft - getPaddingX();
                marginLayoutParams.bottomMargin =
                        parentBottom - rotatedBounds.bottom - getPaddingY();
            }
        }

        params.height = rotatedBounds.height() + 2 * getPaddingX();
        params.width = rotatedBounds.width() + 2 * getPaddingY();
        setLayoutParams(params);


    }

    private void onFingerDown() {
        if (mOverlayParams.getSensorType() == FingerprintSensorProperties.TYPE_UDFPS_OPTICAL) {
            mFingerprintDrawable.setShouldSkipDraw(true);
        }
        mFingerprintDrawable.invalidateSelf();
    }

    private void onFingerUp() {
        mFingerprintDrawable.setShouldSkipDraw(false);
        mFingerprintDrawable.invalidateSelf();
    }

    private int getPaddingX() {
        return Math.min(mProgressBarRadius, mMaxProgressBarRadius);
    }

    private int getPaddingY() {
        return getPaddingX();
    }
}
