package com.nkd.nexbridge.governance;

import com.nkd.nexbridge.audit.AuditRepository;
import com.nkd.nexbridge.domain.ConnectorDefinitionRepository;
import com.nkd.nexbridge.domain.ConsumerDefinitionRepository;
import com.nkd.nexbridge.domain.ConsumerDefinition;
import com.nkd.nexbridge.domain.ConnectorDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceScorer {

    private final AuditRepository auditRepository;
    private final ConnectorDefinitionRepository connectorRepository;
    private final ConsumerDefinitionRepository consumerRepository;

    @Value("${nexbridge.lgpd.auto-mask:true}")
    private boolean autoMask;

    public record NormaScore(String norma, int score, int maxScore, String nivel, List<String> findings) {}

    public record ComplianceReport(
            List<NormaScore> normas,
            int scoreGeral,
            String nivelGeral,
            Instant geradoEm
    ) {}

    private static final int PESO_LGPD = 30;
    private static final int PESO_SOX = 25;
    private static final int PESO_BACEN = 20;
    private static final int PESO_SUSEP = 10;
    private static final int PESO_ISO27001 = 10;
    private static final int PESO_OPEN_FINANCE = 5;

    public ComplianceReport score() {
        log.info("Calculando compliance report");
        NormaScore lgpd = scoreLgpd();
        NormaScore sox = scoreSox();
        NormaScore bacen = scoreBacen();
        NormaScore susep = scoreSusep();
        NormaScore iso = scoreIso27001();
        NormaScore openFinance = scoreOpenFinance();

        List<NormaScore> normas = List.of(lgpd, sox, bacen, susep, iso, openFinance);

        int scoreGeral = (lgpd.score() * PESO_LGPD
                + sox.score() * PESO_SOX
                + bacen.score() * PESO_BACEN
                + susep.score() * PESO_SUSEP
                + iso.score() * PESO_ISO27001
                + openFinance.score() * PESO_OPEN_FINANCE) / 100;

        String nivelGeral = nivel(scoreGeral, 100);

        return new ComplianceReport(normas, scoreGeral, nivelGeral, Instant.now());
    }

    private NormaScore scoreLgpd() {
        List<String> findings = new ArrayList<>();
        int score = 100;

        if (!autoMask) {
            score -= 30;
            findings.add("LGPD: auto-mask desativado (nexbridge.lgpd.auto-mask=false)");
        }

        List<ConsumerDefinition> consumers = consumerRepository.findAll();
        long semDeniedFields = consumers.stream()
                .filter(c -> c.getDeniedFields() == null || c.getDeniedFields().isEmpty())
                .count();
        if (semDeniedFields > 0) {
            score -= (int) (semDeniedFields * 20);
            findings.add("LGPD: " + semDeniedFields + " consumer(s) sem denied_fields configurados");
        }
        score = Math.max(0, score);

        return new NormaScore("LGPD", score, 100, nivel(score, 100), findings);
    }

    private NormaScore scoreSox() {
        List<String> findings = new ArrayList<>();
        int score = 0;

        long totalLogs = auditRepository.countAll();
        if (totalLogs > 0) {
            score += 40;
        } else {
            findings.add("SOX: nenhuma entrada no audit_log encontrada");
        }

        List<ConsumerDefinition> consumers = consumerRepository.findAll();
        boolean algumComRateLimit = consumers.stream().anyMatch(c -> c.getRateLimit() > 0);
        if (algumComRateLimit) {
            score += 30;
        } else {
            findings.add("SOX: nenhum consumer com rateLimit configurado");
        }

        List<ConnectorDefinition> connectors = connectorRepository.findAll();
        boolean algumComTls = connectors.stream()
                .anyMatch(c -> c.getConfig() != null && c.getConfig().containsKey("tls"));
        if (algumComTls) {
            score += 30;
        } else {
            findings.add("SOX: nenhum conector com TLS configurado");
        }

        return new NormaScore("SOX", score, 100, nivel(score, 100), findings);
    }

    private NormaScore scoreBacen() {
        List<String> findings = new ArrayList<>();
        int score = 0;

        List<ConnectorDefinition> ativos = connectorRepository.findByEnabled(true);
        if (!ativos.isEmpty()) {
            score += 50;
        } else {
            findings.add("Bacen 4.893: nenhum conector ativo encontrado");
        }

        List<ConsumerDefinition> consumers = consumerRepository.findAll();
        boolean rateLimitGlobal = consumers.stream().anyMatch(c -> c.getRateLimit() > 0);
        if (rateLimitGlobal) {
            score += 25;
        } else {
            findings.add("Bacen 4.893: rateLimit global não configurado");
        }

        long totalLogs = auditRepository.countAll();
        if (totalLogs > 0) {
            score += 25;
        } else {
            findings.add("Bacen 4.893: sem registros de auditoria");
        }

        return new NormaScore("Bacen 4.893", score, 100, nivel(score, 100), findings);
    }

    private NormaScore scoreSusep() {
        List<String> findings = new ArrayList<>();
        int score = 0;

        List<ConsumerDefinition> consumers = consumerRepository.findAll();
        boolean algumJwt = consumers.stream()
                .anyMatch(c -> "JWT".equalsIgnoreCase(c.getAuthType()));
        if (algumJwt) {
            score += 50;
        } else {
            findings.add("SUSEP: nenhum consumer com autenticação JWT");
        }

        boolean algumComRateLimit = consumers.stream().anyMatch(c -> c.getRateLimit() > 0);
        if (algumComRateLimit) {
            score += 50;
        } else {
            findings.add("SUSEP: nenhum consumer com rateLimit configurado");
        }

        return new NormaScore("SUSEP Open Insurance", score, 100, nivel(score, 100), findings);
    }

    private NormaScore scoreIso27001() {
        List<String> findings = new ArrayList<>();
        int score = 0;

        // +40 if vault is configured (master-key is not default)
        // We check via a value injection; since VaultService may be present, we just trust the property
        // Using a @Value approach would require field injection here; use a property placeholder check
        // For simplicity, we check via environment but VaultService availability isn't directly checkable
        // We'll rely on a separate @Value field
        if (vaultConfigured) {
            score += 40;
        } else {
            findings.add("ISO 27001: vault.master-key não configurado (usando valor padrão)");
        }

        List<ConnectorDefinition> connectors = connectorRepository.findAll();
        boolean algumComTls = connectors.stream()
                .anyMatch(c -> c.getConfig() != null && c.getConfig().containsKey("tls"));
        if (algumComTls) {
            score += 30;
        } else {
            findings.add("ISO 27001: nenhum conector com TLS configurado");
        }

        long totalLogs = auditRepository.countAll();
        if (totalLogs > 0) {
            score += 30;
        } else {
            findings.add("ISO 27001: auditoria imutável não verificada (sem registros)");
        }

        return new NormaScore("ISO 27001", score, 100, nivel(score, 100), findings);
    }

    @Value("${vault.master-key:default-insecure-key}")
    private String vaultMasterKey;

    private boolean vaultConfigured;

    @jakarta.annotation.PostConstruct
    void init() {
        vaultConfigured = vaultMasterKey != null && !vaultMasterKey.equals("default-insecure-key");
    }

    private NormaScore scoreOpenFinance() {
        List<String> findings = new ArrayList<>();
        int score = 0;

        List<ConsumerDefinition> consumers = consumerRepository.findAll();
        if (!consumers.isEmpty()) {
            score += 50;
        } else {
            findings.add("Open Finance: nenhum consumer cadastrado");
        }

        boolean todosComRateLimit = !consumers.isEmpty()
                && consumers.stream().allMatch(c -> c.getRateLimit() > 0);
        if (todosComRateLimit) {
            score += 50;
        } else if (!consumers.isEmpty()) {
            long semRate = consumers.stream().filter(c -> c.getRateLimit() <= 0).count();
            findings.add("Open Finance: " + semRate + " consumer(s) sem rateLimit configurado");
        }

        return new NormaScore("Open Finance", score, 100, nivel(score, 100), findings);
    }

    private String nivel(int score, int max) {
        double pct = max == 0 ? 0 : (double) score / max * 100;
        if (pct >= 80) return "CONFORME";
        if (pct >= 50) return "PARCIAL";
        return "NAO_CONFORME";
    }
}
