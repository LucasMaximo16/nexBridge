package com.nkd.nexbridge.mapper.copybook;

import com.nkd.nexbridge.domain.CopybookDefinition;
import com.nkd.nexbridge.domain.CopybookDefinitionRepository;
import com.nkd.nexbridge.exception.MappingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CopybookRegistry {

    private final CopybookDefinitionRepository copybookRepository;
    private final CopybookParser copybookParser;

    public List<CopybookField> getFields(String copybookId, String version) {
        Optional<CopybookDefinition> def = copybookRepository.findByCopybookIdAndVersion(copybookId, version);
        if (def.isEmpty()) {
            throw new MappingException("Copybook não encontrado: " + copybookId + " versão " + version);
        }
        return copybookParser.parse(def.get().getRawContent());
    }

    public CopybookDefinition register(String copybookId, String version, String connectorId,
                                        String name, String rawContent) {
        List<CopybookField> fields = copybookParser.parse(rawContent);
        int totalLength = fields.stream()
                .filter(f -> !f.isRedefines())
                .mapToInt(CopybookField::getLength)
                .sum();

        List<Map<String, Object>> parsedFieldsMaps = fields.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", f.getName());
                    m.put("cobol_name", f.getCobolName());
                    m.put("offset", f.getOffset());
                    m.put("length", f.getLength());
                    m.put("cobol_type", f.getCobolType().name());
                    m.put("sensitive", f.isSensitive());
                    if (f.getMaskType() != null) m.put("mask_type", f.getMaskType());
                    if (f.getDecimalPlaces() != null) m.put("decimal_places", f.getDecimalPlaces());
                    m.put("trim", f.isTrim());
                    return m;
                })
                .toList();

        CopybookDefinition def = new CopybookDefinition();
        def.setCopybookId(copybookId);
        def.setVersion(version);
        def.setConnectorId(connectorId);
        def.setName(name);
        def.setRawContent(rawContent);
        def.setParsedFields(parsedFieldsMaps);
        def.setTotalLength(totalLength);

        return copybookRepository.save(def);
    }
}
