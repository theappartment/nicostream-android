package com.nicostream.demo;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/** A short branded overlay: home loads underneath and never waits for an addon job. */
final class AppOpening {
    private FrameLayout overlay;
    private View previousFocus;
    private ValueAnimator lightAnimation;
    private final Activity activity;
    AppOpening(Activity activity){this.activity=activity;}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    boolean visible(){return overlay!=null;}
    void show(){
        if(!ValueAnimator.areAnimatorsEnabled())return;
        FrameLayout host=activity.findViewById(android.R.id.content);
        previousFocus=activity.getCurrentFocus();overlay=new FrameLayout(activity);overlay.setBackgroundColor(Color.BLACK);
        overlay.setFocusableInTouchMode(true);overlay.setClickable(true);overlay.setContentDescription("NicoFlix");overlay.setOnClickListener(v->dismiss());
        View lights=new View(activity){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float progress;
            {lightAnimation=ValueAnimator.ofFloat(0,1);lightAnimation.setDuration(2300);lightAnimation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});}
            @Override protected void onDraw(Canvas canvas){
                float intensity=(float)Math.sin(Math.PI*progress);int width=getWidth(),height=getHeight();
                for(int n=0;n<29;n++){float offset=(n-14)/14f;float x=width/2f+offset*width*(.09f+progress*.65f);int alpha=(int)(intensity*(1-Math.abs(offset)*.6f)*95);paint.setShader(new LinearGradient(0,0,0,height,new int[]{Color.TRANSPARENT,Color.argb(alpha,112,240,202),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawRect(x-dp(1+n%3),0,x+dp(1+n%3),height,paint);}
                paint.setShader(null);
            }
        };overlay.addView(lights,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout brand=new LinearLayout(activity);brand.setOrientation(LinearLayout.VERTICAL);brand.setGravity(Gravity.CENTER);
        ImageView mark=new ImageView(activity);mark.setImageResource(R.drawable.app_icon);mark.setScaleType(ImageView.ScaleType.FIT_CENTER);brand.addView(mark,new LinearLayout.LayoutParams(dp(122),dp(122)));
        TextView name=new TextView(activity);name.setText("NicoFlix");name.setTextSize(42);name.setTextColor(Color.WHITE);name.setLetterSpacing(.12f);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(-2,-2);title.topMargin=dp(20);brand.addView(name,title);
        overlay.addView(brand,new FrameLayout.LayoutParams(-1,-1));host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.requestFocus();
        mark.setAlpha(0);mark.setScaleX(.7f);mark.setScaleY(.7f);
        mark.animate().alpha(1).scaleX(1).scaleY(1).setDuration(900).setInterpolator(new DecelerateInterpolator()).start();
        name.setAlpha(0);name.setTranslationY(dp(12));name.animate().alpha(1).translationY(0).setStartDelay(550).setDuration(650).start();
        lightAnimation.start();
        overlay.postDelayed(()->{if(overlay!=null){brand.animate().scaleX(1.12f).scaleY(1.12f).alpha(0).setDuration(380).start();dismiss();}},2400);
    }
    void dismiss(){
        FrameLayout view=overlay;if(view==null)return;overlay=null;if(lightAnimation!=null){lightAnimation.cancel();lightAnimation=null;}
        view.animate().alpha(0).setDuration(280).withEndAction(()->{if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);if(previousFocus!=null&&previousFocus.isAttachedToWindow())previousFocus.requestFocus();else{View content=activity.findViewById(android.R.id.content);content.requestFocus(View.FOCUS_FORWARD);}}).start();
    }
}
