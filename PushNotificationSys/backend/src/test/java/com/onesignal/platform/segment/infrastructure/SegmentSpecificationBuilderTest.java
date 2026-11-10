package com.onesignal.platform.segment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.segment.domain.FilterAst;
import com.onesignal.platform.segment.domain.FilterRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentSpecificationBuilderTest {

    private SegmentSpecificationBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SegmentSpecificationBuilder(new ObjectMapper());
    }

    @Test
    @DisplayName("Should successfully parse Map into typed FilterAst")
    void testParseAstFromMap() {
        Map<String, Object> raw = Map.of(
                "conjunction", "AND",
                "rules", List.of(
                        Map.of("field", "tag", "tagKey", "tier", "op", "EQ", "value", "VIP"),
                        Map.of("field", "platform", "op", "IN", "value", List.of("ANDROID", "IOS"))
                )
        );

        FilterAst ast = builder.parseAst(raw);

        assertThat(ast).isNotNull();
        assertThat(ast.getConjunction()).isEqualTo("AND");
        assertThat(ast.getRules()).hasSize(2);

        FilterRule rule1 = ast.getRules().get(0);
        assertThat(rule1.getField()).isEqualTo("tag");
        assertThat(rule1.getTagKey()).isEqualTo("tier");
        assertThat(rule1.getOp()).isEqualTo("EQ");
        assertThat(rule1.getValue()).isEqualTo("VIP");

        FilterRule rule2 = ast.getRules().get(1);
        assertThat(rule2.getField()).isEqualTo("platform");
        assertThat(rule2.getOp()).isEqualTo("IN");
    }

    @Test
    @DisplayName("Should gracefully handle null or empty AST")
    void testNullAstHandling() {
        FilterAst ast = builder.parseAst(null);
        assertThat(ast).isNotNull();
        assertThat(ast.getRules()).isEmpty();

        var spec = builder.buildSpecification(UUID.randomUUID(), null);
        assertThat(spec).isNotNull();
    }
}
