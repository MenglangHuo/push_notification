package com.onesignal.platform.dispatch.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDispatchResult implements Serializable {
    private int successCount;
    private int failureCount;

    @Builder.Default
    private List<RecipientFailure> failures = new ArrayList<>();
}
