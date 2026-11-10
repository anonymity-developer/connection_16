package com.tagme.participation;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.tagme.participation.ParticipationModels.*;
import static com.tagme.participation.ParticipationRepository.error;

@Service
public class MissionCompletionService {
    private final ParticipationRepository repository;
    private final List<CompletionRule> rules;
    public MissionCompletionService(ParticipationRepository repository,List<CompletionRule> rules) { this.repository = repository; this.rules = rules; }
    @Transactional
    public boolean complete(long userId, MissionCode code, long receiptId) {
        return completeInternal(userId,code,receiptId,false);
    }
    @Transactional
    public boolean completeModerated(long userId,MissionCode code,long receiptId) {
        if (code != MissionCode.MAKE_A_WISH && code != MissionCode.SAY_SOMETHING) throw error(403,"INVALID_EVIDENCE","검수형 미션을 확인해 주세요.");
        return completeInternal(userId,code,receiptId,true);
    }
    /** No tag is required. Pre-registration is reconciled only when actually processed in OPEN. */
    @Transactional
    public boolean completeShowMe(long userId) {
        repository.lockUser(userId);
        var event = repository.event(true);
        if (!"OPEN".equals(event.status())) return false;
        var mission = repository.mission(userId,MissionCode.SHOW_ME);
        if (!mission.active() || "COMPLETED".equals(mission.state()) || !repository.showMeEligible(userId)) return false;
        repository.activeMission(mission.id());
        var processed = repository.requireOpenTime();
        var profile = repository.profile(userId);
        var receipt = repository.profileReceipt(userId,profile.version());
        if (receipt == null) receipt = repository.receipt(userId,"PROFILE",profile.version(),processed);
        if ("LOCKED".equals(mission.state())) repository.unlock(userId,mission.id(),processed);
        return completeInternal(userId,MissionCode.SHOW_ME,receipt.id(),false);
    }
    private boolean completeInternal(long userId,MissionCode code,long receiptId,boolean moderated) {
        repository.lockUser(userId);
        if (!moderated) repository.requireOpen(false);
        var mission = repository.mission(userId,code); repository.activeMission(mission.id());
        if ("LOCKED".equals(mission.state())) throw error(403,"MISSION_LOCKED","먼저 현장 태그로 미션을 열어주세요.");
        if ("COMPLETED".equals(mission.state())) return false;
        var receipt = repository.receipt(receiptId);
        if (moderated) repository.requireModeratedReceipt(receipt);
        if (receipt.userId() != userId) throw error(403,"INVALID_EVIDENCE","완료 근거를 확인할 수 없어요.");
        if (code != MissionCode.SHOW_ME && receipt.receivedAt().isBefore(mission.unlockedAt()))
            throw error(403,"INVALID_EVIDENCE","미션을 열기 전의 수행은 완료로 인정하지 않아요.");
        var rule = rules.stream().filter(r -> r.code() == code).findFirst().orElseThrow(() -> error(409,"MISSION_NOT_READY","아직 준비 중인 미션이에요."));
        if (!rule.accepts(userId,mission,receipt)) throw error(409,"MISSION_REQUIREMENTS","프로필과 공개 동의를 확인해 주세요.");
        var accepted = receipt.receivedAt().isAfter(mission.unlockedAt()) ? receipt.receivedAt() : mission.unlockedAt();
        var condition = rule.conditionReceivedAt(userId,receipt);
        if (condition.isAfter(accepted)) accepted = condition;
        // Never backdate a newly granted SHOW ME, including pre-event profiles and old tag unlocks.
        if (code == MissionCode.SHOW_ME) accepted = repository.requireOpenTime();
        repository.complete(userId,mission,receipt,accepted); return true;
    }
}
