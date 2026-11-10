package com.onesignal.platform.dispatch.event;

import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApnsDispatchBatchMessage implements Serializable {
    private UUID notificationId;
    private UUID appId;
    private PushNotificationMessage message;
    private List<PushRecipient> recipients;
}
