package com.tagme.content;

import java.time.Instant;
import java.util.List;
import com.tagme.participation.ParticipationModels.MissionCode;

public final class ContentModels {
    private ContentModels() { }
    public enum Kind { WISH, CHEER, COMMENT;
        public MissionCode mission() { return switch(this) { case WISH -> MissionCode.MAKE_A_WISH; case CHEER -> MissionCode.SAY_SOMETHING; case COMMENT -> throw new IllegalStateException("Comments do not award a mission"); }; }
        public String source() { return switch(this) { case WISH -> "WISH_REVISION"; case CHEER -> "CHEER_REVISION"; case COMMENT -> "COMMENT_REVISION"; }; }
    }
    public record Item(long id,long authorId,Kind kind,String visibility,Long approvedId,Long pendingId,Integer luckScore,Integer landingZone,long version) { }
    public record Revision(long id,long itemId,int number,String content,String templateId,String style,boolean anonymous,Instant submittedAt,String status,String reason,@com.fasterxml.jackson.annotation.JsonIgnore String hash) { }
    public record OwnSlot(Long id,long version,String visibility,Revision approvedRevision,Revision pendingRevision,Revision latestRevision,boolean missionCompleted,Integer luckScore,Integer landingZone) { }
    // A profile ID is disclosed only for explicitly nonanonymous, currently public content.
    public record PublicContent(long id,String content,String templateId,String style,boolean anonymous,String nickname,Integer luckScore,Integer landingZone,Long profileId) { }
    public record Feed(List<PublicContent> items,Long nextCursor) { }
    public record Job(long id,long revisionId,long authorId,long itemId,String status,int attempts,long fence,Instant leaseUntil) { }
    public record Work(Job job,String text) { }
    public record ReviewTask(Job job,Revision revision,Revision approvedRevision) { }
    public record ReviewRequest(long expectedFence,String decision,String reason) { }
}
