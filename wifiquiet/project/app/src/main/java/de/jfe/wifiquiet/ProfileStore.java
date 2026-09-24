package de.jfe.wifiquiet;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

public final class ProfileStore {
    private static final String KEY="profiles_v2";
    private ProfileStore(){}
    public static List<RoutineProfile> load(Context c){
        ArrayList<RoutineProfile> out=new ArrayList<>();
        String raw=Config.prefs(c).getString(KEY,"[]");
        try{ JSONArray a=new JSONArray(raw); for(int i=0;i<a.length();i++) out.add(RoutineProfile.from(a.getJSONObject(i))); }catch(Exception ignored){}
        return out;
    }
    public static void saveAll(Context c,List<RoutineProfile> ps){
        JSONArray a=new JSONArray(); for(RoutineProfile p:ps)a.put(p.json());
        Config.prefs(c).edit().putString(KEY,a.toString()).apply();
    }
    public static RoutineProfile get(Context c,String id){ for(RoutineProfile p:load(c))if(p.id.equals(id))return p; return null; }
    public static RoutineProfile findByRule(Context c,String ruleId){ for(RoutineProfile p:load(c))if(ruleId!=null&&ruleId.equals(p.zenRuleId))return p; return null; }
    public static void upsert(Context c,RoutineProfile p){
        List<RoutineProfile> ps=load(c); boolean found=false;
        for(int i=0;i<ps.size();i++) if(ps.get(i).id.equals(p.id)){ps.set(i,p);found=true;break;}
        if(!found)ps.add(p); saveAll(c,ps);
    }
    public static void delete(Context c,String id){
        List<RoutineProfile> ps=load(c); Iterator<RoutineProfile> it=ps.iterator();
        while(it.hasNext()){RoutineProfile p=it.next();if(p.id.equals(id)){ZenModeManager.deleteRule(c,p);it.remove();}}
        saveAll(c,ps);
    }
}