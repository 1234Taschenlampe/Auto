package de.jfe.wifiquiet;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.media.AudioManager;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.os.*;
import android.provider.Settings;
import java.util.*;

public class AutomationService extends Service {
    public static final String ACTION_START="de.jfe.wifiquiet.START",ACTION_STOP="de.jfe.wifiquiet.STOP";
    private static final String CHANNEL="wifiquiet_modes";
    private final Handler h=new Handler(Looper.getMainLooper());
    private ConnectivityManager cm; private NotificationManager nm; private AudioManager am; private LocationManager lm; private BluetoothAdapter bt;
    private ConnectivityManager.NetworkCallback netCb; private LocationListener locListener; private Location lastLocation;
    private final Map<Integer,BluetoothProfile> btProfiles=new HashMap<>(); private final Set<String> connectedBt=new HashSet<>();
    private BroadcastReceiver powerRx,btRx;
    private final Runnable tick=new Runnable(){public void run(){evaluate();h.postDelayed(this,20000);}};

    @Override public void onCreate(){
        super.onCreate();
        cm=getSystemService(ConnectivityManager.class);nm=getSystemService(NotificationManager.class);am=getSystemService(AudioManager.class);lm=getSystemService(LocationManager.class);
        BluetoothManager bm=getSystemService(BluetoothManager.class);bt=bm==null?null:bm.getAdapter();
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Pixel Routinen",NotificationManager.IMPORTANCE_LOW);nm.createNotificationChannel(ch);
        startForeground(42,notification("Automatik wird gestartet"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        Config.prefs(this).edit().putBoolean(Config.SERVICE_RUNNING,true).apply();
        registerWatchers();h.post(tick);
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&ACTION_STOP.equals(i.getAction())){Config.prefs(this).edit().putBoolean(Config.ENGINE_ENABLED,false).apply();restoreBaseline();stopSelf();return START_NOT_STICKY;}
        h.post(this::evaluate);return START_STICKY;
    }

    private void registerWatchers(){
        netCb=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){h.post(AutomationService.this::evaluate);}public void onLost(Network n){h.post(AutomationService.this::evaluate);}public void onCapabilitiesChanged(Network n,NetworkCapabilities c){h.post(AutomationService.this::evaluate);}};
        try{cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),netCb);}catch(Exception ignored){}

        locListener=l->{lastLocation=l;h.post(this::evaluate);};
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            try{for(String p:lm.getProviders(true))lm.requestLocationUpdates(p,30000L,25f,locListener,Looper.getMainLooper());}catch(Exception ignored){}
            for(String p:lm.getProviders(true))try{Location q=lm.getLastKnownLocation(p);if(q!=null&&(lastLocation==null||q.getAccuracy()<lastLocation.getAccuracy()))lastLocation=q;}catch(Exception ignored){}
        }

        powerRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){h.post(AutomationService.this::evaluate);}};
        IntentFilter pf=new IntentFilter();pf.addAction(Intent.ACTION_BATTERY_CHANGED);pf.addAction(Intent.ACTION_POWER_CONNECTED);pf.addAction(Intent.ACTION_POWER_DISCONNECTED);registerReceiver(powerRx,pf);

        btRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);if(d==null)return;try{String n=d.getName();if(n!=null){if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction()))connectedBt.add(n);else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(i.getAction()))connectedBt.remove(n);h.post(AutomationService.this::evaluate);}}catch(Exception ignored){}}};
        IntentFilter bf=new IntentFilter();bf.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);bf.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);registerReceiver(btRx,bf);
        refreshBluetoothProfiles();
    }

    private void refreshBluetoothProfiles(){
        if(bt==null||Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;
        BluetoothProfile.ServiceListener sl=new BluetoothProfile.ServiceListener(){
            public void onServiceConnected(int profile,BluetoothProfile proxy){btProfiles.put(profile,proxy);rebuildBtNames();}
            public void onServiceDisconnected(int profile){btProfiles.remove(profile);rebuildBtNames();}
        };
        try{bt.getProfileProxy(this,sl,BluetoothProfile.A2DP);}catch(Exception ignored){}
        try{bt.getProfileProxy(this,sl,BluetoothProfile.HEADSET);}catch(Exception ignored){}
        try{bt.getProfileProxy(this,sl,BluetoothProfile.GATT);}catch(Exception ignored){}
    }
    private void rebuildBtNames(){
        connectedBt.clear();
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;
        for(BluetoothProfile p:btProfiles.values())try{for(BluetoothDevice d:p.getConnectedDevices()){String n=d.getName();if(n!=null)connectedBt.add(n);}}catch(Exception ignored){}
        h.post(this::evaluate);
    }

    private void evaluate(){
        List<RoutineProfile> profiles=ProfileStore.load(this);
        SharedPreferences sp=Config.prefs(this);
        Set<String> manual=new HashSet<>(sp.getStringSet(Config.MANUAL_ACTIVE_IDS,Collections.emptySet()));
        Set<String> snoozed=new HashSet<>(sp.getStringSet(Config.SNOOZED_IDS,Collections.emptySet()));
        Set<String> oldAuto=new HashSet<>(sp.getStringSet(Config.ENGINE_ACTIVE_IDS,Collections.emptySet()));
        Set<String> newAuto=new HashSet<>(),activeIds=new HashSet<>(manual);
        boolean engine=sp.getBoolean(Config.ENGINE_ENABLED,false);

        for(RoutineProfile p:profiles){
            if(!p.enabled){if(oldAuto.contains(p.id))ZenModeManager.setActive(this,p,false);continue;}
            boolean auto=engine&&conditionsMatch(p);
            if(!auto)snoozed.remove(p.id);
            if(auto&&!snoozed.contains(p.id)){newAuto.add(p.id);activeIds.add(p.id);}
            if(p.dndEnabled){
                if(newAuto.contains(p.id)&&!oldAuto.contains(p.id))ZenModeManager.setActive(this,p,true);
                else if(!newAuto.contains(p.id)&&oldAuto.contains(p.id))ZenModeManager.setActive(this,p,false);
            }
        }
        sp.edit().putStringSet(Config.ENGINE_ACTIVE_IDS,newAuto).putStringSet(Config.SNOOZED_IDS,snoozed).apply();

        List<RoutineProfile> active=new ArrayList<>();for(RoutineProfile p:profiles)if(activeIds.contains(p.id)&&p.enabled)active.add(p);
        if(active.isEmpty())restoreBaseline();else applyActions(active);

        String text=active.isEmpty()?"Kein Modus aktiv":active.size()==1?active.get(0).name:active.size()+" Modi aktiv";
        sp.edit().putString(Config.LAST_STATUS,text).apply();nm.notify(42,notification(text));

        if(!engine&&manual.isEmpty())stopSelf();
    }

    private boolean conditionsMatch(RoutineProfile p){
        boolean any=false;
        if(p.wifiEnabled){any=true;if(!p.wifiSsid.equals(currentSsid()))return false;}
        if(p.timeEnabled){any=true;if(!timeMatches(p))return false;}
        if(p.locationEnabled){any=true;if(lastLocation==null)return false;float[] r=new float[1];Location.distanceBetween(lastLocation.getLatitude(),lastLocation.getLongitude(),p.latitude,p.longitude,r);if(r[0]>p.radiusM)return false;}
        if(p.chargingMode!=0){any=true;boolean charging=isCharging();if(p.chargingMode==1&&!charging)return false;if(p.chargingMode==2&&charging)return false;}
        if(p.batteryEnabled){any=true;int b=batteryPct();if(b<p.batteryMin||b>p.batteryMax)return false;}
        if(p.bluetoothEnabled){any=true;if(p.bluetoothName==null||p.bluetoothName.isEmpty()){if(connectedBt.isEmpty())return false;}else{boolean ok=false;for(String n:connectedBt)if(p.bluetoothName.equalsIgnoreCase(n)){ok=true;break;}if(!ok)return false;}}
        return any;
    }
    private boolean timeMatches(RoutineProfile p){
        Calendar c=Calendar.getInstance();int idx=(c.get(Calendar.DAY_OF_WEEK)+5)%7;if((p.daysMask&(1<<idx))==0)return false;
        int m=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);return p.startMin<=p.endMin?(m>=p.startMin&&m<=p.endMin):(m>=p.startMin||m<=p.endMin);
    }
    private String currentSsid(){try{for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.getTransportInfo() instanceof WifiInfo){String s=((WifiInfo)c.getTransportInfo()).getSSID();if(s!=null&&!"<unknown ssid>".equals(s)){if(s.startsWith("\"")&&s.endsWith("\""))s=s.substring(1,s.length()-1);return s;}}}}catch(Exception ignored){}return null;}
    private Intent battery(){return registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}
    private boolean isCharging(){Intent i=battery();if(i==null)return false;int s=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);return s==BatteryManager.BATTERY_STATUS_CHARGING||s==BatteryManager.BATTERY_STATUS_FULL;}
    private int batteryPct(){Intent i=battery();if(i==null)return -1;int l=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);return scale<=0?-1:Math.round(l*100f/scale);}

    private void captureBaseline(){
        SharedPreferences sp=Config.prefs(this);if(sp.getBoolean(Config.BASELINE_VALID,false))return;
        int bright=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);
        int mode=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        int rot=Settings.System.getInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,1);
        int timeout=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,30000);
        sp.edit().putBoolean(Config.BASELINE_VALID,true)
                .putInt(Config.ORIGINAL_MEDIA,am.getStreamVolume(AudioManager.STREAM_MUSIC))
                .putInt(Config.ORIGINAL_RING,am.getStreamVolume(AudioManager.STREAM_RING))
                .putInt(Config.ORIGINAL_NOTIFICATION,am.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                .putInt(Config.ORIGINAL_BRIGHTNESS,bright).putInt(Config.ORIGINAL_BRIGHTNESS_MODE,mode)
                .putInt(Config.ORIGINAL_ROTATION,rot).putInt(Config.ORIGINAL_TIMEOUT,timeout).apply();
    }

    private void applyActions(List<RoutineProfile> active){
        captureBaseline();SharedPreferences sp=Config.prefs(this);boolean restoreWanted=sp.getBoolean(Config.BASELINE_RESTORE,false);
        int mediaPct=101,ringPct=101,brightPct=101,timeoutSec=Integer.MAX_VALUE,rotation=-1;
        for(RoutineProfile p:active){restoreWanted|=p.restore;if(p.mediaEnabled)mediaPct=Math.min(mediaPct,p.mediaPercent);if(p.ringEnabled)ringPct=Math.min(ringPct,p.ringPercent);if(p.brightnessEnabled)brightPct=Math.min(brightPct,p.brightnessPercent);if(p.timeoutEnabled)timeoutSec=Math.min(timeoutSec,p.timeoutSec);if(rotation==-1&&p.rotationMode!=-1)rotation=p.rotationMode;}
        sp.edit().putBoolean(Config.BASELINE_RESTORE,restoreWanted).apply();
        try{if(mediaPct<=100)lower(AudioManager.STREAM_MUSIC,mediaPct);if(ringPct<=100){lower(AudioManager.STREAM_RING,ringPct);lower(AudioManager.STREAM_NOTIFICATION,ringPct);}}catch(Exception ignored){}
        if(Settings.System.canWrite(this)){
            try{
                if(brightPct<=100){Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.max(1,Math.round(255*brightPct/100f)));}
                if(rotation!=-1)Settings.System.putInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,rotation);
                if(timeoutSec!=Integer.MAX_VALUE)Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,timeoutSec*1000);
            }catch(Exception ignored){}
        }
    }
    private void lower(int stream,int pct){int max=am.getStreamMaxVolume(stream),cur=am.getStreamVolume(stream),to=Math.round(max*Math.max(0,Math.min(100,pct))/100f);if(cur>to)am.setStreamVolume(stream,to,0);}

    private void restoreBaseline(){
        SharedPreferences sp=Config.prefs(this);if(!sp.getBoolean(Config.BASELINE_VALID,false))return;
        boolean restore=sp.getBoolean(Config.BASELINE_RESTORE,false);
        if(restore){
            try{am.setStreamVolume(AudioManager.STREAM_MUSIC,sp.getInt(Config.ORIGINAL_MEDIA,am.getStreamVolume(AudioManager.STREAM_MUSIC)),0);am.setStreamVolume(AudioManager.STREAM_RING,sp.getInt(Config.ORIGINAL_RING,am.getStreamVolume(AudioManager.STREAM_RING)),0);am.setStreamVolume(AudioManager.STREAM_NOTIFICATION,sp.getInt(Config.ORIGINAL_NOTIFICATION,am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)),0);}catch(Exception ignored){}
            if(Settings.System.canWrite(this))try{
                Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,sp.getInt(Config.ORIGINAL_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
                Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,sp.getInt(Config.ORIGINAL_BRIGHTNESS,128));
                Settings.System.putInt(getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,sp.getInt(Config.ORIGINAL_ROTATION,1));
                Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,sp.getInt(Config.ORIGINAL_TIMEOUT,30000));
            }catch(Exception ignored){}
        }
        sp.edit().putBoolean(Config.BASELINE_VALID,false).putBoolean(Config.BASELINE_RESTORE,false).apply();
    }

    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,AutomationService.class).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_mode_wifi).setContentTitle("Pixel Routinen").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stoppen",stop).build()).build();
    }

    @Override public void onDestroy(){
        h.removeCallbacksAndMessages(null);if(netCb!=null)try{cm.unregisterNetworkCallback(netCb);}catch(Exception ignored){}
        if(locListener!=null)try{lm.removeUpdates(locListener);}catch(Exception ignored){}
        if(powerRx!=null)try{unregisterReceiver(powerRx);}catch(Exception ignored){}if(btRx!=null)try{unregisterReceiver(btRx);}catch(Exception ignored){}
        if(bt!=null)for(Map.Entry<Integer,BluetoothProfile> e:btProfiles.entrySet())try{bt.closeProfileProxy(e.getKey(),e.getValue());}catch(Exception ignored){}
        if(!Config.prefs(this).getBoolean(Config.ENGINE_ENABLED,false))restoreBaseline();
        Config.prefs(this).edit().putBoolean(Config.SERVICE_RUNNING,false).apply();super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
