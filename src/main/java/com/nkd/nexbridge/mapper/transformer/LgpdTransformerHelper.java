package com.nkd.nexbridge.mapper.transformer;

import java.math.BigDecimal;

public class LgpdTransformerHelper {

    public static String maskCpf(String cpf) {
        if (cpf == null) return null;
        String clean = cpf.replaceAll("[^0-9]", "");
        if (clean.length() != 11) return "***.***.***-**";
        return clean.substring(0, 3) + ".***." + "***-" + clean.substring(9);
    }

    public static String maskCnpj(String cnpj) {
        if (cnpj == null) return null;
        String clean = cnpj.replaceAll("[^0-9]", "");
        if (clean.length() != 14) return "**.***.***/****.--";
        return clean.substring(0, 2) + ".***.***/0001-" + clean.substring(12);
    }

    public static String maskConta(String conta) {
        if (conta == null) return null;
        if (conta.length() <= 3) return "****";
        return "****" + conta.substring(conta.length() - 3);
    }

    public static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String maskTelefone(String tel) {
        if (tel == null) return null;
        String clean = tel.replaceAll("[^0-9]", "");
        if (clean.length() < 8) return "(*)****-****";
        String ddd = clean.length() >= 10 ? clean.substring(0, 2) : "**";
        String suffix = clean.substring(clean.length() - 4);
        return "(" + ddd + ")****-" + suffix;
    }

    public static String maskSalario(String salario) {
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
}
