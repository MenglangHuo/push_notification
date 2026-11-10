package com.onesignal.platform.segment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.domain.DeviceTag;
import com.onesignal.platform.segment.domain.FilterAst;
import com.onesignal.platform.segment.domain.FilterRule;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SegmentSpecificationBuilder {

    private final ObjectMapper objectMapper;

    public Specification<Device> buildSpecification(UUID appId, Object rawAst) {
        FilterAst ast = parseAst(rawAst);
        return (root, query, cb) -> {
            // Base condition: device belongs to app and is active
            Predicate basePredicate = cb.and(
                    cb.equal(root.get("app").get("id"), appId),
                    cb.isTrue(root.get("active"))
            );

            if (ast == null || ((ast.getRules() == null || ast.getRules().isEmpty())
                    && (ast.getGroups() == null || ast.getGroups().isEmpty()))) {
                return basePredicate;
            }

            Predicate astPredicate = buildNodePredicate(ast, root, query, cb);
            if (astPredicate != null) {
                return cb.and(basePredicate, astPredicate);
            }
            return basePredicate;
        };
    }

    public FilterAst parseAst(Object rawAst) {
        if (rawAst == null) {
            return new FilterAst();
        }
        if (rawAst instanceof FilterAst ast) {
            return ast;
        }
        try {
            return objectMapper.convertValue(rawAst, FilterAst.class);
        } catch (Exception e) {
            log.warn("Failed to parse filter AST from raw input: {}", e.getMessage());
            return new FilterAst();
        }
    }

    private Predicate buildNodePredicate(FilterAst node, Root<Device> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        boolean isOr = "OR".equalsIgnoreCase(node.getConjunction());

        if (node.getRules() != null) {
            for (FilterRule rule : node.getRules()) {
                Predicate rulePred = buildRulePredicate(rule, root, query, cb);
                if (rulePred != null) {
                    predicates.add(rulePred);
                }
            }
        }

        if (node.getGroups() != null) {
            for (FilterAst childGroup : node.getGroups()) {
                Predicate groupPred = buildNodePredicate(childGroup, root, query, cb);
                if (groupPred != null) {
                    predicates.add(groupPred);
                }
            }
        }

        if (predicates.isEmpty()) {
            return null;
        }

        return isOr
                ? cb.or(predicates.toArray(new Predicate[0]))
                : cb.and(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildRulePredicate(FilterRule rule, Root<Device> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (rule.getField() == null) {
            return null;
        }

        String field = rule.getField().trim().toLowerCase();
        String op = rule.getOp() != null ? rule.getOp().trim().toUpperCase() : "EQ";
        Object val = rule.getValue();

        try {
            switch (field) {
                case "tag" -> {
                    return buildTagPredicate(rule, root, query, cb);
                }
                case "platform" -> {
                    return buildPlatformPredicate(op, val, root, cb);
                }
                case "language" -> {
                    return buildStringFieldPredicate("language", op, val, root, cb);
                }
                case "timezone" -> {
                    return buildStringFieldPredicate("timezone", op, val, root, cb);
                }
                case "app_version" -> {
                    return buildStringFieldPredicate("appVersion", op, val, root, cb);
                }
                case "device_model" -> {
                    return buildStringFieldPredicate("deviceModel", op, val, root, cb);
                }
                case "is_active" -> {
                    boolean targetVal = val != null && Boolean.parseBoolean(val.toString());
                    return targetVal ? cb.isTrue(root.get("active")) : cb.isFalse(root.get("active"));
                }
                case "last_active_days" -> {
                    return buildLastActiveDaysPredicate(op, val, root, cb);
                }
                default -> {
                    log.warn("Unknown filter field: {}", field);
                    return null;
                }
            }
        } catch (Exception e) {
            log.warn("Error building rule predicate for field {}: {}", field, e.getMessage());
            return null;
        }
    }

    private Predicate buildTagPredicate(FilterRule rule, Root<Device> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String tagKey = rule.getTagKey();
        if (tagKey == null || tagKey.isBlank()) {
            return null;
        }

        String op = rule.getOp() != null ? rule.getOp().trim().toUpperCase() : "EQ";
        Object val = rule.getValue();

        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<DeviceTag> tagRoot = subquery.from(DeviceTag.class);
        subquery.select(tagRoot.get("device").get("id"));

        Predicate keyPredicate = cb.equal(tagRoot.get("tagKey"), tagKey.trim());

        switch (op) {
            case "EXISTS" -> {
                subquery.where(keyPredicate);
                return root.get("id").in(subquery);
            }
            case "NOT_EXISTS" -> {
                subquery.where(keyPredicate);
                return cb.not(root.get("id").in(subquery));
            }
            case "NEQ" -> {
                if (val == null) return null;
                Predicate valPredicate = cb.equal(tagRoot.get("tagValue"), val.toString());
                subquery.where(cb.and(keyPredicate, valPredicate));
                return cb.not(root.get("id").in(subquery));
            }
            case "CONTAINS" -> {
                if (val == null) return null;
                Predicate valPredicate = cb.like(
                        cb.lower(tagRoot.get("tagValue")),
                        "%" + val.toString().toLowerCase() + "%"
                );
                subquery.where(cb.and(keyPredicate, valPredicate));
                return root.get("id").in(subquery);
            }
            case "IN" -> {
                List<String> inVals = extractList(val);
                if (inVals.isEmpty()) return null;
                Predicate valPredicate = tagRoot.get("tagValue").in(inVals);
                subquery.where(cb.and(keyPredicate, valPredicate));
                return root.get("id").in(subquery);
            }
            case "EQ" -> {
                if (val == null) return null;
                Predicate valPredicate = cb.equal(tagRoot.get("tagValue"), val.toString());
                subquery.where(cb.and(keyPredicate, valPredicate));
                return root.get("id").in(subquery);
            }
            default -> {
                if (val == null) return null;
                Predicate valPredicate = cb.equal(tagRoot.get("tagValue"), val.toString());
                subquery.where(cb.and(keyPredicate, valPredicate));
                return root.get("id").in(subquery);
            }
        }
    }

    private Predicate buildPlatformPredicate(String op, Object val, Root<Device> root, CriteriaBuilder cb) {
        if ("IN".equals(op)) {
            List<String> strList = extractList(val);
            List<DevicePlatform> platforms = strList.stream()
                    .map(s -> {
                        try {
                            return DevicePlatform.valueOf(s.toUpperCase());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            if (platforms.isEmpty()) return null;
            return root.get("platform").in(platforms);
        } else if ("NEQ".equals(op) && val != null) {
            DevicePlatform p = DevicePlatform.valueOf(val.toString().toUpperCase());
            return cb.notEqual(root.get("platform"), p);
        } else if (val != null) {
            DevicePlatform p = DevicePlatform.valueOf(val.toString().toUpperCase());
            return cb.equal(root.get("platform"), p);
        }
        return null;
    }

    private Predicate buildStringFieldPredicate(String attribute, String op, Object val, Root<Device> root, CriteriaBuilder cb) {
        if ("IN".equals(op)) {
            List<String> list = extractList(val);
            if (list.isEmpty()) return null;
            return cb.lower(root.get(attribute)).in(list.stream().map(String::toLowerCase).toList());
        } else if ("CONTAINS".equals(op) && val != null) {
            return cb.like(cb.lower(root.get(attribute)), "%" + val.toString().toLowerCase() + "%");
        } else if ("NEQ".equals(op) && val != null) {
            return cb.notEqual(cb.lower(root.get(attribute)), val.toString().toLowerCase());
        } else if (val != null) {
            return cb.equal(cb.lower(root.get(attribute)), val.toString().toLowerCase());
        }
        return null;
    }

    private Predicate buildLastActiveDaysPredicate(String op, Object val, Root<Device> root, CriteriaBuilder cb) {
        if (val == null) return null;
        int days;
        try {
            days = Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        if ("GTE".equals(op) || "GREATER_THAN".equals(op) || "INACTIVE_DAYS".equals(op)) {
            // Inactive for more than X days
            return cb.lessThanOrEqualTo(root.get("lastActiveAt"), cutoff);
        } else {
            // Active within last X days
            return cb.greaterThanOrEqualTo(root.get("lastActiveAt"), cutoff);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractList(Object val) {
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        } else if (val instanceof String str) {
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return Collections.emptyList();
    }
}
