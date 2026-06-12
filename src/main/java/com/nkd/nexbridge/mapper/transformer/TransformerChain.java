package com.nkd.nexbridge.mapper.transformer;

import com.nkd.nexbridge.mapper.FieldMapping;
import com.nkd.nexbridge.mapper.TransformRule;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
public class TransformerChain {

    public static Object apply(Object value, FieldMapping mapping) {
        if (mapping.getTransform() == null || mapping.getTransform() == TransformRule.COPY) {
            return value;
        }
        TransformRule rule = mapping.getTransform();
        String strValue = value != null ? value.toString() : null;

        return switch (rule) {
            case COPY -> value;
            case TRIM -> strValue != null ? strValue.trim() : null;
            case LOWERCASE -> strValue != null ? strValue.toLowerCase() : null;
            case UPPERCASE_PAD_RIGHT -> {
                int len = mapping.getPadLength() != null ? mapping.getPadLength() : (strValue != null ? strValue.length() : 0);
                yield PadTransformer.uppercasePadRight(strValue, len);
            }
            case REMOVE_SPECIAL_CHARS -> RemoveSpecialCharsTransformer.removeSpecialChars(strValue);
            case INTEGER_PAD_LEFT -> {
                int len = mapping.getPadLength() != null ? mapping.getPadLength() : 10;
                yield PadTransformer.padLeftZero(strValue, len);
            }
            case INTEGER_PAD_RIGHT -> {
                int len = mapping.getPadLength() != null ? mapping.getPadLength() : 10;
                char pad = mapping.getPadChar() != null && !mapping.getPadChar().isEmpty() ? mapping.getPadChar().charAt(0) : ' ';
                yield PadTransformer.padRight(strValue, len, pad);
            }
            case PACKED_TO_DECIMAL -> {
                if (value instanceof byte[] bytes) {
                    int dp = mapping.getDecimalPlaces() != null ? mapping.getDecimalPlaces() : 2;
                    yield PackedDecimalTransformer.packedToDecimal(bytes, dp);
                }
                yield value;
            }
            case DECIMAL_TO_PACKED -> {
                int dp = mapping.getDecimalPlaces() != null ? mapping.getDecimalPlaces() : 2;
                BigDecimal bd = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(strValue != null ? strValue : "0");
                yield PackedDecimalTransformer.decimalToPacked(bd, dp);
            }
            case DATE_COBOL_TO_ISO -> CobolDateTransformer.cobolToIso(strValue);
            case DATE_DDMMYYYY_TO_ISO -> CobolDateTransformer.ddmmyyyyToIso(strValue);
            case DATE_YYMMDD_TO_ISO -> CobolDateTransformer.yymmddToIso(strValue);
            case DATE_YYYYMMDDHHMMSS_TO_ISO -> CobolDateTransformer.timestampCobolToIso(strValue);
            case DATE_ISO_TO_COBOL -> {
                LocalDate d = value instanceof LocalDate ? (LocalDate) value : LocalDate.parse(strValue);
                yield CobolDateTransformer.isoToCobol(d);
            }
            case EBCDIC_TO_UTF8 -> {
                if (value instanceof byte[] bytes) yield EbcdicTransformer.ebcdicToUtf8(bytes);
                yield value;
            }
            case UTF8_TO_EBCDIC -> {
                int len = mapping.getPadLength() != null ? mapping.getPadLength() : (strValue != null ? strValue.length() : 0);
                yield EbcdicTransformer.utf8ToEbcdic(strValue != null ? strValue : "", len);
            }
            case ENUM_MAP -> EnumTransformer.map(strValue, mapping.getEnumMap());
            case MULTIPLY_BY_100 -> {
                BigDecimal bd = new BigDecimal(strValue != null ? strValue : "0");
                yield bd.multiply(BigDecimal.valueOf(100)).longValue();
            }
            case DIVIDE_BY_100 -> {
                BigDecimal bd = new BigDecimal(strValue != null ? strValue : "0");
                yield bd.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            }
            case MASK_CPF -> LgpdTransformerHelper.maskCpf(strValue);
            case MASK_CNPJ -> LgpdTransformerHelper.maskCnpj(strValue);
            case MASK_CONTA -> LgpdTransformerHelper.maskConta(strValue);
            case MASK_EMAIL -> LgpdTransformerHelper.maskEmail(strValue);
            case MASK_TELEFONE -> LgpdTransformerHelper.maskTelefone(strValue);
            case MASK_SALARIO -> LgpdTransformerHelper.maskSalario(strValue);
            case BOOLEAN_TO_COBOL -> Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(strValue) ? "S" : "N";
            case COBOL_TO_BOOLEAN -> "S".equalsIgnoreCase(strValue) || "Y".equalsIgnoreCase(strValue);
            case NULL_TO_EMPTY -> value == null ? "" : value;
            case EMPTY_TO_NULL -> (strValue != null && strValue.isBlank()) ? null : value;
            default -> value;
        };
    }
}
