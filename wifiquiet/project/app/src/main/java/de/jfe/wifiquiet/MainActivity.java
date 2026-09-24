package de.jfe.wifiquiet;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ=301;
    private LinearLayout list; private TextView status;
    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(6),0,dp(6));return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private void build(){
        ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(18),dp(20),dp(28));sc.addView(box);
        box.addView(t("Pixel Routinen",30));box.addView(t("Eigene Modi wie bei Samsung: WENN Bedingungen erfüllt sind, DANN Aktionen ausführen.",14));
        status=t("",14);box.addView(status);
        Button add=btn("+ Neuer Modus");Button start=btn("Automatik aktivieren");Button stop=btn("Automatik stoppen");Button access=btn("Pixel-Modi / DND-Zugriff");Button write=btn("Systemeinstellungen-Zugriff");
        box.addView(add,new LinearLayout.LayoutParams(-1,dp(56)));box.addView(start);box.addView(stop);box.addView(access);box.addView(write);box.addView(t("Meine Modi",22));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);box.addView(list);setContentView(sc);
        add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class)));
        start.setOnClickListener(v->startEngine());stop.setOnClickListener(v->{Config.prefs(this).edit().putBoolean(Config.ENGINE_ENABLED,false).apply();stopService(new Intent(this,AutomationService.class));refresh();});
        access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        write.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));
    }
    @Override protected void onResume(){super.onResume();refresh();}
    private void refresh(){
        boolean on=Config.prefs(this).getBoolean(Config.ENGINE_ENABLED,false),run=Config.prefs(this).getBoolean(Config.SERVICE_RUNNING,false);
        status.setText(on?(run?"Automatik läuft":"Automatik aktiviert – Dienst wird beim nächsten Start geladen"):"Automatik ist aus");
        list.removeAllViews();
        for(RoutineProfile p:ProfileStore.load(this)){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(12));card.setBackgroundColor(0x11000000);
            TextView name=t(p.name+(p.enabled?"":" (deaktiviert)"),19);card.addView(name);card.addView(t(ZenModeManager.summary(p),13));
            LinearLayout row=new LinearLayout(this);Button edit=btn("Bearbeiten");Button pixel=btn("Pixel-Modus");Button del=btn("Löschen");row.addView(edit,new LinearLayout.LayoutParams(0,-2,1));row.addView(pixel,new LinearLayout.LayoutParams(0,-2,1));row.addView(del,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
            edit.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class).putExtra("profile_id",p.id)));
            pixel.setOnClickListener(v->{NotificationManager nm=getSystemService(NotificationManager.class);if(nm==null||!nm.isNotificationPolicyAccessGranted()){startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));return;}ZenModeManager.ensureRule(this,p);ZenModeManager.openRule(this,p);});
            del.setOnClickListener(v->{new AlertDialog.Builder(this).setTitle("Modus löschen?").setMessage(p.name).setPositiveButton("Löschen",(d,w)->{ProfileStore.delete(this,p.id);refresh();}).setNegativeButton("Abbrechen",null).show();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));list.addView(card,lp);
        }
    }
    private void startEngine(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        boolean anyDnd=false,anyBt=false;for(RoutineProfile p:ProfileStore.load(this)){anyDnd|=p.dndEnabled;anyBt|=p.bluetoothEnabled;}
        if(anyDnd&&(nm==null||!nm.isNotificationPolicyAccessGranted())){startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));Toast.makeText(this,"Bitte zuerst Pixel-Modi/DND-Zugriff erlauben.",Toast.LENGTH_LONG).show();return;}
        ArrayList<String> req=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){req.add(Manifest.permission.ACCESS_FINE_LOCATION);req.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);
        if(anyBt&&Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(!req.isEmpty()){requestPermissions(req.toArray(new String[0]),REQ);return;}
        reallyStart();
    }
    private void reallyStart(){
        Config.prefs(this).edit().putBoolean(Config.ENGINE_ENABLED,true).apply();
        for(RoutineProfile p:ProfileStore.load(this))if(p.dndEnabled)ZenModeManager.ensureRule(this,p);
        try{startForegroundService(new Intent(this,AutomationService.class).setAction(AutomationService.ACTION_START));}catch(Exception e){Toast.makeText(this,"Dienst konnte nicht gestartet werden: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        refresh();
    }
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r==REQ)reallyStart();}
}
