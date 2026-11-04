package com.nicostream.demo;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

final class AppUpdates {
    final Activity activity;final BooleanSupplier canPrompt;final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
    final android.content.SharedPreferences prefs;final String repository=BuildConfig.UPDATE_REPOSITORY;
    volatile boolean closed=false,cancelled=false,busy=false;volatile HttpURLConnection connection;
    long lastAttempt=0;ProgressDialog progress;UpdateRelease available;File pendingApk;boolean waitingPermission=false;
    AppUpdates(Activity activity,BooleanSupplier canPrompt){this.activity=activity;this.canPrompt=canPrompt;prefs=activity.getSharedPreferences("nico",0);}
    void maybeCheck(){if(!busy&&System.currentTimeMillis()-lastAttempt>600000&&System.currentTimeMillis()-prefs.getLong("updateChecked",0)>24*60*60*1000L)check(false);}
    long installedCode()throws Exception {PackageInfo p=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    void check(boolean manual){
        if(busy||closed)return;busy=true;cancelled=false;lastAttempt=System.currentTimeMillis();if(manual)showProgress("Controllo aggiornamenti…",false);
        worker.execute(()->{try{
            JSONArray releases=new JSONArray(fetch("https://api.github.com/repos/"+repository+"/releases?per_page=20",1024*1024));UpdateRelease found=null;long current=installedCode();
            for(int n=0;n<releases.length();n++){
                JSONObject release=releases.getJSONObject(n);if(release.optBoolean("draft")||release.optBoolean("prerelease"))continue;
                String manifestUrl=UpdateRelease.asset(release,UpdateRelease.MANIFEST,repository),apkUrl=UpdateRelease.asset(release,UpdateRelease.APK,repository);if(manifestUrl.isEmpty()||apkUrl.isEmpty())continue;
                JSONObject manifest=new JSONObject(fetch(manifestUrl,256*1024));UpdateRelease candidate=new UpdateRelease(manifest,repository,apkUrl,release.optString("body"));
                if(manifest.optInt("minSdk",26)>Build.VERSION.SDK_INT)continue;
                if(candidate.versionCode>current&&(found==null||candidate.versionCode>found.versionCode))found=candidate;
            }
            final UpdateRelease update=found;main.post(()->{busy=false;dismissProgress();if(!usable()||cancelled)return;prefs.edit().putLong("updateChecked",System.currentTimeMillis()).apply();available=update;
                if(update!=null&&(manual||canPrompt.getAsBoolean()))prompt(update);else if(manual)message("Sei aggiornato", "È installata la versione "+BuildConfig.VERSION_NAME+".");
            });
        }catch(Exception e){main.post(()->{busy=false;dismissProgress();if(usable()&&!cancelled&&manual)message("Controllo non riuscito","Non riesco a raggiungere gli aggiornamenti GitHub. Riprova più tardi.");});}});
    }
    void prompt(UpdateRelease update){new AlertDialog.Builder(activity).setTitle("Nuova versione "+update.versionName).setMessage("Versione installata: "+BuildConfig.VERSION_NAME+"\n\n"+(update.notes.isEmpty()?"È disponibile un aggiornamento di NicoFlix.":update.notes)).setPositiveButton("Scarica e aggiorna",(d,w)->download(update)).setNegativeButton("Più tardi",null).show();}
    void download(UpdateRelease update){
        if(busy||closed)return;busy=true;cancelled=false;showProgress("Download aggiornamento…",true);
        worker.execute(()->{File part=new File(activity.getCacheDir(),"updates/update.part");try{
            File folder=part.getParentFile();if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException();
            HttpURLConnection c=open(update.apkUrl);MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0,lastPercent=-1;
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part)){
                byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){if(cancelled||closed)throw new InterruptedIOException();size+=n;if(size>update.size||size>UpdateRelease.MAX_APK)throw new IOException("Dimensioni non valide");out.write(bytes,0,n);digest.update(bytes,0,n);long percent=size*100/update.size;if(percent!=lastPercent){lastPercent=percent;final int value=(int)percent;main.post(()->{if(progress!=null)progress.setProgress(value);});}}out.getFD().sync();
            }finally{c.disconnect();connection=null;}
            if(size!=update.size||!hex(digest.digest()).equals(update.sha256))throw new SecurityException("File incompleto o non verificato");
            verifyArchive(part,update);File apk=new File(folder,"update.apk");if(apk.exists()&&!apk.delete())throw new IOException();if(!part.renameTo(apk))throw new IOException();
            main.post(()->{busy=false;dismissProgress();if(!usable()||cancelled)return;pendingApk=apk;available=update;requestInstall();});
        }catch(Exception e){part.delete();main.post(()->{busy=false;dismissProgress();if(usable()&&!cancelled)message("Aggiornamento non scaricato",e instanceof SecurityException?e.getMessage():"Download non riuscito. Nessuna modifica all’app: puoi riprovare.");});}});
    }
    void verifyArchive(File apk,UpdateRelease update)throws Exception {
        PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(activity.getPackageName(),flags);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName))throw new SecurityException("L’APK non appartiene a questa app.");
        long code=Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode;
        if(code!=update.versionCode||code<=installedCode()||!update.versionName.equals(candidate.versionName))throw new SecurityException("La versione dell’APK non è valida.");
        if(!signatures(candidate).equals(signatures(current))||signatures(current).isEmpty())throw new SecurityException("La firma dell’APK non corrisponde a quella dell’app installata.");
    }
    static Set<String> signatures(PackageInfo info)throws Exception {android.content.pm.Signature[] values=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;Set<String> result=new HashSet<>();if(values!=null)for(android.content.pm.Signature value:values)result.add(hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray())));return result;}
    void requestInstall(){
        if(pendingApk==null||!pendingApk.isFile())return;
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Autorizza aggiornamenti").setMessage("Android richiede di autorizzare NicoFlix a installare i propri aggiornamenti. Poi tornerai all’app per confermare.").setPositiveButton("Apri impostazioni",(d,w)->{try{waitingPermission=true;activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(RuntimeException e){waitingPermission=false;message("Impostazione non disponibile","Autorizza NicoFlix nelle impostazioni di sicurezza del dispositivo.");}}).setNegativeButton("Annulla",null).show();return;
        }
        final File apk=pendingApk;final UpdateRelease update=available;
        new AlertDialog.Builder(activity).setTitle("Installa "+update.versionName).setMessage("Il download e la firma sono stati verificati. Android chiederà la conferma dell’aggiornamento; dati e preferenze vengono conservati.").setPositiveButton("Continua",(d,w)->install(apk,update)).setNegativeButton("Annulla",null).show();
    }
    void install(File apk,UpdateRelease update){
        if(busy||closed)return;busy=true;cancelled=false;showProgress("Preparazione installazione…",false);
        worker.execute(()->{PackageInstaller installer=activity.getPackageManager().getPackageInstaller();int id=-1;try{
            verifyArchive(apk,update);PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(activity.getPackageName());params.setSize(apk.length());if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            id=installer.createSession(params);
            try(PackageInstaller.Session session=installer.openSession(id);InputStream in=new FileInputStream(apk)){
                try(OutputStream out=session.openWrite("base.apk",0,apk.length())){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){if(cancelled||closed)throw new InterruptedIOException();out.write(bytes,0,n);}session.fsync(out);}
                Intent callback=new Intent(activity,UpdateInstallReceiver.class).setAction(activity.getPackageName()+".UPDATE_RESULT");int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                session.commit(PendingIntent.getBroadcast(activity,id,callback,flags).getIntentSender());
            }
            main.post(()->{busy=false;dismissProgress();pendingApk=null;});
        }catch(Exception e){if(id>=0)try{installer.abandonSession(id);}catch(Exception ignored){}main.post(()->{busy=false;dismissProgress();if(usable())message("Installazione non avviata","Puoi riprovare dalle impostazioni dell’app.");});}});
    }
    void onResume(){if(waitingPermission){waitingPermission=false;if(activity.getPackageManager().canRequestPackageInstalls())requestInstall();}String result=prefs.getString("updateResult","");if(!result.isEmpty()){prefs.edit().remove("updateResult").apply();Toast.makeText(activity,result,Toast.LENGTH_LONG).show();}}
    HttpURLConnection open(String address)throws Exception {
        for(int n=0;n<=5;n++){
            if(cancelled||closed)throw new InterruptedIOException();if(!UpdateRelease.downloadHost(address))throw new SecurityException("Indirizzo aggiornamento non consentito");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();connection=c;c.setConnectTimeout(10000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","NicoStream-Android/"+BuildConfig.VERSION_NAME);c.setRequestProperty("Accept","application/vnd.github+json, application/octet-stream, */*");
            int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException();address=new URL(new URL(address),location).toString();continue;}
            if(status!=200){c.disconnect();throw new IOException("GitHub HTTP "+status);}return c;
        }throw new IOException("Troppi reindirizzamenti");
    }
    String fetch(String address,int maximum)throws Exception {HttpURLConnection c=open(address);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(cancelled||closed)throw new InterruptedIOException();out.write(bytes,0,n);if(out.size()>maximum)throw new IOException("Risposta troppo grande");}return out.toString("UTF-8");}finally{c.disconnect();connection=null;}}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    boolean usable(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    void showProgress(String title,boolean determinate){progress=new ProgressDialog(activity);progress.setTitle(title);progress.setMessage("Puoi continuare dopo la verifica.");progress.setProgressStyle(determinate?ProgressDialog.STYLE_HORIZONTAL:ProgressDialog.STYLE_SPINNER);progress.setMax(100);progress.setCancelable(true);progress.setOnCancelListener(d->{cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();});progress.show();}
    void dismissProgress(){if(progress!=null){progress.dismiss();progress=null;}}
    void message(String title,String body){new AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("OK",null).show();}
    void close(){closed=true;cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();worker.shutdownNow();dismissProgress();main.removeCallbacksAndMessages(null);}
}
