package com.tagme.operations;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.tagme.operations.AwardModels.*;

/** Exact production calculator, without Spring/DB. JUnit also calls this harness. */
public class AwardsCalculatorChecks {
    static final Instant T=Instant.parse("2026-01-01T00:00:00Z");
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static List<Proof> proofs(long user,Instant finish){var rows=new ArrayList<Proof>();var codes=List.of("SHOW_ME","SYNC_ME","MAKE_A_WISH","FAST_TAP","SAY_SOMETHING");for(int i=0;i<5;i++)rows.add(new Proof(user,codes.get(i),List.of("PROFILE","SYNC_RESULT","WISH_REVISION","GAME_ATTEMPT","CHEER_REVISION").get(i),user*10+i,user*100+i,finish.minusSeconds(4-i),true));return rows;}
    static Facts facts(List<User> users,List<Proof> proofs,List<Activity> comments,List<Activity> hearts,List<Activity> taps){return new Facts(new Window(T.plusSeconds(1000),T,T.plusSeconds(500),T.plusSeconds(900)),users,proofs,comments,hearts,taps,List.of(),0);}
    public static void verify() throws Exception {
        var a=new User(1,"fixture-a",T);var b=new User(2,"fixture-b",T.minusSeconds(1));
        var evidence=new ArrayList<>(proofs(1,T.plusSeconds(100)));evidence.addAll(proofs(2,T.plusSeconds(90)));
        var f=facts(List.of(a,b),evidence,List.of(new Activity(1,3),new Activity(2,2)),List.of(new Activity(1,5),new Activity(2,1)),List.of(new Activity(1,140),new Activity(2,100)));
        var ranked=AwardCalculator.rank(f);
        check(ranked.categories().get(Category.FIRST_COMPLETION).get(0).userId()==2,"receipt time beats approval/order");
        check(ranked.categories().get(Category.COMMENT_KING).get(0).score()==3,"distinct wish count");
        check(ranked.categories().get(Category.POPULAR_CHEER).get(0).score()==5,"valid hearts");
        check(ranked.categories().get(Category.FAST_TAP).get(0).score()==140,"official valid score");
        check(ranked.categories().get(Category.FAST_TAP).get(0).overlappingTopCategories().size()==3,"overlap, no allocation");
        var tie=new ArrayList<>(proofs(1,T.plusSeconds(100)));tie.addAll(proofs(2,T.plusSeconds(100)));
        var tied=AwardCalculator.rank(facts(List.of(a,b),tie,List.of(new Activity(1,3),new Activity(2,3)),List.of(),List.of()));
        check(tied.categories().get(Category.FIRST_COMPLETION).get(0).userId()==2,"first profile, not receipt id");
        check(tied.categories().get(Category.COMMENT_KING).get(0).userId()==2,"profile breaks score ties");
        check(tied.categories().get(Category.COMMENT_KING).get(0).metricTie(),"metric tie shown");
        var exact=AwardCalculator.rank(facts(List.of(a,new User(2,"fixture-b",T)),tie,List.of(),List.of(),List.of()));
        check(exact.categories().get(Category.FIRST_COMPLETION).stream().allMatch(c->c.rank()==1&&c.unresolvedTie()),"unresolved exact tie stays shared");
        var bad=new ArrayList<>(evidence);var p=bad.get(0);bad.set(0,new Proof(p.userId(),p.mission(),p.sourceKind(),p.sourceId(),p.receiptOrder(),p.acceptedAt(),false));
        var filtered=AwardCalculator.rank(facts(List.of(a,b),bad,List.of(),List.of(),List.of()));
        check(filtered.eligibleCompleteCount()==1&&filtered.invalidProofs()==1,"invalid source excluded");
        check(filtered.categories().get(Category.FAST_TAP).isEmpty(),"absent attempt is not a fake zero");
        var duplicate=new ArrayList<>(proofs(1,T));duplicate.set(4,duplicate.get(0));
        check(AwardCalculator.rank(facts(List.of(a),duplicate,List.of(),List.of(),List.of())).eligibleCompleteCount()==0,"five distinct missions required");
        check(AwardCalculator.rank(facts(List.of(new User(1,"fixture",null)),proofs(1,T),List.of(),List.of(),List.of())).eligibleCompleteCount()==0,"missing registration excluded");
        check(AwardCalculator.rank(facts(List.of(),List.of(),List.of(),List.of(),List.of())).eligibleCompleteCount()==0,"empty stays empty");
        var pool=Executors.newFixedThreadPool(4);
        try{var futures=new ArrayList<Future<Ranking>>();for(int i=0;i<40;i++)futures.add(pool.submit(()->AwardCalculator.rank(f)));for(var future:futures)check(future.get(10,TimeUnit.SECONDS).equals(ranked),"concurrent identical reads");}finally{pool.shutdownNow();}
        check(evidence.size()==10,"inputs unchanged");
    }
    public static void main(String[] args)throws Exception{verify();System.out.println("PASS: actual calculator ranking, ties, overlap, invalid proofs and 40 concurrent repeat reads");}
}
