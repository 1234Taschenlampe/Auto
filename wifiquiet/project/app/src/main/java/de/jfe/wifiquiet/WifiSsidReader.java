package de.jfe.wifiquiet;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WifiSsidReader {
    public interface Callback { void onResult(String ssid); }

    private WifiSsidReader(){}

    public static boolean canRead(Context c){
        if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return false;
        LocationManager lm=c.getSystemService(LocationManager.class);
        return lm!=null && lm.isLocationEnabled();
    }

    public static String current(Context c){
        if(!canRead(c)) return null;

        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
        if(cm!=null){
            try{
                Network active=cm.getActiveNetwork();
                if(active!=null){
                    String s=fromCapabilities(cm.getNetworkCapabilities(active));
                    if(s!=null)return s;
                }
                for(Network n:cm.getAllNetworks()){
                    String s=fromCapabilities(cm.getNetworkCapabilities(n));
                    if(s!=null)return s;
                }
            }catch(Exception ignored){}
        }

        // Compatibility fallback. Deprecated since API 31, but still useful on devices
        // that redact the on-demand NetworkCapabilities object.
        try{
            WifiManager wm=c.getSystemService(WifiManager.class);
            if(wm!=null){
                WifiInfo info=wm.getConnectionInfo();
                String s=normalize(info==null?null:info.getSSID());
                if(s!=null)return s;
            }
        }catch(Exception ignored){}
        return null;
    }

    public static void readAsync(Context c, Callback callback){
        String immediate=current(c);
        if(immediate!=null){
            callback.onResult(immediate);
            return;
        }

        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
        if(cm==null||!canRead(c)){
            callback.onResult(null);
            return;
        }

        AtomicBoolean done=new AtomicBoolean(false);
        Handler main=new Handler(Looper.getMainLooper());

        ConnectivityManager.NetworkCallback cb;
        if(Build.VERSION.SDK_INT>=31){
            cb=new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO){
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps){
                    String s=fromCapabilities(caps);
                    if(s!=null&&done.compareAndSet(false,true)){
                        try{cm.unregisterNetworkCallback(this);}catch(Exception ignored){}
                        main.post(()->callback.onResult(s));
                    }
                }
            };
        }else{
            cb=new ConnectivityManager.NetworkCallback(){
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps){
                    String s=fromCapabilities(caps);
                    if(s!=null&&done.compareAndSet(false,true)){
                        try{cm.unregisterNetworkCallback(this);}catch(Exception ignored){}
                        main.post(()->callback.onResult(s));
                    }
                }
            };
        }

        try{
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),cb);
        }catch(Exception e){
            callback.onResult(null);
            return;
        }

        main.postDelayed(()->{
            if(done.compareAndSet(false,true)){
                try{cm.unregisterNetworkCallback(cb);}catch(Exception ignored){}
                callback.onResult(current(c));
            }
        },2500);
    }

    public static String fromCapabilities(NetworkCapabilities caps){
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return null;
        Object info=caps.getTransportInfo();
        if(info instanceof WifiInfo)return normalize(((WifiInfo)info).getSSID());
        return null;
    }

    private static String normalize(String s){
        if(s==null||WifiManager.UNKNOWN_SSID.equals(s)||"<unknown ssid>".equalsIgnoreCase(s))return null;
        if(s.length()>=2&&s.startsWith("\"")&&s.endsWith("\""))s=s.substring(1,s.length()-1);
        s=s.trim();
        return s.isEmpty()?null:s;
    }
}
