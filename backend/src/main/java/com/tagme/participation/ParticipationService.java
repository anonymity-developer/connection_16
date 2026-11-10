package com.tagme.participation;

import com.tagme.auth.AuthPrincipal;
import com.tagme.auth.AuthRateLimiter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.participation.ParticipationModels.*;
import static com.tagme.participation.ParticipationRepository.error;

@Service
public class ParticipationService {
    private final ParticipationRepository repository;
    private final MissionCompletionService completion;
    private final TransactionTemplate transactions;
    private final AuthRateLimiter limits;
    public ParticipationService(ParticipationRepository repository,MissionCompletionService completion,PlatformTransactionManager manager,AuthRateLimiter limits) {
        this.repository = repository; this.completion = completion; this.transactions = new TransactionTemplate(manager); this.limits = limits;
    }
    public static String tokenHash(String token) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) throw new IllegalArgumentException();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (IllegalArgumentException failure) { throw error(400,"INVALID_TAG","태그 주소를 다시 확인해 주세요."); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public Event event() { return repository.event(false); }
    public String pendingHash(String token,String ip) {
        limits.check("tag-intent-ip",ip,30,60); return tokenHash(token);
    }
    public List<CatalogInterest> interests() { return repository.interests(); }
    @Transactional(readOnly=true)
    public Policies policies(long userId) { return repository.policyState(userId,false); }
    @Transactional(readOnly=true)
    public ProfileState profile(long userId) {
        var profile = repository.profile(userId); var policies = repository.policyState(userId,false);
        return new ProfileState(repository.identity(userId),profile,policies,repository.profileComplete(profile),repository.profileComplete(profile) && profile.isPublic() && policies.ready() && policies.policies().stream().allMatch(Policy::agreed));
    }
    @Transactional(readOnly=true)
    public Collection collection(long userId) {
        var missions = repository.missions(userId);
        return new Collection(missions,(int)missions.stream().filter(m -> "COMPLETED".equals(m.state())).count(),repository.completion(userId));
    }
    public ProfileState save(AuthPrincipal principal,ParticipationController.ProfileRequest value) {
        limits.check("profile-user",Long.toString(principal.userId()),30,60);
        validateUrl(value.githubUrl(),true); validateUrl(value.blogUrl(),false);
        if (value.interestIds().stream().distinct().count() != value.interestIds().size()) throw error(400,"INVALID_INTERESTS","관심사를 중복 없이 선택해 주세요.");
        Set<Long> available = repository.interests().stream().map(CatalogInterest::id).collect(Collectors.toSet());
        if (!available.containsAll(value.interestIds())) throw error(400,"INVALID_INTERESTS","관심사를 다시 선택해 주세요.");
        return transactions.execute(status -> {
            repository.lockPrincipal(principal); repository.requireOpen(true);
            if(!repository.identity(principal.userId()).ready())throw error(409,"MATTERMOST_PROFILE_REQUIRED","Mattermost 프로필 정보를 불러오려면 다시 로그인해 주세요.");
            var old = repository.profile(principal.userId());
            if ((old == null ? 0 : old.version()) != value.expectedVersion()) throw error(409,"PROFILE_CHANGED","프로필이 변경됐어요. 새로 불러온 뒤 다시 저장해 주세요.");
            var policies = requirePolicies(principal.userId());
            var acceptances = value.acceptPolicies();
            if (acceptances.stream().map(ParticipationController.Acceptance::type).distinct().count() != acceptances.size()) throw error(400,"INVALID_CONSENT","동의 항목을 다시 확인해 주세요.");
            for (var accepted : acceptances) {
                Policy current = policies.get(accepted.type());
                if (current == null || !current.version().equals(accepted.version())) throw error(409,"POLICY_CHANGED","동의 문구가 변경됐어요. 최신 문구를 확인해 주세요.");
                repository.agree(principal.userId(),accepted.type(),accepted.version());
            }
            for (PolicyType type : PolicyType.values()) {
                if (!repository.agreed(principal.userId(),type,policies.get(type).version())) throw error(409,"CONSENT_REQUIRED","필요한 동의 문구를 확인하고 선택해 주세요.");
            }
            repository.saveProfile(principal.userId(),value);
            var saved = repository.profile(principal.userId());
            if (repository.showMeEligible(principal.userId())) repository.receipt(principal.userId(),"PROFILE",saved.version(),repository.now());
            completion.completeShowMe(principal.userId());
            return profile(principal.userId());
        });
    }
    // Explicit authenticated POST: reads stay read-only and CSRF remains required.
    public boolean reconcileShowMe(AuthPrincipal principal) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            repository.lockPrincipal(principal);
            return completion.completeShowMe(principal.userId());
        }));
    }
    private Map<PolicyType,Policy> requirePolicies(long userId) {
        var state = repository.policyState(userId,true);
        if (!state.ready()) throw error(503,"POLICIES_NOT_CONFIGURED","참여 안내와 동의 문구를 준비하고 있어요.");
        return state.policies().stream().collect(Collectors.toMap(Policy::type,p -> p));
    }
    private void validateUrl(String value,boolean github) {
        if (value == null) return;
        try {
            var uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() != -1 && uri.getPort() != 443 || value.chars().anyMatch(c -> c <= 32 || c == 127)
                    || github && !"github.com".equalsIgnoreCase(uri.getHost())) throw new IllegalArgumentException();
        } catch (IllegalArgumentException failure) { throw error(400,"INVALID_URL","링크는 HTTPS 주소로 입력해 주세요. GitHub는 github.com 주소를 사용해 주세요."); }
    }
    public TagResult resolve(AuthPrincipal principal,String hash) {
        Instant received = Instant.now();
        limits.check("tag-user",Long.toString(principal.userId()),60,60);
        return transactions.execute(status -> {
            repository.lockPrincipal(principal); repository.requireOpen(false);
            var code = repository.tag(hash); var mission = repository.mission(principal.userId(),code); repository.activeMission(mission.id());
            // Legacy SHOW ME tags are harmless links to the profile; they cannot unlock a mission.
            if (code == MissionCode.SHOW_ME) {
                boolean granted = completion.completeShowMe(principal.userId());
                return new TagResult(code,repository.mission(principal.userId(),code).state(),"/me/profile",false,granted);
            }
            boolean newlyUnlocked = "LOCKED".equals(mission.state());
            if (newlyUnlocked) {
                repository.unlock(principal.userId(),mission.id(),received);
                repository.receipt(principal.userId(),"TAG_UNLOCK",mission.id(),received);
            }
            var current = repository.mission(principal.userId(),code);
            return new TagResult(code,current.state(),route(code),newlyUnlocked,false);
        });
    }
    private String route(MissionCode code) { return "/missions/" + code.name().toLowerCase(java.util.Locale.ROOT).replace('_','-'); }
}
