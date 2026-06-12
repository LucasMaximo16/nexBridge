package com.nkd.nexbridge.governance;

import com.nkd.nexbridge.mapper.transformer.LgpdTransformerHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
                return LgpdTransformerHelper.maskCpf(strVal);
            }
            if (CNPJ_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return LgpdTransformerHelper.maskCnpj(strVal);
            }
            if (CONTA_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return LgpdTransformerHelper.maskConta(strVal);
            }
            if (EMAIL_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return LgpdTransformerHelper.maskEmail(strVal);
            }
            if (TEL_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return LgpdTransformerHelper.maskTelefone(strVal);
            }
            if (SAL_KEYS.stream().anyMatch(lowerKey::contains)) {
                maskedFields.add(key);
                return LgpdTransformerHelper.maskSalario(strVal);
            }
            return value;
        });

        return new MaskResult(data, maskedFields);
    }

    public record MaskResult(Map<String, Object> data, List<String> maskedFields) {}
}
