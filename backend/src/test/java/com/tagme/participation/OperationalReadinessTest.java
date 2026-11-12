package com.tagme.participation;

import com.tagme.auth.AuthException;
import com.tagme.auth.AuthPrincipal;
import com.tagme.auth.AuthRateLimiter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import static com.tagme.participation.ParticipationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No Spring context, database, Redis or Mattermost connection. */
class OperationalReadinessTest {
    private final ParticipationRepository repository = mock(ParticipationRepository.class);
    private final MissionCompletionService completion = mock(MissionCompletionService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final AuthPrincipal principal = new AuthPrincipal(1L, 1L, "PARTICIPANT");
    private ParticipationService service() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(repository.interests()).thenReturn(List.of(new CatalogInterest(1,"산업","INDUSTRY",null),new CatalogInterest(2,"개발","DEVELOPMENT",null)));
        when(repository.identity(1L)).thenReturn(new Identity("Local fixture", "서울", 1, null, true));
        return new ParticipationService(repository, completion, manager, mock(AuthRateLimiter.class));
    }
    private ParticipationController.ProfileRequest request(List<ParticipationController.Acceptance> acceptances) {
        return new ParticipationController.ProfileRequest("fixture", "소개", null, null, List.of(1L,2L), acceptances, 0L);
    }
    private void rejected(ParticipationService service, ParticipationController.ProfileRequest request, String code) {
        assertThatThrownBy(() -> service.save(principal, request)).isInstanceOf(AuthException.class)
                .satisfies(failure -> assertThat(((AuthException) failure).code()).isEqualTo(code));
        verify(repository, never()).saveProfile(anyLong(), any());
        verifyNoInteractions(completion);
    }
    @Test void missingEventStopsBeforeAnyConsentWrite() {
        var service = service();
        doThrow(new AuthException(503, "EVENT_NOT_CONFIGURED", "fixture")).when(repository).requireOpen(true);
        rejected(service, request(List.of()), "EVENT_NOT_CONFIGURED");
        verify(repository, never()).agree(anyLong(), any(), anyString());
    }
    @Test void missingCurrentPoliciesCannotBeReplacedByClientCheckbox() {
        var service = service();
        when(repository.policyState(1L, true)).thenReturn(new Policies(false, List.of()));
        rejected(service, request(List.of(new ParticipationController.Acceptance(PolicyType.PARTICIPATION, "fixture-v1"))), "POLICIES_NOT_CONFIGURED");
        verify(repository, never()).agree(anyLong(), any(), anyString());
    }
    @Test void outdatedAcceptanceCannotPersistProfileOrGrantLetter() {
        var service = service(); policies();
        rejected(service, request(List.of(new ParticipationController.Acceptance(PolicyType.PARTICIPATION, "outdated"))), "POLICY_CHANGED");
        verify(repository, never()).agree(anyLong(), any(), anyString());
    }
    @Test void noAcceptanceCannotPersistProfileOrGrantLetter() {
        var service = service(); policies();
        rejected(service, request(List.of()), "CONSENT_REQUIRED");
    }
    private void policies() {
        when(repository.policyState(1L, true)).thenReturn(new Policies(true, List.of(
                new Policy(PolicyType.PARTICIPATION, "fixture-v1", "Fixture", "Test only", false),
                new Policy(PolicyType.PROFILE_PUBLICATION, "fixture-v1", "Fixture", "Test only", false))));
    }
}
