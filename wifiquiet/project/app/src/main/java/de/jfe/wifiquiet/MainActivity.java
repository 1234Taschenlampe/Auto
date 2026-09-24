package de.jfe.wifiquiet;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ=301;
    private LinearLayout list,permissions;
    private TextView statusTitle,statusText;
    private MaterialButton engineButton;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        build();
    }

    private void build(){
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,36));
        sc.addView(root);

        root.addView(Ui.title(this,"Routinen"));
        TextView sub=Ui.text(this,"Automationen für dein Pixel – lokal, ruhig und unter deiner Kontrolle.",15);
        sub.setAlpha(.68f);
        LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);
        subLp.setMargins(0,Ui.dp(this,4),0,Ui.dp(this,18));
        root.addView(sub,subLp);

        MaterialCardView stateCard=Ui.card(this);
        LinearLayout state=Ui.cardBody(this);
        statusTitle=Ui.text(this,"Automatik",19);
        statusTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        statusText=Ui.text(this,"",14);
        statusText.setAlpha(.7f);
        state.addView(statusTitle);
        state.addView(statusText);
        Ui.gap(this,state,12);
        engineButton=Ui.primary(this,"Automatik aktivieren");
        state.addView(engineButton);
        stateCard.addView(state);
        root.addView(stateCard);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView h=Ui.section(this,"DEINE ROUTINEN");
        MaterialButton add=Ui.button(this,"+ Neu");
        header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(add);
        root.addView(header);

        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        root.addView(Ui.section(this,"BERECHTIGUNGEN"));
        permissions=new LinearLayout(this);
        permissions.setOrientation(LinearLayout.VERTICAL);
        root.addView(permissions);

        TextView foot=Ui.text(this,"Android 17 · API 37",12);
        foot.setAlpha(.45f);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp=new LinearLayout.LayoutParams(-1,-2);
        footLp.setMargins(0,Ui.dp(this,18),0,0);
        root.addView(foot,footLp);

        setContentView(sc);

        add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class)));
        engineButton.setOnClickListener(v->{
            boolean on=Config.prefs(this).getBoolean(Config.ENGINE_ENABLED,false);
            if(on){
                Config.prefs(this).edit().putBoolean(Config.ENGINE_ENABLED,false).apply();
                stopService(new Intent(this,AutomationService.class));
                refresh();
            }else startEngine();
        });
    }

    @Override protected void onResume(){
        super.onResume();

        SharedPreferences sp=Config.prefs(this);
        if(!sp.contains(Config.ENGINE_ENABLED)){
            boolean anyEnabled=false;
            for(RoutineProfile p:ProfileStore.load(this)) if(p.enabled){anyEnabled=true;break;}
            if(anyEnabled)sp.edit().putBoolean(Config.ENGINE_ENABLED,true).apply();
        }

        if(sp.getBoolean(Config.ENGINE_ENABLED,false) &&
                !sp.getBoolean(Config.SERVICE_RUNNING,false)){
            try{
                startForegroundService(new Intent(this,AutomationService.class)
                        .setAction(AutomationService.ACTION_START));
            }catch(Exception ignored){}
        }
        refresh();
    }

    private void refresh(){
        boolean enabled=Config.prefs(this).getBoolean(Config.ENGINE_ENABLED,false);
        boolean running=Config.prefs(this).getBoolean(Config.SERVICE_RUNNING,false);
        String last=Config.prefs(this).getString(Config.LAST_STATUS,"");

        statusTitle.setText(enabled?"Automatik aktiv":"Automatik pausiert");
        statusText.setText(enabled?(running?(last.isEmpty()?"Warte auf Bedingungen":last):"Dienst wird gestartet"):"Keine Routine wird automatisch ausgelöst");
        engineButton.setText(enabled?"Automatik pausieren":"Automatik aktivieren");

        renderProfiles();
        renderPermissions();
    }

    private void renderProfiles(){
        list.removeAllViews();
        List<RoutineProfile> profiles=ProfileStore.load(this);
        if(profiles.isEmpty()){
            MaterialCardView empty=Ui.card(this);
            LinearLayout body=Ui.cardBody(this);
            TextView t=Ui.text(this,"Noch keine Routine",18);
            t.setTypeface(null,android.graphics.Typeface.BOLD);
            TextView s=Ui.text(this,"Erstelle eine Routine aus Auslösern und Aktionen – zum Beispiel „Kopfhörer getrennt → Display sperren“.",14);
            s.setAlpha(.68f);
            body.addView(t);Ui.gap(this,body,5);body.addView(s);
            empty.addView(body);list.addView(empty);
            return;
        }

        for(RoutineProfile p:profiles){
            MaterialCardView card=Ui.card(this);
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout body=Ui.cardBody(this);

            LinearLayout top=new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=Ui.text(this,p.name,19);
            name.setTypeface(null,android.graphics.Typeface.BOLD);
            TextView badge=Ui.text(this,p.enabled?"Aktiv":"Aus",12);
            badge.setAlpha(p.enabled ? .82f : .45f);
            top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            top.addView(badge);
            body.addView(top);

            TextView when=Ui.text(this,triggerSummary(p),14);
            when.setAlpha(.7f);
            LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-1,-2);
            wlp.setMargins(0,Ui.dp(this,7),0,0);
            body.addView(when,wlp);

            TextView then=Ui.text(this,actionSummary(p),14);
            then.setAlpha(.7f);
            body.addView(then);

            LinearLayout actions=new LinearLayout(this);
            actions.setGravity(Gravity.END);
            MaterialButton pixel=Ui.button(this,"Pixel-Modus");
            MaterialButton edit=Ui.button(this,"Bearbeiten");
            actions.addView(pixel);
            actions.addView(edit);
            LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2);
            alp.setMargins(0,Ui.dp(this,9),0,0);
            body.addView(actions,alp);

            card.addView(body);
            card.setOnClickListener(v->edit(p));
            edit.setOnClickListener(v->edit(p));
            pixel.setEnabled(p.dndEnabled);
            pixel.setAlpha(p.dndEnabled?1f:.45f);
            pixel.setOnClickListener(v->openPixelMode(p));
            list.addView(card);
        }
    }

    private void renderPermissions(){
        permissions.removeAllViews();
        addPermissionRow("Pixel-Modi",notificationAccess()?"Erlaubt":"Nicht erlaubt",
                notificationAccess(),()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        addPermissionRow("Systemeinstellungen",
                Settings.System.canWrite(this)?"Erlaubt":"Für Helligkeit, Drehung und Timeout",
                Settings.System.canWrite(this),
                ()->startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:"+getPackageName()))));
        addPermissionRow("Display sperren",
                DeviceAdminHelper.isActive(this)?"Erlaubt":"Optional für die Aktion „Display sperren“",
                DeviceAdminHelper.isActive(this),
                ()->DeviceAdminHelper.request(this));
    }

    private void addPermissionRow(String title,String detail,boolean ok,Runnable action){
        MaterialCardView card=Ui.card(this);
        LinearLayout body=Ui.cardBody(this);
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout txt=new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView a=Ui.text(this,title,16);
        a.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView b=Ui.text(this,detail,13);
        b.setAlpha(.65f);
        txt.addView(a);txt.addView(b);

        MaterialButton button=Ui.button(this,ok?"Öffnen":"Einrichten");
        row.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(button);
        body.addView(row);
        card.addView(body);
        button.setOnClickListener(v->action.run());
        permissions.addView(card);
    }

    private void edit(RoutineProfile p){
        startActivity(new Intent(this,ProfileEditActivity.class).putExtra("profile_id",p.id));
    }

    private void openPixelMode(RoutineProfile p){
        if(!notificationAccess()){
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        ZenModeManager.ensureRule(this,p);
        ZenModeManager.openRule(this,p);
    }

    private boolean notificationAccess(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        return nm!=null&&nm.isNotificationPolicyAccessGranted();
    }

    private String triggerSummary(RoutineProfile p){
        if(p.bluetoothMode==2)
            return "Wenn · "+(p.bluetoothName.isEmpty()?"Bluetooth getrennt":p.bluetoothName+" getrennt");
        String s=ZenModeManager.summary(p);
        return s.startsWith("Wenn ")?"Wenn · "+s.substring(5):"Wenn · "+s;
    }

    private String actionSummary(RoutineProfile p){
        ArrayList<String> a=new ArrayList<>();
        if(p.lockScreenEnabled)a.add("Display sperren");
        if(p.dndEnabled)a.add("Pixel-Modus");
        if(p.mediaEnabled)a.add("Medien "+p.mediaPercent+" %");
        if(p.ringEnabled)a.add("Klingeln "+p.ringPercent+" %");
        if(p.brightnessEnabled)a.add("Helligkeit "+p.brightnessPercent+" %");
        if(p.rotationMode==0)a.add("Drehung aus");
        if(p.rotationMode==1)a.add("Drehung an");
        if(p.timeoutEnabled)a.add("Timeout "+p.timeoutSec+" s");
        return a.isEmpty()?"Dann · Keine Aktion ausgewählt":"Dann · "+android.text.TextUtils.join(" · ",a);
    }

    private void startEngine(){
        List<RoutineProfile> profiles=ProfileStore.load(this);
        boolean anyDnd=false,anyBt=false,anyLock=false;
        for(RoutineProfile p:profiles){
            anyDnd|=p.dndEnabled;
            anyBt|=p.bluetoothMode!=0;
            anyLock|=p.lockScreenEnabled;
        }

        if(anyDnd&&!notificationAccess()){
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            Toast.makeText(this,"Erlaube zuerst den Zugriff auf Pixel-Modi.",Toast.LENGTH_LONG).show();
            return;
        }

        if(anyLock&&!DeviceAdminHelper.isActive(this)){
            DeviceAdminHelper.request(this);
            Toast.makeText(this,"Erlaube „Display sperren“ und aktiviere danach die Automatik.",Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<String> req=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            req.add(Manifest.permission.ACCESS_FINE_LOCATION);
            req.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)
            req.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            req.add(Manifest.permission.POST_NOTIFICATIONS);
        if(anyBt&&Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            req.add(Manifest.permission.BLUETOOTH_CONNECT);

        if(!req.isEmpty()){
            requestPermissions(req.toArray(new String[0]),REQ);
            return;
        }
        reallyStart();
    }

    private void reallyStart(){
        Config.prefs(this).edit().putBoolean(Config.ENGINE_ENABLED,true).apply();
        for(RoutineProfile p:ProfileStore.load(this))
            if(p.dndEnabled)ZenModeManager.ensureRule(this,p);
        try{
            startForegroundService(new Intent(this,AutomationService.class).setAction(AutomationService.ACTION_START));
        }catch(Exception e){
            Toast.makeText(this,"Automatik konnte nicht gestartet werden.",Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==REQ)reallyStart();
    }
}
