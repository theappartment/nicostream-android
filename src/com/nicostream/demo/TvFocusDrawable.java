package com.nicostream.demo;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Focus stays inside the control bounds: no scaling or shadows to clip in TV rails. */
final class TvFocusDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final float density;
    private final int idle;
    private boolean focused,pressed,selected;
    TvFocusDrawable(float density,int idle){this.density=density;this.idle=idle;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){
        boolean f=false,p=false,s=false;
        for(int state:states){f|=state==android.R.attr.state_focused;p|=state==android.R.attr.state_pressed;s|=state==android.R.attr.state_selected;}
        if(f==focused&&p==pressed&&s==selected)return false;
        focused=f;pressed=p;selected=s;invalidateSelf();return true;
    }
    @Override public void draw(Canvas canvas){
        rect.set(getBounds());float radius=10*density;
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);
        if(focused||pressed){
            paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
                pressed?Color.rgb(38,54,67):Color.rgb(27,38,48),Color.rgb(15,20,26),Shader.TileMode.CLAMP));
        }else paint.setColor(selected?Color.rgb(22,32,41):idle);
        canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);
        if(focused||pressed){
            float inset=2*density;rect.inset(inset,inset);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(pressed?Color.rgb(124,159,181):Color.rgb(224,235,243));canvas.drawRoundRect(rect,radius-inset,radius-inset,paint);
        }else if(selected){
            paint.setColor(Color.rgb(124,159,181));float width=Math.min(rect.width()*.35f,40*density);float y=rect.bottom-4*density;canvas.drawRoundRect(rect.centerX()-width/2,y,rect.centerX()+width/2,y+2*density,density,density,paint);
        }
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
