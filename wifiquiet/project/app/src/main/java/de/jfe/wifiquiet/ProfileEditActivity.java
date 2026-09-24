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
import android.view.*;
import android.widget.*;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.*;

public class ProfileEditActivity extends Activity {
    private static final int REQ_LOC=210,REQ_BT=211;

    private RoutineProfile p;
    private TextInputEditText name,wifi,lat,lon,radius,btName,battMin,battMax;
    private MaterialSwitch enabled,wifiOn,timeOn,locOn,battOn,dndOn,mediaOn,ringOn,brightOn,timeoutOn,lockOn,restore;
    private MaterialAutoCompleteTextView charging,btMode,dndMode,rotation,timeout;
    private Slider media,ring,bright;
    private MaterialButton startBtn,endBtn,adminBtn;
    private Chip[] day=new Chip[7];
    private int startMin,endMin;
    private String pending="";
    private TextView eventHint,adminStatus;

    private final String[] chargingItems={"Egal","Nur wenn geladen wird","Nur wenn nicht geladen wird"};
    private final String[] btItems={"Aus","Gerät verbunden","Verbindung verloren"};
    private final String[] dndItems={"Wichtige Unterbrechungen","Nur Alarme","Keine Unterbrechungen"};
    private final String[] rotationItems={"Unverändert","Aus","An"};
    private final String[] timeoutItems={"15 Sekunden","30 Sekunden","1 Minute","2 Minuten","5 Minuten","10 Minuten"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);

