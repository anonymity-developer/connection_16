package com.tagme.profile;

import com.tagme.auth.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ProfilePhotoController {
    private final ProfilePhotoService service;
    public ProfilePhotoController(ProfilePhotoService service) { this.service=service; }
    private AuthPrincipal actor(Authentication auth) { if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p; }
    private ResponseEntity<byte[]> response(byte[] image) { return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(image); }
    @GetMapping("/users/me/photo") public ResponseEntity<byte[]> own(Authentication auth) { var p=actor(auth);return response(service.photo(p,p.userId(),true)); }
    @GetMapping("/discovery/profiles/{id}/photo") public ResponseEntity<byte[]> detail(Authentication auth,@PathVariable long id) { return response(service.photo(actor(auth),id,false)); }
}
