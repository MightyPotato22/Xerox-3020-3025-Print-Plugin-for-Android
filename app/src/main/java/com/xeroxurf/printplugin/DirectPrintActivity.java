package com.xeroxurf.printplugin;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import java.io.*;

public class DirectPrintActivity extends AppCompatActivity {
    private MaterialButton photoButton,fileButton,sendButton;
    private TextView status,selection;
    private ImageView preview;
    private MaterialButton previousSheet,nextSheet,fullPreview;
    private TextView sheetPosition;
    private LinearLayout previewNav;
    private int sheetIndex,totalSheets;
    private float swipeStart;
    private DirectPageViewer viewer;
    private Spinner copies,paper,dpiChoice,orientation,scaleMode,margin,toner,nup,pageFilter;
    private EditText percent,range;
    private SeekBar brightness,contrast;
    private CheckBox reverse;
    private MaterialButton refresh;
    private final java.util.List<View> controls=new java.util.ArrayList<>();
    private ProgressBar progress;
    private Uri selected;
    private boolean selectedPdf,busy,ready;
    private String filename,ip;
    private int pages;
    private Bitmap previewBitmap;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout root,String text){TextView t=new TextView(this);t.setText(text);t.setPadding(0,dp(5),0,dp(5));root.addView(t);return t;}
    private MaterialButton button(LinearLayout root,String text){MaterialButton b=new MaterialButton(this);b.setText(text);root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private Spinner options(LinearLayout root,String caption,String... entries){label(root,caption);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,entries));root.addView(s);controls.add(s);return s;}
    private EditText field(LinearLayout root,String caption,String value,boolean number){label(root,caption);EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(number?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT);e.setText(value);root.addView(e);controls.add(e);return e;}
    private SeekBar slider(LinearLayout root,String caption,int max,int initial,int offset){TextView t=label(root,caption+" "+(initial+offset));SeekBar b=new SeekBar(this);b.setMax(max);b.setProgress(initial);root.addView(b);controls.add(b);b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){t.setText(caption+" "+(p+offset));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});return b;}
    private DirectPrintOptions currentOptions(){
        int pct=100;if(scaleMode.getSelectedItemPosition()==2||scaleMode.getSelectedItemPosition()==3){try{pct=Integer.parseInt(percent.getText().toString());}catch(NumberFormatException e){throw new IllegalArgumentException("Enter scale 10–200%.");}if(pct<10||pct>200)throw new IllegalArgumentException("Scale must be 10–200%.");}
        return new DirectPrintOptions(paper.getSelectedItemPosition(),new int[]{600,300,150}[dpiChoice.getSelectedItemPosition()],orientation.getSelectedItemPosition(),scaleMode.getSelectedItemPosition(),pct,new int[]{5,10,15,20}[margin.getSelectedItemPosition()],toner.getSelectedItemPosition(),brightness.getProgress()-60,contrast.getProgress()+50,new int[]{1,2,4}[nup.getSelectedItemPosition()],range.getText().toString(),reverse.isChecked(),pageFilter.getSelectedItemPosition());
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(16));scroll.addView(root);setContentView(scroll);
        button(root,"Back").setOnClickListener(v->onBackPressed());
        TextView title=label(root,"Print photos & files");title.setTextSize(24);title.setTypeface(null,Typeface.BOLD);
        android.content.SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(this);
        ip=prefs.getString("printer_ip","192.168.1.11").trim();
        label(root,"Printer: "+prefs.getString("printer_name","Xerox WorkCentre 3025")+" ("+ip+")");
        LinearLayout choices=new LinearLayout(this);root.addView(choices);
        photoButton=new MaterialButton(this);photoButton.setText("Choose photo");choices.addView(photoButton,new LinearLayout.LayoutParams(0,-2,1));
        fileButton=new MaterialButton(this);fileButton.setText("Choose PDF");choices.addView(fileButton,new LinearLayout.LayoutParams(0,-2,1));
        selection=label(root,"No file selected");
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.TRANSPARENT);preview.setVisibility(View.GONE);
        ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);preview.setColorFilter(new ColorMatrixColorFilter(gray));root.addView(preview,new LinearLayout.LayoutParams(-1,dp(320)));
        previewNav=new LinearLayout(this);previewNav.setGravity(android.view.Gravity.CENTER_VERTICAL);root.addView(previewNav);previousSheet=new MaterialButton(this);previousSheet.setText("‹ Prev");previewNav.addView(previousSheet,new LinearLayout.LayoutParams(0,-2,1));sheetPosition=new TextView(this);sheetPosition.setGravity(android.view.Gravity.CENTER);previewNav.addView(sheetPosition,new LinearLayout.LayoutParams(0,-2,1));nextSheet=new MaterialButton(this);nextSheet.setText("Next ›");previewNav.addView(nextSheet,new LinearLayout.LayoutParams(0,-2,1));previewNav.setVisibility(View.GONE);
        fullPreview=button(root,"Fullscreen preview & select pages");fullPreview.setEnabled(false);controls.add(fullPreview);
        previousSheet.setOnClickListener(v->moveSheet(-1));nextSheet.setOnClickListener(v->moveSheet(1));fullPreview.setOnClickListener(v->showPageViewer());
        preview.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){swipeStart=event.getX();return true;}if(event.getAction()==android.view.MotionEvent.ACTION_UP){float delta=event.getX()-swipeStart;if(Math.abs(delta)>dp(45))moveSheet(delta<0?1:-1);else {v.performClick();showPageViewer();}return true;}return true;});
        label(root,"Copies");copies=new Spinner(this);String[] counts={"1","2","3","4","5","6","7","8","9","10"};
        copies.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,counts));root.addView(copies);
        label(root,"Print settings · black and white · single-sided");
        paper=options(root,"Paper size","A4","A5 (experimental)","Letter (experimental)","Legal (experimental)");
        dpiChoice=options(root,"Raster resolution","600 DPI – standard","300 DPI – draft (experimental)","150 DPI – draft (experimental)");
        orientation=options(root,"Orientation","Auto","Portrait","Landscape");
        scaleMode=options(root,"Sizing","Fit to printable area","Fill / crop","Custom % of fit","Actual PDF size (%)");
        percent=field(root,"Custom scale (10–200%)","100",true);
        margin=options(root,"Margins","5 mm","10 mm","15 mm","20 mm");
        toner=options(root,"Toner saving (software)","Off","Eco – lighter","Maximum – much lighter");
        brightness=slider(root,"Brightness",120,60,-60);
        contrast=slider(root,"Contrast %",100,50,50);
        nup=options(root,"Pages per sheet","1","2","4");
        range=field(root,"PDF pages (e.g. 1,3-5; blank = all; none = no pages)","",false);
        pageFilter=options(root,"Print subset","All selected pages","Odd pages only","Even pages only");
        reverse=new CheckBox(this);reverse.setText("Reverse page order");root.addView(reverse);controls.add(reverse);
        label(root,"For manual two-sided printing, print odd pages, reinsert the paper and print even pages. Test one sheet first. Actual PDF size may crop; photos use fit scaling.");
        label(root,"Load matching paper. Non-A4 sizes and lower DPI may be rejected by this printer. If that happens, use A4 / 600 DPI.");
        refresh=button(root,"Update preview with settings");controls.add(refresh);
        refresh.setOnClickListener(v->{if(selected!=null&&!busy){sheetIndex=0;loadPreview();}});
        sendButton=button(root,"Print now");sendButton.setEnabled(false);
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(dp(36),dp(36)));
        status=label(root,"");
        photoButton.setOnClickListener(v->pick(false));fileButton.setOnClickListener(v->pick(true));sendButton.setOnClickListener(v->print());
        if(state!=null&&state.getString("uri")!=null){selected=Uri.parse(state.getString("uri"));selectedPdf=state.getBoolean("pdf");copies.setSelection(state.getInt("copies"));range.setText(state.getString("range",""));pageFilter.setSelection(state.getInt("filter"));reverse.setChecked(state.getBoolean("reverse"));sheetIndex=state.getInt("sheetIndex");loadPreview();}
    }
    private void pick(boolean pdf){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(pdf?"application/pdf":"image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(i,pdf?802:801);}catch(Exception e){status.setText("Unable to open file picker.");}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if((req!=801&&req!=802)||result!=RESULT_OK||data==null||data.getData()==null)return;selected=data.getData();selectedPdf=req==802;sheetIndex=0;range.setText("");pageFilter.setSelection(0);try{getContentResolver().takePersistableUriPermission(selected,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}loadPreview();}
    private String nameOf(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)return c.getString(0);}catch(Exception ignored){}return selectedPdf?"Document.pdf":"Photo";}
    private void setBusy(boolean value){busy=value;if(value)getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);photoButton.setEnabled(!value);fileButton.setEnabled(!value);sendButton.setEnabled(!value&&ready);copies.setEnabled(!value);for(View control:controls)control.setEnabled(!value);refresh.setEnabled(!value&&selected!=null);fullPreview.setEnabled(!value&&ready);previousSheet.setEnabled(!value&&ready&&sheetIndex>0);nextSheet.setEnabled(!value&&ready&&sheetIndex+1<totalSheets);progress.setVisibility(value?View.VISIBLE:View.GONE);}
    private void showStatus(String s){runOnUiThread(()->{if(!isDestroyed())status.setText(s);});}
    private String error(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void moveSheet(int delta){if(busy||!ready||totalSheets==0)return;int next=sheetIndex+delta;if(next<0||next>=totalSheets)return;sheetIndex=next;loadPreview();}
    private void showPageViewer(){
        if(busy||!ready||selected==null)return;
        try{
            DirectPrintOptions opts=currentOptions();
            java.util.BitSet checked=new java.util.BitSet(pages);
            for(int index:opts.selectedPages(pages))checked.set(index);
            viewer=new DirectPageViewer(this,selected,selectedPdf,filename,pages,opts,checked,result->{
                range.setText(DirectPrintOptions.formatSelection(result,pages));
                pageFilter.setSelection(0);sheetIndex=0;loadPreview();
            });
            viewer.show();
        }catch(IllegalArgumentException e){status.setText(e.getMessage());Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void loadPreview(){
        final DirectPrintOptions opts;try{opts=currentOptions();}catch(IllegalArgumentException e){status.setText(e.getMessage());return;}
        ready=false;setBusy(true);status.setText("Rendering preview…");preview.setImageDrawable(null);preview.setVisibility(View.GONE);previewNav.setVisibility(View.GONE);
        if(previewBitmap!=null){previewBitmap.recycle();previewBitmap=null;}Uri uri=selected;boolean pdf=selectedPdf;int requested=sheetIndex;
        new Thread(()->{try(DirectPrintDocument doc=DirectPrintDocument.open(this,uri,pdf,true)){
            String name=nameOf(uri);int count=doc.pageCount();int selectedCount=opts.selectedPages(count).size();int sheets=opts.sheets(count);int index=Math.max(0,Math.min(requested,sheets-1));
            Bitmap image=sheets>0?doc.preview(opts,index):null;
            runOnUiThread(()->{if(isDestroyed()){if(image!=null)image.recycle();return;}
                filename=name;pages=count;sheetIndex=index;totalSheets=sheets;previewBitmap=image;preview.setImageBitmap(image);
                preview.setVisibility(image!=null?View.VISIBLE:View.GONE);previewNav.setVisibility(sheets>0?View.VISIBLE:View.GONE);
                sheetPosition.setText((index+1)+" / "+sheets);
                selection.setText(name+" · "+selectedCount+" of "+count+" pages selected · "+sheets+" sheet(s)");
                ready=true;status.setText(sheets==0?"No pages selected. Open fullscreen preview to select pages.":"Swipe or use Prev / Next to view every print sheet.");
                setBusy(false);
            });
        }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(!isDestroyed()){status.setText("Could not preview: "+error(e));setBusy(false);}});}},"direct-preview").start();
    }
    private void print(){if(!ready||busy)return;if(ip.isEmpty()){status.setText("Set the printer IP in the main screen first.");return;}final DirectPrintOptions opts;try{opts=currentOptions();opts.pages(pages);}catch(IllegalArgumentException e){status.setText(e.getMessage());return;}
        if(opts.paper!=0||opts.dpi!=600){new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Experimental print settings").setMessage("Use matching paper. This printer may reject the selected paper size or DPI. Continue?").setNegativeButton("Cancel",null).setPositiveButton("Print",(dialog,which)->send(opts)).show();}else send(opts);
    }
    private void send(DirectPrintOptions opts){if(busy)return;setBusy(true);Uri uri=selected;boolean pdf=selectedPdf;String name=filename;int count=copies.getSelectedItemPosition()+1;
        new Thread(()->{File raster=null;int accepted=0,pageCount=0;String message;try{raster=File.createTempFile("direct_urf_",".urf",getCacheDir());try(DirectPrintDocument doc=DirectPrintDocument.open(this,uri,pdf,false)){pageCount=opts.sheets(doc.pageCount());doc.writeUrf(raster,opts,(page,total)->showStatus("Preparing page "+page+" of "+total+"…"));}for(int i=0;i<count;i++){showStatus("Sending copy "+(i+1)+" of "+count+"…");IppClient.IppResult result=IppClient.sendPrintJob(ip,raster,name,opts.paper==0?null:opts.media());if(!result.success)throw new IOException(result.message);accepted++;}message="Printer accepted "+(pageCount*accepted)+" sheet(s). Check the printer for physical output.";}catch(Exception|OutOfMemoryError e){message=accepted+" of "+count+" copies accepted. Could not finish: "+error(e);PrintLog.e("DirectPrint",message);}finally{if(raster!=null)raster.delete();}
        PrintJobHistory.addJob(getApplicationContext(),new PrintJobHistory.JobRecord(name,System.currentTimeMillis(),accepted==count?"COMPLETED":"FAILED",message,pageCount*accepted));String finalMessage=message;runOnUiThread(()->{if(!isDestroyed()){status.setText(finalMessage);setBusy(false);}});},"direct-print").start();
    }
    @Override public void onBackPressed(){if(busy)Toast.makeText(this,"Please keep this screen open until printing finishes.",Toast.LENGTH_SHORT).show();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(selected!=null)state.putString("uri",selected.toString());state.putBoolean("pdf",selectedPdf);state.putInt("copies",copies.getSelectedItemPosition());state.putString("range",range.getText().toString());state.putInt("filter",pageFilter.getSelectedItemPosition());state.putBoolean("reverse",reverse.isChecked());state.putInt("sheetIndex",sheetIndex);}
    @Override protected void onDestroy(){if(viewer!=null)viewer.dismiss();if(preview!=null)preview.setImageDrawable(null);if(previewBitmap!=null)previewBitmap.recycle();super.onDestroy();}
}
