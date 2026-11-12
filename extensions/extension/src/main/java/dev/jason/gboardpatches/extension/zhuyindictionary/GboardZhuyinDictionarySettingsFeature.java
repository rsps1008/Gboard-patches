package dev.jason.gboardpatches.extension.zhuyindictionary;

import android.content.Context;
import android.content.SharedPreferences;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardZhuyinDictionarySettingsFeature extends GboardBooleanFlagSettingsFeature {
    public GboardZhuyinDictionarySettingsFeature(Context context) {
        super(GboardZhuyinDictionarySettings.FEATURE,
                GboardSettingsText.get(context, R.string.gboard_patches_zhuyin_dictionary_title),
                summary(context),
                GboardSettingsText.get(context, R.string.gboard_patches_zhuyin_dictionary_title),
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context, R.string.gboard_patches_zhuyin_dictionary_error),
                GboardSettingsText.get(context, R.string.gboard_patches_zhuyin_dictionary_retry),
                GboardSettingsText.get(context, R.string.gboard_patches_zhuyin_dictionary_section),
                null,
                new SettingsStore() {
                    public void ensureDefault(SharedPreferences preferences) {
                        GboardZhuyinDictionarySettings.ensureDefault(preferences);
                    }
                    public boolean readEnabled(SharedPreferences preferences) {
                        return GboardZhuyinDictionarySettings.readEnabled(preferences);
                    }
                    public boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
                        if (!GboardZhuyinDictionarySettings.writeEnabled(preferences, enabled)) return false;
                        GboardZhuyinPersonalDictionaryRuntime.requestRefresh(context);
                        return true;
                    }
                });
    }

    private static String summary(Context context) {
        SharedPreferences preferences=dev.jason.gboardpatches.extension.settings.GboardPatchesSettings.preferences(context);
        int words=preferences.getStringSet(GboardZhuyinPersonalDictionaryRuntime.WORDS,java.util.Collections.emptySet()).size();
        int count=preferences.getInt(GboardZhuyinPersonalDictionaryRuntime.LAST_COUNT,-1);
        String status=count>=0
                ? GboardSettingsText.format(context,R.string.gboard_patches_zhuyin_dictionary_status,words,count)
                : GboardSettingsText.format(context,R.string.gboard_patches_zhuyin_dictionary_pending,words);
        return GboardSettingsText.get(context,R.string.gboard_patches_zhuyin_dictionary_summary)+"\n"+status;
    }
}
