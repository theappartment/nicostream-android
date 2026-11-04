package com.nicostream.demo;
import android.graphics.Color;
import android.graphics.drawable.*;

/** Doomsday's emerald identity is scoped to its collection and editorial guide. */
final class DoomTheme {
    static final int GREEN=Color.rgb(119,222,139);
    static GradientDrawable panel(float density){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(12,35,21),Color.rgb(4,12,8),Color.BLACK});d.setCornerRadius(14*density);return d;}
    static Drawable focus(float density){StateListDrawable states=new StateListDrawable();GradientDrawable on=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(34,69,43),Color.rgb(10,25,15)});on.setCornerRadius(10*density);on.setStroke(Math.max(1,Math.round(density)),Color.argb(140,119,222,139));GradientDrawable selected=new GradientDrawable();selected.setColor(Color.rgb(17,40,24));selected.setCornerRadius(10*density);GradientDrawable off=new GradientDrawable();off.setColor(Color.rgb(8,15,11));off.setCornerRadius(10*density);states.addState(new int[]{android.R.attr.state_focused},on);states.addState(new int[]{android.R.attr.state_pressed},on);states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{},off);states.setEnterFadeDuration(120);states.setExitFadeDuration(120);return states;}
}
