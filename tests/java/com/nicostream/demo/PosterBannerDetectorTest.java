package com.nicostream.demo;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterBannerDetectorTest {
    int[] poster(int width,int height,int top,boolean letters){
        int[] pixels=new int[width*height];java.util.Arrays.fill(pixels,0xff224466);
        for(int y=top;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=0xffff0000;
        if(letters)for(int y=top+5;y<height-5;y++)for(int x=15;x<width-15;x++)if(x%12<4)pixels[y*width+x]=0xffffffff;
        return pixels;
    }
    @Test public void detectsRedWhiteFooterAtDifferentResolutions(){
        for(int width:new int[]{200,400}){int height=width*3/2,top=height*9/10;int[] pixels=poster(width,height,top,true);assertEquals(top,PosterBannerDetector.findBand(width,height,(x,y)->pixels[y*width+x]));}
    }
    @Test public void whiteLetterStrokesAndCompressionDoNotSplitTheBand(){
        int[] pixels=poster(200,300,270,true);
        for(int x=10;x<140;x++)pixels[281*200+x]=0xffffffff;
        for(int x=145;x<170;x++)pixels[279*200+x]=0xffff9999;
        assertEquals(270,PosterBannerDetector.findBand(200,300,(x,y)->pixels[y*200+x]));
    }
    @Test public void redArtworkWithoutLettersAndLargeRedAreasAreNotBadges(){
        int[] blank=poster(200,300,270,false);assertEquals(-1,PosterBannerDetector.findBand(200,300,(x,y)->blank[y*200+x]));
        int[] large=poster(200,300,200,true);assertEquals(-1,PosterBannerDetector.findBand(200,300,(x,y)->large[y*200+x]));
        int[] normal=poster(200,300,300,false);assertEquals(-1,PosterBannerDetector.findBand(200,300,(x,y)->normal[y*200+x]));
    }
    @Test public void landscapeAndThinRedRulesAreNotPosterBanners(){
        int[] landscape=poster(300,200,180,true);assertEquals(-1,PosterBannerDetector.findBand(300,200,(x,y)->landscape[y*300+x]));
        int[] thin=poster(200,300,290,true);assertEquals(-1,PosterBannerDetector.findBand(200,300,(x,y)->thin[y*200+x]));
    }
    @Test public void exactLabelIsRequiredBeforeRedrawing(){
        assertTrue(PosterBannerDetector.confirmedText("Aggiunto di recente"));assertTrue(PosterBannerDetector.confirmedText("AGGIUNTO\ndi recente"));
        assertFalse(PosterBannerDetector.confirmedText("Nuovi episodi"));assertFalse(PosterBannerDetector.confirmedText("NETFLIX"));assertFalse(PosterBannerDetector.confirmedText("Aggiunto alla lista"));assertFalse(PosterBannerDetector.confirmedText(null));
    }
}
