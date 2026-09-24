package de.jfe.wifiquiet;

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.Condition;

public final class ZenModeManager {
    private ZenModeManager(){}

    static int filter(int mode){
        if(mode==1) return NotificationManager.INTERRUPTION_FILTER_ALARMS;
        if(mode==2) return NotificationManager.INTERRUPTION_FILTER_NONE;
        return NotificationManager.INTERRUPTION_FILTER_PRIORITY;
    }

    static Uri conditionUri(RoutineProfile p){ return Uri.parse("condition://de.jfe.wifiquiet/profile/"+p.id); }

    public static String ensureRule(Context c,RoutineProfile p){
        if(Build.VERSION.SDK_INT<35 || !p.dndEnabled) return null;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null || !nm.isNotificationPolicyAccessGranted()) return null;
        if(p.zenRuleId!=null&&!p.zenRuleId.isEmpty()){
            try{ if(nm.getAutomaticZenRule(p.zenRuleId)!=null){ updateRule(c,p); return p.zenRuleId; } }catch(Exception ignored){}
            p.zenRuleId="";
        }
        AutomaticZenRule rule=new AutomaticZenRule.Builder(p.name,conditionUri(p))
                .setConfigurationActivity(new ComponentName(c,ProfileEditActivity.class))
                .setEnabled(p.enabled)
                .setInterruptionFilter(filter(p.dndMode))
                .setType(p.timeEnabled?AutomaticZenRule.TYPE_SCHEDULE_TIME:AutomaticZenRule.TYPE_OTHER)
                .setIconResId(R.drawable.ic_mode_wifi)
                .setManualInvocationAllowed(true)
                .setTriggerDescription(summary(p))
                .build();
        try{
            String id=nm.addAutomaticZenRule(rule);
            if(id!=null){ p.zenRuleId=id; ProfileStore.upsert(c,p); }
            return id;
        }catch(Exception e){ return null; }
    }

    public static void updateRule(Context c,RoutineProfile p){
        if(Build.VERSION.SDK_INT<35 || !p.dndEnabled) return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null || !nm.isNotificationPolicyAccessGranted()) return;
        if(p.zenRuleId==null||p.zenRuleId.isEmpty()){ ensureRule(c,p); return; }
        try{
            AutomaticZenRule old=nm.getAutomaticZenRule(p.zenRuleId);
            if(old==null){ p.zenRuleId=""; ProfileStore.upsert(c,p); ensureRule(c,p); return; }
            AutomaticZenRule r=new AutomaticZenRule.Builder(old)
                    .setName(p.name)
                    .setEnabled(p.enabled)
                    .setInterruptionFilter(filter(p.dndMode))
                    .setType(p.timeEnabled?AutomaticZenRule.TYPE_SCHEDULE_TIME:AutomaticZenRule.TYPE_OTHER)
                    .setIconResId(R.drawable.ic_mode_wifi)
                    .setManualInvocationAllowed(true)
                    .setTriggerDescription(summary(p))
                    .build();
            nm.updateAutomaticZenRule(p.zenRuleId,r);
        }catch(Exception ignored){}
    }

    public static void setActive(Context c,RoutineProfile p,boolean active){
        if(Build.VERSION.SDK_INT<35||!p.dndEnabled)return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null||!nm.isNotificationPolicyAccessGranted())return;
        String id=ensureRule(c,p); if(id==null)return;
        try{
            nm.setAutomaticZenRuleState(id,new Condition(conditionUri(p),
                    active?"Bedingungen erfüllt":"Bedingungen nicht erfüllt",
                    active?Condition.STATE_TRUE:Condition.STATE_FALSE));
        }catch(Exception ignored){}
    }

    public static void deleteRule(Context c,RoutineProfile p){
        if(Build.VERSION.SDK_INT<35||p==null||p.zenRuleId==null||p.zenRuleId.isEmpty())return;
        try{ NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null)nm.removeAutomaticZenRule(p.zenRuleId); }catch(Exception ignored){}
    }

    public static void openRule(Context c,RoutineProfile p){
        if(Build.VERSION.SDK_INT>=35 && p!=null && p.zenRuleId!=null&&!p.zenRuleId.isEmpty()){
            Intent i=new Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS);
            i.putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID,p.zenRuleId);
            try{c.startActivity(i);return;}catch(Exception ignored){}
        }
        c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    static String summary(RoutineProfile p){
        StringBuilder s=new StringBuilder();
        if(p.wifiEnabled) add(s,"WLAN "+p.wifiSsid);
        if(p.timeEnabled) add(s,fmt(p.startMin)+"–"+fmt(p.endMin));
        if(p.locationEnabled) add(s,"Standort "+Math.round(p.radiusM)+" m");
        if(p.chargingMode==1)add(s,"beim Laden"); else if(p.chargingMode==2)add(s,"nicht am Ladegerät");
        if(p.batteryEnabled)add(s,"Akku "+p.batteryMin+"–"+p.batteryMax+" %");
        if(p.bluetoothEnabled)add(s,p.bluetoothName.isEmpty()?"Bluetooth verbunden":"Bluetooth "+p.bluetoothName);
        return s.length()==0?"Manuell oder per App":"Wenn "+s;
    }
    private static void add(StringBuilder s,String x){if(s.length()>0)s.append(" + ");s.append(x);}
    private static String fmt(int m){return String.format(java.util.Locale.GERMANY,"%02d:%02d",(m/60)%24,m%60);}
}
