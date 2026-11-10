package com.tagme.discovery;

import java.util.List;
import com.tagme.participation.ParticipationModels.Interest;

public final class DiscoveryModels {
    private DiscoveryModels() { }
    public record Type(String code,String title,String version) { }
    public record Person(long id,String nickname,List<Interest> interests,Type type,List<String> sharedInterests,boolean sameType,boolean visited,boolean visitedMe,boolean mutual) { }
    public record People(List<Person> items,Long nextCursor) { }
    public record Detail(Person person,String realName,String region,int classNo,String bio,String githubUrl,String blogUrl,String photoUrl,boolean newlyVisited) { }
    public record Stats(long visitedCount,long visitorCount,long mutualCount) { }
    public record Self(long id,String nickname) { }
    public record Edge(long from,long to,int weight,String strength) { }
    public record Graph(Self self,List<Person> nodes,List<Edge> edges,boolean truncated) { }
}
