package com.onesignal.platform.dispatch.service;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.dispatch.domain.BatchDispatchResult;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;

import java.util.List;

public interface PushProvider {

    ProviderType getProviderType();

    BatchDispatchResult dispatchBatch(
            App app,
            ProviderCredential credential,
            List<PushRecipient> recipients,
            PushNotificationMessage message
    );

    boolean testConnection(ProviderCredential credential);
}
