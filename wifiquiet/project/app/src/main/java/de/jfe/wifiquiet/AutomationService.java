package de.jfe.wifiquiet;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.os.*;
import android.text.TextUtils;

public class AutomationService extends Service {
    public static final String ACTION_START="de.jfe.wifiquiet.START", ACTION_STOP="de.jfe.wifiquiet.STOP", ACTION_STATUS="de.jfe.wifiquiet.STATUS";
    private static final String CHANNEL="wifiquiet_service";
    private ConnectivityManager cm; private NotificationManager nm; private AudioManager am;
    private ConnectivityManager.NetworkCallback cb; private final Handler h=new Handler(Looper.getMainLooper());
    private boolean applied; private int oldMedia=-1,oldRing=-1,oldNotif=-1; private boolean didMedia,didRing,didDnd,restore=true;
    private final Runnable check=this::evaluate;

    @Override public void onCreate(){
        super.onCreate(); cm=getSystemService(ConnectivityManager.class); nm=getSystemService(NotificationManager.class); am=getSystemService(AudioManager.class);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"WLAN-Automatik",NotificationManager.IMPORTANCE_LOW); nm.createNotificationChannel(ch);
        SharedPreferences p=Config.prefs(this); applied=p.getBoolean(Config.PROFILE_ACTIVE,false); oldMedia=p.getInt(Config.ORIGINAL_MEDIA,-1); oldRing=p.getInt(Config.ORIGINAL_RING,-1); oldNotif=p.getInt(Config.ORIGINAL_NOTIFICATION,-1); didMedia=p.getBoolean(Config.APPLIED_MEDIA,false); didRing=p.getBoolean(Config.APPLIED_RING,false); restore=p.getBoolean(Config.RESTORE_AT_APPLY,true);
        startForeground(42,notification("Automatik aktiv"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        p.edit().putBoolean(Config.SERVICE_RUNNING,true).apply(); register(); h.post(check);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && ACTION_STOP.equals(i.getAction())){ restore(false); stopSelf(); return START_NOT_STICKY; }
        if(applied) restore(true); ZenModeManager.updateRule(this); h.post(check); return START_STICKY;
    }
    private void register(){
        cb=new ConnectivityManager.NetworkCallback(){ public void onAvailable(Network n){later(300);} public void onCapabilitiesChanged(Network n,NetworkCapabilities c){later(250);} public void onLost(Network n){later(700);} };
        try{ cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),cb);}catch(Exception ignored){}
    }
    private void later(long ms){ h.removeCallbacks(check); h.postDelayed(check,ms); }
    private void evaluate(){
        String current=ssid(), target=Config.prefs(this).getString(Config.SSID,""); boolean match=current!=null&&!TextUtils.isEmpty(target)&&current.equals(target);
        if(match&&!applied) apply(); else if(!match&&applied) restore(false); else if(!match&&Config.prefs(this).getBoolean(Config.DND_ENABLED,true)) ZenModeManager.setActive(this,false);
        Config.prefs(this).edit().putString(Config.LAST_SSID,current==null?"":current).putString(Config.LAST_STATUS,match?"Ziel-WLAN erkannt":current==null?"Kein lesbares WLAN erkannt":"Anderes WLAN verbunden").apply();
        nm.notify(42,notification(applied?"WLAN Ruhe aktiv":"Warte auf "+target)); sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()));
    }
    private String ssid(){
        try{ for(Network n:cm.getAllNetworks()){ NetworkCapabilities c=cm.getNetworkCapabilities(n); if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.getTransportInfo() instanceof WifiInfo){ String s=((WifiInfo)c.getTransportInfo()).getSSID(); if(s!=null&&!"<unknown ssid>".equals(s)){ if(s.startsWith("\"")&&s.endsWith("\"")&&s.length()>1)s=s.substring(1,s.length()-1); return s; } } } }catch(Exception ignored){} return null;
    }
    private void apply(){
        SharedPreferences p=Config.prefs(this); didMedia=p.getBoolean(Config.MEDIA_ENABLED,true); didRing=p.getBoolean(Config.RING_ENABLED,true); didDnd=p.getBoolean(Config.DND_ENABLED,true); restore=p.getBoolean(Config.RESTORE,true);
        oldMedia=didMedia?am.getStreamVolume(AudioManager.STREAM_MUSIC):-1; oldRing=didRing?am.getStreamVolume(AudioManager.STREAM_RING):-1; oldNotif=didRing?am.getStreamVolume(AudioManager.STREAM_NOTIFICATION):-1;
        p.edit().putInt(Config.ORIGINAL_MEDIA,oldMedia).putInt(Config.ORIGINAL_RING,oldRing).putInt(Config.ORIGINAL_NOTIFICATION,oldNotif).putBoolean(Config.APPLIED_MEDIA,didMedia).putBoolean(Config.APPLIED_RING,didRing).putBoolean(Config.RESTORE_AT_APPLY,restore).apply();
        try{ if(didMedia)lower(AudioManager.STREAM_MUSIC,p.getInt(Config.MEDIA_PERCENT,20)); if(didRing){ lower(AudioManager.STREAM_RING,p.getInt(Config.RING_PERCENT,10)); lower(AudioManager.STREAM_NOTIFICATION,p.getInt(Config.RING_PERCENT,10)); }}catch(Exception ignored){}
        if(didDnd) ZenModeManager.setActive(this,true); applied=true; p.edit().putBoolean(Config.PROFILE_ACTIVE,true).apply();
    }
    private void lower(int stream,int pct){ int max=am.getStreamMaxVolume(stream),cur=am.getStreamVolume(stream),to=Math.round(max*Math.max(0,Math.min(100,pct))/100f); if(cur>to)am.setStreamVolume(stream,to,0); }
    private void restore(boolean force){
        if(!applied){ if(Config.prefs(this).getBoolean(Config.DND_ENABLED,true))ZenModeManager.setActive(this,false); return; }
        if(force||restore)try{ if(didMedia&&oldMedia>=0)am.setStreamVolume(AudioManager.STREAM_MUSIC,oldMedia,0); if(didRing&&oldRing>=0){am.setStreamVolume(AudioManager.STREAM_RING,oldRing,0);am.setStreamVolume(AudioManager.STREAM_NOTIFICATION,oldNotif,0);} }catch(Exception ignored){}
        if(didDnd)ZenModeManager.setActive(this,false); applied=false; Config.prefs(this).edit().putBoolean(Config.PROFILE_ACTIVE,false).apply();
    }
    private Notification notification(String t){
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,AutomationService.class).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_mode_wifi).setContentTitle("WLAN Ruhe").setContentText(t).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stoppen",stop).build()).build();
    }
    @Override public void onDestroy(){ h.removeCallbacksAndMessages(null); if(cb!=null)try{cm.unregisterNetworkCallback(cb);}catch(Exception ignored){} restore(false); Config.prefs(this).edit().putBoolean(Config.SERVICE_RUNNING,false).apply(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i){return null;}
}
