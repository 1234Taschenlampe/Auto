package de.jfe.wifiquiet;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ=1001;
    private EditText ssid; private Switch dnd,media,ring,restore; private Spinner dndMode; private SeekBar mediaPct,ringPct; private TextView status,mediaLabel,ringLabel; private boolean pendingStart,pendingRead;
    private final BroadcastReceiver rx=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){refresh();} };

    @Override protected void onCreate(Bundle b){ super.onCreate(b); buildUi(); load(); }
    private TextView tv(String s,int sp){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setPadding(0,10,0,6);return v; }
    private Button button(String s){ Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b; }
    private void buildUi(){
        ScrollView sc=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p=(int)(22*getResources().getDisplayMetrics().density); box.setPadding(p,p,p,p); sc.addView(box);
        TextView title=tv("WifiQuiet – WLAN Ruhe",28); box.addView(title); status=tv("Automatik gestoppt",14); box.addView(status);
        box.addView(tv("WLAN",18)); ssid=new EditText(this); ssid.setHint("SSID, z. B. Schul-WLAN"); ssid.setSingleLine(true); ssid.setInputType(InputType.TYPE_CLASS_TEXT); box.addView(ssid,new LinearLayout.LayoutParams(-1,-2));
        Button current=button("Aktuelles WLAN übernehmen");box.addView(current);
        box.addView(tv("Pixel-Modus / Bitte nicht stören",18)); dnd=new Switch(this);dnd.setText("WLAN Ruhe als Modus aktivieren");dnd.setChecked(true);box.addView(dnd);
        dndMode=new Spinner(this);dndMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Priorität / wichtige Unterbrechungen","Nur Alarme","Keine Unterbrechungen"}));box.addView(dndMode);
        Button modeSettings=button("Pixel-Modus öffnen");box.addView(modeSettings);
        box.addView(tv("Lautstärke",18)); media=new Switch(this);media.setText("Medienlautstärke begrenzen");media.setChecked(true);box.addView(media); mediaLabel=tv("",14);box.addView(mediaLabel);mediaPct=new SeekBar(this);mediaPct.setMax(100);box.addView(mediaPct);
        ring=new Switch(this);ring.setText("Klingeln/Benachrichtigungen begrenzen");ring.setChecked(true);box.addView(ring);ringLabel=tv("",14);box.addView(ringLabel);ringPct=new SeekBar(this);ringPct.setMax(100);box.addView(ringPct);
        restore=new Switch(this);restore.setText("Beim Verlassen vorherige Lautstärke wiederherstellen");restore.setChecked(true);box.addView(restore);
        Button start=button("Automatik starten / aktualisieren");Button stop=button("Automatik stoppen");box.addView(start,new LinearLayout.LayoutParams(-1,(int)(58*getResources().getDisplayMetrics().density)));box.addView(stop);
        box.addView(tv("Die SSID-Erkennung bleibt lokal. Android verlangt dafür präzisen Standortzugriff und einen sichtbaren Vordergrunddienst.",12)); setContentView(sc);
        SeekBar.OnSeekBarChangeListener l=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean f){labels();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};mediaPct.setOnSeekBarChangeListener(l);ringPct.setOnSeekBarChangeListener(l);
        current.setOnClickListener(v->capture()); modeSettings.setOnClickListener(v->{save(); if(dnd.isChecked()&&Build.VERSION.SDK_INT>=35)ZenModeManager.ensureRule(this); ZenModeManager.openModeSettings(this);}); start.setOnClickListener(v->startAuto()); stop.setOnClickListener(v->{stopService(new Intent(this,AutomationService.class));Config.prefs(this).edit().putBoolean(Config.SERVICE_RUNNING,false).putBoolean(Config.PROFILE_ACTIVE,false).apply();refresh();});
    }
    private void load(){ SharedPreferences p=Config.prefs(this);ssid.setText(p.getString(Config.SSID,""));dnd.setChecked(p.getBoolean(Config.DND_ENABLED,true));dndMode.setSelection(Math.max(0,Math.min(2,p.getInt(Config.DND_MODE,0))));media.setChecked(p.getBoolean(Config.MEDIA_ENABLED,true));mediaPct.setProgress(p.getInt(Config.MEDIA_PERCENT,20));ring.setChecked(p.getBoolean(Config.RING_ENABLED,true));ringPct.setProgress(p.getInt(Config.RING_PERCENT,10));restore.setChecked(p.getBoolean(Config.RESTORE,true));labels();refresh(); }
    private void save(){Config.prefs(this).edit().putString(Config.SSID,ssid.getText().toString().trim()).putBoolean(Config.DND_ENABLED,dnd.isChecked()).putInt(Config.DND_MODE,dndMode.getSelectedItemPosition()).putBoolean(Config.MEDIA_ENABLED,media.isChecked()).putInt(Config.MEDIA_PERCENT,mediaPct.getProgress()).putBoolean(Config.RING_ENABLED,ring.isChecked()).putInt(Config.RING_PERCENT,ringPct.getProgress()).putBoolean(Config.RESTORE,restore.isChecked()).apply();}
    private void labels(){mediaLabel.setText("Medien: höchstens "+mediaPct.getProgress()+" %");ringLabel.setText("Klingeln: höchstens "+ringPct.getProgress()+" %");}
    private void startAuto(){save();if(ssid.getText().toString().trim().isEmpty()){Toast.makeText(this,"Bitte WLAN eintragen.",Toast.LENGTH_LONG).show();return;} NotificationManager nm=getSystemService(NotificationManager.class);if(dnd.isChecked()&&!nm.isNotificationPolicyAccessGranted()){startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));Toast.makeText(this,"WifiQuiet bitte den Modus-/DND-Zugriff erlauben.",Toast.LENGTH_LONG).show();return;}List<String> m=missing();if(!m.isEmpty()){pendingStart=true;requestPermissions(m.toArray(new String[0]),REQ);return;}reallyStart();}
    private void reallyStart(){LocationManager lm=getSystemService(LocationManager.class);if(!lm.isLocationEnabled()){startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));Toast.makeText(this,"Standort muss für die WLAN-SSID eingeschaltet sein.",Toast.LENGTH_LONG).show();return;}if(dnd.isChecked()&&Build.VERSION.SDK_INT>=35&&ZenModeManager.ensureRule(this)==null){Toast.makeText(this,"Pixel-Modus konnte nicht angelegt werden.",Toast.LENGTH_LONG).show();return;}ZenModeManager.updateRule(this);startForegroundService(new Intent(this,AutomationService.class).setAction(AutomationService.ACTION_START));Toast.makeText(this,"WLAN-Automatik läuft.",Toast.LENGTH_SHORT).show();}
    private List<String> missing(){List<String>x=new ArrayList<>();if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){x.add(Manifest.permission.ACCESS_FINE_LOCATION);x.add(Manifest.permission.ACCESS_COARSE_LOCATION);}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.NEARBY_WIFI_DEVICES);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)x.add(Manifest.permission.POST_NOTIFICATIONS);return x;}
    private void capture(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingRead=true;List<String>m=missing();requestPermissions(m.toArray(new String[0]),REQ);return;}String s=currentSsid();if(s==null)Toast.makeText(this,"SSID nicht lesbar. Präzisen Standortzugriff prüfen.",Toast.LENGTH_LONG).show();else ssid.setText(s);}
    private String currentSsid(){try{ConnectivityManager c=getSystemService(ConnectivityManager.class);for(Network n:c.getAllNetworks()){NetworkCapabilities cp=c.getNetworkCapabilities(n);if(cp!=null&&cp.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&cp.getTransportInfo() instanceof WifiInfo){String s=((WifiInfo)cp.getTransportInfo()).getSSID();if(s!=null&&!"<unknown ssid>".equals(s)){if(s.startsWith("\"")&&s.endsWith("\""))s=s.substring(1,s.length()-1);return s;}}}}catch(Exception ignored){}return null;}
    private void refresh(){SharedPreferences p=Config.prefs(this);boolean r=p.getBoolean(Config.SERVICE_RUNNING,false),a=p.getBoolean(Config.PROFILE_ACTIVE,false);String w=p.getString(Config.LAST_SSID,"");status.setText(!r?"Automatik gestoppt":a?"WLAN Ruhe aktiv":"Automatik aktiv – Ziel-WLAN nicht erkannt"+(w.isEmpty()?"":"\nWLAN: "+w));}
    @Override protected void onStart(){super.onStart();IntentFilter f=new IntentFilter(AutomationService.ACTION_STATUS);if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(rx,f);refresh();}
    @Override protected void onStop(){try{unregisterReceiver(rx);}catch(Exception ignored){}super.onStop();}
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r!=REQ)return;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingStart=pendingRead=false;Toast.makeText(this,"Präziser Standortzugriff ist erforderlich.",Toast.LENGTH_LONG).show();return;}if(pendingRead){pendingRead=false;capture();}if(pendingStart){pendingStart=false;reallyStart();}}
}
