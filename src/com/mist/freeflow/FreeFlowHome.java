/*
 * SPDX-FileCopyrightText: 2026 MistOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mist.freeflow;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settings.homepage.TopLevelSettings;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.SettingsThemeHelper;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FreeFlowHome implements LifecycleEventObserver, HeroCarouselView.Listener,
        FreeFlowGridView.Listener {

    private static final String KEY_ABOUT = "top_level_about_device";
    private static final String KEY_ACCOUNT_CATEGORY = "top_level_account_category";
    private static final String PREFIX = "top_level_";
    private static final String KEY_BATTERY = "battery";
    private static final String KEY_STORAGE = "storage";
    private static final String PREFS_HOME = "mist_home";
    private static final String PREF_LAYOUT_VERSION = "mist_home_layout_version";
    private static final String PREF_ACCOUNT_REMOVED = "mist_home_account_removed";
    private static final int LAYOUT_VERSION = 2;

    private final SettingsHomepageActivity mActivity;
    private final Palette mPalette;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Set<String> mHiddenByUs = new HashSet<>();
    private final Set<PreferenceGroup> mHiddenGroups = new HashSet<>();
    private final CardData mBattery = new CardData();
    private final CardData mStorage = new CardData();
    private final Runnable mRefresh = this::refresh;

    private NestedScrollView mScroll;
    private View mHeader;
    private HeroCarouselView mHero;
    private View[] mDots;
    private FreeFlowGridView mGrid;
    private View mTopBar;
    private View mSearchRow;
    private View mSpacer;
    private View mStatusBand;
    private View mSheetDivider;
    private View mScrim;
    private SheetFrame mSheet;
    private ImageView mChevron;
    private View mSheetToggle;
    private BottomSheetBehavior<View> mBehavior;
    private TopLevelSettings mFragment;
    private RecyclerView.Adapter<?> mObservedAdapter;
    private BottomSheetDialog mAddDialog;
    private boolean mEditing;
    private boolean mHasAccountProvider;
    private boolean mReceiverRegistered;
    private int mTopInset;
    private int mBottomInset;
    private float mDividerTarget;

    private final OnBackPressedCallback mBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (mEditing) {
                exitEdit();
            } else if (mBehavior != null
                    && mBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
                mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            updateBack();
        }
    };

    private final RecyclerView.AdapterDataObserver mAdapterObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    scheduleRefresh();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    scheduleRefresh();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                    scheduleRefresh();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    scheduleRefresh();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    scheduleRefresh();
                }
            };

    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(@NonNull FragmentManager fm,
                        @NonNull Fragment f, @NonNull View v, Bundle savedInstanceState) {
                    if (f == mFragment) {
                        applyListInsets();
                        scheduleRefresh();
                    }
                }

                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    if (f == mFragment) {
                        observeAdapter();
                        scheduleRefresh();
                        mHandler.postDelayed(mRefresh, 800);
                    }
                }

                @Override
                public void onFragmentViewDestroyed(@NonNull FragmentManager fm,
                        @NonNull Fragment f) {
                    if (f == mFragment) {
                        unobserveAdapter();
                    }
                }
            };

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readBattery(intent);
            scheduleRefresh();
        }
    };

    public FreeFlowHome(SettingsHomepageActivity activity) {
        mActivity = activity;
        mPalette = new Palette(activity);
    }

    public void init(boolean animate) {
        mHasAccountProvider = isInstalled("com.google.android.gms");
        mScroll = mActivity.findViewById(R.id.ff_scroll);
        mHeader = mActivity.findViewById(R.id.app_bar_container);
        mHero = mActivity.findViewById(R.id.ff_hero);
        mDots = new View[] {mActivity.findViewById(R.id.ff_dot_0),
                mActivity.findViewById(R.id.ff_dot_1)};
        mGrid = mActivity.findViewById(R.id.ff_grid);
        mTopBar = mActivity.findViewById(R.id.ff_top_bar);
        mSearchRow = mActivity.findViewById(R.id.ff_search_row);
        mSpacer = mActivity.findViewById(R.id.ff_search_spacer);
        mStatusBand = mActivity.findViewById(R.id.ff_status_band);
        mSheetDivider = mActivity.findViewById(R.id.ff_sheet_divider);
        mScrim = mActivity.findViewById(R.id.ff_scrim);
        mSheet = mActivity.findViewById(R.id.ff_sheet);
        mChevron = mActivity.findViewById(R.id.ff_sheet_chevron);
        mSheetToggle = mActivity.findViewById(R.id.ff_sheet_toggle);

        TextView title = mActivity.findViewById(R.id.homepage_title);
        if (title != null) {
            title.setContentDescription(mActivity.getString(R.string.settings_label));
        }

        setupMistButton(mActivity.findViewById(R.id.btnMistVersion));
        mHero.setListener(this);
        updateDots(false);

        mGrid.setListener(this);
        mGrid.setOrder(loadLayout(), false);

        mActivity.findViewById(R.id.ff_edit_reset).setOnClickListener(v -> resetLayout());
        mActivity.findViewById(R.id.ff_edit_done).setOnClickListener(v -> exitEdit());
        mScroll.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> updatePin());
        mSearchRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            ViewGroup.LayoutParams lp = mSpacer.getLayoutParams();
            if (h > 0 && lp.height != h) {
                lp.height = h;
                mSpacer.setLayoutParams(lp);
            }
            updatePin();
        });
        mHeader.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updatePin());

        setupSheet();
        attachFragment();

        mActivity.getOnBackPressedDispatcher().addCallback(mActivity, mBack);
        mActivity.getLifecycle().addObserver(this);
        updateTopBarMode();
        if (animate && FreeFlowStyle.motion()) {
            animateIn();
        }
    }

    private android.content.SharedPreferences homePrefs() {
        return mActivity.getSharedPreferences(PREFS_HOME, Context.MODE_PRIVATE);
    }

    private List<CardLayout.Spec> loadLayout() {
        List<CardLayout.Spec> order = CardLayout.load(mActivity, mHasAccountProvider);
        android.content.SharedPreferences sp = homePrefs();
        if (sp.getInt(PREF_LAYOUT_VERSION, 0) < LAYOUT_VERSION) {
            if (mHasAccountProvider) {
                order = CardLayout.withAccountOnTop(order);
            }
            CardLayout.save(mActivity, order);
            sp.edit().putInt(PREF_LAYOUT_VERSION, LAYOUT_VERSION)
                    .putBoolean(PREF_ACCOUNT_REMOVED, false).apply();
        } else if (mHasAccountProvider && !sp.getBoolean(PREF_ACCOUNT_REMOVED, false)
                && CardLayout.find(order, CardLayout.KEY_ACCOUNT) == null) {
            order = CardLayout.withAccountOnTop(order);
            CardLayout.save(mActivity, order);
        }
        return order;
    }

    private void animateIn() {
        View[] views = {mActivity.findViewById(R.id.ff_hero), mGrid};
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            v.setAlpha(0f);
            v.setTranslationY(FreeFlowStyle.dp(mActivity, 28));
            v.setScaleX(0.94f);
            v.setScaleY(0.94f);
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(60 + i * 90L).setDuration(650)
                    .setInterpolator(FreeFlowStyle.SPRING).start();
        }
        View bar = mActivity.findViewById(R.id.search_action_bar);
        View search = bar.getParent() instanceof View ? (View) bar.getParent() : bar;
        View mist = mActivity.findViewById(R.id.btnMistVersion);
        search.setAlpha(0f);
        search.setTranslationY(FreeFlowStyle.dp(mActivity, 12));
        search.animate().alpha(1f).translationY(0f).setStartDelay(20).setDuration(500)
                .setInterpolator(FreeFlowStyle.SPRING).start();
        mist.setAlpha(0f);
        mist.setScaleX(0.5f);
        mist.setScaleY(0.5f);
        mist.setRotation(-45f);
        mist.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(80)
                .setDuration(700).setInterpolator(FreeFlowStyle.SPRING).start();
        mSheet.setTranslationY(FreeFlowStyle.dp(mActivity, 120));
        mSheet.animate().translationY(0f).setStartDelay(200).setDuration(600)
                .setInterpolator(FreeFlowStyle.SHEET).start();
    }

    public void onInsets(int top, int bottom) {
        mTopInset = top;
        mBottomInset = bottom;
        if (mTopBar != null) {
            mTopBar.setPadding(0, top, 0, 0);
        }
        if (mStatusBand != null) {
            ViewGroup.LayoutParams lp = mStatusBand.getLayoutParams();
            if (lp.height != top) {
                lp.height = top;
                mStatusBand.setLayoutParams(lp);
            }
        }
        if (mScroll != null) {
            mScroll.setPadding(0, 0, 0, bottom);
        }
        if (mSheet != null) {
            mSheet.setTopOffset(FreeFlowStyle.dpi(mActivity, 52) + top);
        }
        if (mBehavior != null) {
            mBehavior.setPeekHeight(FreeFlowStyle.dpi(mActivity, 92) + bottom);
        }
        applyListInsets();
    }

    private void applyListInsets() {
        RecyclerView list = mFragment != null ? mFragment.getListView() : null;
        if (list == null) {
            return;
        }
        int bottom = mBottomInset + FreeFlowStyle.dpi(mActivity, 24);
        list.setClipToPadding(false);
        if (list.getPaddingBottom() != bottom) {
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(),
                    list.getPaddingRight(), bottom);
        }
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        View container = mActivity.findViewById(R.id.ff_all_settings_container);
        if (container != null && !container.getClipToOutline()) {
            container.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
            container.setClipToOutline(true);
        }
        if (list.getTag(R.id.ff_sheet_divider) == null) {
            list.setTag(R.id.ff_sheet_divider, Boolean.TRUE);
            list.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    updateDivider(rv);
                }
            });
        }
        updateDivider(list);
    }

    private void updateDivider(RecyclerView list) {
        if (mSheetDivider == null) {
            return;
        }
        float target = list.canScrollVertically(-1) ? 1f : 0f;
        if (mDividerTarget != target) {
            mDividerTarget = target;
            mSheetDivider.animate().alpha(target)
                    .setDuration(FreeFlowStyle.duration(120)).start();
        }
    }

    private boolean isInstalled(String pkg) {
        try {
            mActivity.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void setupMistButton(View logo) {
        if (logo == null) {
            return;
        }
        logo.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.85f).scaleY(0.85f).rotation(-6f)
                            .setDuration(FreeFlowStyle.duration(400))
                            .setInterpolator(FreeFlowStyle.SPRING).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).rotation(0f)
                            .setDuration(FreeFlowStyle.duration(400))
                            .setInterpolator(FreeFlowStyle.SPRING).start();
                    break;
            }
            return false;
        });
    }

    private void setupSheet() {
        mBehavior = BottomSheetBehavior.from(mSheet);
        mBehavior.setPeekHeight(FreeFlowStyle.dpi(mActivity, 92) + mBottomInset);
        mBehavior.setHideable(false);
        mBehavior.setFitToContents(true);
        mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        mBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View sheet, int state) {
                if (state == BottomSheetBehavior.STATE_COLLAPSED && !mEditing) {
                    mBehavior.setHideable(false);
                }
                mSheetToggle.setContentDescription(mActivity.getString(
                        state == BottomSheetBehavior.STATE_EXPANDED
                                ? R.string.ff_close_all : R.string.ff_open_all));
                if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                    View list = FreeFlowSheetBehavior.findList(mSheet);
                    if (list instanceof RecyclerView) {
                        ((RecyclerView) list).scrollToPosition(0);
                    }
                }
                updateBack();
            }

            @Override
            public void onSlide(@NonNull View sheet, float offset) {
                applySheetOffset(offset);
            }
        });
        mSheetToggle.setOnClickListener(v -> {
            boolean open = mBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
            mBehavior.setState(open ? BottomSheetBehavior.STATE_COLLAPSED
                    : BottomSheetBehavior.STATE_EXPANDED);
        });
        mScrim.setOnClickListener(v -> mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
        applySheetOffset(0f);
    }

    private void applySheetOffset(float offset) {
        float o = FreeFlowStyle.clamp(offset, 0f, 1f);
        mScrim.setAlpha(o);
        mScrim.setVisibility(o > 0.01f ? View.VISIBLE : View.GONE);
        mChevron.setRotation(180f * o);
    }

    private void attachFragment() {
        FragmentManager fm = mActivity.getSupportFragmentManager();
        fm.registerFragmentLifecycleCallbacks(mFragmentCallbacks, false);
        Fragment f = fm.findFragmentById(R.id.ff_all_settings_container);
        if (f instanceof TopLevelSettings) {
            mFragment = (TopLevelSettings) f;
        } else {
            mFragment = new TopLevelSettings();
            fm.beginTransaction().replace(R.id.ff_all_settings_container, mFragment).commit();
        }
    }

    private void observeAdapter() {
        RecyclerView list = mFragment != null ? mFragment.getListView() : null;
        RecyclerView.Adapter<?> adapter = list != null ? list.getAdapter() : null;
        if (adapter == mObservedAdapter) {
            return;
        }
        unobserveAdapter();
        if (adapter != null) {
            adapter.registerAdapterDataObserver(mAdapterObserver);
            mObservedAdapter = adapter;
        }
    }

    private void unobserveAdapter() {
        if (mObservedAdapter != null) {
            try {
                mObservedAdapter.unregisterAdapterDataObserver(mAdapterObserver);
            } catch (IllegalStateException ignored) {
            }
            mObservedAdapter = null;
        }
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        switch (event) {
            case ON_RESUME:
                Intent sticky = mActivity.registerReceiver(mBatteryReceiver,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                mReceiverRegistered = true;
                if (sticky != null) {
                    readBattery(sticky);
                }
                loadStorage();
                scheduleRefresh();
                break;
            case ON_PAUSE:
                if (mReceiverRegistered) {
                    mActivity.unregisterReceiver(mBatteryReceiver);
                    mReceiverRegistered = false;
                }
                break;
            case ON_DESTROY:
                mHandler.removeCallbacksAndMessages(null);
                unobserveAdapter();
                mActivity.getSupportFragmentManager()
                        .unregisterFragmentLifecycleCallbacks(mFragmentCallbacks);
                if (mAddDialog != null) {
                    mAddDialog.dismiss();
                }
                break;
            default:
                break;
        }
    }

    private void readBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        mBattery.level = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        mBattery.temperature = temp == Integer.MIN_VALUE ? null
                : mActivity.getString(R.string.ff_temperature, Math.round(temp / 10f));
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        int res;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                res = R.string.ff_charging;
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                res = R.string.ff_full;
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                res = R.string.ff_not_charging;
                break;
            default:
                res = R.string.ff_discharging;
                break;
        }
        mBattery.status = mActivity.getString(res);
    }

    private void loadStorage() {
        final Context app = mActivity.getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            StorageManager sm = app.getSystemService(StorageManager.class);
            if (sm == null) {
                return;
            }
            PrivateStorageInfo info = PrivateStorageInfo.getPrivateStorageInfo(
                    new StorageManagerVolumeProvider(sm));
            final long total = info.totalBytes;
            final long free = info.freeBytes;
            ThreadUtils.postOnMainThread(() -> {
                if (mActivity.isDestroyed()) {
                    return;
                }
                long used = Math.max(0, total - free);
                mStorage.used = Formatter.formatShortFileSize(mActivity, used);
                mStorage.total = Formatter.formatShortFileSize(mActivity, total);
                mStorage.free = Formatter.formatShortFileSize(mActivity, free);
                mStorage.usedFraction = total > 0 ? (float) used / total : 0f;
                scheduleRefresh();
            });
        });
    }

    private void scheduleRefresh() {
        mHandler.removeCallbacks(mRefresh);
        mHandler.post(mRefresh);
    }

    private PreferenceScreen screen() {
        return mFragment != null && mFragment.isAdded() && mFragment.getPreferenceManager() != null
                ? mFragment.getPreferenceScreen() : null;
    }

    private interface PrefVisitor {
        void visit(Preference pref);
    }

    private static void forEach(PreferenceGroup group, PrefVisitor visitor) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                forEach((PreferenceGroup) p, visitor);
            } else {
                visitor.visit(p);
            }
        }
    }

    private boolean available(Preference p) {
        if (p == null || p.getKey() == null) {
            return false;
        }
        if (!p.isVisible() && !mHiddenByUs.contains(p.getKey())) {
            return false;
        }
        PreferenceGroup parent = p.getParent();
        return parent == null || parent.isVisible() || mHiddenGroups.contains(parent);
    }

    private Preference accountPref(PreferenceScreen screen) {
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference p = screen.getPreference(i);
            if (p instanceof PreferenceGroup && KEY_ACCOUNT_CATEGORY.equals(p.getKey())) {
                PreferenceGroup g = (PreferenceGroup) p;
                for (int k = 0; k < g.getPreferenceCount(); k++) {
                    Preference child = g.getPreference(k);
                    if (child.getKey() != null
                            && (child.isVisible() || mHiddenByUs.contains(child.getKey()))) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    private Preference resolve(PreferenceScreen screen, String cardKey) {
        if (CardLayout.KEY_ACCOUNT.equals(cardKey)) {
            return accountPref(screen);
        }
        String prefKey;
        if ("modes".equals(cardKey)) {
            prefKey = PREFIX + "priority_modes";
        } else if (cardKey.startsWith(PREFIX)) {
            prefKey = cardKey;
        } else {
            prefKey = PREFIX + cardKey;
        }
        Preference p = screen.findPreference(prefKey);
        if (p == null) {
            p = screen.findPreference(cardKey);
        }
        return p;
    }

    private String cardKeyFor(PreferenceScreen screen, Preference pref) {
        if (pref == accountPref(screen)) {
            return CardLayout.KEY_ACCOUNT;
        }
        String key = pref.getKey();
        if ((PREFIX + "priority_modes").equals(key)) {
            return "modes";
        }
        return key.startsWith(PREFIX) ? key.substring(PREFIX.length()) : key;
    }

    private Set<String> pinnedPrefKeys(PreferenceScreen screen) {
        Set<String> pinned = new HashSet<>();
        for (CardLayout.Spec s : mGrid.getOrder()) {
            Preference p = resolve(screen, s.key);
            if (p != null && p.getKey() != null) {
                pinned.add(p.getKey());
            }
        }
        return pinned;
    }

    private void applyVisibility(PreferenceScreen screen) {
        final Set<String> pinned = pinnedPrefKeys(screen);
        forEach(screen, pref -> {
            String key = pref.getKey();
            if (key == null) {
                return;
            }
            boolean hide = KEY_ABOUT.equals(key) || pinned.contains(key);
            if (hide) {
                if (pref.isVisible()) {
                    pref.setVisible(false);
                    mHiddenByUs.add(key);
                }
            } else if (mHiddenByUs.remove(key)) {
                pref.setVisible(true);
            }
        });
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference p = screen.getPreference(i);
            if (!(p instanceof PreferenceGroup)) {
                continue;
            }
            PreferenceGroup group = (PreferenceGroup) p;
            boolean ours = mHiddenGroups.contains(group);
            if (!group.isVisible() && !ours) {
                continue;
            }
            boolean empty = group.getPreferenceCount() > 0;
            for (int k = 0; k < group.getPreferenceCount() && empty; k++) {
                if (group.getPreference(k).isVisible()) {
                    empty = false;
                }
            }
            if (empty && group.isVisible()) {
                group.setVisible(false);
                mHiddenGroups.add(group);
            } else if (!empty && ours) {
                mHiddenGroups.remove(group);
                group.setVisible(true);
            }
        }
    }

    private void dedupeAccount(PreferenceScreen screen) {
        if (mEditing) {
            return;
        }
        Preference account = accountPref(screen);
        if (account == null) {
            return;
        }
        List<CardLayout.Spec> order = mGrid.getOrder();
        boolean hasAccount = CardLayout.find(order, CardLayout.KEY_ACCOUNT) != null;
        boolean changed = false;
        List<CardLayout.Spec> out = new ArrayList<>();
        for (CardLayout.Spec s : order) {
            if (!CardLayout.KEY_ACCOUNT.equals(s.key) && resolve(screen, s.key) == account) {
                changed = true;
                if (!hasAccount) {
                    hasAccount = true;
                    out.add(new CardLayout.Spec(CardLayout.KEY_ACCOUNT, s.col, s.row,
                            s.colSpan, s.rowSpan));
                }
                continue;
            }
            out.add(s);
        }
        if (changed) {
            out = CardLayout.pack(out);
            mGrid.setOrder(out, false);
            CardLayout.save(mActivity, out);
        }
    }

    private void refresh() {
        PreferenceScreen screen = screen();
        if (screen == null) {
            return;
        }
        observeAdapter();
        dedupeAccount(screen);
        applyVisibility(screen);
        Map<String, CardData> map = new HashMap<>();
        for (CardLayout.Spec s : mGrid.getOrder()) {
            Preference p = resolve(screen, s.key);
            if (CardLayout.KEY_ACCOUNT.equals(s.key) ? p == null : !available(p)) {
                continue;
            }
            map.put(s.key, dataFor(s.key, p));
        }
        mGrid.setData(map);
    }

    private CardData dataFor(String cardKey, Preference p) {
        CardData d = new CardData();
        if (KEY_BATTERY.equals(cardKey)) {
            d.kind = CardData.BATTERY;
            d.level = mBattery.level;
            d.temperature = mBattery.temperature;
            d.status = mBattery.status;
        } else if (KEY_STORAGE.equals(cardKey)) {
            d.kind = CardData.STORAGE;
            d.used = mStorage.used;
            d.total = mStorage.total;
            d.free = mStorage.free;
            d.usedFraction = mStorage.usedFraction;
        } else if (CardLayout.KEY_ACCOUNT.equals(cardKey)) {
            d.kind = CardData.ACCOUNT;
        }
        d.title = p.getTitle();
        d.summary = p.getSummary();
        d.icon = p.getIcon();
        d.click = p::performClick;
        return d;
    }

    @Override
    public void onCardClick(String key) {
        PreferenceScreen screen = screen();
        Preference p = screen != null ? resolve(screen, key) : null;
        if (p != null) {
            p.performClick();
        }
    }

    @Override
    public void onRequestEdit() {
        if (mEditing) {
            return;
        }
        mEditing = true;
        mGrid.setOrder(CardLayout.pack(mGrid.shownOrder()), false);
        mGrid.setEditing(true);
        mBehavior.setHideable(true);
        mBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        updateTopBarMode();
        updateBack();
    }

    private void exitEdit() {
        if (!mEditing) {
            return;
        }
        mEditing = false;
        mGrid.setEditing(false);
        List<CardLayout.Spec> order = CardLayout.pack(mGrid.getOrder());
        mGrid.setOrder(order, false);
        CardLayout.save(mActivity, order);
        if (mHasAccountProvider) {
            homePrefs().edit().putBoolean(PREF_ACCOUNT_REMOVED,
                    CardLayout.find(order, CardLayout.KEY_ACCOUNT) == null).apply();
        }
        mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        if (mAddDialog != null) {
            mAddDialog.dismiss();
        }
        updateTopBarMode();
        updateBack();
        scheduleRefresh();
    }

    private void resetLayout() {
        homePrefs().edit().putBoolean(PREF_ACCOUNT_REMOVED, false).apply();
        mGrid.setOrder(CardLayout.defaults(mHasAccountProvider), true);
        scheduleRefresh();
    }

    @Override
    public void onLayoutChanged() {
        scheduleRefresh();
    }

    @Override
    public void onAddCard() {
        PreferenceScreen screen = screen();
        if (screen == null) {
            return;
        }
        final List<Preference> items = new ArrayList<>();
        final Set<String> pinned = pinnedPrefKeys(screen);
        forEach(screen, pref -> {
            if (available(pref) && !KEY_ABOUT.equals(pref.getKey())
                    && !pinned.contains(pref.getKey()) && pref.getTitle() != null) {
                items.add(pref);
            }
        });
        showAddDialog(screen, items);
    }

    private void showAddDialog(PreferenceScreen screen, List<Preference> items) {
        if (mAddDialog != null) {
            mAddDialog.dismiss();
        }
        Context c = mActivity;
        BottomSheetDialog dialog = new BottomSheetDialog(c, R.style.MistBottomSheetDialogTheme);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        float top = FreeFlowStyle.dp(c, 32);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mPalette.surfaceBright);
        bg.setCornerRadii(new float[] {top, top, top, top, 0, 0, 0, 0});
        root.setBackground(bg);
        int pad = FreeFlowStyle.dpi(c, 16);
        root.setPadding(pad, FreeFlowStyle.dpi(c, 10), pad, 0);

        View handle = new View(c);
        handle.setBackground(Ui.round(FreeFlowStyle.withAlpha(mPalette.outline, 0.6f),
                FreeFlowStyle.dp(c, 3)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                FreeFlowStyle.dpi(c, 36), FreeFlowStyle.dpi(c, 5));
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(handle, hlp);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(FreeFlowStyle.dpi(c, 6), FreeFlowStyle.dpi(c, 12),
                FreeFlowStyle.dpi(c, 6), FreeFlowStyle.dpi(c, 12));
        TextView title = Ui.line(c, 22, 700, mPalette.onSurface);
        Ui.width(title, 112);
        title.setText(R.string.ff_add_card_title);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = new ImageView(c);
        close.setImageResource(R.drawable.ff_ic_close);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(mPalette.onSurface));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackground(Ui.oval(mPalette.surfaceContainerHigh));
        close.setContentDescription(c.getString(R.string.ff_close));
        close.setOnClickListener(v -> dialog.dismiss());
        int closeSize = FreeFlowStyle.dpi(c, 48);
        header.addView(close, new LinearLayout.LayoutParams(closeSize, closeSize));
        root.addView(header);

        NestedScrollView scroll = new NestedScrollView(c);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, FreeFlowStyle.dpi(c, 28));
        boolean expressive = SettingsThemeHelper.isExpressiveTheme(c);
        for (int i = 0; i < items.size(); i++) {
            final Preference pref = items.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(FreeFlowStyle.dpi(c, 64));
            row.setPadding(pad, FreeFlowStyle.dpi(c, 12), pad, FreeFlowStyle.dpi(c, 12));
            GradientDrawable rowBg = groupShape(c, i, items.size(), mPalette.surfaceContainerLow);
            GradientDrawable mask = groupShape(c, i, items.size(), 0xffffffff);
            row.setBackground(rowBg);
            row.setForeground(Ui.ripple(FreeFlowStyle.withAlpha(mPalette.onSurface, 0.12f),
                    mask));
            int iconSize = FreeFlowStyle.dpi(c, 40);
            row.addView(Ui.circleIcon(c, pref.getIcon(), iconSize, expressive, mPalette.primary,
                    mPalette.onPrimary), new LinearLayout.LayoutParams(iconSize, iconSize));
            TextView name = Ui.line(c, 16, 400, mPalette.onSurface);
            name.setText(pref.getTitle());
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.setMarginStart(pad);
            row.addView(name, nlp);
            ImageView plus = new ImageView(c);
            plus.setImageResource(R.drawable.ff_ic_add);
            plus.setImageTintList(android.content.res.ColorStateList.valueOf(mPalette.primary));
            plus.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(plus);
            row.setOnClickListener(v -> {
                mGrid.addCard(cardKeyFor(screen, pref));
                dialog.dismiss();
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rlp.topMargin = FreeFlowStyle.dpi(c, 3);
            }
            list.addView(row, rlp);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (mAddDialog == dialog) {
                mAddDialog = null;
            }
        });
        mAddDialog = dialog;
        dialog.show();
    }

    static GradientDrawable groupShape(Context c, int index, int count, int color) {
        float outer = FreeFlowStyle.dp(c, 26);
        float inner = FreeFlowStyle.dp(c, 4);
        float t = index == 0 ? outer : inner;
        float b = index == count - 1 ? outer : inner;
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[] {t, t, t, t, b, b, b, b});
        return d;
    }

    @Override
    public void onOpen(int index) {
        if (mBehavior != null) {
            mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        if (index == 1) {
            openAbout();
            return;
        }
        if (FreeFlowStyle.canOpenMistify(mActivity)) {
            mActivity.startActivity(FreeFlowStyle.mistifyIntent(mActivity));
            mActivity.overridePendingTransition(R.anim.ff_slide_in_end, R.anim.ff_hold);
        }
    }

    private void openAbout() {
        PreferenceScreen screen = screen();
        Preference about = screen != null ? screen.findPreference(KEY_ABOUT) : null;
        if (about != null && about.isEnabled()) {
            about.performClick();
            return;
        }
        Intent intent = new Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS);
        intent.setPackage(mActivity.getPackageName());
        if (intent.resolveActivity(mActivity.getPackageManager()) != null) {
            mActivity.startActivity(intent);
        }
    }

    @Override
    public void onPageChanged(int page) {
        updateDots(true);
    }

    private void updateDots(boolean animate) {
        int page = mHero.getPage();
        for (int i = 0; i < mDots.length; i++) {
            final View dot = mDots[i];
            final boolean on = i == page;
            final int fromW = dot.getLayoutParams().width;
            final int toW = FreeFlowStyle.dpi(mActivity, on ? 24 : 8);
            final int off = FreeFlowStyle.withAlpha(mPalette.onSurface, 0.22f);
            final int fromC = on ? off : mPalette.primary;
            final int toC = on ? mPalette.primary : off;
            final GradientDrawable bg = Ui.round(fromC, FreeFlowStyle.dp(mActivity, 4));
            dot.setBackground(bg);
            if (!animate || !FreeFlowStyle.motion()) {
                bg.setColor(toC);
                dot.getLayoutParams().width = toW;
                dot.requestLayout();
                continue;
            }
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(500);
            a.setInterpolator(FreeFlowStyle.SPRING);
            final ArgbEvaluator argb = new ArgbEvaluator();
            a.addUpdateListener(an -> {
                float f = (float) an.getAnimatedValue();
                dot.getLayoutParams().width = Math.max(1,
                        Math.round(FreeFlowStyle.lerp(fromW, toW, f)));
                bg.setColor((int) argb.evaluate(FreeFlowStyle.clamp(f, 0f, 1f), fromC, toC));
                dot.requestLayout();
            });
            a.start();
        }
    }

    private void updateTopBarMode() {
        mTopBar.setBackgroundColor(mPalette.surfaceContainer);
        mTopBar.setVisibility(mEditing ? View.VISIBLE : View.INVISIBLE);
        mSearchRow.setVisibility(mEditing ? View.INVISIBLE : View.VISIBLE);
        updatePin();
    }

    private void updatePin() {
        if (mSearchRow == null || mHeader == null || mSpacer == null) {
            return;
        }
        int rowTop = mHeader.getTop() + mSpacer.getTop() - mScroll.getScrollY();
        int y = Math.max(mTopInset, rowTop);
        mSearchRow.setTranslationY(y);
        float p = mEditing ? 0f : FreeFlowStyle.clamp((mTopInset - rowTop)
                / FreeFlowStyle.dp(mActivity, 24), 0f, 1f);
        int color = (int) new ArgbEvaluator().evaluate(p, mPalette.background,
                mPalette.surfaceContainer);
        mSearchRow.setBackgroundColor(color);
        mStatusBand.setBackgroundColor(mEditing ? mPalette.surfaceContainer : color);
    }

    private void updateBack() {
        boolean expanded = mBehavior != null
                && mBehavior.getState() != BottomSheetBehavior.STATE_COLLAPSED
                && mBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN;
        mBack.setEnabled(mEditing || expanded);
    }
}
