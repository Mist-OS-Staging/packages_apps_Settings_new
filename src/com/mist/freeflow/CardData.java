/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.graphics.drawable.Drawable;

final class CardData {

    static final int SIMPLE = 0;
    static final int BATTERY = 1;
    static final int STORAGE = 2;
    static final int ACCOUNT = 3;

    int kind = SIMPLE;
    CharSequence title;
    CharSequence summary;
    Drawable icon;
    Runnable click;

    int level = -1;
    String temperature;
    String status;

    String used;
    String total;
    String free;
    float usedFraction = -1f;
}
