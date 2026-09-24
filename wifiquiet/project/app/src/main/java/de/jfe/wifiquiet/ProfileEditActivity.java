package de.jfe.wifiquiet;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.util.*;

public class ProfileEditActivity extends Activity {
    private static final int REQ_LOC=210,REQ_BT=211;
    private RoutineProfile p;
    private EditText name,wifi,lat,lon,radius,btName,battMin,battMax;
    private Switch enabled,wifiOn,timeOn,locOn,battOn,btOn,dndOn,mediaOn,ringOn,brightOn,timeoutOn,restore;
    private Spinner charging,dndMode,rotation,timeout;
    private SeekBar media,ring,bright;
    private Button startBtn,endBtn;
    private CheckBox[] day=new CheckBox[7];
    private int startMin,endMin;
    private String pending="";

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String id=getIntent().getStringExtra("profile_id");
        String ruleId=getIntent().getStringExtra(NotificationManager.EXTRA_AUTOMATIC_RULE_ID);
        p=ruleId!=null?ProfileStore.findByRule(this,ruleId):(id==null?null:ProfileStore.get(this,id));
        if(p==null)p=new RoutineProfile();
        startMin=p.startMin;endMin=p.endMin;
        build();load();
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(10),0,dp(5));return v;}
    private Button b(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);return x;}
    private EditText e(String hint){EditText x=new EditText(this);x.setHint(hint);x.setSingleLine(true);return x;}
    private Switch sw(String s){Switch x=new Switch(this);x.setText(s);return x;}
    private Spinner spin(String...items){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items));return s;}
    private void build(){
        ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(18),dp(20),dp(30));sc.addView(box);
        box.addView(t("Modus bearbeiten",28));name=e("Name, z. B. Schule");box.addView(name);enabled=sw("Modus aktiviert");box.addView(enabled);

        box.addView(t("WENN – Bedingungen",22));
        wifiOn=sw("Bestimmtes WLAN");box.addView(wifiOn);wifi=e("SSID");box.addView(wifi);Button curWifi=b("Aktuelles WLAN übernehmen");box.addView(curWifi);
        timeOn=sw("Uhrzeit und Wochentage");box.addView(timeOn);LinearLayout tr=new LinearLayout(this);startBtn=b("Start");endBtn=b("Ende");tr.addView(startBtn,new LinearLayout.LayoutParams(0,-2,1));tr.addView(endBtn,new LinearLayout.LayoutParams(0,-2,1));box.addView(tr);
        LinearLayout drow=new LinearLayout(this);String[] dn={"Mo","Di","Mi","Do","Fr","Sa","So"};for(int i=0;i<7;i++){day[i]=new CheckBox(this);day[i].setText(dn[i]);drow.addView(day[i],new LinearLayout.LayoutParams(0,-2,1));}box.addView(drow);
        locOn=sw("Standort / Radius");box.addView(locOn);lat=e("Breitengrad");lon=e("Längengrad");radius=e("Radius in Metern");lat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);lon.setInputType(lat.getInputType());radius.setInputType(InputType.TYPE_CLASS_NUMBER);box.addView(lat);box.addView(lon);box.addView(radius);Button curLoc=b("Aktuellen Standort übernehmen");box.addView(curLoc);
        box.addView(t("Ladezustand",16));charging=spin("Egal","Nur wenn geladen wird","Nur wenn nicht geladen wird");box.addView(charging);
        battOn=sw("Akkustand begrenzen");box.addView(battOn);LinearLayout br=new LinearLayout(this);battMin=e("Min %");battMax=e("Max %");battMin.setInputType(InputType.TYPE_CLASS_NUMBER);battMax.setInputType(InputType.TYPE_CLASS_NUMBER);br.addView(battMin,new LinearLayout.LayoutParams(0,-2,1));br.addView(battMax,new LinearLayout.LayoutParams(0,-2,1));box.addView(br);
        btOn=sw("Bluetooth-Gerät verbunden");box.addView(btOn);btName=e("Gerätename; leer = beliebiges Gerät");box.addView(btName);Button pickBt=b("Gekoppeltes Gerät auswählen");box.addView(pickBt);

        box.addView(t("DANN – Aktionen",22));
        dndOn=sw("Pixel-Modus / Bitte nicht stören");box.addView(dndOn);dndMode=spin("Priorität / wichtige Unterbrechungen","Nur Alarme","Keine Unterbrechungen");box.addView(dndMode);
        mediaOn=sw("Medienlautstärke begrenzen");box.addView(mediaOn);media=new SeekBar(this);media.setMax(100);box.addView(media);
        ringOn=sw("Klingeln & Benachrichtigungen begrenzen");box.addView(ringOn);ring=new SeekBar(this);ring.setMax(100);box.addView(ring);
        brightOn=sw("Helligkeit setzen");box.addView(brightOn);bright=new SeekBar(this);bright.setMax(100);box.addView(bright);
        Button write=b("Zugriff für Helligkeit/Drehung/Timeout");box.addView(write);
        box.addView(t("Automatische Drehung",16));rotation=spin("Unverändert","Aus","An");box.addView(rotation);
        timeoutOn=sw("Bildschirm-Timeout setzen");box.addView(timeoutOn);timeout=spin("15 Sekunden","30 Sekunden","1 Minute","2 Minuten","5 Minuten","10 Minuten");box.addView(timeout);
        restore=sw("Vorherige Einstellungen wiederherstellen, wenn der Modus endet");box.addView(restore);

        Button save=b("Speichern");box.addView(save,new LinearLayout.LayoutParams(-1,dp(58)));Button pixel=b("Im Pixel-Menü „Modi“ öffnen");box.addView(pixel);
        if(ProfileStore.get(this,p.id)!=null){Button del=b("Modus löschen");box.addView(del);del.setOnClickListener(v->{ProfileStore.delete(this,p.id);finish();});}
        box.addView(t("Alle aktivierten WENN-Bedingungen müssen gleichzeitig erfüllt sein. Ohne automatische Bedingung kann der Modus manuell im Pixel-Menü gestartet werden.",12));
        setContentView(sc);

        curWifi.setOnClickListener(v->captureWifi());curLoc.setOnClickListener(v->captureLocation());pickBt.setOnClickListener(v->pickBluetooth());
        startBtn.setOnClickListener(v->pickTime(true));endBtn.setOnClickListener(v->pickTime(false));
        write.setOnClickListener(v->{Intent i=new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName()));startActivity(i);});
        save.setOnClickListener(v->save());
        pixel.setOnClickListener(v->{save();RoutineProfile q=ProfileStore.get(this,p.id);if(q!=null){ZenModeManager.ensureRule(this,q);ZenModeManager.openRule(this,q);}});
    }
    private void load(){
        name.setText(p.name);enabled.setChecked(p.enabled);wifiOn.setChecked(p.wifiEnabled);wifi.setText(p.wifiSsid);timeOn.setChecked(p.timeEnabled);
        updateTimes();for(int i=0;i<7;i++)day[i].setChecked((p.daysMask&(1<<i))!=0);
        locOn.setChecked(p.locationEnabled);lat.setText(String.valueOf(p.latitude));lon.setText(String.valueOf(p.longitude));radius.setText(String.valueOf(Math.round(p.radiusM)));
        charging.setSelection(p.chargingMode);battOn.setChecked(p.batteryEnabled);battMin.setText(String.valueOf(p.batteryMin));battMax.setText(String.valueOf(p.batteryMax));
        btOn.setChecked(p.bluetoothEnabled);btName.setText(p.bluetoothName);dndOn.setChecked(p.dndEnabled);dndMode.setSelection(p.dndMode);
        mediaOn.setChecked(p.mediaEnabled);media.setProgress(p.mediaPercent);ringOn.setChecked(p.ringEnabled);ring.setProgress(p.ringPercent);
        brightOn.setChecked(p.brightnessEnabled);bright.setProgress(p.brightnessPercent);rotation.setSelection(p.rotationMode+1);
        timeoutOn.setChecked(p.timeoutEnabled);timeout.setSelection(timeoutIndex(p.timeoutSec));restore.setChecked(p.restore);
    }
    private void save(){
        p.name=name.getText().toString().trim();if(p.name.isEmpty())p.name="Unbenannter Modus";p.enabled=enabled.isChecked();
        p.wifiEnabled=wifiOn.isChecked();p.wifiSsid=wifi.getText().toString().trim();p.timeEnabled=timeOn.isChecked();p.startMin=startMin;p.endMin=endMin;p.daysMask=0;for(int i=0;i<7;i++)if(day[i].isChecked())p.daysMask|=1<<i;
        p.locationEnabled=locOn.isChecked();p.latitude=dbl(lat,0);p.longitude=dbl(lon,0);p.radiusM=(float)dbl(radius,250);
        p.chargingMode=charging.getSelectedItemPosition();p.batteryEnabled=battOn.isChecked();p.batteryMin=integer(battMin,0);p.batteryMax=integer(battMax,100);
        p.bluetoothEnabled=btOn.isChecked();p.bluetoothName=btName.getText().toString().trim();p.dndEnabled=dndOn.isChecked();p.dndMode=dndMode.getSelectedItemPosition();
        p.mediaEnabled=mediaOn.isChecked();p.mediaPercent=media.getProgress();p.ringEnabled=ringOn.isChecked();p.ringPercent=ring.getProgress();
        p.brightnessEnabled=brightOn.isChecked();p.brightnessPercent=bright.getProgress();p.rotationMode=rotation.getSelectedItemPosition()-1;
        p.timeoutEnabled=timeoutOn.isChecked();p.timeoutSec=timeoutSeconds(timeout.getSelectedItemPosition());p.restore=restore.isChecked();
        ProfileStore.upsert(this,p);
        if(p.dndEnabled){NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null&&nm.isNotificationPolicyAccessGranted())ZenModeManager.ensureRule(this,p);}
        try{startForegroundService(new Intent(this,AutomationService.class).setAction(AutomationService.ACTION_START));}catch(Exception ignored){}
        Toast.makeText(this,"Modus gespeichert.",Toast.LENGTH_SHORT).show();
    }
    private void pickTime(boolean start){int m=start?startMin:endMin;new TimePickerDialog(this,(v,h,min)->{if(start)startMin=h*60+min;else endMin=h*60+min;updateTimes();},m/60,m%60,true).show();}
    private void updateTimes(){startBtn.setText("Start "+fmt(startMin));endBtn.setText("Ende "+fmt(endMin));}
    private String fmt(int m){return String.format(Locale.GERMANY,"%02d:%02d",(m/60)%24,m%60);}
    private int integer(EditText e,int def){try{return Math.max(0,Math.min(100,Integer.parseInt(e.getText().toString().trim())));}catch(Exception x){return def;}}
    private double dbl(EditText e,double def){try{return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}catch(Exception x){return def;}}
    private int timeoutSeconds(int i){int[] a={15,30,60,120,300,600};return a[Math.max(0,Math.min(a.length-1,i))];}
    private int timeoutIndex(int s){int[]a={15,30,60,120,300,600};for(int i=0;i<a.length;i++)if(a[i]==s)return i;return 2;}

    private void captureWifi(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pending="wifi";requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOC);return;}
        String s=currentSsid();if(s==null)Toast.makeText(this,"WLAN-SSID nicht lesbar.",Toast.LENGTH_LONG).show();else{wifi.setText(s);wifiOn.setChecked(true);}
    }
    private String currentSsid(){try{ConnectivityManager cm=getSystemService(ConnectivityManager.class);for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.getTransportInfo() instanceof WifiInfo){String s=((WifiInfo)c.getTransportInfo()).getSSID();if(s!=null&&!"<unknown ssid>".equals(s)){if(s.startsWith("\"")&&s.endsWith("\""))s=s.substring(1,s.length()-1);return s;}}}}catch(Exception ignored){}return null;}
    private void captureLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pending="loc";requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOC);return;}
        LocationManager lm=getSystemService(LocationManager.class);if(!lm.isLocationEnabled()){startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;}
        Location best=null;for(String pr:lm.getProviders(true))try{Location q=lm.getLastKnownLocation(pr);if(q!=null&&(best==null||q.getAccuracy()<best.getAccuracy()))best=q;}catch(Exception ignored){}
        if(best!=null){setLoc(best);return;}
        if(Build.VERSION.SDK_INT>=30)try{lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER,null,getMainExecutor(),this::setLoc);return;}catch(Exception ignored){}
        Toast.makeText(this,"Noch kein Standort verfügbar.",Toast.LENGTH_LONG).show();
    }
    private void setLoc(Location l){if(l==null)return;lat.setText(String.format(Locale.US,"%.6f",l.getLatitude()));lon.setText(String.format(Locale.US,"%.6f",l.getLongitude()));locOn.setChecked(true);}
    private void pickBluetooth(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){pending="bt";requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);return;}
        try{BluetoothManager bm=getSystemService(BluetoothManager.class);BluetoothAdapter a=bm.getAdapter();if(a==null){Toast.makeText(this,"Bluetooth nicht verfügbar.",Toast.LENGTH_LONG).show();return;}ArrayList<String> names=new ArrayList<>();names.add("Beliebiges verbundenes Bluetooth-Gerät");for(BluetoothDevice d:a.getBondedDevices()){String n=d.getName();if(n!=null&&!n.isEmpty())names.add(n);}new AlertDialog.Builder(this).setTitle("Bluetooth-Gerät").setItems(names.toArray(new String[0]),(d,w)->{btName.setText(w==0?"":names.get(w));btOn.setChecked(true);}).show();}catch(Exception e){Toast.makeText(this,"Bluetooth-Geräte konnten nicht gelesen werden.",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int r,String[]ps,int[]g){super.onRequestPermissionsResult(r,ps,g);String x=pending;pending="";if(r==REQ_LOC){if("wifi".equals(x))captureWifi();else if("loc".equals(x))captureLocation();}else if(r==REQ_BT)pickBluetooth();}
}
