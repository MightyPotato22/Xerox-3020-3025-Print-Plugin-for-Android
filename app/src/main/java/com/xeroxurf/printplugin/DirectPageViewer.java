package com.xeroxurf.printplugin;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.widget.AppCompatCheckBox;
import com.google.android.material.button.MaterialButton;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DirectPageViewer extends Dialog {
    interface Result {void apply(BitSet pages);}
    private final Context context;
    private final Uri uri;
    private final boolean pdf;
    private final int count;
    private final String name;
    private final DirectPrintOptions options;
    private final BitSet checked;
    private final Result result;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Integer> pending=new HashSet<>();
    private final Set<Integer> failed=new HashSet<>();
    private final LruCache<Integer,Bitmap> cache=new LruCache<Integer,Bitmap>(24*1024*1024){
        @Override protected int sizeOf(Integer key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    private volatile boolean closed;
    // Only accessed by the serial worker.
    private DirectPrintDocument document;
    private TextView counter;
    private PageAdapter adapter;
    DirectPageViewer(Context context,Uri uri,boolean pdf,String name,int count,DirectPrintOptions options,BitSet checked,Result result){
        super(context,android.R.style.Theme_Material_NoActionBar);
        this.context=context;this.uri=uri;this.pdf=pdf;this.name=name;this.count=count;this.options=options;this.checked=(BitSet)checked.clone();this.result=result;
    }
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);return t;}
    private MaterialButton button(LinearLayout row,String title){MaterialButton b=new MaterialButton(context);b.setText(title);b.setAllCaps(false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);return b;}
    @Override protected void onCreate(android.os.Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff202124);root.setPadding(dp(10),dp(10),dp(10),dp(8));
        LinearLayout top=new LinearLayout(getContext());root.addView(top);button(top,"Cancel").setOnClickListener(v->dismiss());button(top,"Done").setOnClickListener(v->{BitSet copy=(BitSet)checked.clone();dismiss();result.apply(copy);});
        TextView title=text("Select pages",22);root.addView(title);
        TextView file=text(name==null?"Document":name,13);file.setMaxLines(2);root.addView(file);
        counter=text("",15);counter.setPadding(0,dp(7),0,dp(5));root.addView(counter);
        LinearLayout actions=new LinearLayout(getContext());root.addView(actions);
        button(actions,"Select all").setOnClickListener(v->{checked.set(0,count);changed();});
        button(actions,"Deselect all").setOnClickListener(v->{checked.clear();changed();});
        TextView help=text("Scroll to see every source page. Tick the box in the top-right corner. Done applies your selection. Layout preview is on the print screen.",13);help.setPadding(0,dp(5),0,dp(8));root.addView(help);
        ListView list=new ListView(getContext());list.setDividerHeight(dp(12));list.setCacheColorHint(Color.TRANSPARENT);list.setClipToPadding(false);list.setPadding(dp(4),0,dp(4),dp(8));
        adapter=new PageAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);changed();
        setOnDismissListener(dialog->shutdown());
    }
    @Override public void show(){super.show();Window w=getWindow();if(w!=null){w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}}
    private void changed(){counter.setText(checked.cardinality()+" of "+count+" pages selected");if(adapter!=null)adapter.notifyDataSetChanged();}
    private void shutdown(){if(closed)return;closed=true;cache.evictAll();worker.execute(()->{if(document!=null){document.close();document=null;}});worker.shutdown();}
    private void requestPage(int position){
        if(closed||pending.contains(position)||failed.contains(position)||cache.get(position)!=null)return;
        pending.add(position);
        worker.execute(()->{
            if(closed)return;
            Bitmap bitmap=null;try{
                if(document==null)document=DirectPrintDocument.open(context,uri,pdf,true);
                bitmap=document.previewSource(options,position,Math.min(1050,Math.max(500,context.getResources().getDisplayMetrics().widthPixels- dp(32))));
            }catch(Exception|OutOfMemoryError e){PrintLog.e("DirectPageViewer","Page preview failed: "+e.getMessage());}
            final Bitmap rendered=bitmap;
            main.post(()->{pending.remove(position);if(closed){if(rendered!=null)rendered.recycle();return;}if(rendered==null)failed.add(position);else cache.put(position,rendered);adapter.notifyDataSetChanged();});
        });
    }
    private final class Holder {
        final ImageView image;final TextView placeholder,label;final AppCompatCheckBox check;final FrameLayout page;
        Holder(ImageView image,TextView placeholder,TextView label,AppCompatCheckBox check,FrameLayout page){this.image=image;this.placeholder=placeholder;this.label=label;this.check=check;this.page=page;}
    }
    private final class PageAdapter extends BaseAdapter {
        public int getCount(){return count;}
        public Object getItem(int position){return position;}
        public long getItemId(int position){return position;}
        @Override public boolean hasStableIds(){return true;}
        public View getView(int position,View convertView,ViewGroup parent){
            Holder h;
            if(convertView==null){
                LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.VERTICAL);
                TextView label=text("",15);label.setPadding(0,dp(2),0,dp(6));row.addView(label);
                FrameLayout page=new FrameLayout(getContext());page.setBackgroundColor(Color.WHITE);
                int screenWidth=context.getResources().getDisplayMetrics().widthPixels-dp(32);
                int pageHeight=Math.round(screenWidth*options.height()/(float)options.width());
                row.addView(page,new LinearLayout.LayoutParams(-1,pageHeight));
                ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);image.setColorFilter(new ColorMatrixColorFilter(gray));page.addView(image,new FrameLayout.LayoutParams(-1,-1));
                TextView placeholder=new TextView(getContext());placeholder.setTextColor(0xff444444);placeholder.setGravity(Gravity.CENTER);page.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
                AppCompatCheckBox check=new AppCompatCheckBox(context);check.setBackgroundColor(0xffeeeeee);
                check.setSupportButtonTintList(android.content.res.ColorStateList.valueOf(0xffc83d3d));check.setGravity(Gravity.CENTER);check.setPadding(dp(5),0,0,0);
                FrameLayout.LayoutParams checkLp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);checkLp.setMargins(0,dp(6),dp(6),0);page.addView(check,checkLp);
                h=new Holder(image,placeholder,label,check,page);row.setTag(h);convertView=row;
            }else h=(Holder)convertView.getTag();
            h.label.setText("Page "+(position+1)+" of "+count+(checked.get(position)?" · Selected":" · Not selected"));
            h.check.setOnCheckedChangeListener(null);h.check.setChecked(checked.get(position));h.check.setContentDescription("Include page "+(position+1));
            h.check.setOnCheckedChangeListener((view,value)->{checked.set(position,value);changed();});
            h.page.setOnClickListener(v->{checked.flip(position);changed();});
            Bitmap bitmap=cache.get(position);h.image.setImageBitmap(bitmap);h.image.setAlpha(checked.get(position)?1f:0.55f);
            h.placeholder.setVisibility(bitmap==null?View.VISIBLE:View.GONE);h.placeholder.setText(failed.contains(position)?"Preview unavailable":"Rendering page "+(position+1)+"…");
            if(bitmap==null)requestPage(position);
            return convertView;
        }
    }
}
