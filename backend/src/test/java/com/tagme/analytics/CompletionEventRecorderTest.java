package com.tagme.analytics;

import com.tagme.participation.MissionCompletedNotice;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompletionEventRecorderTest {
    @Test void saturatedQueueAndUnavailableSessionNeverEscapeBusinessCommit() {
        var events=mock(PageEventService.class);var sessions=mock(TelemetrySessions.class);var worker=mock(ThreadPoolTaskExecutor.class);
        var recorder=new CompletionEventRecorder(events,sessions,worker);ReflectionTestUtils.setField(recorder,"enabled",true);
        var notice=new MissionCompletedNotice(1,"SYNC_ME",2,"SYNC_RESULT",Instant.now());
        doThrow(new RejectedExecutionException("synthetic full queue")).when(worker).execute(any(Runnable.class));
        assertThatCode(()->recorder.completed(notice)).doesNotThrowAnyException();
        when(sessions.current(1)).thenThrow(new IllegalStateException("session unavailable"));
        assertThatCode(()->recorder.completed(notice)).doesNotThrowAnyException();
        assertThat(recorder.dropped()).isEqualTo(2);verifyNoInteractions(events);
    }
}
