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
    private Spinner copies;
    private ProgressBar progress;
    private Uri selected;
    private boolean selectedPdf,busy,ready;
    private String filename,ip;
    private int pages;
    private Bitmap previewBitmap;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout root,String text){TextView t=new TextView(this);t.setText(text);t.setPadding(0,dp(5),0,dp(5));root.addView(t);return t;}
    private MaterialButton button(LinearLayout root,String text){MaterialButton b=new MaterialButton(this);b.setText(text);root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
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
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.WHITE);preview.setVisibility(View.GONE);
        ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);preview.setColorFilter(new ColorMatrixColorFilter(gray));root.addView(preview,new LinearLayout.LayoutParams(-1,dp(250)));
        label(root,"Copies");copies=new Spinner(this);String[] counts={"1","2","3","4","5","6","7","8","9","10"};
        copies.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,counts));root.addView(copies);
        label(root,"A4 · black and white · automatic orientation · one-sided");
        sendButton=button(root,"Print now");sendButton.setEnabled(false);
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(dp(36),dp(36)));
        status=label(root,"");
        photoButton.setOnClickListener(v->pick(false));fileButton.setOnClickListener(v->pick(true));sendButton.setOnClickListener(v->print());
        if(state!=null&&state.getString("uri")!=null){selected=Uri.parse(state.getString("uri"));selectedPdf=state.getBoolean("pdf");copies.setSelection(state.getInt("copies"));loadPreview();}
    }
    private void pick(boolean pdf){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(pdf?"application/pdf":"image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(i,pdf?802:801);}catch(Exception e){status.setText("Unable to open file picker.");}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if((req!=801&&req!=802)||result!=RESULT_OK||data==null||data.getData()==null)return;selected=data.getData();selectedPdf=req==802;try{getContentResolver().takePersistableUriPermission(selected,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}loadPreview();}
    private String nameOf(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)return c.getString(0);}catch(Exception ignored){}return selectedPdf?"Document.pdf":"Photo";}
    private void setBusy(boolean value){busy=value;if(value)getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);photoButton.setEnabled(!value);fileButton.setEnabled(!value);sendButton.setEnabled(!value&&ready);copies.setEnabled(!value);progress.setVisibility(value?View.VISIBLE:View.GONE);}
    private void showStatus(String s){runOnUiThread(()->{if(!isDestroyed())status.setText(s);});}
    private String error(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void loadPreview(){ready=false;setBusy(true);status.setText("Opening file…");selection.setText("No file selected");preview.setImageDrawable(null);preview.setVisibility(View.GONE);if(previewBitmap!=null){previewBitmap.recycle();previewBitmap=null;}Uri uri=selected;boolean pdf=selectedPdf;
        new Thread(()->{try(DirectPrintDocument doc=DirectPrintDocument.open(this,uri,pdf,true)){String name=nameOf(uri);int count=doc.pageCount();Bitmap image=doc.preview();runOnUiThread(()->{if(isDestroyed()){image.recycle();return;}filename=name;pages=count;previewBitmap=image;preview.setImageBitmap(image);preview.setVisibility(View.VISIBLE);selection.setText(name+" · "+count+" page(s) · first-page preview");ready=true;status.setText("Ready to print.");setBusy(false);});}catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(!isDestroyed()){status.setText("Could not open: "+error(e));setBusy(false);}});}},"direct-preview").start();
    }
    private void print(){if(!ready||busy)return;if(ip.isEmpty()){status.setText("Set the printer IP in the main screen first.");return;}setBusy(true);Uri uri=selected;boolean pdf=selectedPdf;String name=filename;int count=copies.getSelectedItemPosition()+1;
        new Thread(()->{File raster=null;int accepted=0,pageCount=0;String message;try{raster=File.createTempFile("direct_urf_",".urf",getCacheDir());try(DirectPrintDocument doc=DirectPrintDocument.open(this,uri,pdf,false)){pageCount=doc.pageCount();doc.writeUrf(raster,(page,total)->showStatus("Preparing page "+page+" of "+total+"…"));}for(int i=0;i<count;i++){showStatus("Sending copy "+(i+1)+" of "+count+"…");IppClient.IppResult result=IppClient.sendPrintJob(ip,raster,name);if(!result.success)throw new IOException(result.message);accepted++;}message="Printer accepted "+(pageCount*accepted)+" page(s). Check the printer for physical output.";}catch(Exception|OutOfMemoryError e){message=accepted+" of "+count+" copies accepted. Could not finish: "+error(e);PrintLog.e("DirectPrint",message);}finally{if(raster!=null)raster.delete();}
        PrintJobHistory.addJob(getApplicationContext(),new PrintJobHistory.JobRecord(name,System.currentTimeMillis(),accepted==count?"COMPLETED":"FAILED",message,pageCount*accepted));String finalMessage=message;runOnUiThread(()->{if(!isDestroyed()){status.setText(finalMessage);setBusy(false);}});},"direct-print").start();
    }
    @Override public void onBackPressed(){if(busy)Toast.makeText(this,"Please keep this screen open until printing finishes.",Toast.LENGTH_SHORT).show();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(selected!=null)state.putString("uri",selected.toString());state.putBoolean("pdf",selectedPdf);state.putInt("copies",copies.getSelectedItemPosition());}
    @Override protected void onDestroy(){if(preview!=null)preview.setImageDrawable(null);if(previewBitmap!=null)previewBitmap.recycle();super.onDestroy();}
}
