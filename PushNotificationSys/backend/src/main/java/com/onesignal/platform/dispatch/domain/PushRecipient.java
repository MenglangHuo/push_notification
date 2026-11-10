package com.onesignal.platform.dispatch.domain;

import com.onesignal.platform.device.domain.DevicePlatform;
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
public class PushRecipient implements Serializable {
    private UUID deviceId;
    private DevicePlatform platform;
    private String pushToken;
    private String webAuthKey;
    private String webP256dhKey;
}
