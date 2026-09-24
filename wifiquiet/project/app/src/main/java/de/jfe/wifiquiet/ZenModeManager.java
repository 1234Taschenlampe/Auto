package de.jfe.wifiquiet;

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.Condition;
import android.content.Intent;

final class ZenModeManager {
    private static final String RULE_NAME="WLAN Ruhe";
    private static final Uri CONDITION_ID=Uri.parse("condition://de.jfe.wifiquiet/wifi");
    private ZenModeManager() {}

    static int filter(int mode){
        if(mode==1) return NotificationManager.INTERRUPTION_FILTER_ALARMS;
        if(mode==2) return NotificationManager.INTERRUPTION_FILTER_NONE;
        return NotificationManager.INTERRUPTION_FILTER_PRIORITY;
    }

    static String ensureRule(Context c){
        if(Build.VERSION.SDK_INT<35) return null;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null || !nm.isNotificationPolicyAccessGranted()) return null;
        SharedPreferences p=Config.prefs(c);
        String id=p.getString(Config.ZEN_RULE_ID,null);
        if(id!=null){
            try { if(nm.getAutomaticZenRule(id)!=null) return id; } catch(Exception ignored) {}
            p.edit().remove(Config.ZEN_RULE_ID).apply();
        }
        String ssid=p.getString(Config.SSID,"");
        AutomaticZenRule rule=new AutomaticZenRule.Builder(RULE_NAME, CONDITION_ID)
                .setConfigurationActivity(new ComponentName(c,MainActivity.class))
                .setEnabled(true)
                .setInterruptionFilter(filter(p.getInt(Config.DND_MODE,0)))
                .setType(AutomaticZenRule.TYPE_OTHER)
                .setIconResId(R.drawable.ic_mode_wifi)
                .setManualInvocationAllowed(true)
                .setTriggerDescription(ssid.isEmpty()?"Bei Verbindung mit dem gewählten WLAN":"Verbunden mit "+ssid)
                .build();
        try {
            id=nm.addAutomaticZenRule(rule);
            if(id!=null) p.edit().putString(Config.ZEN_RULE_ID,id).apply();
            return id;
        } catch(Exception e){ return null; }
    }

    static void updateRule(Context c){
        if(Build.VERSION.SDK_INT<35) return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null || !nm.isNotificationPolicyAccessGranted()) return;
        String id=Config.prefs(c).getString(Config.ZEN_RULE_ID,null);
        if(id==null){ ensureRule(c); return; }
        try {
            AutomaticZenRule old=nm.getAutomaticZenRule(id);
            if(old==null){ Config.prefs(c).edit().remove(Config.ZEN_RULE_ID).apply(); ensureRule(c); return; }
            if(nm.areAutomaticZenRulesUserManaged()) return;
            String ssid=Config.prefs(c).getString(Config.SSID,"");
            AutomaticZenRule updated=new AutomaticZenRule.Builder(old)
                    .setName(RULE_NAME)
                    .setConfigurationActivity(new ComponentName(c,MainActivity.class))
                    .setInterruptionFilter(filter(Config.prefs(c).getInt(Config.DND_MODE,0)))
                    .setIconResId(R.drawable.ic_mode_wifi)
                    .setManualInvocationAllowed(true)
                    .setTriggerDescription(ssid.isEmpty()?"Bei Verbindung mit dem gewählten WLAN":"Verbunden mit "+ssid)
                    .build();
            nm.updateAutomaticZenRule(id,updated);
        } catch(Exception ignored) {}
    }

    static boolean setActive(Context c, boolean active){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null || !nm.isNotificationPolicyAccessGranted()) return false;
        if(Build.VERSION.SDK_INT>=35){
            String id=ensureRule(c);
            if(id==null) return false;
            try {
                nm.setAutomaticZenRuleState(id,new Condition(CONDITION_ID,
                        active?"Ziel-WLAN verbunden":"Ziel-WLAN nicht verbunden",
                        active?Condition.STATE_TRUE:Condition.STATE_FALSE));
                return true;
            } catch(Exception e){ return false; }
        }
        try {
            nm.setInterruptionFilter(active?filter(Config.prefs(c).getInt(Config.DND_MODE,0)):NotificationManager.INTERRUPTION_FILTER_ALL);
            return true;
        } catch(Exception e){ return false; }
    }

    static void openModeSettings(Context c){
        if(Build.VERSION.SDK_INT>=35){
            String id=Config.prefs(c).getString(Config.ZEN_RULE_ID,null);
            if(id!=null){
                Intent i=new Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS);
                i.putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID,id);
                try { c.startActivity(i); return; } catch(Exception ignored) {}
            }
        }
        c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }
}
