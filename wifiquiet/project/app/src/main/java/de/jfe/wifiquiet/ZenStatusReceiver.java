package de.jfe.wifiquiet;

import android.app.NotificationManager;
import android.content.*;
import android.os.Build;
import java.util.*;

public class ZenStatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if(!NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED.equals(i.getAction())) return;
        String ruleId=i.getStringExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_ID);
        int status=i.getIntExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS,NotificationManager.AUTOMATIC_RULE_STATUS_UNKNOWN);
        RoutineProfile p=ProfileStore.findByRule(c,ruleId);
        if(p==null) return;
        SharedPreferences sp=Config.prefs(c);
        Set<String> manual=new HashSet<>(sp.getStringSet(Config.MANUAL_ACTIVE_IDS,Collections.emptySet()));
        Set<String> snoozed=new HashSet<>(sp.getStringSet(Config.SNOOZED_IDS,Collections.emptySet()));
        if(Build.VERSION.SDK_INT>=35 && status==NotificationManager.AUTOMATIC_RULE_STATUS_ACTIVATED){
            manual.add(p.id); snoozed.remove(p.id);
        }else if(Build.VERSION.SDK_INT>=35 && status==NotificationManager.AUTOMATIC_RULE_STATUS_DEACTIVATED){
            manual.remove(p.id); snoozed.add(p.id);
        }else if(status==NotificationManager.AUTOMATIC_RULE_STATUS_REMOVED){
            manual.remove(p.id); snoozed.remove(p.id); p.zenRuleId=""; ProfileStore.upsert(c,p);
        }
        sp.edit().putStringSet(Config.MANUAL_ACTIVE_IDS,manual).putStringSet(Config.SNOOZED_IDS,snoozed).apply();
        try{ c.startForegroundService(new Intent(c,AutomationService.class).setAction(AutomationService.ACTION_START)); }catch(Exception ignored){}
    }
}
