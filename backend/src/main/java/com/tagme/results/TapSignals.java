package com.tagme.results;

import java.util.*;

/** Operator observations only. Never changes validation, completion or ranking. */
public final class TapSignals {
    private TapSignals() { }
    public record Summary(List<String> codes,int sameTimestampPairs,Integer commonIntervalMs,int intervalSharePermille) { }
    public static Summary inspect(List<Integer> taps) {
        var intervals=new HashMap<Integer,Integer>();int duplicates=0;
        for(int i=1;i<taps.size();i++) {
            int gap=taps.get(i)-taps.get(i-1);
            if(gap==0)duplicates++;
            if(gap>0)intervals.merge(gap,1,Integer::sum);
        }
        var mode=intervals.entrySet().stream().sorted(Comparator.<Map.Entry<Integer,Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparingInt(Map.Entry::getKey)).findFirst();
        int share=mode.isEmpty()?0:mode.get().getValue()*1000/Math.max(1,taps.size()-1);
        var codes=new ArrayList<String>();
        if(duplicates>=10)codes.add("SAME_TIMESTAMP");
        if(taps.size()>=30&&share>=800)codes.add("REGULAR_INTERVALS");
        if(taps.size()>=300)codes.add("HIGH_VOLUME");
        return new Summary(List.copyOf(codes),duplicates,mode.map(Map.Entry::getKey).orElse(null),share);
    }
}
