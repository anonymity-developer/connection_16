package com.tagme.content;

import com.tagme.participation.*;
import org.springframework.context.annotation.*;
import static com.tagme.content.ContentModels.*;
import static com.tagme.participation.ParticipationModels.*;

@Configuration
public class ContentCompletionRule {
    private CompletionRule rule(Kind kind,ContentRepository repository) {
        return new CompletionRule() {
            public MissionCode code() { return kind.mission(); }
            public boolean accepts(long user,Mission mission,Receipt receipt) {
                if(!kind.source().equals(receipt.sourceKind()))return false;
                var revision=repository.revision(receipt.sourceId());if(revision==null)return false;
                var item=repository.item(revision.itemId(),false);
                if(item.authorId()!=user||item.kind()!=kind||!"VISIBLE".equals(item.visibility()))return false;
                var id=Long.valueOf(revision.id());
                return ("QUEUED".equals(revision.status())&&id.equals(item.pendingId()))
                    || ("APPROVED".equals(revision.status())&&id.equals(item.approvedId()));
            }
        };
    }
    @Bean CompletionRule wishCompletionRule(ContentRepository repository) { return rule(Kind.WISH,repository); }
    @Bean CompletionRule cheerCompletionRule(ContentRepository repository) { return rule(Kind.CHEER,repository); }
}
