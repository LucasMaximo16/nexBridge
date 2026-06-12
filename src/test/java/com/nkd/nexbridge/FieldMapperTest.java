package com.nkd.nexbridge;

import com.nkd.nexbridge.exception.MappingException;
import com.nkd.nexbridge.mapper.FieldAction;
import com.nkd.nexbridge.mapper.FieldMapper;
import com.nkd.nexbridge.mapper.FieldMapping;
import com.nkd.nexbridge.mapper.MappingConfig;
import com.nkd.nexbridge.mapper.TransformRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldMapperTest {

    private final FieldMapper fieldMapper = new FieldMapper();

    @Test
    void shouldMapRequestFields() {
        // Arrange
        Map<String, Object> input = Map.of("cpf", "123.456.789-01", "nome", "Maria", "telefone", "11999");

        FieldMapping cpfMapping = FieldMapping.builder()
                .from("cpf").to("WS-CPF")
                .action(FieldAction.MAP)
                .transform(TransformRule.REMOVE_SPECIAL_CHARS)
                .required(true).build();

        FieldMapping telMapping = FieldMapping.builder()
                .from("telefone").to("WS-TEL")
                .action(FieldAction.DISCARD)
                .transform(TransformRule.COPY)
                .required(false).build();

        MappingConfig config = MappingConfig.builder()
                .mappingId("test").version("v1")
                .connectorId("c1").sourceFormat("JSON").targetFormat("COBOL_FIXED")
                .requestFields(List.of(cpfMapping, telMapping))
                .responseFields(List.of())
                .build();

        // Act
        FieldMapper.MapResult result = fieldMapper.mapRequest(input, config);

        // Assert
        assertThat(result.output()).containsKey("WS-CPF");
        assertThat(result.output().get("WS-CPF")).isEqualTo("12345678901");
        assertThat(result.discardedFields()).contains("telefone");
        assertThat(result.output()).doesNotContainKey("WS-TEL");
    }

    @Test
    void shouldThrowOnMissingRequiredField() {
        Map<String, Object> input = Map.of("nome", "Maria"); // cpf ausente

        FieldMapping cpfMapping = FieldMapping.builder()
                .from("cpf").to("WS-CPF")
                .action(FieldAction.MAP)
                .transform(TransformRule.COPY)
                .required(true).build();

        MappingConfig config = MappingConfig.builder()
                .mappingId("test").version("v1")
                .connectorId("c1").sourceFormat("JSON").targetFormat("COBOL_FIXED")
                .requestFields(List.of(cpfMapping))
                .responseFields(List.of())
                .build();

        assertThatThrownBy(() -> fieldMapper.validateRequest(input, config))
                .isInstanceOf(MappingException.class);
    }
}
