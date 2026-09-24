package de.jfe.wifiquiet;
import android.content.Context;
import android.content.SharedPreferences;
final class Config {
    static final String PREFS="wifiquiet", SSID="ssid", DND_ENABLED="dnd_enabled", DND_MODE="dnd_mode",
            MEDIA_ENABLED="media_enabled", MEDIA_PERCENT="media_percent", RING_ENABLED="ring_enabled",
            RING_PERCENT="ring_percent", RESTORE="restore", SERVICE_RUNNING="service_running",
            PROFILE_ACTIVE="profile_active", LAST_SSID="last_ssid", LAST_STATUS="last_status",
            ORIGINAL_MEDIA="original_media", ORIGINAL_RING="original_ring", ORIGINAL_NOTIFICATION="original_notification",
            APPLIED_MEDIA="applied_media", APPLIED_RING="applied_ring", RESTORE_AT_APPLY="restore_at_apply",
            ZEN_RULE_ID="zen_rule_id";
    private Config() {}
    static SharedPreferences prefs(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