        String id=getIntent().getStringExtra("profile_id");
        String ruleId=getIntent().getStringExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_ID);
        p=ruleId!=null?ProfileStore.findByRule(this,ruleId):(id==null?null:ProfileStore.get(this,id));
        if(p==null)p=new RoutineProfile();

        startMin=p.startMin;
        endMin=p.endMin;
        build();
        load();
    }

    private TextInputLayout input(String hint,boolean numeric){
        TextInputLayout l=Ui.input(this,hint);
        TextInputEditText e=Ui.edit(l);
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return l;
    }

    private TextInputLayout dropdown(String hint,String[] items){
        TextInputLayout l=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);
        l.setHint(hint);
        l.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        l.setBoxCornerRadii(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16));
        MaterialAutoCompleteTextView v=new MaterialAutoCompleteTextView(l.getContext());
        v.setInputType(InputType.TYPE_NULL);
        v.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,items));
        l.addView(v,new TextInputLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,Ui.dp(this,5),0,Ui.dp(this,7));
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout body(){
        return Ui.cardBody(this);
    }

    private void addCard(LinearLayout root,String title,LinearLayout body){
        MaterialCardView c=Ui.card(this);
        TextView h=Ui.text(this,title,18);
        h.setTypeface(null,android.graphics.Typeface.BOLD);
        body.addView(h,0);
        c.addView(body);
        root.addView(c);
    }

    private void build(){
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,36));
        sc.addView(root);

        MaterialButton back=Ui.button(this,"← Zurück");
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);
        blp.setMargins(0,0,0,Ui.dp(this,8));
        root.addView(back,blp);
        back.setOnClickListener(v->finish());

        root.addView(Ui.title(this,"Routine"));
        TextView intro=Ui.text(this,"Definiere Auslöser und wähle genau die Aktionen, die danach passieren sollen.",15);
        intro.setAlpha(.68f);
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(-1,-2);
        ilp.setMargins(0,Ui.dp(this,4),0,Ui.dp(this,18));
        root.addView(intro,ilp);

        LinearLayout general=body();
        TextInputLayout nameLayout=input("Name",false);
        name=Ui.edit(nameLayout);
        general.addView(nameLayout);
        enabled=Ui.toggle(this,"Routine aktiviert");
        general.addView(enabled);
        addCard(root,"Allgemein",general);

        root.addView(Ui.section(this,"WENN"));

        LinearLayout wifiBody=body();
        wifiOn=Ui.toggle(this,"Bestimmtes WLAN");
        wifiBody.addView(wifiOn);
        TextInputLayout wifiLayout=input("WLAN-Name (SSID)",false);
        wifi=Ui.edit(wifiLayout);
        wifiBody.addView(wifiLayout);
        MaterialButton currentWifi=Ui.button(this,"Aktuelles WLAN übernehmen");
        wifiBody.addView(currentWifi);
        addCard(root,"WLAN",wifiBody);

        LinearLayout timeBody=body();
        timeOn=Ui.toggle(this,"Zeitfenster verwenden");
        timeBody.addView(timeOn);
        LinearLayout tr=new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        startBtn=Ui.button(this,"Start");
        endBtn=Ui.button(this,"Ende");
        tr.addView(startBtn,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams eLp=new LinearLayout.LayoutParams(0,-2,1);
        eLp.setMargins(Ui.dp(this,8),0,0,0);
        tr.addView(endBtn,eLp);
        timeBody.addView(tr);

        ChipGroup days=new ChipGroup(this);
        days.setSingleSelection(false);
        days.setSelectionRequired(false);
        String[] dn={"Mo","Di","Mi","Do","Fr","Sa","So"};
        for(int i=0;i<7;i++){
            day[i]=new Chip(this);
            day[i].setText(dn[i]);
            day[i].setCheckable(true);
            days.addView(day[i]);
        }
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2);
        dlp.setMargins(0,Ui.dp(this,8),0,0);
        timeBody.addView(days,dlp);
        addCard(root,"Zeit",timeBody);

        LinearLayout locBody=body();
        locOn=Ui.toggle(this,"Standort-Radius");
        locBody.addView(locOn);
        TextInputLayout latL=input("Breitengrad",true);
        TextInputLayout lonL=input("Längengrad",true);
        TextInputLayout radL=input("Radius in Metern",true);
        lat=Ui.edit(latL);lon=Ui.edit(lonL);radius=Ui.edit(radL);
        locBody.addView(latL);locBody.addView(lonL);locBody.addView(radL);
        MaterialButton currentLoc=Ui.button(this,"Aktuellen Standort übernehmen");
        locBody.addView(currentLoc);
        addCard(root,"Standort",locBody);

        LinearLayout deviceBody=body();
        TextInputLayout chargeL=dropdown("Ladezustand",chargingItems);
        charging=(MaterialAutoCompleteTextView)chargeL.getEditText();
        deviceBody.addView(chargeL);

        battOn=Ui.toggle(this,"Akkustand berücksichtigen");
        deviceBody.addView(battOn);
        LinearLayout br=new LinearLayout(this);
        TextInputLayout minL=input("Min. %",true);
        TextInputLayout maxL=input("Max. %",true);
        battMin=Ui.edit(minL);battMax=Ui.edit(maxL);
        br.addView(minL,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams mx=new LinearLayout.LayoutParams(0,-2,1);
        mx.setMargins(Ui.dp(this,8),0,0,0);
        br.addView(maxL,mx);
        deviceBody.addView(br);

        TextInputLayout btModeL=dropdown("Bluetooth-Auslöser",btItems);
        btMode=(MaterialAutoCompleteTextView)btModeL.getEditText();
        deviceBody.addView(btModeL);
        TextInputLayout btNameL=input("Bluetooth-Gerät (leer = beliebiges)",false);
        btName=Ui.edit(btNameL);
        deviceBody.addView(btNameL);
        MaterialButton pickBt=Ui.button(this,"Gekoppeltes Gerät auswählen");
        deviceBody.addView(pickBt);

        eventHint=Ui.text(this,"„Verbindung verloren“ ist ein Ereignis: Die gewählten Aktionen werden beim Trennen einmalig ausgeführt.",13);
        eventHint.setAlpha(.65f);
        LinearLayout.LayoutParams evlp=new LinearLayout.LayoutParams(-1,-2);
        evlp.setMargins(0,Ui.dp(this,9),0,0);
        deviceBody.addView(eventHint,evlp);
        addCard(root,"Gerätezustand",deviceBody);

        root.addView(Ui.section(this,"DANN"));

        LinearLayout soundBody=body();
        dndOn=Ui.toggle(this,"Pixel-Modus / Bitte nicht stören");
        soundBody.addView(dndOn);
        TextInputLayout dndL=dropdown("Unterbrechungen",dndItems);
        dndMode=(MaterialAutoCompleteTextView)dndL.getEditText();
        soundBody.addView(dndL);

        mediaOn=Ui.toggle(this,"Medienlautstärke begrenzen");
        soundBody.addView(mediaOn);
        TextView mediaLabel=Ui.text(this,"Medien",13);mediaLabel.setAlpha(.65f);soundBody.addView(mediaLabel);
        media=new Slider(this);media.setValueFrom(0);media.setValueTo(100);media.setStepSize(1);soundBody.addView(media);

        ringOn=Ui.toggle(this,"Klingeln & Benachrichtigungen begrenzen");
        soundBody.addView(ringOn);
        TextView ringLabel=Ui.text(this,"Klingeln",13);ringLabel.setAlpha(.65f);soundBody.addView(ringLabel);
        ring=new Slider(this);ring.setValueFrom(0);ring.setValueTo(100);ring.setStepSize(1);soundBody.addView(ring);
        addCard(root,"Ton & Modi",soundBody);

        LinearLayout displayBody=body();
        brightOn=Ui.toggle(this,"Helligkeit setzen");
        displayBody.addView(brightOn);
        bright=new Slider(this);bright.setValueFrom(1);bright.setValueTo(100);bright.setStepSize(1);displayBody.addView(bright);

        TextInputLayout rotL=dropdown("Automatische Drehung",rotationItems);
        rotation=(MaterialAutoCompleteTextView)rotL.getEditText();
        displayBody.addView(rotL);

        timeoutOn=Ui.toggle(this,"Bildschirm-Timeout setzen");
        displayBody.addView(timeoutOn);
        TextInputLayout toutL=dropdown("Timeout",timeoutItems);
        timeout=(MaterialAutoCompleteTextView)toutL.getEditText();
        displayBody.addView(toutL);

        MaterialButton settings=Ui.button(this,"Systemeinstellungen-Zugriff");
        displayBody.addView(settings);
        addCard(root,"Display",displayBody);

        LinearLayout securityBody=body();
        lockOn=Ui.toggle(this,"Display sofort sperren");
        securityBody.addView(lockOn);
        adminStatus=Ui.text(this,"",13);
        adminStatus.setAlpha(.65f);
        securityBody.addView(adminStatus);
        adminBtn=Ui.button(this,"Sperr-Berechtigung einrichten");
        securityBody.addView(adminBtn);
        addCard(root,"Sicherheit",securityBody);

        LinearLayout restoreBody=body();
        restore=Ui.toggle(this,"Vorherige Einstellungen nach Ende wiederherstellen");
        restoreBody.addView(restore);
        TextView restoreInfo=Ui.text(this,"Gilt für Zustandsroutinen. Ereignisse wie „Bluetooth getrennt“ führen ihre Aktionen einmalig aus.",13);
        restoreInfo.setAlpha(.65f);
        restoreBody.addView(restoreInfo);
        addCard(root,"Nach Ende",restoreBody);

        MaterialButton save=Ui.primary(this,"Routine speichern");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,Ui.dp(this,56));
        slp.setMargins(0,Ui.dp(this,10),0,0);
        root.addView(save,slp);

        if(ProfileStore.get(this,p.id)!=null){
            MaterialButton del=Ui.button(this,"Routine löschen");
            LinearLayout.LayoutParams ddel=new LinearLayout.LayoutParams(-1,-2);
            ddel.setMargins(0,Ui.dp(this,8),0,0);
            root.addView(del,ddel);
            del.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Routine löschen?")
                    .setMessage(p.name)
                    .setPositiveButton("Löschen",(d,w)->{ProfileStore.delete(this,p.id);finish();})
                    .setNegativeButton("Abbrechen",null)
                    .show());
        }

        setContentView(sc);

        currentWifi.setOnClickListener(v->captureWifi());
        currentLoc.setOnClickListener(v->captureLocation());
        pickBt.setOnClickListener(v->pickBluetooth());
        startBtn.setOnClickListener(v->pickTime(true));
        endBtn.setOnClickListener(v->pickTime(false));
        settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:"+getPackageName()))));
        adminBtn.setOnClickListener(v->DeviceAdminHelper.request(this));
        save.setOnClickListener(v->save());

        btMode.setOnItemClickListener((parent,view,pos,id)->updateEventUi(pos));
    }

    @Override protected void onResume(){
        super.onResume();
        updateAdminStatus();
    }

    private void load(){
        name.setText(p.name);
        enabled.setChecked(p.enabled);

        wifiOn.setChecked(p.wifiEnabled);wifi.setText(p.wifiSsid);

        timeOn.setChecked(p.timeEnabled);
        updateTimes();
        for(int i=0;i<7;i++)day[i].setChecked((p.daysMask&(1<<i))!=0);

        locOn.setChecked(p.locationEnabled);
        lat.setText(String.valueOf(p.latitude));
        lon.setText(String.valueOf(p.longitude));
        radius.setText(String.valueOf(Math.round(p.radiusM)));

        setDropdown(charging,chargingItems,p.chargingMode);
        battOn.setChecked(p.batteryEnabled);
        battMin.setText(String.valueOf(p.batteryMin));
        battMax.setText(String.valueOf(p.batteryMax));

        setDropdown(btMode,btItems,p.bluetoothMode);
        btName.setText(p.bluetoothName);

        dndOn.setChecked(p.dndEnabled);
        setDropdown(dndMode,dndItems,p.dndMode);

        mediaOn.setChecked(p.mediaEnabled);media.setValue(p.mediaPercent);
        ringOn.setChecked(p.ringEnabled);ring.setValue(p.ringPercent);
        brightOn.setChecked(p.brightnessEnabled);bright.setValue(Math.max(1,p.brightnessPercent));

        setDropdown(rotation,rotationItems,p.rotationMode+1);
        timeoutOn.setChecked(p.timeoutEnabled);
        setDropdown(timeout,timeoutItems,timeoutIndex(p.timeoutSec));

        lockOn.setChecked(p.lockScreenEnabled);
        restore.setChecked(p.restore);
        updateEventUi(p.bluetoothMode);
        updateAdminStatus();
    }

    private void setDropdown(MaterialAutoCompleteTextView v,String[] items,int index){
        index=Math.max(0,Math.min(items.length-1,index));
        v.setText(items[index],false);
    }

    private int dropdownIndex(MaterialAutoCompleteTextView v,String[] items,int def){
        String s=String.valueOf(v.getText());
        for(int i=0;i<items.length;i++)if(items[i].equals(s))return i;
        return def;
    }

    private void updateEventUi(int mode){
        boolean event=mode==2;
        eventHint.setVisibility(event?View.VISIBLE:View.GONE);
        restore.setEnabled(!event);
        restore.setAlpha(event?.45f:1f);
    }

    private void updateAdminStatus(){
        boolean ok=DeviceAdminHelper.isActive(this);
        adminStatus.setText(ok?"Sperr-Berechtigung ist aktiv.":"Für diese Aktion einmal Geräteadministrator erlauben.");
        adminBtn.setText(ok?"Berechtigung verwalten":"Sperr-Berechtigung einrichten");
    }

    private void save(){
        p.name=text(name).trim();
        if(p.name.isEmpty())p.name="Unbenannte Routine";
        p.enabled=enabled.isChecked();

        p.wifiEnabled=wifiOn.isChecked();
        p.wifiSsid=text(wifi).trim();

        p.timeEnabled=timeOn.isChecked();
        p.startMin=startMin;p.endMin=endMin;
        p.daysMask=0;
        for(int i=0;i<7;i++)if(day[i].isChecked())p.daysMask|=1<<i;

        p.locationEnabled=locOn.isChecked();
        p.latitude=dbl(lat,0);p.longitude=dbl(lon,0);p.radiusM=(float)Math.max(25,dbl(radius,250));

        p.chargingMode=dropdownIndex(charging,chargingItems,0);
        p.batteryEnabled=battOn.isChecked();
        p.batteryMin=integer(battMin,0);p.batteryMax=integer(battMax,100);

        p.bluetoothMode=dropdownIndex(btMode,btItems,0);
        p.bluetoothName=text(btName).trim();

        p.dndEnabled=dndOn.isChecked();
        p.dndMode=dropdownIndex(dndMode,dndItems,0);

        p.mediaEnabled=mediaOn.isChecked();p.mediaPercent=Math.round(media.getValue());
        p.ringEnabled=ringOn.isChecked();p.ringPercent=Math.round(ring.getValue());
        p.brightnessEnabled=brightOn.isChecked();p.brightnessPercent=Math.round(bright.getValue());

        p.rotationMode=dropdownIndex(rotation,rotationItems,0)-1;
        p.timeoutEnabled=timeoutOn.isChecked();
        p.timeoutSec=timeoutSeconds(dropdownIndex(timeout,timeoutItems,2));

        p.lockScreenEnabled=lockOn.isChecked();
        p.restore=p.bluetoothMode==2?false:restore.isChecked();

        ProfileStore.upsert(this,p);

        if(p.dndEnabled){
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm!=null&&nm.isNotificationPolicyAccessGranted())ZenModeManager.ensureRule(this,p);
        }

        if(p.lockScreenEnabled&&!DeviceAdminHelper.isActive(this)){
            Toast.makeText(this,"Routine gespeichert. Erlaube jetzt noch „Display sperren“.",Toast.LENGTH_LONG).show();
            DeviceAdminHelper.request(this);
        }else{
            Toast.makeText(this,"Routine gespeichert.",Toast.LENGTH_SHORT).show();
        }

        if(Config.prefs(this).getBoolean(Config.ENGINE_ENABLED,false)){
            try{startForegroundService(new Intent(this,AutomationService.class).setAction(AutomationService.ACTION_START));}
            catch(Exception ignored){}
        }
        finish();
    }

    private String text(TextInputEditText e){
        return e.getText()==null?"":e.getText().toString();
    }

    private void pickTime(boolean start){
        int m=start?startMin:endMin;
        new TimePickerDialog(this,(v,h,min)->{
            if(start)startMin=h*60+min;else endMin=h*60+min;
            updateTimes();
        },m/60,m%60,true).show();
    }

    private void updateTimes(){
        startBtn.setText("Start · "+fmt(startMin));
        endBtn.setText("Ende · "+fmt(endMin));
    }

    private String fmt(int m){
        return String.format(Locale.GERMANY,"%02d:%02d",(m/60)%24,m%60);
    }

    private int integer(TextInputEditText e,int def){
        try{return Math.max(0,Math.min(100,Integer.parseInt(text(e).trim())));}
        catch(Exception x){return def;}
    }

    private double dbl(TextInputEditText e,double def){
        try{return Double.parseDouble(text(e).trim().replace(',','.'));}
        catch(Exception x){return def;}
    }

    private int timeoutSeconds(int i){
        int[] a={15,30,60,120,300,600};
        return a[Math.max(0,Math.min(a.length-1,i))];
    }

    private int timeoutIndex(int s){
        int[] a={15,30,60,120,300,600};
        for(int i=0;i<a.length;i++)if(a[i]==s)return i;
        return 2;
    }

    private void captureWifi(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            pending="wifi";
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOC);
            return;
        }
        String s=currentSsid();
        if(s==null)Toast.makeText(this,"WLAN-Name konnte nicht gelesen werden.",Toast.LENGTH_LONG).show();
        else{wifi.setText(s);wifiOn.setChecked(true);}
    }

    private String currentSsid(){
        try{
            ConnectivityManager cm=getSystemService(ConnectivityManager.class);
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities c=cm.getNetworkCapabilities(n);
                if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.getTransportInfo() instanceof WifiInfo){
                    String s=((WifiInfo)c.getTransportInfo()).getSSID();
                    if(s!=null&&!"<unknown ssid>".equals(s)){
                        if(s.startsWith("\"")&&s.endsWith("\"")&&s.length()>1)s=s.substring(1,s.length()-1);
                        return s;
                    }
                }
            }
        }catch(Exception ignored){}
        return null;
    }

    private void captureLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            pending="loc";
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOC);
            return;
        }
        LocationManager lm=getSystemService(LocationManager.class);
        if(!lm.isLocationEnabled()){
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        Location best=null;
        for(String provider:lm.getProviders(true)){
            try{
                Location q=lm.getLastKnownLocation(provider);
                if(q!=null&&(best==null||q.getAccuracy()<best.getAccuracy()))best=q;
            }catch(Exception ignored){}
        }
        if(best!=null){setLoc(best);return;}
        if(Build.VERSION.SDK_INT>=30){
            try{
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER,null,getMainExecutor(),this::setLoc);
                return;
            }catch(Exception ignored){}
        }
        Toast.makeText(this,"Noch kein Standort verfügbar.",Toast.LENGTH_LONG).show();
    }

    private void setLoc(Location l){
        if(l==null)return;
        lat.setText(String.format(Locale.US,"%.6f",l.getLatitude()));
        lon.setText(String.format(Locale.US,"%.6f",l.getLongitude()));
        locOn.setChecked(true);
    }

    private void pickBluetooth(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            pending="bt";
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);
            return;
        }
        try{
            BluetoothManager bm=getSystemService(BluetoothManager.class);
            BluetoothAdapter a=bm==null?null:bm.getAdapter();
            if(a==null){
                Toast.makeText(this,"Bluetooth ist nicht verfügbar.",Toast.LENGTH_LONG).show();
                return;
            }
            ArrayList<String> names=new ArrayList<>();
            names.add("Beliebiges Bluetooth-Gerät");
            for(BluetoothDevice d:a.getBondedDevices()){
                String n=d.getName();
                if(n!=null&&!n.isEmpty()&&!names.contains(n))names.add(n);
            }
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth-Gerät")
                    .setItems(names.toArray(new String[0]),(dialog,which)->btName.setText(which==0?"":names.get(which)))
                    .show();
        }catch(Exception e){
            Toast.makeText(this,"Bluetooth-Geräte konnten nicht gelesen werden.",Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        String x=pending;
        pending="";
        if(request==REQ_LOC){
            if("wifi".equals(x))captureWifi();
            else if("loc".equals(x))captureLocation();
        }else if(request==REQ_BT)pickBluetooth();
    }
}
