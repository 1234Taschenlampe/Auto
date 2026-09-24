package de.jfe.wifiquiet;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class Ui {
    private Ui(){}

    public static int dp(Context c,int v){
        return Math.round(v*c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c,String value,float sp){
        TextView v=new TextView(c);
        v.setText(value);
        v.setTextSize(sp);
        v.setLineSpacing(0,1.08f);
        return v;
    }

    public static TextView title(Context c,String value){
        TextView v=text(c,value,32);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }

    public static TextView section(Context c,String value){
        TextView v=text(c,value,14);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setAllCaps(true);
        v.setLetterSpacing(.08f);
        v.setPadding(dp(c,4),dp(c,22),0,dp(c,8));
        v.setAlpha(.72f);
        return v;
    }

    public static MaterialCardView card(Context c){
        MaterialCardView card=new MaterialCardView(c);
        card.setRadius(dp(c,24));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(c,1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(c,12));
        card.setLayoutParams(lp);
        return card;
    }

    public static LinearLayout cardBody(Context c){
        LinearLayout body=new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16));
        return body;
    }

    public static MaterialButton button(Context c,String label){
        MaterialButton b=new MaterialButton(c,null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c,18));
        b.setMinHeight(dp(c,48));
        return b;
    }

    public static MaterialButton primary(Context c,String label){
        MaterialButton b=new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c,18));
        b.setMinHeight(dp(c,52));
        return b;
    }

    public static MaterialSwitch toggle(Context c,String label){
        MaterialSwitch s=new MaterialSwitch(c);
        s.setText(label);
        s.setMinHeight(dp(c,52));
        return s;
    }

    public static TextInputLayout input(Context c,String hint){
        TextInputLayout l=new TextInputLayout(c,null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        l.setHint(hint);
        l.setBoxCornerRadii(dp(c,16),dp(c,16),dp(c,16),dp(c,16));
        TextInputEditText e=new TextInputEditText(l.getContext());
        e.setSingleLine(true);
        l.addView(e,new TextInputLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(c,5),0,dp(c,7));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextInputEditText edit(TextInputLayout l){
        return (TextInputEditText)l.getEditText();
    }

    public static void gap(Context c,LinearLayout p,int h){
        Space s=new Space(c);
        p.addView(s,new LinearLayout.LayoutParams(1,dp(c,h)));
    }
}
