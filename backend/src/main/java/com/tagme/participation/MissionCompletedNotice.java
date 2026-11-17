package com.tagme.participation;

import java.time.Instant;

/** Internal notification only; never accepts a browser completion claim. */
public record MissionCompletedNotice(long userId,String mission,long receiptId,String sourceKind,Instant completedAt) {}
