/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CardLayout {

    static final int COLS = 2;
    static final int MAX_CARDS = 8;
    static final int MAX_ROW_SPAN = 4;
    static final int MAX_ROW = 60;

    static final String KEY_ACCOUNT = "google_account";

    private static final String PREFS = "mist_home";
    private static final String PREF_CARDS = "mist_home_cards";

    static final class Spec {
        final String key;
        int col;
        int row;
        int colSpan;
        int rowSpan;

        Spec(String key, int col, int row, int colSpan, int rowSpan) {
            this.key = key;
            this.col = col;
            this.row = row;
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }

        Spec copy() {
            return new Spec(key, col, row, colSpan, rowSpan);
        }

        int bottom() {
            return row + rowSpan;
        }
    }

    private CardLayout() {
    }

    static List<Spec> defaults(boolean withAccount) {
        List<Spec> o = new ArrayList<>();
        int shift = withAccount ? 0 : 2;
        if (withAccount) {
            o.add(new Spec(KEY_ACCOUNT, 0, 0, 2, 2));
        }
        o.add(new Spec("network", 0, 2 - shift, 1, 2));
        o.add(new Spec("connected_devices", 1, 2 - shift, 1, 2));
        o.add(new Spec("modes", 0, 4 - shift, 1, 1));
        o.add(new Spec("display", 1, 4 - shift, 1, 1));
        o.add(new Spec("battery", 0, 5 - shift, 1, 2));
        o.add(new Spec("storage", 1, 5 - shift, 1, 2));
        return o;
    }

    static List<Spec> copy(List<Spec> o) {
        List<Spec> out = new ArrayList<>(o.size());
        for (Spec s : o) {
            out.add(s.copy());
        }
        return out;
    }

    static Spec find(List<Spec> o, String key) {
        for (Spec s : o) {
            if (s.key.equals(key)) {
                return s;
            }
        }
        return null;
    }

    static boolean hit(Spec a, int col, int row, int c, int r) {
        return a.col < col + c && col < a.col + a.colSpan
                && a.row < row + r && row < a.row + a.rowSpan;
    }

    static boolean freeAt(List<Spec> o, int col, int row, int c, int r, String skip) {
        if (col < 0 || row < 0 || col + c > COLS) {
            return false;
        }
        for (Spec s : o) {
            if (s.key.equals(skip)) {
                continue;
            }
            if (hit(s, col, row, c, r)) {
                return false;
            }
        }
        return true;
    }

    static int[] nearestFree(List<Spec> o, Spec card, String skip) {
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int row = 0; row < MAX_ROW; row++) {
            for (int col = 0; col + card.colSpan <= COLS; col++) {
                if (!freeAt(o, col, row, card.colSpan, card.rowSpan, skip)) {
                    continue;
                }
                float d = Math.abs(row - card.row) * 2 + Math.abs(col - card.col)
                        + (row < card.row ? 0.5f : 0f);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new int[] {col, row};
                }
            }
            if (best != null && row > card.row + bestDistance) {
                break;
            }
        }
        return best != null ? best : new int[] {0, MAX_ROW};
    }

    static int[] firstFree(List<Spec> o, int c, int r) {
        for (int row = 0; row < MAX_ROW; row++) {
            for (int col = 0; col + c <= COLS; col++) {
                if (freeAt(o, col, row, c, r, null)) {
                    return new int[] {col, row};
                }
            }
        }
        return new int[] {0, 0};
    }

    static List<Spec> place(List<Spec> order, String key, int col, int row, int c, int r,
            int[] oldPos) {
        List<Spec> o = copy(order);
        Spec me = find(o, key);
        if (me == null) {
            return o;
        }
        me.col = col;
        me.row = row;
        me.colSpan = c;
        me.rowSpan = r;
        List<Spec> hits = new ArrayList<>();
        for (Spec s : o) {
            if (s != me && hit(s, col, row, c, r)) {
                hits.add(s);
            }
        }
        if (oldPos != null && hits.size() == 1) {
            Spec h = hits.get(0);
            Spec target = new Spec(h.key, oldPos[0], oldPos[1], h.colSpan, h.rowSpan);
            if (freeAt(o, oldPos[0], oldPos[1], h.colSpan, h.rowSpan, h.key)
                    && !hit(target, col, row, c, r)) {
                h.col = oldPos[0];
                h.row = oldPos[1];
                return o;
            }
        }
        for (Spec h : hits) {
            int[] p = nearestFree(o, h, h.key);
            h.col = p[0];
            h.row = p[1];
        }
        return o;
    }

    static int bottom(List<Spec> o) {
        int b = 0;
        for (Spec s : o) {
            b = Math.max(b, s.bottom());
        }
        return b;
    }

    static List<Spec> load(Context context, boolean withAccount) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Spec> parsed = parse(sp.getString(PREF_CARDS, null));
        return parsed != null ? parsed : defaults(withAccount);
    }

    static void save(Context context, List<Spec> o) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_CARDS, toJson(o)).apply();
    }

    static String toJson(List<Spec> o) {
        JSONArray array = new JSONArray();
        try {
            for (Spec s : o) {
                JSONObject j = new JSONObject();
                j.put("key", s.key);
                j.put("col", s.col);
                j.put("row", s.row);
                j.put("colSpan", s.colSpan);
                j.put("rowSpan", s.rowSpan);
                array.put(j);
            }
        } catch (JSONException e) {
            return "[]";
        }
        return array.toString();
    }

    static List<Spec> parse(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(json);
            List<Spec> o = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < array.length() && o.size() < MAX_CARDS; i++) {
                JSONObject j = array.getJSONObject(i);
                String key = j.getString("key");
                int col = j.getInt("col");
                int row = j.getInt("row");
                int c = j.getInt("colSpan");
                int r = j.getInt("rowSpan");
                if (TextUtils.isEmpty(key) || !seen.add(key)
                        || c < 1 || c > COLS || r < 1 || r > MAX_ROW_SPAN
                        || col < 0 || col + c > COLS || row < 0 || row >= MAX_ROW
                        || !freeAt(o, col, row, c, r, null)) {
                    return null;
                }
                o.add(new Spec(key, col, row, c, r));
            }
            return o;
        } catch (JSONException e) {
            return null;
        }
    }
}
