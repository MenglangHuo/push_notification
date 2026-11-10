package com.onesignal.platform.dispatch.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientFailure implements Serializable {
    private String pushToken;
    private UUID deviceId;
    private boolean shouldDeactivate;
    private String errorCode;
    private String errorMessage;
}
