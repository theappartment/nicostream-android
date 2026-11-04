package com.nicostream.demo;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.ScrollView;

/** Keep the active TV item near the middle instead of barely exposing its bottom edge. */
final class CenteredScrollView extends ScrollView {
    private View pending;
    private final Runnable center=()->{
        View target=pending;if(target==null||!target.isAttachedToWindow()||!target.hasFocus())return;
        View anchor=target;for(android.view.ViewParent parent=target.getParent();parent instanceof View&&parent!=this;parent=parent.getParent()){View ancestor=(View)parent;if("tv-focus-anchor".equals(ancestor.getTag())){anchor=ancestor;break;}}
        Rect bounds=new Rect();anchor.getDrawingRect(bounds);offsetDescendantRectToMyCoords(anchor,bounds);
        int destination=Math.max(0,bounds.centerY()-getHeight()/2);
        smoothScrollTo(0,destination);
    };
    CenteredScrollView(Context context){super(context);setSmoothScrollingEnabled(true);setClipChildren(true);setClipToPadding(false);setVerticalScrollBarEnabled(false);}
    @Override protected void dispatchDraw(android.graphics.Canvas canvas){
        int saved=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(saved);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        // Tail room lets even the final row reach the middle of the viewport.
        setPadding(0,Math.round(12*getResources().getDisplayMetrics().density),0,h/2);
    }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){return 0;}
    @Override public void requestChildFocus(View child,View focused){
        super.requestChildFocus(child,focused);pending=focused;removeCallbacks(center);post(center);
    }
    @Override public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){
        View focused=findFocus();if(focused==null)return super.requestChildRectangleOnScreen(child,rectangle,immediate);
        pending=focused;removeCallbacks(center);post(center);return true;
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(center);pending=null;super.onDetachedFromWindow();}
}
