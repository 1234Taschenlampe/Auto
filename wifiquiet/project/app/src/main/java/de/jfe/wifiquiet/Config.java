package de.jfe.wifiquiet;

import android.content.Context;
import android.content.SharedPreferences;

final class Config {
    static final String PREFS="wifiquiet";
    static final String ENGINE_ENABLED="engine_enabled", SERVICE_RUNNING="service_running",
            ENGINE_ACTIVE_IDS="engine_active_ids", MANUAL_ACTIVE_IDS="manual_active_ids",
            LAST_ACTIVE_IDS="last_active_ids", SNOOZED_IDS="snoozed_ids",
            LAST_STATUS="last_status", BASELINE_VALID="baseline_valid", BASELINE_RESTORE="baseline_restore",
            ORIGINAL_MEDIA="original_media", ORIGINAL_RING="original_ring", ORIGINAL_NOTIFICATION="original_notification",
            ORIGINAL_BRIGHTNESS="original_brightness", ORIGINAL_BRIGHTNESS_MODE="original_brightness_mode",
            ORIGINAL_ROTATION="original_rotation", ORIGINAL_TIMEOUT="original_timeout";
    private Config(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}
