package com.nkd.nexbridge.governance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LgpdMasker {

    private static final List<String> CPF_KEYS = List.of("cpf", "ws-cpf", "nr_cpf", "num_cpf");
    private static final List<String> CNPJ_KEYS = List.of("cnpj", "ws-cnpj", "nr_cnpj");
    private static final List<String> CONTA_KEYS = List.of("conta", "num_conta", "ws-conta", "nr_conta");
    private static final List<String> EMAIL_KEYS = List.of("email", "ds_email");
    private static final List<String> TEL_KEYS = List.of("telefone", "celular", "fone", "nr_tel");
    private static final List<String> SAL_KEYS = List.of("salario", "vl_salario", "salary", "remuneracao");

    public MaskResult mask(Map<String, Object> data) {
        List<String> maskedFields = new ArrayList<>();
        if (data == null) return new MaskResult(data, maskedFields);

        data.replaceAll((key, value) -> {
            if (value == null) return null;
            String lowerKey = key.toLowerCase();
            String strVal = value.toString();

            if (CPF_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return maskCpf(strVal);
            }
            if (CNPJ_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return maskCnpj(strVal);
            }
            if (CONTA_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return maskConta(strVal);
            }
            if (EMAIL_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return maskEmail(strVal);
            }
            if (TEL_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return maskTelefone(strVal);
            }
            if (SAL_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return maskSalario(strVal);
            }
            return value;
        });

        return new MaskResult(data, maskedFields);
    }

    private static String maskCpf(String cpf) {
        if (cpf == null) return null;
        String clean = cpf.replaceAll("[^0-9]", "");
        if (clean.length() != 11) return "***.***.***-**";
        return clean.substring(0, 3) + ".***." + "***-" + clean.substring(9);
    }

    private static String maskCnpj(String cnpj) {
        if (cnpj == null) return null;
        String clean = cnpj.replaceAll("[^0-9]", "");
        if (clean.length() != 14) return "**.***.***/****.--";
        return clean.substring(0, 2) + ".***.***/0001-" + clean.substring(12);
    }

    private static String maskConta(String conta) {
        if (conta == null) return null;
        if (conta.length() <= 3) return "****";
        return "****" + conta.substring(conta.length() - 3);
    }

    private static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskTelefone(String tel) {
        if (tel == null) return null;
        String clean = tel.replaceAll("[^0-9]", "");
        if (clean.length() < 8) return "(*)****-****";
        String ddd = clean.length() >= 10 ? clean.substring(0, 2) : "**";
        String suffix = clean.substring(clean.length() - 4);
        return "(" + ddd + ")****-" + suffix;
    }

    private static String maskSalario(String salario) {
        if (salario == null) return null;
        try {
            BigDecimal val = new BigDecimal(salario.replaceAll("[^0-9.]", ""));
            double d = val.doubleValue();
            if (d < 2000) return "Faixa A (até R$ 2.000)";
            if (d < 5000) return "Faixa B (R$ 2.001–5.000)";
            if (d < 10000) return "Faixa C (R$ 5.001–10.000)";
            return "Faixa D (acima R$ 10.000)";
        } catch (Exception e) {
            return "Faixa *";
        }
    }

    public record MaskResult(Map<String, Object> data, List<String> maskedFields) {}
}
