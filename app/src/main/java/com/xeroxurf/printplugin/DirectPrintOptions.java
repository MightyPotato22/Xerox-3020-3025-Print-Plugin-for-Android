package com.xeroxurf.printplugin;

import java.util.*;

final class DirectPrintOptions {
    final int paper,dpi,orientation,scaleMode,percent,margin,toner,brightness,contrast,nup,pageFilter;
    final String range;
    final boolean reverse;
    DirectPrintOptions(int paper,int dpi,int orientation,int scaleMode,int percent,int margin,int toner,int brightness,int contrast,int nup,String range,boolean reverse,int pageFilter){
        this.paper=paper;this.dpi=dpi;this.orientation=orientation;this.scaleMode=scaleMode;this.percent=percent;this.margin=margin;this.toner=toner;this.brightness=brightness;this.contrast=contrast;this.nup=nup;this.range=range;this.reverse=reverse;this.pageFilter=pageFilter;
    }
    int width(){return (int)Math.round(new double[]{4960,3496,5100,5100}[paper]*dpi/600.0);}
    int height(){return (int)Math.round(new double[]{7015,4960,6600,8400}[paper]*dpi/600.0);}
    String media(){return new String[]{"iso_a4_210x297mm","iso_a5_148x210mm","na_letter_8.5x11in","na_legal_8.5x14in"}[paper];}
    List<Integer> selectedPages(int count){
        LinkedHashSet<Integer> chosen=new LinkedHashSet<>();
        if(range.trim().equalsIgnoreCase("none"))return new ArrayList<>();
        if(range.trim().isEmpty()||range.trim().equalsIgnoreCase("all")){for(int i=0;i<count;i++)chosen.add(i);}
        else for(String part:range.split(",",-1)){
            String[] bounds=part.trim().split("-", -1);
            if(bounds.length>2)throw new IllegalArgumentException("Invalid page range.");
            int a,b;try{a=Integer.parseInt(bounds[0].trim());b=bounds.length==2?Integer.parseInt(bounds[1].trim()):a;}catch(NumberFormatException e){throw new IllegalArgumentException("Use page numbers such as 1,3-5.");}
            if(a<1||b<a||b>count)throw new IllegalArgumentException("Page range must be within 1–"+count+".");
            for(int i=a;i<=b;i++)chosen.add(i-1);
        }
        List<Integer> list=new ArrayList<>(chosen);if(pageFilter!=0){java.util.Iterator<Integer> it=list.iterator();while(it.hasNext()){int page=it.next()+1;if((pageFilter==1&&page%2==0)||(pageFilter==2&&page%2!=0))it.remove();}}if(reverse)Collections.reverse(list);return list;
    }
    List<Integer> pages(int count){List<Integer> list=selectedPages(count);if(list.isEmpty())throw new IllegalArgumentException("Select at least one page.");return list;}
    int sheets(int count){return (selectedPages(count).size()+nup-1)/nup;}
    DirectPrintOptions singlePage(){return new DirectPrintOptions(paper,dpi,orientation,scaleMode,percent,margin,toner,brightness,contrast,1,"",false,0);}
    static String formatSelection(java.util.BitSet bits,int count){if(bits.cardinality()==0)return "none";if(bits.cardinality()==count)return "";StringBuilder out=new StringBuilder();for(int start=bits.nextSetBit(0);start>=0&&start<count;){int end=Math.min(count,bits.nextClearBit(start));if(out.length()>0)out.append(',');out.append(start+1);if(end>start+1)out.append('-').append(end);start=bits.nextSetBit(end);}return out.toString();}
    int gray(int value){
        if(value==255)return 255;
        int g=Math.max(0,Math.min(255,Math.round((value-128)*(contrast/100f)+128+brightness)));
        return 255-Math.round((255-g)*(toner==1?0.72f:toner==2?0.48f:1f));
    }
}
