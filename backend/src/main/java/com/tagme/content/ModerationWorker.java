package com.tagme.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnExpression("${tagme.moderation.enabled:false} && ${tagme.content.worker-enabled:true}")
public class ModerationWorker {
    private final ModerationService service;
    public ModerationWorker(ModerationService service) { this.service=service; }
    @Scheduled(fixedDelayString="${tagme.content.poll-ms:2000}")
    public void poll() { service.processOne(); }
}
