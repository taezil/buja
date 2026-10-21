package com.dealflash.app;

/** Pure image-size math (JVM-testable). */
public final class ImageSampling {
    private ImageSampling(){}
    /** Largest power-of-two sample size that still leaves BOTH sides >= target pixels (never decodes below what is displayed). */
    public static int sampleSize(int w,int h,int target){
        int s=1;
        while(w/(s*2)>=target&&h/(s*2)>=target)s*=2;
        return s;
    }
    /** Decoded bytes for a w x h bitmap at the given sample size. */
    public static long bytes(int w,int h,int sample,boolean rgb565){
        long ww=Math.max(1,w/sample),hh=Math.max(1,h/sample);
        return ww*hh*(rgb565?2:4);
    }
}
