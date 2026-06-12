package com.nkd.nexbridge.mapper.copybook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CopybookParser {

    private static final Pattern PIC_X    = Pattern.compile("PIC\\s+X\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIC_9    = Pattern.compile("PIC\\s+9\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIC_9V9  = Pattern.compile("PIC\\s+9\\((\\d+)\\)V9\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMP3    = Pattern.compile("COMP-3|COMPUTATIONAL-3", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMP     = Pattern.compile("\\bCOMP\\b|\\bCOMPUTATIONAL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_LINE = Pattern.compile("^\\s*(\\d{2})\\s+(\\S+)(.*)$");
    private static final Pattern OCCURS   = Pattern.compile("OCCURS\\s+(\\d+)\\s+TIMES", Pattern.CASE_INSENSITIVE);
    private static final Pattern REDEFINES = Pattern.compile("REDEFINES\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private static final List<String> SENSITIVE_KEYWORDS = List.of("CPF", "CNPJ", "CONTA", "SALARIO", "SALARY");

    public List<CopybookField> parse(String rawContent) {
        List<CopybookField> fields = new ArrayList<>();
        int[] offset = {0};

        for (String line : rawContent.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("*")) continue;

            Matcher m = FIELD_LINE.matcher(trimmed);
            if (!m.matches()) continue;

            int level = Integer.parseInt(m.group(1));
            String cobolName = m.group(2);
            String rest = m.group(3).trim();

            if (level == 1 || level == 77 || rest.isEmpty()) continue;

            CopybookField.CopybookFieldBuilder builder = CopybookField.builder()
                    .cobolName(cobolName)
                    .name(cobolNameToJava(cobolName));

            boolean isRedefines = false;
            Matcher redefMatcher = REDEFINES.matcher(rest);
            if (redefMatcher.find()) {
                isRedefines = true;
                builder.redefines(true).redefinesTarget(redefMatcher.group(1));
            }

            Matcher occursMatcher = OCCURS.matcher(rest);
            if (occursMatcher.find()) {
                builder.occursCount(Integer.parseInt(occursMatcher.group(1)));
            }

            boolean isComp3 = COMP3.matcher(rest).find();
            boolean isComp  = !isComp3 && COMP.matcher(rest).find();

            Matcher pic9v9 = PIC_9V9.matcher(rest);
            Matcher picX   = PIC_X.matcher(rest);
            Matcher pic9   = PIC_9.matcher(rest);

            int length = 0;
            CobolType cobolType = CobolType.PIC_X;
            Integer decimalPlaces = null;

            if (pic9v9.find()) {
                int intDig = Integer.parseInt(pic9v9.group(1));
                int decDig = Integer.parseInt(pic9v9.group(2));
                decimalPlaces = decDig;
                cobolType = isComp3 ? CobolType.COMP_3 : CobolType.PIC_9V9;
                length = isComp3 ? (intDig + decDig + 2) / 2 : intDig + decDig;
            } else if (pic9.find()) {
                int digits = Integer.parseInt(pic9.group(1));
                cobolType = isComp3 ? CobolType.COMP_3 : (isComp ? CobolType.COMP : CobolType.PIC_9);
                length = isComp3 ? (digits + 2) / 2 : digits;
            } else if (picX.find()) {
                length = Integer.parseInt(picX.group(1));
                cobolType = CobolType.PIC_X;
            } else {
                continue;
            }

            boolean sensitive = SENSITIVE_KEYWORDS.stream().anyMatch(k -> cobolName.toUpperCase().contains(k));
            String maskType = sensitive ? inferMaskType(cobolName) : null;

            CopybookField field = builder
                    .offset(offset[0])
                    .length(length)
                    .cobolType(cobolType)
                    .sensitive(sensitive)
                    .maskType(maskType)
                    .decimalPlaces(decimalPlaces)
                    .trim(cobolType == CobolType.PIC_X)
                    .build();

            fields.add(field);
            if (!isRedefines) {
                offset[0] += length;
            }
        }
        return fields;
    }

    private String cobolNameToJava(String cobolName) {
        String[] parts = cobolName.toLowerCase().replace("ws-", "").split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    private String inferMaskType(String cobolName) {
        String upper = cobolName.toUpperCase();
        if (upper.contains("CPF"))    return "MASK_CPF";
        if (upper.contains("CNPJ"))   return "MASK_CNPJ";
        if (upper.contains("CONTA"))  return "MASK_CONTA";
        if (upper.contains("SALARIO") || upper.contains("SALARY")) return "MASK_SALARIO";
        return null;
    }
}
