package com.tagme.analytics;

import com.tagme.participation.MissionCompletedNotice;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CompletionEventRecorder {
    private final PageEventService events;private final TelemetrySessions sessions;private final ThreadPoolTaskExecutor analyticsExecutor;
    private final AtomicLong dropped=new AtomicLong();
    @Value("${tagme.analytics.completion-recording-enabled:true}") private boolean enabled;
    public CompletionEventRecorder(PageEventService events,TelemetrySessions sessions,ThreadPoolTaskExecutor analyticsExecutor) {
        this.events=events;this.sessions=sessions;this.analyticsExecutor=analyticsExecutor;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void completed(MissionCompletedNotice notice) {
        if(!enabled) return;
        if(!Set.of("PROFILE","SYNC_RESULT","WISH_REVISION","GAME_ATTEMPT","CHEER_REVISION").contains(notice.sourceKind())) return;
        try {
            String session=sessions.current(notice.userId());
            analyticsExecutor.execute(()->{try{events.committed(notice,session);}catch(RuntimeException unavailable){dropped.incrementAndGet();}});
        } catch(RuntimeException unavailable) { dropped.incrementAndGet(); }
    }
    public long dropped() { return dropped.get(); }
    @Configuration
    static class Worker {
        @Bean ThreadPoolTaskExecutor analyticsExecutor() {
            var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(1);executor.setQueueCapacity(256);
            executor.setThreadNamePrefix("analytics-completion-");executor.setDaemon(true);return executor;
        }
    }
}
