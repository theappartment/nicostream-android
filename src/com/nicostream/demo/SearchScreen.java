package com.nicostream.demo;

import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.util.LruCache;
import org.json.*;
import java.util.*;
import java.util.function.*;

/** Split TV search: text and history on the left, real title cards on the right. */
final class SearchScreen {
    interface Loader {void load(String query,BiConsumer<JSONArray,Boolean> complete);}
    final MainActivity app;final Loader loader;final Consumer<String> submit;
    final LinearLayout view,left,recent,list,grid,field;
    final EditText input;final TextView clear,search,status,heading,gridTitle,gridStatus;
    final int previousSoftInputMode;
    final Handler main=new Handler(Looper.getMainLooper());final LruCache<String,JSONArray> cache=new LruCache<>(12);
    final List<List<View>> leftRows=new ArrayList<>(),rightRows=new ArrayList<>();
    JSONArray remote=new JSONArray(),displayed=new JSONArray(),completed;String completedQuery="";
    long revision;boolean closed,inFlight,unavailable,searching;View lastLeft,lastRight;
    SearchScreen(MainActivity app,String initial,JSONArray preset,Loader loader,Consumer<String> submit){
        this.app=app;this.loader=loader;this.submit=submit;previousSoftInputMode=app.getWindow().getAttributes().softInputMode;view=new LinearLayout(app);view.setClipChildren(false);view.setClipToPadding(false);
        left=app.col();left.setClipChildren(true);LinearLayout.LayoutParams leftParams=new LinearLayout.LayoutParams(0,-1,1);leftParams.rightMargin=app.dp(24);view.addView(left,leftParams);
        left.addView(app.title("Cerca",28));left.addView(app.text("Film, serie e anime",12,app.MUTED));app.space(left,12);
        field=new LinearLayout(app);field.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(app);input.setSingleLine(true);input.setTextSize(18);input.setTextColor(app.WHITE);input.setHintTextColor(app.MUTED);input.setHint("Scrivi un titolo…");input.setContentDescription("Cerca un titolo");input.setPadding(app.dp(14),app.dp(10),app.dp(14),app.dp(10));input.setBackground(app.focus());input.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_FULLSCREEN|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        field.addView(input,new LinearLayout.LayoutParams(0,app.dp(50),1));clear=smallButton("✕",()->{input.setText("");input.requestFocus();});clear.setContentDescription("Cancella il testo");LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,app.dp(50));cp.leftMargin=app.dp(8);field.addView(clear,cp);
        search=smallButton("Cerca",()->{if(searching)app.cancelSearch();else run(input.getText().toString());});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,app.dp(50));sp.leftMargin=app.dp(8);field.addView(search,sp);left.addView(field);app.space(left,10);
        CenteredScrollView textScroll=new CenteredScrollView(app);LinearLayout textContent=app.col();textScroll.addView(textContent);left.addView(textScroll,new LinearLayout.LayoutParams(-1,0,1));
        recent=app.col();textContent.addView(recent);heading=app.title("Suggerimenti",18);textContent.addView(heading);status=app.text("",11,app.MUTED);textContent.addView(status);app.space(textContent,8);list=app.col();textContent.addView(list);
        LinearLayout right=app.col();view.addView(right,new LinearLayout.LayoutParams(0,-1,1));gridTitle=app.title("Da scoprire",22);right.addView(gridTitle);gridStatus=app.text("",11,app.MUTED);right.addView(gridStatus);app.space(right,8);CenteredScrollView cardsScroll=new CenteredScrollView(app);grid=app.col();cardsScroll.addView(grid);right.addView(cardsScroll,new LinearLayout.LayoutParams(-1,0,1));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){changed();}public void afterTextChanged(Editable e){}});
        input.setOnFocusChangeListener((v,on)->{if(on&&!closed){lastLeft=input;render();}});
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||event!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER){run(input.getText().toString());return true;}return false;});
        app.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        if(preset!=null){completed=preset;completedQuery=SearchMemory.clean(initial);}input.setText(initial);input.setSelection(input.length());changed();input.post(()->{if(!closed)input.requestFocus();});
    }
    TextView smallButton(String label,Runnable action){TextView result=app.button(label,action);result.setTextSize(13);result.setPadding(app.dp(12),app.dp(10),app.dp(12),app.dp(10));result.setMinHeight(app.dp(40));return result;}
    JSONArray stored(String key){try{return new JSONArray(app.prefs.getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    String query(){return SearchMemory.clean(input.getText().toString());}
    void hideKeyboard(){InputMethodManager keyboard=(InputMethodManager)app.getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);}
    void run(String query){query=SearchMemory.clean(query);if(query.isEmpty()){input.requestFocus();return;}search.requestFocus();if(!query.equals(input.getText().toString())){input.setText(query);input.setSelection(input.length());}hideKeyboard();submit.accept(query);}
    void setSearching(boolean value){searching=value;if(value)main.removeCallbacks(fetch);search.setText(value?"Annulla":"Cerca");if(!closed)render();}
    void showResults(String query,JSONArray items){completedQuery=query;completed=items;searching=false;search.setText("Cerca");if(!hasFocusWithin(grid))render();else gridStatus.setText("Risultati pronti · torna al campo per visualizzarli");}
    void renderRecent(){
        recent.removeAllViews();if(!query().isEmpty())return;JSONArray history=stored("recentSearches");if(history.length()==0)return;
        recent.addView(app.title("Recenti",17));recent.addView(app.text("OK per cercare · Tieni premuto per rimuovere",10,app.MUTED));HorizontalScrollView scroll=app.tvShelf();LinearLayout row=new LinearLayout(app);List<View> controls=new ArrayList<>();
        for(int n=0;n<history.length();n++){String query=history.optString(n);TextView chip=smallButton(query,()->run(query));chip.setOnLongClickListener(v->{app.prefs.edit().putString("recentSearches",SearchMemory.forget(stored("recentSearches"),query).toString()).apply();input.requestFocus();render();Toast.makeText(app,"Ricerca rimossa",Toast.LENGTH_SHORT).show();return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=app.dp(8);row.addView(chip,lp);controls.add(chip);}
        TextView erase=smallButton("Svuota recenti",()->{app.prefs.edit().remove("recentSearches").apply();input.requestFocus();render();});erase.setTextColor(app.MUTED);row.addView(erase);controls.add(erase);scroll.addView(row);recent.addView(scroll);leftRows.add(controls);app.space(recent,10);
    }
    final Runnable fetch=()->fetch();
    void changed(){
        if(closed)return;revision++;unavailable=false;remote=new JSONArray();main.removeCallbacks(fetch);String query=query();clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);JSONArray cached=cache.get(SearchMemory.key(query));if(cached!=null)remote=cached;render();
        if(query.codePointCount(0,query.length())>=2&&cached==null)main.postDelayed(fetch,550);
    }
    void fetch(){
        if(closed||inFlight||searching)return;String query=query();if(query.codePointCount(0,query.length())<2)return;long request=revision;inFlight=true;status.setText("Completo i suggerimenti…");
        loader.load(query,(items,failed)->{
            inFlight=false;if(closed)return;cache.put(SearchMemory.key(query),items);
            if(request==revision){remote=items;unavailable=failed;if(!hasFocusWithin(list)&&!hasFocusWithin(grid))render();else status.setText("Aggiornati · torna al campo per visualizzarli");}
            else{main.removeCallbacks(fetch);if(!searching&&query().codePointCount(0,query().length())>=2)main.postDelayed(fetch,120);}
        });
    }
    void render(){
        if(closed)return;String query=query();leftRows.clear();List<View> fieldControls=new ArrayList<>();for(int n=0;n<field.getChildCount();n++)if(field.getChildAt(n).getVisibility()==View.VISIBLE)fieldControls.add(field.getChildAt(n));leftRows.add(fieldControls);renderRecent();list.removeAllViews();
        try{
            JSONArray catalog=SearchMemory.catalog(app.discoverFeed,app.favorites(),stored("searchTitles"));JSONArray suggestions=SearchMemory.suggestions(query,stored("recentSearches"),catalog,remote);
            status.setText(searching?"Cerco nei due addon…":unavailable?"Suggerimenti salvati · premi Cerca per verificare":query.isEmpty()?"Scegli un titolo o inizia a scrivere":suggestions.length()==0?"Premi Cerca per interrogare i due addon":"OK sul suggerimento per cercare");
            for(int n=0;n<suggestions.length();n++){
                JSONObject suggestion=suggestions.getJSONObject(n);String value=suggestion.getString("query");LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(app.dp(12),app.dp(8),app.dp(12),app.dp(8));row.setBackground(app.focus());row.setFocusable(true);row.setClickable(true);row.setContentDescription("Cerca "+value);row.setOnClickListener(v->run(value));
                LinearLayout copy=app.col();TextView title=app.title(value,15);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);TextView subtitle=app.text(suggestion.optString("subtitle"),10,app.MUTED);subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);copy.addView(subtitle);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(app.text("↗",16,app.MINT));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=app.dp(6);list.addView(row,rp);leftRows.add(Collections.singletonList(row));
            }
            JSONArray cards=completed!=null&&SearchMemory.key(query).equals(SearchMemory.key(completedQuery))?completed:SearchMemory.content(query,catalog,remote);renderCards(cards,completed!=null&&SearchMemory.key(query).equals(SearchMemory.key(completedQuery)));
        }catch(Exception ignored){status.setText("Premi Cerca per mostrare i risultati");}
    }
    void renderCards(JSONArray cards,boolean full){
        grid.removeAllViews();rightRows.clear();displayed=new JSONArray();StringBuilder notices=new StringBuilder();for(int n=0;n<cards.length();n++){JSONObject item=cards.optJSONObject(n);if(item==null)continue;if(item.optBoolean("isNotice")){if(notices.length()>0)notices.append(" · ");notices.append(item.optString("title"));}else if(!item.optString("url").isEmpty())displayed.put(item);}
        gridTitle.setText(query().isEmpty()?"Da scoprire":full?"Risultati":"Contenuti suggeriti");gridStatus.setText(notices.length()>0?notices.toString():displayed.length()+(displayed.length()==1?" titolo":" titoli")+(full?" · tutti gli addon":query().isEmpty()?"":" · Cerca include anche ToonItalia"));
        if(displayed.length()==0){grid.addView(app.text("Nessun titolo da mostrare. Prova un altro nome o premi Cerca.",14,app.MUTED));return;}
        int screen=app.getResources().getDisplayMetrics().widthPixels;int available=(screen-app.dp(52+24))/2-app.dp(8);int gap=app.dp(10),columns=Math.max(1,(available+gap)/(app.dp(125)+gap)),width=(available-gap*(columns-1))/columns;
        LinearLayout row=null;List<View> controls=null;
        for(int n=0;n<displayed.length();n++){
            JSONObject item=displayed.optJSONObject(n);if(n%columns==0){row=new LinearLayout(app);row.setClipChildren(false);row.setPadding(app.dp(4),app.dp(6),app.dp(4),app.dp(6));grid.addView(row);controls=new ArrayList<>();rightRows.add(controls);}
            LinearLayout card=app.col();card.setPadding(app.dp(7),app.dp(7),app.dp(7),app.dp(8));app.focusCard(card,item);card.setOnClickListener(v->{hideKeyboard();app.openSearchItem(item,query(),displayed);});card.setOnLongClickListener(v->{if(item.optString("addonId").contains("toonitalia")){app.searchItemActions(item,query(),displayed);return true;}return false;});int imageHeight=Math.round((width-app.dp(14))*1.5f/app.getResources().getDisplayMetrics().density);card.addView(app.artwork(item.optString("art"),-1,imageHeight));app.space(card,4);TextView title=app.title(item.optString("title"),13);title.setLines(2);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);String provider=item.optString("searchProvider");if(provider.isEmpty())provider=item.optString("addonId").contains("toonitalia")?"ToonItalia":"StreamingCommunity";if(provider.contains("StreamingCommunity"))provider="StreamingCommunity";TextView source=app.text(provider,9,app.MUTED);source.setSingleLine(true);source.setEllipsize(TextUtils.TruncateAt.END);card.addView(source);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(width,-2);cp.rightMargin=n%columns==columns-1?0:gap;cp.bottomMargin=app.dp(8);row.addView(card,cp);controls.add(card);
        }
    }
    boolean hasFocusWithin(View parent){View focused=app.getCurrentFocus();if(focused==parent)return true;for(android.view.ViewParent node=focused==null?null:focused.getParent();node!=null;node=node.getParent())if(node==parent)return true;return false;}
    boolean focus(View view){if(view!=null&&view.isAttachedToWindow()&&view.isShown())view.requestFocus();return true;}
    boolean move(View focused,int key){
        boolean horizontal=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;if(!horizontal&&key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
        if(hasFocusWithin(app.nav)){if(key==KeyEvent.KEYCODE_DPAD_DOWN)return focus(lastLeft!=null&&lastLeft.isAttachedToWindow()?lastLeft:input);return false;}
        for(int r=0;r<leftRows.size();r++){
            List<View> row=leftRows.get(r);int column=row.indexOf(focused);if(column<0)continue;lastLeft=focused;
            if(horizontal){if(focused==input){if(key==KeyEvent.KEYCODE_DPAD_LEFT&&input.getSelectionStart()>0||key==KeyEvent.KEYCODE_DPAD_RIGHT&&input.getSelectionEnd()<input.length())return false;}
                int next=column+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);if(next>=0&&next<row.size())return focus(row.get(next));if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&!rightRows.isEmpty())return focus(lastRight!=null&&lastRight.isAttachedToWindow()?lastRight:rightRows.get(0).get(0));return true;}
            int next=r+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);if(next<0)return focus(app.nav.getChildAt(1));if(next>=leftRows.size())return true;return focus(leftRows.get(next).get(Math.min(column,leftRows.get(next).size()-1)));
        }
        for(int r=0;r<rightRows.size();r++){List<View> row=rightRows.get(r);int column=row.indexOf(focused);if(column<0)continue;lastRight=focused;
            if(horizontal){int next=column+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1);if(next<0)return focus(lastLeft!=null&&lastLeft.isAttachedToWindow()?lastLeft:input);if(next>=row.size())return true;return focus(row.get(next));}
            int next=r+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);if(next<0)return focus(app.nav.getChildAt(1));if(next>=rightRows.size())return true;return focus(rightRows.get(next).get(Math.min(column,rightRows.get(next).size()-1)));
        }return false;
    }
    void close(){closed=true;revision++;main.removeCallbacksAndMessages(null);hideKeyboard();app.getWindow().setSoftInputMode(previousSoftInputMode);}
}
