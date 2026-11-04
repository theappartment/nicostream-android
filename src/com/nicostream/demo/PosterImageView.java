package com.nicostream.demo;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Draw the replacement label at screen resolution, leaving the original artwork intact. */
final class PosterImageView extends ImageView {
    private float band=-1;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    PosterImageView(Context context){super(context);}
    void setBand(float fraction){band=fraction;invalidate();}
    @Override public void setImageBitmap(Bitmap bitmap){band=-1;super.setImageBitmap(bitmap);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);Drawable drawable=getDrawable();if(band<=0||drawable==null||drawable.getIntrinsicHeight()<=0)return;
        RectF rect=new RectF(0,drawable.getIntrinsicHeight()*band,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight());getImageMatrix().mapRect(rect);rect.offset(getPaddingLeft(),getPaddingTop());
        float density=getResources().getDisplayMetrics().density;
        rect.left=Math.max(getPaddingLeft(),rect.left);rect.right=Math.min(getWidth()-getPaddingRight(),rect.right);rect.top=Math.max(getPaddingTop(),rect.top-density);rect.bottom=Math.min(getHeight()-getPaddingBottom(),rect.bottom);
        if(rect.width()<30||rect.height()<8)return;
        paint.setColor(Color.rgb(229,9,20));canvas.drawRect(rect,paint);
        String label="Aggiunto di recente";paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.min(14*density,rect.height()*.61f));
        float available=rect.width()*.9f;if(paint.measureText(label)>available)paint.setTextSize(paint.getTextSize()*available/paint.measureText(label));
        paint.setColor(Color.WHITE);Paint.FontMetrics metrics=paint.getFontMetrics();canvas.drawText(label,rect.centerX(),rect.centerY()-(metrics.ascent+metrics.descent)/2,paint);
    }
}
