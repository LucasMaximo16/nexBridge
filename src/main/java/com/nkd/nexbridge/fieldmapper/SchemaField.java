package com.nkd.nexbridge.fieldmapper;

public record SchemaField(
        String name,
        String type,
        boolean required,
        String description
) {}
