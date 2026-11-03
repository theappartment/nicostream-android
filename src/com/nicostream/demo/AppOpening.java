package com.nicostream.demo;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/** A short branded overlay: home loads underneath and never waits for an addon job. */
final class AppOpening {
    private FrameLayout overlay;
    private View previousFocus;
    private final Activity activity;
    AppOpening(Activity activity){this.activity=activity;}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    boolean visible(){return overlay!=null;}
    void show(){
        if(!ValueAnimator.areAnimatorsEnabled())return;
        FrameLayout host=activity.findViewById(android.R.id.content);
        previousFocus=activity.getCurrentFocus();overlay=new FrameLayout(activity);overlay.setBackgroundColor(Color.BLACK);
        overlay.setFocusableInTouchMode(true);overlay.setClickable(true);overlay.setContentDescription("NicoStream");overlay.setOnClickListener(v->dismiss());
        LinearLayout brand=new LinearLayout(activity);brand.setOrientation(LinearLayout.VERTICAL);brand.setGravity(Gravity.CENTER);
        ImageView mark=new ImageView(activity);mark.setImageResource(R.drawable.app_icon);mark.setScaleType(ImageView.ScaleType.FIT_CENTER);brand.addView(mark,new LinearLayout.LayoutParams(dp(104),dp(104)));
        TextView name=new TextView(activity);name.setText("NicoStream");name.setTextSize(36);name.setTextColor(Color.WHITE);name.setLetterSpacing(.08f);name.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(-2,-2);title.topMargin=dp(20);brand.addView(name,title);
        View line=new View(activity);line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.TRANSPARENT,Color.rgb(153,239,202),Color.TRANSPARENT}));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(170),dp(2));lp.topMargin=dp(24);brand.addView(line,lp);
        overlay.addView(brand,new FrameLayout.LayoutParams(-1,-1));host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.requestFocus();
        brand.setAlpha(0);brand.setScaleX(.88f);brand.setScaleY(.88f);brand.animate().alpha(1).scaleX(1).scaleY(1).setDuration(850).setInterpolator(new DecelerateInterpolator()).start();
        overlay.postDelayed(this::dismiss,1900);
    }
    void dismiss(){
        FrameLayout view=overlay;if(view==null)return;overlay=null;
        view.animate().alpha(0).setDuration(280).withEndAction(()->{if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);if(previousFocus!=null&&previousFocus.isAttachedToWindow())previousFocus.requestFocus();else{View content=activity.findViewById(android.R.id.content);content.requestFocus(View.FOCUS_FORWARD);}}).start();
    }
}
