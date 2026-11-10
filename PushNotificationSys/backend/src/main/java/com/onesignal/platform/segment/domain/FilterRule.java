package com.onesignal.platform.segment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterRule {
    /**
     * Target field: 'tag', 'platform', 'language', 'timezone', 'is_active', 'last_active_days', 'app_version', 'device_model'
     */
    private String field;

    /**
     * Required when field is 'tag' (e.g. 'tier', 'plan', 'cohort')
     */
    private String tagKey;

    /**
     * Operator: 'EQ', 'NEQ', 'CONTAINS', 'IN', 'EXISTS', 'NOT_EXISTS', 'GTE', 'LTE'
     */
    private String op;

    /**
     * Target literal value, number, boolean, or array for 'IN' operator
     */
    private Object value;
}
