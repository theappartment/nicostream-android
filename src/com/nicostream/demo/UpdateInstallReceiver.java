package com.nicostream.demo;

import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
import java.io.File;

/** Explicit system callback only; Android owns the final installation confirmation. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null){confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{context.startActivity(confirmation);}catch(RuntimeException e){message(context,"Apri l’app per completare l’aggiornamento.");}}
            return;
        }
        if(status==PackageInstaller.STATUS_SUCCESS){new File(context.getCacheDir(),"updates/update.apk").delete();message(context,"Aggiornamento installato.");}
        else message(context,status==PackageInstaller.STATUS_FAILURE_ABORTED?"Installazione annullata.":"Aggiornamento non installato. Puoi riprovare dalle impostazioni.");
    }
    void message(Context context,String value){context.getSharedPreferences("nico",0).edit().putString("updateResult",value).apply();Toast.makeText(context,value,Toast.LENGTH_LONG).show();}
}
