package com.nicostream.demo;

import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Category and detail panes, with explicit remote focus paths. */
final class SettingsScreen {
    final MainActivity app;final LinearLayout view,content;final List<TextView> categories=new ArrayList<>();final List<View> options=new ArrayList<>();int selected=0;
    SettingsScreen(MainActivity app){
        this.app=app;view=new LinearLayout(app);view.setPadding(0,app.dp(18),0,0);
        LinearLayout left=app.col();left.setPadding(0,0,app.dp(24),0);view.addView(left,new LinearLayout.LayoutParams(0,-1,.32f));left.addView(app.title("Impostazioni",26));left.addView(app.text("Personalizza NicoFlix",12,app.MUTED));app.space(left,22);
        ScrollView scroll=new ScrollView(app);scroll.setFillViewport(true);content=app.col();content.setPadding(app.dp(22),app.dp(18),app.dp(22),app.dp(18));content.setBackground(app.bg(Color.rgb(10,12,15),12));scroll.addView(content);view.addView(scroll,new LinearLayout.LayoutParams(0,-1,.68f));
        String[] names={"Contenuti e home","Aggiornamenti","Sorgenti","Connessione","Informazioni"};int[] icons={R.drawable.ic_nav_catalog,R.drawable.ic_nav_discover,R.drawable.ic_nav_settings,R.drawable.ic_nav_settings,R.drawable.ic_nav_favorites};
        for(int n=0;n<names.length;n++){final int index=n;TextView button=app.button(names[n],()->show(index));button.setTextSize(14);button.setGravity(Gravity.CENTER_VERTICAL);android.graphics.drawable.Drawable icon=app.getDrawable(icons[n]).mutate();icon.setTint(app.MUTED);button.setCompoundDrawablesWithIntrinsicBounds(icon,null,null,null);button.setCompoundDrawablePadding(app.dp(12));button.setId(View.generateViewId());button.setOnFocusChangeListener((v,on)->{if(on)show(index);});button.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(!options.isEmpty())options.get(0).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT)return true;return false;});categories.add(button);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=app.dp(8);left.addView(button,cp);}
        show(0);categories.get(0).post(()->categories.get(0).requestFocus());
    }
    void row(String title,String description,Runnable action){
        LinearLayout row=app.col();row.setPadding(app.dp(16),app.dp(12),app.dp(16),app.dp(12));row.setBackground(app.focus());row.setFocusable(true);row.setClickable(true);row.setId(View.generateViewId());row.setOnClickListener(v->action.run());row.setContentDescription(title+". "+description);row.addView(app.title(title+"  ›",16));row.addView(app.text(description,12,app.MUTED));row.setOnKeyListener((v,key,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_LEFT){categories.get(selected).requestFocus();return true;}return false;});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=app.dp(10);content.addView(row,rp);options.add(row);
    }
    void show(int index){
        if(selected==index&&content.getChildCount()>0)return;selected=index;options.clear();content.removeAllViews();for(int n=0;n<categories.size();n++)categories.get(n).setSelected(n==index);
        content.addView(app.title(categories.get(index).getText().toString(),24));app.space(content,16);
        switch(index){
            case 0:
                row("Catalogo e addon","Apri Stream4me, ToonItalia e Program Feed",app::showAddons);
                row("Aggiorna la home","Ricarica titoli e proposte di oggi",()->{app.section="Scopri";app.home();app.refreshSourceIndex(true,()->app.loadDiscover(true));app.loadProgramHome(true);});
                content.addView(app.text("Le proposte di Program Feed si aggiornano ogni 15 minuti quando la home è aperta.",12,app.MUTED));break;
            case 1:
                content.addView(app.text("Versione installata · "+BuildConfig.VERSION_NAME,14,app.MINT));app.space(content,12);
                row("Controlla aggiornamenti","Cerca una nuova versione di NicoFlix",()->app.updates.check(true));content.addView(app.text("Il controllo automatico avviene una volta al giorno. L’installazione richiede la tua conferma.",12,app.MUTED));break;
            case 2:
                app.sourceDomainStatus=app.text("StreamingCommunity · "+(app.providerOrigin().isEmpty()?"Verifica in corso":android.net.Uri.parse(app.providerOrigin()).getHost())+(app.sourceIndexWarning?" · ultima copia valida":""),14,app.MINT);content.addView(app.sourceDomainStatus);app.space(content,12);
                row("Aggiorna domini sorgenti","Verifica gli indirizzi correnti dei cataloghi",()->app.refreshSourceIndex(true,()->Toast.makeText(app,app.sourceIndexWarning?"Uso l’ultima copia valida":"Sorgenti aggiornate",Toast.LENGTH_LONG).show()));content.addView(app.text("Gli indirizzi si aggiornano automaticamente. Se la rete non risponde, conserviamo l’ultima copia valida.",12,app.MUTED));break;
            case 3:
                boolean authorized=!app.prefs.getString("serverKey","").isEmpty();content.addView(app.text(authorized?"Dispositivo autorizzato":"Dispositivo da autorizzare",14,app.MINT));content.addView(app.text(EngineAccess.SERVER,12,app.MUTED));app.space(content,12);
                row(authorized?"Modifica chiave di accesso":"Autorizza dispositivo","La chiave resta salvata solo su questo dispositivo",app::editEngineAccess);content.addView(app.text("Il server è già configurato. La connessione è automatica all’apertura.",12,app.MUTED));break;
            default:content.addView(app.title("NicoFlix",30));content.addView(app.text("Versione "+BuildConfig.VERSION_NAME,14,app.MINT));content.addView(app.text("Film, serie e dirette. Pensata per il tuo telecomando.",14,app.MUTED));break;
        }
    }
}
