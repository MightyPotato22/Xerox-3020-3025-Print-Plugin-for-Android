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
    Bitmap preview(DirectPrintOptions opts){
        int width=500,height=Math.round(width*opts.height()/(float)opts.width());
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{renderSheet(bitmap,opts,opts.pages(pageCount()),0,0,width,height);applyTone(bitmap,opts);return bitmap;}catch(RuntimeException|OutOfMemoryError e){bitmap.recycle();throw e;}
    }
    interface Progress{void page(int page,int total);}
    void writeUrf(File target,DirectPrintOptions opts,Progress progress)throws IOException{
        java.util.List<Integer> selected=opts.pages(pageCount());int total=(selected.size()+opts.nup-1)/opts.nup;
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target),65536)){
            UrfEncoder.writeDocumentHeader(out,total);
            for(int sheet=0;sheet<total;sheet++){
                progress.page(sheet+1,total);UrfEncoder.writePageHeader(out,opts.width(),opts.height(),opts.dpi);
                writeSheet(out,opts,selected,sheet);
            }
        }
    }
    private void writeSheet(OutputStream out,DirectPrintOptions opts,java.util.List<Integer> selected,int sheet)throws IOException{
        int width=opts.width(),height=opts.height();
        for(int y=0;y<height;y+=128){
            Bitmap strip=Bitmap.createBitmap(width,Math.min(128,height-y),Bitmap.Config.ARGB_8888);
            try{renderSheet(strip,opts,selected,sheet,y,width,height);applyTone(strip,opts);UrfEncoder.writeStrip(out,strip);}finally{strip.recycle();}
        }
    }
    private void renderSheet(Bitmap target,DirectPrintOptions opts,java.util.List<Integer> selected,int sheet,int offset,int width,int height){
        target.eraseColor(Color.WHITE);
        int cols=opts.nup==4?2:1,rows=opts.nup==1?1:2;
        float paperMm=new float[]{210,148,215.9f,215.9f}[opts.paper],margin=width*opts.margin/paperMm;
        float cellW=(width-2*margin)/cols,cellH=(height-2*margin)/rows,gutter=opts.nup>1?margin/2:0;
        for(int slot=0;slot<opts.nup;slot++){
            int index=sheet*opts.nup+slot;if(index>=selected.size())break;
            float left=margin+(slot%cols)*cellW+gutter/2,top=margin+(slot/cols)*cellH+gutter/2-offset;
            float right=left+cellW-gutter,bottom=top+cellH-gutter;
            Rect clip=new Rect(Math.max(0,(int)Math.ceil(left)),Math.max(0,(int)Math.ceil(top)),Math.min(target.getWidth(),(int)Math.floor(right)),Math.min(target.getHeight(),(int)Math.floor(bottom)));
            if(clip.isEmpty())continue;
            if(pdf!=null){try(PdfRenderer.Page page=pdf.openPage(selected.get(index))){renderCell(target,page,opts,left,top,right,bottom,clip);}}
            else renderCell(target,null,opts,left,top,right,bottom,clip);
        }
    }
    private void renderCell(Bitmap target,PdfRenderer.Page page,DirectPrintOptions opts,float left,float top,float right,float bottom,Rect clip){
        int w=page==null?photo.getWidth():page.getWidth(),h=page==null?photo.getHeight():page.getHeight();
        float cw=right-left,ch=bottom-top;
        boolean rotate=opts.orientation==2||(opts.orientation==0&&Math.min(cw/h,ch/w)>Math.min(cw/w,ch/h));
        float rw=rotate?h:w,rh=rotate?w:h;
        float scale=opts.scaleMode==1?Math.max(cw/rw,ch/rh):Math.min(cw/rw,ch/rh);
        if(opts.scaleMode==2)scale*=opts.percent/100f;
        float x=left+(cw-scale*rw)/2,y=top+(ch-scale*rh)/2;
        Matrix m=new Matrix();if(rotate)m.setValues(new float[]{0,-scale,x+h*scale,scale,0,y,0,0,1});else m.setValues(new float[]{scale,0,x,0,scale,y,0,0,1});
        if(page!=null)page.render(target,clip,m,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        else{Canvas c=new Canvas(target);c.save();c.clipRect(clip);c.drawBitmap(photo,m,new Paint(Paint.FILTER_BITMAP_FLAG));c.restore();}
    }
    private static void applyTone(Bitmap bitmap,DirectPrintOptions opts){
        if(opts.toner==0&&opts.brightness==0&&opts.contrast==100)return;
        int w=bitmap.getWidth(),h=bitmap.getHeight();int[] row=new int[w];
        for(int y=0;y<h;y++){bitmap.getPixels(row,0,w,0,y,w,1);for(int x=0;x<w;x++){int c=row[x],g=(Color.red(c)*77+Color.green(c)*150+Color.blue(c)*29)>>8;row[x]=Color.rgb(opts.gray(g),opts.gray(g),opts.gray(g));}bitmap.setPixels(row,0,w,0,y,w,1);}
    }
    @Override public void close(){if(pdf!=null){pdf.close();pdf=null;}if(photo!=null){photo.recycle();photo=null;}if(pdfFile!=null){pdfFile.delete();pdfFile=null;}}
}
