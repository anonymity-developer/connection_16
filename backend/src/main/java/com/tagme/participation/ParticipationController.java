package com.tagme.participation;

import com.tagme.auth.AuthException;
import com.tagme.auth.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.tagme.participation.ParticipationModels.*;

@RestController
@RequestMapping("/api/v1")
public class ParticipationController {
    public record TagRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "TagRequest[redacted]"; }
    }
    public record Acceptance(@NotNull PolicyType type,@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String version) { }
    public record ConsentRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String version,@NotNull Boolean agreed) { }
    public record ProfileRequest(@NotBlank @Size(max=30) String nickname,@Size(max=200) String bio,
                                 @Size(max=2048) String githubUrl,@Size(max=2048) String blogUrl,
                                 @NotNull @Size(max=10) List<@NotNull @Positive Long> interestIds,
                                 @NotNull @Size(max=2) List<@Valid Acceptance> acceptPolicies,@Min(0) long expectedVersion) {
        @Override public String toString() { return "ProfileRequest[redacted]"; }
    }
    record PendingTag(String hash,Instant expiresAt) implements Serializable {
        @Override public String toString() { return "PendingTag[redacted]"; }
    }
    private static final String PENDING_TAG = "TAGME_PENDING_TAG";
    private final ParticipationService service;
    public ParticipationController(ParticipationService service) { this.service = service; }
    @GetMapping("/event") public Event event() { return service.event(); }
    @GetMapping("/policies") public Policies policies(Authentication auth) { return service.policies(principal(auth).userId()); }
    @GetMapping("/interests") public List<CatalogInterest> interests() { return service.interests(); }
    @GetMapping("/users/me/profile") public ProfileState profile(Authentication auth) { return service.profile(principal(auth).userId()); }
    @PutMapping("/users/me/profile") public ProfileState save(Authentication auth,@Valid @RequestBody ProfileRequest body) { return service.save(principal(auth),body); }
    @GetMapping({"/missions","/missions/collection"}) public Collection collection(Authentication auth) { return service.collection(principal(auth).userId()); }
    @GetMapping("/missions/completion") public Completion completion(Authentication auth) { return service.collection(principal(auth).userId()).completion(); }
    public record ShowMeReconciled(boolean newlyCompleted) { }
    @PostMapping("/missions/show-me/reconcile") public ShowMeReconciled reconcile(Authentication auth) { return new ShowMeReconciled(service.reconcileShowMe(principal(auth))); }
    @PostMapping("/tags/resolve") public TagResult resolve(Authentication auth,@Valid @RequestBody TagRequest body) { return service.resolve(principal(auth),ParticipationService.tokenHash(body.token())); }
    @PostMapping("/tags/pending") @ResponseStatus(HttpStatus.ACCEPTED)
    public void pending(HttpServletRequest request,@Valid @RequestBody TagRequest body) {
        // Anonymous intent only: no tag lookup or unlock. Session ID rotation preserves this hash.
        request.getSession(true).setAttribute(PENDING_TAG,new PendingTag(service.pendingHash(body.token(),request.getRemoteAddr()),Instant.now().plusSeconds(900)));
    }
    @GetMapping("/tags/pending") public Pending pending(HttpServletRequest request) { return new Pending(pendingTag(request) != null); }
    @PostMapping("/tags/resume") public TagResult resume(Authentication auth,HttpServletRequest request) {
        var pending = pendingTag(request);
        if (pending == null) throw ParticipationRepository.error(404,"TAG_INTENT_EXPIRED","태그 연결이 만료됐어요. 현장 태그를 다시 읽어주세요.");
        var result = service.resolve(principal(auth),pending.hash()); request.getSession().removeAttribute(PENDING_TAG); return result;
    }
    private PendingTag pendingTag(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING_TAG) instanceof PendingTag tag)) return null;
        if (!tag.expiresAt().isAfter(Instant.now())) { session.removeAttribute(PENDING_TAG); return null; }
        return tag;
    }
    private AuthPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) throw AuthException.invalid(); return principal;
    }
}
