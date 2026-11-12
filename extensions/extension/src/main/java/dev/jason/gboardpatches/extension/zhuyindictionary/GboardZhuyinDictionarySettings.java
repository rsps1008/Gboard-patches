package dev.jason.gboardpatches.extension.zhuyindictionary;

import android.content.Context;
import android.content.SharedPreferences;
import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardZhuyinDictionarySettings {
    public static final String FEATURE = "dev.jason.gboardpatches.feature.zhuyin_classic_candidates";
    public static final String KEY = "pref_zhuyin_classic_dictionary_candidates";

    private GboardZhuyinDictionarySettings() {}

    public static boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(context, FEATURE);
    }

    public static boolean isEnabled(Context context) {
        if (context == null) return false;
        return readEnabled(GboardPatchesSettings.preferences(context));
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        return GboardBooleanFlagSettings.readEnabled(preferences, KEY, true);
    }

    public static void ensureDefault(SharedPreferences preferences) {
        GboardBooleanFlagSettings.ensureDefault(preferences, KEY, true);
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        return GboardBooleanFlagSettings.writeEnabled(preferences, KEY, enabled);
    }
}
