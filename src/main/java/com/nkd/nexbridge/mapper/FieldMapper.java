package com.nkd.nexbridge.mapper;

import com.nkd.nexbridge.exception.MappingException;
import com.nkd.nexbridge.mapper.transformer.TransformerChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class FieldMapper {

    public MapResult mapRequest(Map<String, Object> input, MappingConfig config) {
        Map<String, Object> output = new LinkedHashMap<>();
        List<String> discarded = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (FieldMapping fm : config.getRequestFields()) {
            if (fm.getAction() == FieldAction.DISCARD) {
                if (fm.getAuditNote() != null) {
                    log.debug("Field discarded: {} — {}", fm.getFrom(), fm.getAuditNote());
                }
                discarded.add(fm.getFrom());
                continue;
            }
            if (fm.getAction() == FieldAction.CONSTANT) {
                output.put(fm.getTo(), fm.getConstantValue());
                continue;
            }
            Object value = input.get(fm.getFrom());
            if (value == null && fm.isRequired()) {
                missing.add(fm.getFrom());
                continue;
            }
            if (value != null) {
                output.put(fm.getTo(), TransformerChain.apply(value, fm));
            }
        }

        return new MapResult(output, discarded, missing);
    }

    public MapResult mapResponse(Map<String, Object> legacyResponse, MappingConfig config) {
        Map<String, Object> output = new LinkedHashMap<>();
        List<String> discarded = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (FieldMapping fm : config.getResponseFields()) {
            if (fm.getAction() == FieldAction.DISCARD) {
                discarded.add(fm.getFrom());
                continue;
            }
            if (fm.getAction() == FieldAction.CONSTANT) {
                output.put(fm.getTo(), fm.getConstantValue());
                continue;
            }
            Object value = legacyResponse.get(fm.getFrom());
            if (value == null && fm.isRequired()) {
                missing.add(fm.getFrom());
                continue;
            }
            if (value != null) {
                output.put(fm.getTo(), TransformerChain.apply(value, fm));
            }
        }

        return new MapResult(output, discarded, missing);
    }

    public void validateRequest(Map<String, Object> input, MappingConfig config) {
        List<String> errors = new ArrayList<>();
        for (FieldMapping fm : config.getRequestFields()) {
            if (fm.isRequired() && fm.getAction() == FieldAction.MAP) {
                if (!input.containsKey(fm.getFrom()) || input.get(fm.getFrom()) == null) {
                    errors.add("Campo obrigatório ausente: " + fm.getFrom());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new MappingException("VALIDATION_FAILED",
                    "Validação do mapeamento falhou: " + String.join(", ", errors));
        }
    }

    public record MapResult(Map<String, Object> output, List<String> discardedFields, List<String> missingFields) {}
}
