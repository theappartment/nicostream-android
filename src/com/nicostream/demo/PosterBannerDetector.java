package com.nicostream.demo;
import java.util.Locale;

/** Finds only plausible red-and-white footer bands; OCR confirms their meaning separately. */
final class PosterBannerDetector {
    interface Pixels {int at(int x,int y);}
    static boolean red(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r>=170&&g<115&&b<125&&r>g*1.65&&r>b*1.5;}
    static boolean white(int c){return ((c>>16)&255)>175&&((c>>8)&255)>145&&(c&255)>145;}
    static boolean bandRow(int width,int y,Pixels pixels){
        int red=0,total=0,known=0,step=Math.max(1,width/160);
        for(int x=width/20;x<width-width/20;x+=step){int c=pixels.at(x,y);total++;if(red(c)){red++;known++;}else if(white(c))known++;}
        return total>0&&red>=total*.2&&known>=total*.78;
    }
    static int findBand(int width,int height,Pixels pixels){
        if(width<80||height<width*1.2||height>width*2.1)return -1;
        int bottom=height-1,lowest=height-Math.max(3,height/25);
        while(bottom>lowest&&!bandRow(width,bottom,pixels))bottom--;
        if(bottom<=lowest)return -1;
        int top=bottom,gaps=0;for(int y=bottom-1;y>height*.77;y--){if(bandRow(width,y,pixels)){top=y;gaps=0;}else if(++gaps>=2)break;}
        int bandHeight=bottom-top+1;if(bandHeight<height*.045||bandHeight>height*.16||top<=height*.78)return -1;
        int whites=0,total=0,step=Math.max(1,width/160);
        for(int y=top;y<=bottom;y++)for(int x=width/20;x<width-width/20;x+=step){total++;if(white(pixels.at(x,y)))whites++;}
        return whites>=total*.015&&whites<total*.4?top:-1;
    }
    static boolean confirmedText(String text){return text!=null&&text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","").equals("aggiuntodirecente");}
}
