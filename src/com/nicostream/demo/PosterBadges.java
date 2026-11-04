package com.nicostream.demo;

import android.graphics.Bitmap;
import android.os.*;
import android.util.LruCache;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-only recognition: geometry first, then confirm the exact label with bundled OCR. */
final class PosterBadges {
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService executor=Executors.newSingleThreadExecutor();
    final LruCache<String,Float> results=new LruCache<>(256);
    final Map<String,List<WeakReference<PosterImageView>>> pending=new HashMap<>();
    final ArrayDeque<Job> queue=new ArrayDeque<>();
    TextRecognizer recognizer;boolean busy,closed;
    static final class Job {final String key;final Bitmap bitmap;Job(String key,Bitmap bitmap){this.key=key;this.bitmap=bitmap;}}
    void inspect(String key,Bitmap bitmap,PosterImageView image){
        if(closed)return;Float cached=results.get(key);if(cached!=null){image.setBand(cached);return;}
        List<WeakReference<PosterImageView>> views=pending.get(key);if(views!=null){views.add(new WeakReference<>(image));return;}
        views=new ArrayList<>();views.add(new WeakReference<>(image));pending.put(key,views);queue.add(new Job(key,bitmap));next();
    }
    void next(){
        if(closed||busy||queue.isEmpty())return;busy=true;Job job=queue.remove();
        executor.execute(()->{
            int top=PosterBannerDetector.findBand(job.bitmap.getWidth(),job.bitmap.getHeight(),job.bitmap::getPixel);
            if(top<0){main.post(()->complete(job.key,-1));return;}
            int start=Math.max(0,top-2),height=job.bitmap.getHeight()-start;
            Bitmap crop=Bitmap.createBitmap(job.bitmap,0,start,job.bitmap.getWidth(),height);
            Bitmap input=Bitmap.createScaledBitmap(crop,720,Math.max(32,Math.round(height*720f/crop.getWidth())),true);if(crop!=input)crop.recycle();
            final float band=(float)top/job.bitmap.getHeight();
            main.post(()->{
                if(closed){input.recycle();return;}
                try{
                    if(recognizer==null)recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    recognizer.process(InputImage.fromBitmap(input,0))
                        .addOnSuccessListener(text->{boolean confirmed=PosterBannerDetector.confirmedText(text.getText());if(confirmed)android.util.Log.i("NicoStreamArtwork","RECENT_BADGE confirmed");complete(job.key,confirmed?band:-1);})
                        .addOnFailureListener(error->complete(job.key,-1))
                        .addOnCompleteListener(task->input.recycle());
                }catch(Exception error){input.recycle();complete(job.key,-1);}
            });
        });
    }
    void complete(String key,float band){
        if(closed)return;results.put(key,band);List<WeakReference<PosterImageView>> views=pending.remove(key);
        if(views!=null)for(WeakReference<PosterImageView> reference:views){PosterImageView view=reference.get();if(view!=null&&key.equals(view.getTag()))view.setBand(band);}
        busy=false;next();
    }
    void close(){closed=true;executor.shutdown();queue.clear();pending.clear();results.evictAll();if(recognizer!=null){recognizer.close();recognizer=null;}}
}
