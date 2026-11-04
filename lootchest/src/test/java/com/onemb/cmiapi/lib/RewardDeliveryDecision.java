package com.onemb.cmiapi.lib;

import java.util.concurrent.CompletionStage;

/** Test-only API fixture, never a production dependency or bundled class. */
public record RewardDeliveryDecision(boolean allowed, boolean stateChanged, String message,
                                     String actionCommand, String actionLabel, CompletionStage<Void> readiness) { }
