package com.xeroxurf.printplugin;

import java.util.*;

final class DirectPrintOptions {
    final int paper,dpi,orientation,scaleMode,percent,margin,toner,brightness,contrast,nup;
    final String range;
    final boolean reverse;
    DirectPrintOptions(int paper,int dpi,int orientation,int scaleMode,int percent,int margin,int toner,int brightness,int contrast,int nup,String range,boolean reverse){
        this.paper=paper;this.dpi=dpi;this.orientation=orientation;this.scaleMode=scaleMode;this.percent=percent;this.margin=margin;this.toner=toner;this.brightness=brightness;this.contrast=contrast;this.nup=nup;this.range=range;this.reverse=reverse;
    }
    int width(){return (int)Math.round(new double[]{4960,3496,5100,5100}[paper]*dpi/600.0);}
    int height(){return (int)Math.round(new double[]{7015,4960,6600,8400}[paper]*dpi/600.0);}
    String media(){return new String[]{"iso_a4_210x297mm","iso_a5_148x210mm","na_letter_8.5x11in","na_legal_8.5x14in"}[paper];}
    List<Integer> pages(int count){
        LinkedHashSet<Integer> chosen=new LinkedHashSet<>();
        if(range.trim().isEmpty()||range.trim().equalsIgnoreCase("all")){for(int i=0;i<count;i++)chosen.add(i);}
        else for(String part:range.split(",",-1)){
            String[] bounds=part.trim().split("-", -1);
            if(bounds.length>2)throw new IllegalArgumentException("Invalid page range.");
            int a,b;try{a=Integer.parseInt(bounds[0].trim());b=bounds.length==2?Integer.parseInt(bounds[1].trim()):a;}catch(NumberFormatException e){throw new IllegalArgumentException("Use page numbers such as 1,3-5.");}
            if(a<1||b<a||b>count)throw new IllegalArgumentException("Page range must be within 1–"+count+".");
            for(int i=a;i<=b;i++)chosen.add(i-1);
        }
        List<Integer> list=new ArrayList<>(chosen);if(reverse)Collections.reverse(list);if(list.isEmpty())throw new IllegalArgumentException("Select at least one page.");return list;
    }
    int sheets(int count){return (pages(count).size()+nup-1)/nup;}
    int gray(int value){
        if(value==255)return 255;
        int g=Math.max(0,Math.min(255,Math.round((value-128)*(contrast/100f)+128+brightness)));
        return 255-Math.round((255-g)*(toner==1?0.72f:toner==2?0.48f:1f));
    }
}
