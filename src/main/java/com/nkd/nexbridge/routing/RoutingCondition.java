package com.nkd.nexbridge.routing;

import java.util.Map;

public record RoutingCondition(String field, ConditionOperator operator, String value) {

    public boolean evaluate(Map<String, Object> data) {
        Object fieldValue = data.get(field);
        try {
            return switch (operator) {
                case EQUALS -> String.valueOf(fieldValue).equals(value);
                case NOT_EQUALS -> !String.valueOf(fieldValue).equals(value);
                case CONTAINS -> String.valueOf(fieldValue).contains(value);
                case NOT_CONTAINS -> !String.valueOf(fieldValue).contains(value);
                case STARTS_WITH -> String.valueOf(fieldValue).startsWith(value);
                case ENDS_WITH -> String.valueOf(fieldValue).endsWith(value);
                case GREATER_THAN -> Double.parseDouble(String.valueOf(fieldValue)) > Double.parseDouble(value);
                case LESS_THAN -> Double.parseDouble(String.valueOf(fieldValue)) < Double.parseDouble(value);
                case GREATER_THAN_OR_EQUAL -> Double.parseDouble(String.valueOf(fieldValue)) >= Double.parseDouble(value);
                case LESS_THAN_OR_EQUAL -> Double.parseDouble(String.valueOf(fieldValue)) <= Double.parseDouble(value);
                case IS_NULL -> fieldValue == null;
                case IS_NOT_NULL -> fieldValue != null;
                case MATCHES_REGEX -> String.valueOf(fieldValue).matches(value);
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
