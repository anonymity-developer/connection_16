package com.tagme.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!${tagme.moderation.enabled:false} && ${tagme.content.worker-enabled:true}")
public class DirectPublicationWorker {
    private final PendingSubmissionSettlement settlement;
    public DirectPublicationWorker(PendingSubmissionSettlement settlement){this.settlement=settlement;}
    @Scheduled(fixedDelayString="${tagme.content.poll-ms:2000}")
    public void poll(){settlement.processBatch();}
}
