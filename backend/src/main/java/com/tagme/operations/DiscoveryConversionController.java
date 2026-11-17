package com.tagme.operations;

import com.tagme.auth.AuthException;
import com.tagme.auth.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/discovery-conversion")
public class DiscoveryConversionController {
    private final DiscoveryConversionAnalytics analytics;
    public DiscoveryConversionController(DiscoveryConversionAnalytics analytics) { this.analytics=analytics; }

    @GetMapping
    public DiscoveryConversionAnalytics.Report report(Authentication authentication) {
        if (authentication==null || !(authentication.getPrincipal() instanceof AuthPrincipal actor)) throw AuthException.invalid();
        return analytics.report(actor);
    }
}
