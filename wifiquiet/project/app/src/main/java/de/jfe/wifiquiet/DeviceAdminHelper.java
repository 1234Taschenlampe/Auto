package de.jfe.wifiquiet;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public final class DeviceAdminHelper {
    private DeviceAdminHelper(){}

    public static ComponentName component(Context c){
        return new ComponentName(c,RoutineDeviceAdminReceiver.class);
    }

    public static boolean isActive(Context c){
        DevicePolicyManager dpm=c.getSystemService(DevicePolicyManager.class);
        return dpm!=null&&dpm.isAdminActive(component(c));
    }

    public static void request(Activity a){
        Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,component(a));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Erlaubt Routinen, das Gerät auf deine ausdrückliche Regel hin sofort zu sperren.");
        a.startActivity(i);
    }

    public static boolean lockNow(Context c){
        DevicePolicyManager dpm=c.getSystemService(DevicePolicyManager.class);
        if(dpm==null||!dpm.isAdminActive(component(c)))return false;
        try{dpm.lockNow();return true;}catch(Exception e){return false;}
    }
}
