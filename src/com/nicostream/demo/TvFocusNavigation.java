package com.nicostream.demo;

import android.view.*;
import android.widget.*;
import java.util.*;

/** Explicit row navigation; Android's geometric search can jump across scrolled shelves. */
final class TvFocusNavigation {
    private final Map<View,View> remembered=new WeakHashMap<>();
    private final Map<View,View> shelfActions=new WeakHashMap<>();
    private final Set<View> secondary=Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    void secondary(View group){List<View> controls=new ArrayList<>();leaves(group,controls);secondary.addAll(controls);}
    void shelf(View shelf,View action){shelfActions.put(shelf,action);}
    private void leaves(View view,List<View> result){
        if(view.getVisibility()!=View.VISIBLE||!view.isEnabled())return;
        if(view.isFocusable()&&!(view instanceof ScrollView)&&!(view instanceof HorizontalScrollView)){result.add(view);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)leaves(group.getChildAt(i),result);}
    }
    private void rows(View view,List<List<View>> result){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(shelfActions.containsValue(view))return;
        if(view instanceof HorizontalScrollView || view instanceof LinearLayout&&((LinearLayout)view).getOrientation()==LinearLayout.HORIZONTAL || view.isFocusable()&&!(view instanceof ScrollView)){
            List<View> row=new ArrayList<>();leaves(view,row);row.removeAll(shelfActions.values());
            View action=shelfActions.get(view);if(action!=null&&action.isShown())row.add(action);
            if(!row.isEmpty())result.add(row);return;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)rows(group.getChildAt(i),result);}
    }
    boolean move(View root,View focused,int key){
        if(focused==null||!focused.isAttachedToWindow())return false;
        boolean horizontal=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
        if(!horizontal&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
        List<List<View>> groups=new ArrayList<>();rows(root,groups);
        // Hero utility controls extend the primary action row horizontally only.
        for(int i=groups.size()-1;i>0;i--)if(secondary.contains(groups.get(i).get(0))){groups.get(i-1).addAll(groups.remove(i));}
        for(int r=0;r<groups.size();r++){
            List<View> row=groups.get(r);int column=row.indexOf(focused);if(column<0)continue;
            remembered.put(row.get(0),focused);
            View target;
            if(horizontal){int next=column+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);if(next<0||next>=row.size())return true;target=row.get(next);}
            else{
                int next=r+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);if(next<0||next>=groups.size())return true;
                List<View> destination=groups.get(next);target=remembered.get(destination.get(0));
                if(target==null||!destination.contains(target)||secondary.contains(target)){
                    int primary=0;while(primary<destination.size()&&!secondary.contains(destination.get(primary)))primary++;
                    target=destination.get(Math.min(column,Math.max(0,primary-1)));
                }
            }
            target.requestFocus();return true;
        }
        return false;
    }
}
