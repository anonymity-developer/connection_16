package com.tagme.auth;

import java.io.Serializable;
import java.security.Principal;

public record AuthPrincipal(long userId, long version, String role) implements Principal, Serializable {
    @Override public String getName() { return Long.toString(userId); }
}
