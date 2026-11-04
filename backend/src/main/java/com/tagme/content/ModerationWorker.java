package com.tagme.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="tagme.content.worker-enabled",havingValue="true",matchIfMissing=true)
public class ModerationWorker {
    private final ModerationService service;
    public ModerationWorker(ModerationService service) { this.service=service; }
    @Scheduled(fixedDelayString="${tagme.content.poll-ms:2000}")
    public void poll() { service.processOne(); }
}
