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
    @GetMapping({"/profiles","/discovery/people"}) public People people(Authentication auth,@RequestParam(required=false) Long cursor,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String q,@RequestParam(required=false) Long interestId,@RequestParam(required=false) String typeCode,@RequestParam(required=false) String questionnaireVersion) { return service.people(actor(auth),cursor,limit,q,interestId,typeCode,questionnaireVersion); }
    @GetMapping("/discovery/recommendations") public java.util.List<Person> recommended(Authentication auth) { return service.recommended(actor(auth)); }
    @PostMapping("/discovery/profiles/{id}/open") public Detail open(Authentication auth,@PathVariable long id) { return service.open(actor(auth),id); }
    @GetMapping("/discovery/stats") public Stats stats(Authentication auth) { return service.stats(actor(auth)); }
    @GetMapping("/discovery/graph") public Graph graph(Authentication auth,@RequestParam(required=false) Integer limit,@RequestParam(defaultValue="0") int offset) { return service.graph(actor(auth),limit,offset); }
}
