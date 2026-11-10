package com.onesignal.platform.segment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterAst {

    /**
     * Logical conjunction: 'AND' or 'OR'
     */
    @Builder.Default
    private String conjunction = "AND";

    /**
     * List of direct rule criteria
     */
    @Builder.Default
    private List<FilterRule> rules = new ArrayList<>();

    /**
     * Optional nested rule groups
     */
    @Builder.Default
    private List<FilterAst> groups = new ArrayList<>();
}
