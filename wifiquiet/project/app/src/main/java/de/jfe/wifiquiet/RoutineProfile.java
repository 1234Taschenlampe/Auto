package de.jfe.wifiquiet;

import org.json.JSONObject;
import java.util.UUID;

public class RoutineProfile {
    public String id=UUID.randomUUID().toString(), name="Neue Routine";
    public boolean enabled=true;

    public boolean wifiEnabled=false; public String wifiSsid="";
    public boolean timeEnabled=false; public int startMin=480,endMin=960,daysMask=127;
    public boolean locationEnabled=false; public double latitude=0,longitude=0; public float radiusM=250;
    public int chargingMode=0;
    public boolean batteryEnabled=false; public int batteryMin=0,batteryMax=100;

    // 0 = aus, 1 = Gerät verbunden (Zustand), 2 = Verbindung verloren (Ereignis)
    public int bluetoothMode=0; public String bluetoothName="";

    public boolean dndEnabled=false; public int dndMode=0;
    public boolean mediaEnabled=false; public int mediaPercent=20;
    public boolean ringEnabled=false; public int ringPercent=10;
    public boolean brightnessEnabled=false; public int brightnessPercent=40;
    public int rotationMode=-1;
    public boolean timeoutEnabled=false; public int timeoutSec=60;
    public boolean lockScreenEnabled=false;
    public boolean restore=true;
    public String zenRuleId="";

    public JSONObject json(){
        JSONObject o=new JSONObject();
        try{
            o.put("id",id);o.put("name",name);o.put("enabled",enabled);
            o.put("wifiEnabled",wifiEnabled);o.put("wifiSsid",wifiSsid);
            o.put("timeEnabled",timeEnabled);o.put("startMin",startMin);o.put("endMin",endMin);o.put("daysMask",daysMask);
            o.put("locationEnabled",locationEnabled);o.put("latitude",latitude);o.put("longitude",longitude);o.put("radiusM",radiusM);
            o.put("chargingMode",chargingMode);o.put("batteryEnabled",batteryEnabled);o.put("batteryMin",batteryMin);o.put("batteryMax",batteryMax);
            o.put("bluetoothMode",bluetoothMode);o.put("bluetoothEnabled",bluetoothMode!=0);o.put("bluetoothName",bluetoothName);
            o.put("dndEnabled",dndEnabled);o.put("dndMode",dndMode);
            o.put("mediaEnabled",mediaEnabled);o.put("mediaPercent",mediaPercent);
            o.put("ringEnabled",ringEnabled);o.put("ringPercent",ringPercent);
            o.put("brightnessEnabled",brightnessEnabled);o.put("brightnessPercent",brightnessPercent);
            o.put("rotationMode",rotationMode);o.put("timeoutEnabled",timeoutEnabled);o.put("timeoutSec",timeoutSec);
            o.put("lockScreenEnabled",lockScreenEnabled);o.put("restore",restore);o.put("zenRuleId",zenRuleId);
        }catch(Exception ignored){}
        return o;
    }

    public static RoutineProfile from(JSONObject o){
        RoutineProfile p=new RoutineProfile();
        p.id=o.optString("id",p.id);p.name=o.optString("name",p.name);p.enabled=o.optBoolean("enabled",true);
        p.wifiEnabled=o.optBoolean("wifiEnabled");p.wifiSsid=o.optString("wifiSsid","");
        p.timeEnabled=o.optBoolean("timeEnabled");p.startMin=o.optInt("startMin",480);p.endMin=o.optInt("endMin",960);p.daysMask=o.optInt("daysMask",127);
        p.locationEnabled=o.optBoolean("locationEnabled");p.latitude=o.optDouble("latitude",0);p.longitude=o.optDouble("longitude",0);p.radiusM=(float)o.optDouble("radiusM",250);
        p.chargingMode=o.optInt("chargingMode",0);p.batteryEnabled=o.optBoolean("batteryEnabled");p.batteryMin=o.optInt("batteryMin",0);p.batteryMax=o.optInt("batteryMax",100);
        p.bluetoothMode=o.has("bluetoothMode")?o.optInt("bluetoothMode",0):(o.optBoolean("bluetoothEnabled")?1:0);
        p.bluetoothName=o.optString("bluetoothName","");
        p.dndEnabled=o.optBoolean("dndEnabled",false);p.dndMode=o.optInt("dndMode",0);
        p.mediaEnabled=o.optBoolean("mediaEnabled");p.mediaPercent=o.optInt("mediaPercent",20);
        p.ringEnabled=o.optBoolean("ringEnabled");p.ringPercent=o.optInt("ringPercent",10);
        p.brightnessEnabled=o.optBoolean("brightnessEnabled");p.brightnessPercent=o.optInt("brightnessPercent",40);
        p.rotationMode=o.optInt("rotationMode",-1);p.timeoutEnabled=o.optBoolean("timeoutEnabled");p.timeoutSec=o.optInt("timeoutSec",60);
        p.lockScreenEnabled=o.optBoolean("lockScreenEnabled",false);
        p.restore=o.optBoolean("restore",true);p.zenRuleId=o.optString("zenRuleId","");
        return p;
    }

    public boolean isEventRoutine(){ return bluetoothMode==2; }
}
