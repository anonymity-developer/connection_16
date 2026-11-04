package com.tagme.content;

/** Only text crosses this boundary, never account/profile identifiers or presentation. */
public interface ModerationProvider {
    record Decision(String status,String reason,String provider,String policyVersion) { }
    Decision inspect(String text);
    class TemporaryFailure extends RuntimeException { public TemporaryFailure() { super("Moderation temporarily unavailable"); } }
}
