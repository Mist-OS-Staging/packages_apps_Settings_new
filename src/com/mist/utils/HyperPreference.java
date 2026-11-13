package com.mist.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.DeviceInfoUtils;
import com.android.settingslib.widget.LayoutPreference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import com.android.settings.R;

public class HyperPreference extends BasePreferenceController implements View.OnTouchListener {

    private static final String TAG = "HyperPreference";
    private static final String FILENAME_PROC_VERSION = "/proc/version";
    private static final String PREFS = "mist_about_phone";
    private static final String KEY_HEADER_BRAND = "header_brand";
    private static final int HOT_PERCENT = 85;
    private static final long DECOR_SPIN_MS = 45_000;

    private final Context context;
    private final AboutPhoneData phoneData;
    private final Handler longPressHandler = new Handler();
    private boolean fullKernelVersion = false;
    private boolean isLongPressDetected = false;
    private boolean brandHeader = false;
    private boolean entranceShown = false;

    public HyperPreference(Context context, String key) {
        super(context, key);
        this.context = context;
        this.phoneData = new AboutPhoneData(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        LayoutPreference mPreference = screen.findPreference("hyper_about_layout");
        if (mPreference != null) {
            onBindItems(mPreference.findViewById(R.id.hyper_mod));
        }
    }

    private static String prop(String key) {
        String value = SystemProperties.get(key);
        return value == null ? "" : value.trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }

    private static int color(Context ctx, int res) {
        return ctx.getColor(res);
    }

    private void onBindItems(View holder) {
        if (holder == null) return;
        final Context ctx = holder.getContext();

        ((TextView) holder.findViewById(R.id.resolution_about)).setText(phoneData.getDisplay());
        ((TextView) holder.findViewById(R.id.battery_about)).setText(phoneData.getBattery());
        ((TextView) holder.findViewById(R.id.soc_about)).setText(phoneData.getSoc());
        ((TextView) holder.findViewById(R.id.rear_camera_about)).setText(phoneData.getCamera());
        ((TextView) holder.findViewById(R.id.front_camera_about)).setText(phoneData.getFront());
        ((TextView) holder.findViewById(R.id.platform_about)).setText(phoneData.getPlatform());
        ((TextView) holder.findViewById(R.id.screen_about)).setText(phoneData.getScreen());

        final TextView kernel = holder.findViewById(R.id.kernel_version);
        kernel.setText(DeviceInfoUtils.getFormattedKernelVersion(context));
        ((TextView) holder.findViewById(R.id.build_number))
                .setText(SystemProperties.get("ro.build.display.id", "X.X.X"));
        ((TextView) holder.findViewById(R.id.build_date))
                .setText(SystemProperties.get("ro.build.date", "X.X.X"));

        final String model = firstNonEmpty(prop("ro.product.model"), Build.MODEL);
        final String codename = prop("ro.mist.device");
        final String name = firstNonEmpty(prop("ro.mist.device.name"),
                prop("ro.product.marketname"), model);
        final String sub;
        if (!TextUtils.isEmpty(model) && !TextUtils.isEmpty(codename)) {
            sub = ctx.getString(R.string.mist_about_device_sub, model, codename);
        } else {
            sub = firstNonEmpty(model, codename);
        }
        ((TextView) holder.findViewById(R.id.device_name)).setText(name);
        ((TextView) holder.findViewById(R.id.device_model)).setText(sub);
        ((TextView) holder.findViewById(R.id.identity_device_name)).setText(name);
        ((TextView) holder.findViewById(R.id.identity_device_sub)).setText(sub);

        final boolean official = "OFFICIAL".equalsIgnoreCase(
                SystemProperties.get("ro.mist.buildtype", "Unofficial"));
        final String versionBase = firstNonEmpty(prop("ro.mist.version.base"), "X.X");
        final String mistCodename = prop("ro.mist.codename");

        ((TextView) holder.findViewById(R.id.mist_version)).setText(versionBase);
        final TextView codenameView = holder.findViewById(R.id.mist_codename);
        codenameView.setText(mistCodename);
        codenameView.setVisibility(TextUtils.isEmpty(mistCodename) ? View.GONE : View.VISIBLE);
        ((TextView) holder.findViewById(R.id.brand_version)).setText(
                TextUtils.isEmpty(mistCodename) ? versionBase
                        : ctx.getString(R.string.mist_about_version_chip, versionBase, mistCodename));

        bindBuildTypeChip(holder.findViewById(R.id.mist_build_type), official);
        bindBuildTypeChip(holder.findViewById(R.id.brand_build_type), official);

        final String maintainerName = firstNonEmpty(prop("ro.mistos.maintainer"), "Unknown");
        final TextView maintainer = holder.findViewById(R.id.mantainer_about);
        maintainer.setText(maintainerName);
        if (official) {
            Drawable badge = ctx.getDrawable(R.drawable.mist_about_ic_verified);
            if (badge != null) {
                int size = Math.round(16 * ctx.getResources().getDisplayMetrics().density);
                badge = badge.mutate();
                badge.setBounds(0, 0, size, size);
                badge.setTintList(ColorStateList.valueOf(color(ctx,
                        com.android.settingslib.widget.theme.R.color.settingslib_materialColorPrimary)));
            }
            maintainer.setCompoundDrawablesRelative(null, null, badge, null);
        } else {
            maintainer.setCompoundDrawablesRelative(null, null, null, null);
        }
        final int initial = maintainerName.codePointAt(0);
        ((TextView) holder.findViewById(R.id.maintainer_avatar)).setText(
                Character.isLetterOrDigit(initial)
                        ? new String(Character.toChars(Character.toUpperCase(initial))) : "M");
        final TextView androidVersion = holder.findViewById(R.id.android_version);
        androidVersion.setText(Build.VERSION.RELEASE_OR_CODENAME);
        androidVersion.setContentDescription(
                ctx.getString(R.string.mist_about_android_value, Build.VERSION.RELEASE_OR_CODENAME));
        bindSecurityPatch(holder.findViewById(R.id.security_update),
                holder.findViewById(R.id.security_update_year));
        spinDecor(holder.findViewById(R.id.android_decor), -40f, true);
        spinDecor(holder.findViewById(R.id.security_decor), 40f, false);

        final SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        brandHeader = prefs.getBoolean(KEY_HEADER_BRAND, false);
        applyHeader(holder, brandHeader, false);
        final View hero = holder.findViewById(R.id.hero_container);
        hero.setOnClickListener(v -> {
            brandHeader = !brandHeader;
            prefs.edit().putBoolean(KEY_HEADER_BRAND, brandHeader).apply();
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            applyHeader(holder, brandHeader, true);
        });

        final TextView romTextView = holder.findViewById(R.id.storage_about);
        final TextView romPercent = holder.findViewById(R.id.storage_percent);
        final WavyProgressView romProgress = holder.findViewById(R.id.rom_progress_bar);
        final TextView ramTextView = holder.findViewById(R.id.ram_about);
        final TextView ramPercent = holder.findViewById(R.id.ram_percent);
        final WavyProgressView ramProgress = holder.findViewById(R.id.ram_progress_bar);
        romPercent.setText(ctx.getString(R.string.mist_about_percent, 0));
        ramPercent.setText(ctx.getString(R.string.mist_about_percent, 0));

        new Thread(() -> {
            SystemInfoUtils.StorageInfo storageInfo = SystemInfoUtils.getStorageInfo(context);
            SystemInfoUtils.MemoryInfo memoryInfo = SystemInfoUtils.getMemoryInfo(context);

            holder.post(() -> {
                romTextView.setText(ctx.getString(R.string.mist_about_storage_value,
                        Formatter.formatFileSize(context, storageInfo.usedBytes),
                        Formatter.formatFileSize(context, storageInfo.totalBytes)));
                animateProgress(romProgress, romPercent, storageInfo.usedPercentage);

                ramTextView.setText(ctx.getString(R.string.mist_about_ram_value,
                        memoryInfo.roundedRamInGB, ctx.getString(R.string.ram_active_label)));
                animateProgress(ramProgress, ramPercent, memoryInfo.usedRamPercentage);
            });
        }).start();

        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName("android", com.android.internal.app.PlatLogoActivity.class.getName());
        final View androidBarInfo = holder.findViewById(R.id.android_bar_info);
        androidBarInfo.setOnTouchListener(this);
        androidBarInfo.setOnLongClickListener(view -> {
            try {
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {
            }
            return false;
        });

        final View kernelRow = holder.findViewById(R.id.kernel_row);
        final View chevron = holder.findViewById(R.id.kernel_chevron);
        kernelRow.setOnClickListener(v -> {
            fullKernelVersion = !fullKernelVersion;
            TransitionManager.beginDelayedTransition((ViewGroup) kernelRow.getParent(),
                    new AutoTransition().setDuration(250));
            kernel.setText(fullKernelVersion ? SystemInfoUtils.getFullKernelVersion()
                    : DeviceInfoUtils.getFormattedKernelVersion(context));
            chevron.animate().rotation(fullKernelVersion ? 180f : 0f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator()).start();
        });

        playEntrance(holder);
    }

    private void bindBuildTypeChip(TextView chip, boolean official) {
        final Context ctx = chip.getContext();
        final int bg = color(ctx, official ? R.color.mist_about_official_container
                : R.color.mist_about_unofficial_container);
        final int fg = color(ctx, official ? R.color.mist_about_on_official_container
                : R.color.mist_about_on_unofficial_container);
        chip.setText(official ? R.string.mist_about_official : R.string.mist_about_unofficial);
        chip.setTextColor(fg);
        chip.setBackgroundTintList(ColorStateList.valueOf(bg));
        Drawable icon = ctx.getDrawable(official ? R.drawable.mist_about_ic_verified
                : R.drawable.mist_about_ic_unverified);
        if (icon != null) {
            int size = Math.round(16 * ctx.getResources().getDisplayMetrics().density);
            icon = icon.mutate();
            icon.setBounds(0, 0, size, size);
            icon.setTintList(ColorStateList.valueOf(fg));
        }
        chip.setCompoundDrawablesRelative(icon, null, null, null);
    }

    private void bindSecurityPatch(TextView day, TextView monthYear) {
        final String patch = Build.VERSION.SECURITY_PATCH;
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch);
            if (date == null) throw new IllegalArgumentException();
            Locale locale = Locale.getDefault();
            day.setText(DateFormat.format("d", date));
            monthYear.setText(DateFormat.format(
                    DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), date));
            monthYear.setVisibility(View.VISIBLE);
            day.setContentDescription(DateFormat.getLongDateFormat(day.getContext()).format(date));
        } catch (Exception e) {
            day.setText(DeviceInfoUtils.getSecurityPatch());
            day.setMaxLines(2);
            monthYear.setVisibility(View.GONE);
        }
    }

    private static void spinDecor(View decor, float fromDegrees, boolean clockwise) {
        if (decor.getTag() instanceof View.OnAttachStateChangeListener) {
            decor.removeOnAttachStateChangeListener(
                    (View.OnAttachStateChangeListener) decor.getTag());
        }
        decor.animate().cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            decor.setRotation(0f);
            return;
        }

        final ObjectAnimator spin = ObjectAnimator.ofFloat(decor, View.ROTATION,
                0f, clockwise ? 360f : -360f);
        spin.setDuration(DECOR_SPIN_MS);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
        final boolean[] settled = {false};

        final View.OnAttachStateChangeListener lifecycle = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (spin.isPaused()) {
                    spin.resume();
                } else if (settled[0] && !spin.isStarted()) {
                    spin.start();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!settled[0]) {
                    v.animate().cancel();
                    v.setRotation(0f);
                    settled[0] = true;
                }
                if (spin.isStarted()) spin.pause();
            }
        };
        decor.addOnAttachStateChangeListener(lifecycle);
        decor.setTag(lifecycle);

        decor.setRotation(fromDegrees);
        decor.animate().rotation(0f).setStartDelay(150).setDuration(900)
                .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                .withEndAction(() -> {
                    settled[0] = true;
                    if (decor.isAttachedToWindow() && !spin.isStarted()) spin.start();
                })
                .start();
    }

    private void applyHeader(View holder, boolean brand, boolean animate) {
        final View device = holder.findViewById(R.id.header_device);
        final View brandView = holder.findViewById(R.id.header_brand);
        final View swap = holder.findViewById(R.id.hero_swap);
        final View idMist = holder.findViewById(R.id.identity_mist);
        final View idDevice = holder.findViewById(R.id.identity_device);
        final ViewGroup identity = holder.findViewById(R.id.identity_card);
        final View in = brand ? brandView : device;
        final View out = brand ? device : brandView;

        if (!animate) {
            in.animate().cancel();
            out.animate().cancel();
            in.setVisibility(View.VISIBLE);
            in.setAlpha(1f);
            in.setScaleX(1f);
            in.setScaleY(1f);
            out.setVisibility(View.GONE);
            out.setAlpha(0f);
            swap.setRotation(brand ? 180f : 0f);
            idMist.setVisibility(brand ? View.GONE : View.VISIBLE);
            idDevice.setVisibility(brand ? View.VISIBLE : View.GONE);
            return;
        }

        final PathInterpolator emphasized = new PathInterpolator(0.2f, 0f, 0f, 1f);
        out.animate().cancel();
        out.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setStartDelay(0).setDuration(220)
                .setInterpolator(emphasized)
                .withEndAction(() -> out.setVisibility(View.GONE)).start();

        in.animate().cancel();
        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setScaleX(0.92f);
        in.setScaleY(0.92f);
        in.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(90).setDuration(420)
                .setInterpolator(emphasized).start();

        swap.animate().rotationBy(180f).setDuration(450).setInterpolator(emphasized).start();

        TransitionManager.beginDelayedTransition(identity, new AutoTransition().setDuration(300));
        idMist.setVisibility(brand ? View.GONE : View.VISIBLE);
        idDevice.setVisibility(brand ? View.VISIBLE : View.GONE);
    }

    private void animateProgress(WavyProgressView bar, TextView percent, int target) {
        final Context ctx = bar.getContext();
        final boolean hot = target >= HOT_PERCENT;
        final int col = color(ctx, hot
                ? com.android.settingslib.widget.theme.R.color.settingslib_materialColorError
                : com.android.settingslib.widget.theme.R.color.settingslib_materialColorPrimary);
        bar.setIndicatorColor(col);
        percent.setTextColor(col);

        ValueAnimator animator = ValueAnimator.ofInt(0, Math.max(0, Math.min(100, target)));
        animator.setDuration(1000);
        animator.setStartDelay(250);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(a -> {
            int v = (int) a.getAnimatedValue();
            bar.setProgress(v);
            percent.setText(ctx.getString(R.string.mist_about_percent, v));
        });
        animator.start();
    }

    private void playEntrance(View holder) {
        if (entranceShown || !(holder instanceof ViewGroup)) return;
        entranceShown = true;
        final ViewGroup group = (ViewGroup) holder;
        final float dy = 24 * holder.getResources().getDisplayMetrics().density;
        final PathInterpolator decel = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dy);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(i, 8) * 45L)
                    .setDuration(500)
                    .setInterpolator(decel)
                    .start();
        }
    }

    public class SystemInfoUtils {

        public static StorageInfo getStorageInfo(Context context) {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            StorageManagerVolumeProvider volumeProvider = new StorageManagerVolumeProvider(storageManager);
            PrivateStorageInfo privateStorageInfo = PrivateStorageInfo.getPrivateStorageInfo(volumeProvider);

            long totalBytes = privateStorageInfo.totalBytes;
            long freeBytes = privateStorageInfo.freeBytes;
            long usedBytes = totalBytes - freeBytes;
            int usedPercentage = totalBytes > 0 ? (int) ((usedBytes * 100) / totalBytes) : 0;

            return new StorageInfo(totalBytes, usedBytes, usedPercentage);
        }

        public static MemoryInfo getMemoryInfo(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);

            long totalMemory = memoryInfo.totalMem;
            long availableMemory = memoryInfo.availMem;
            long usedMemory = totalMemory - availableMemory;
            int usedRamPercentage = totalMemory > 0 ? (int) ((usedMemory * 100) / totalMemory) : 0;

            int roundedRamInGB;
            try {
                double ramInGB = Double.parseDouble(getMem()) / Math.pow(1000, 2);
                roundedRamInGB = (int) Math.ceil(ramInGB);
            } catch (NumberFormatException e) {
                roundedRamInGB = (int) Math.ceil(totalMemory / Math.pow(1000, 3));
            }
            String swapSize = totalSwap();

            return new MemoryInfo(roundedRamInGB, swapSize, usedRamPercentage);
        }

        private static String getMem() {
            Map<String, String> memInfo = getMemInfoMap();
            if (memInfo.containsKey("MemTotal")) {
                return memInfo.get("MemTotal").split(" ")[0];
            }
            return "Unknown";
        }

        private static String totalSwap() {
            Map<String, String> memInfo = getMemInfoMap();
            if (memInfo.containsKey("SwapTotal")) {
                long swapSizeBytes = Long.parseLong(memInfo.get("SwapTotal").split(" ")[0]) * 1024;
                return String.valueOf((int) Math.ceil(swapSizeBytes / Math.pow(1024, 3)));
            }
            return "0";
        }

        private static Map<String, String> getMemInfoMap() {
            Map<String, String> map = new HashMap<>();
            try (Scanner scanner = new Scanner(new File("/proc/meminfo"))) {
                while (scanner.hasNextLine()) {
                    String[] vals = scanner.nextLine().split(": ");
                    if (vals.length > 1) {
                        map.put(vals[0].trim(), vals[1].trim());
                    }
                }
            } catch (Exception e) {
                Log.e("SystemInfoUtils", "Error reading meminfo", e);
            }
            return map;
        }

        public static class StorageInfo {
            public long totalBytes;
            public long usedBytes;
            public int usedPercentage;

            public StorageInfo(long totalBytes, long usedBytes, int usedPercentage) {
                this.totalBytes = totalBytes;
                this.usedBytes = usedBytes;
                this.usedPercentage = usedPercentage;
            }
        }

        public static class MemoryInfo {
            public int roundedRamInGB;
            public String swapSize;
            public int usedRamPercentage;

            public MemoryInfo(int roundedRamInGB, String swapSize, int usedRamPercentage) {
                this.roundedRamInGB = roundedRamInGB;
                this.swapSize = swapSize;
                this.usedRamPercentage = usedRamPercentage;
            }
        }

        private static String getFullKernelVersion() {
            try {
                return readLine(FILENAME_PROC_VERSION);
            } catch (IOException e) {
                Log.e("DeviceInfoUtils", "IO Exception when getting kernel version", e);
                return "Unavailable";
            }
        }

        private static String readLine(String filename) throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(filename), 256)) {
                return reader.readLine();
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isLongPressDetected = false;
                v.setPressed(true);
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(150).start();
                longPressHandler.postDelayed(() -> {
                    isLongPressDetected = true;
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (v.getOnLongClickListener() != null) {
                        v.getOnLongClickListener().onLongClick(v);
                    }
                }, ViewConfiguration.getLongPressTimeout());
                return true;

            case MotionEvent.ACTION_MOVE:
                final float x = motionEvent.getX();
                final float y = motionEvent.getY();
                if (x < 0 || y < 0 || x > v.getWidth() || y > v.getHeight()) {
                    longPressHandler.removeCallbacksAndMessages(null);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                longPressHandler.removeCallbacksAndMessages(null);
                v.setPressed(false);

                ObjectAnimator upX = ObjectAnimator.ofFloat(v, "scaleX", 1.03f);
                ObjectAnimator upY = ObjectAnimator.ofFloat(v, "scaleY", 1.03f);
                upX.setDuration(140);
                upY.setDuration(140);
                AnimatorSet bounce = new AnimatorSet();
                bounce.play(upX).with(upY);
                bounce.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                });
                bounce.start();

                if (!isLongPressDetected && motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return true;
        }
        return false;
    }
}
