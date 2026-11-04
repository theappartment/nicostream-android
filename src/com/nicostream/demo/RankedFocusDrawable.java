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
        if(!active)return;
        Rect b=getBounds();float w=b.width(),h=b.height();int save=canvas.save();
        canvas.translate(b.exactCenterX(),b.exactCenterY());
        // An elliptical radial light reaches zero opacity before every edge.
        canvas.scale(w*.49f,h*.49f);
        paint.setShader(new RadialGradient(0,0,1f,
            new int[]{Color.argb(145,124,159,181),Color.argb(80,124,159,181),Color.argb(24,124,159,181),Color.TRANSPARENT},
            new float[]{0,.35f,.72f,1},Shader.TileMode.CLAMP));
        canvas.drawCircle(0,0,1f,paint);
        paint.setShader(null);canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
