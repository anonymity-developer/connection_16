package com.tagme.discovery;

import com.tagme.auth.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.tagme.discovery.DiscoveryModels.*;

@RestController @RequestMapping("/api/v1")
public class DiscoveryController {
    private final DiscoveryService service;
    public DiscoveryController(DiscoveryService service) { this.service=service; }
    private AuthPrincipal actor(Authentication auth) { if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p; }
    @GetMapping({"/profiles","/discovery/people"}) public People people(Authentication auth,@RequestParam(required=false) Long cursor,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String q,@RequestParam(required=false) Long interestId,@RequestParam(required=false) String typeCode,@RequestParam(required=false) String questionnaireVersion,@RequestParam(required=false) java.util.List<Long> industryIds,@RequestParam(required=false) java.util.List<Long> developmentIds) { return service.people(actor(auth),cursor,limit,q,interestId,typeCode,questionnaireVersion,industryIds,developmentIds); }
    @GetMapping("/discovery/recommendations") public java.util.List<Person> recommended(Authentication auth,@RequestParam(required=false) String seed) { return service.recommended(actor(auth),seed); }
    @GetMapping("/discovery/recommendations/batch") public RecommendationBatch batch(Authentication auth,@RequestParam(required=false) String seed){return service.batch(actor(auth),seed);}
    public record ExposureRequest(@jakarta.validation.constraints.NotBlank String batchId,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=1,max=12) java.util.List<@jakarta.validation.constraints.Positive Long> profileIds) { }
    @PostMapping("/discovery/recommendations/exposures") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void expose(Authentication auth,@jakarta.validation.Valid @RequestBody ExposureRequest body){service.expose(actor(auth),body.batchId(),body.profileIds());}
    @PostMapping("/discovery/profiles/{id}/open") public Detail open(Authentication auth,@PathVariable long id) { return service.open(actor(auth),id); }
    @GetMapping("/discovery/stats") public Stats stats(Authentication auth) { return service.stats(actor(auth)); }
    @GetMapping("/discovery/graph") public Graph graph(Authentication auth,@RequestParam(required=false) Integer limit,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="all") String relationship) { return service.graph(actor(auth),limit,offset,relationship); }
}
