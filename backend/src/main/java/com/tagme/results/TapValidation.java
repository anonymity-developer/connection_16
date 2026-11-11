package com.tagme.results;

import java.util.List;

public final class TapValidation {
    private TapValidation() { }
    public record Interruption(String type,int atMs) { }
    public record Verdict(String status,String reason,int count) { }
    public static Verdict inspect(List<Integer> taps,List<Interruption> interruptions,int durationMs) {
        if(taps==null||taps.size()>1000||interruptions==null||interruptions.size()>20)throw new IllegalArgumentException();
        boolean dense=false;int previous=-1;
        for(Integer at:taps){
            if(at==null||at<0||at>=durationMs||at<previous)throw new IllegalArgumentException();
            if(previous>=0&&at-previous<1)dense=true;previous=at;
        }
        for(var event:interruptions)if(event==null||!List.of("HIDDEN","PAGE_HIDE").contains(event.type())||event.atMs()<-5000||event.atMs()>durationMs+5000)throw new IllegalArgumentException();
        if(!interruptions.isEmpty())return new Verdict("INTERRUPTED","PAGE_INTERRUPTED",taps.size());
        if(dense)return new Verdict("INVALID","DUPLICATE_MILLISECOND",taps.size());
        return new Verdict("VALID",null,taps.size());
    }
}
