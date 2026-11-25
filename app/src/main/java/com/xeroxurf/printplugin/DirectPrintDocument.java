package com.xeroxurf.printplugin;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.*;

final class DirectPrintDocument implements Closeable {
    private File pdfFile;
    private PdfRenderer pdf;
    private Bitmap photo;
    static DirectPrintDocument open(Context context,Uri uri,boolean isPdf,boolean preview)throws IOException{
        DirectPrintDocument doc=new DirectPrintDocument();
        try{
            if(isPdf){
                doc.pdfFile=File.createTempFile("direct_pdf_",".pdf",context.getCacheDir());
                try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(doc.pdfFile)){
                    if(in==null)throw new IOException("Unable to read file.");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                }
                ParcelFileDescriptor fd=ParcelFileDescriptor.open(doc.pdfFile,ParcelFileDescriptor.MODE_READ_ONLY);
                try{doc.pdf=new PdfRenderer(fd);}catch(RuntimeException|IOException e){fd.close();throw e;}
            }else{
                doc.photo=decodeImage(context,uri,preview?900:4000);
                if(doc.photo==null)throw new IOException("Unsupported image.");
            }
            if(doc.pageCount()==0)throw new IOException("File has no pages.");
            return doc;
        }catch(IOException|RuntimeException|OutOfMemoryError e){doc.close();throw e;}
    }
    private static Bitmap decodeImage(Context context,Uri uri,int maxSide)throws IOException{
        if(Build.VERSION.SDK_INT>=28)return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();float f=Math.min(1f,maxSide/(float)Math.max(w,h));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetSize(Math.max(1,Math.round(w*f)),Math.max(1,Math.round(h*f)));
        });
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>maxSide)options.inSampleSize*=2;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(in,null,options);}
    }
    int pageCount(){return pdf==null?1:pdf.getPageCount();}
    Bitmap preview(){Bitmap bitmap=Bitmap.createBitmap(420,594,Bitmap.Config.ARGB_8888);try{if(pdf!=null){try(PdfRenderer.Page page=pdf.openPage(0)){render(bitmap,page,0,420,594);}}else render(bitmap,null,0,420,594);return bitmap;}catch(RuntimeException|OutOfMemoryError e){bitmap.recycle();throw e;}}
    interface Progress{void page(int page,int total);}
    void writeUrf(File target,Progress progress)throws IOException{
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target),65536)){
            UrfEncoder.writeDocumentHeader(out,pageCount());
            for(int i=0;i<pageCount();i++){progress.page(i+1,pageCount());UrfEncoder.writePageHeader(out);if(pdf!=null){try(PdfRenderer.Page page=pdf.openPage(i)){writePage(out,page);}}else writePage(out,null);}
        }
    }
    private void writePage(OutputStream out,PdfRenderer.Page page)throws IOException{
        int width=UrfEncoder.A4_WIDTH_600,height=UrfEncoder.A4_HEIGHT_600;
        for(int y=0;y<height;y+=128){Bitmap strip=Bitmap.createBitmap(width,Math.min(128,height-y),Bitmap.Config.ARGB_8888);try{render(strip,page,y,width,height);UrfEncoder.writeStrip(out,strip);}finally{strip.recycle();}}
    }
    private void render(Bitmap target,PdfRenderer.Page page,int offset,int width,int height){
        target.eraseColor(Color.WHITE);int w=page==null?photo.getWidth():page.getWidth(),h=page==null?photo.getHeight():page.getHeight();boolean rotate=w>h;
        float margin=width*5f/210f,scale=Math.min((width-2*margin)/(rotate?h:w),(height-2*margin)/(rotate?w:h));
        float x=(width-scale*(rotate?h:w))/2,y=(height-scale*(rotate?w:h))/2-offset;
        Matrix m=new Matrix();if(rotate)m.setValues(new float[]{0,-scale,x+h*scale,scale,0,y,0,0,1});else m.setValues(new float[]{scale,0,x,0,scale,y,0,0,1});
        if(page!=null)page.render(target,null,m,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);else new Canvas(target).drawBitmap(photo,m,new Paint(Paint.FILTER_BITMAP_FLAG));
    }
    @Override public void close(){if(pdf!=null){pdf.close();pdf=null;}if(photo!=null){photo.recycle();photo=null;}if(pdfFile!=null){pdfFile.delete();pdfFile=null;}}
}
