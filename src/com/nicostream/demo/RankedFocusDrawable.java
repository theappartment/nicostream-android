package com.nicostream.demo;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Soft light contained in the Top 10 card's reserved outer gutter. */
final class RankedFocusDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active;
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){boolean next=false;for(int state:states)next|=state==android.R.attr.state_focused||state==android.R.attr.state_pressed;if(next==active)return false;active=next;invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){
        if(!active)return;Rect b=getBounds();float w=b.width(),h=b.height();int save=canvas.save();canvas.translate(b.left,b.top);int layer=canvas.saveLayer(0,0,w,h,null);
        for(float center:new float[]{.26f,.72f}){
            paint.setShader(new RadialGradient(w*center,h*.52f,Math.max(w*.7f,h*.65f),new int[]{Color.argb(110,112,239,199),Color.argb(48,112,239,199),Color.TRANSPARENT},new float[]{0,.48f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);
        }
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        paint.setShader(new LinearGradient(0,0,w,0,new int[]{Color.TRANSPARENT,Color.WHITE,Color.WHITE,Color.TRANSPARENT},new float[]{0,.08f,.92f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{Color.TRANSPARENT,Color.WHITE,Color.WHITE,Color.TRANSPARENT},new float[]{0,.08f,.92f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);
        paint.setShader(null);paint.setXfermode(null);canvas.restoreToCount(layer);canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
