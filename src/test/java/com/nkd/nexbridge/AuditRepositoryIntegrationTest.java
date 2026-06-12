package com.nkd.nexbridge;

import com.nkd.nexbridge.audit.AuditEntry;
import com.nkd.nexbridge.audit.AuditRepository;
import com.nkd.nexbridge.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AuditRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nexbridge_test")
            .withUsername("nexbridge")
            .withPassword("nexbridge");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private AuditService auditService;

    @Test
    void shouldSaveAuditEntry() {
        AuditEntry entry = AuditEntry.builder()
                .traceId("nx-test1234")
                .timestampUtc(Instant.now())
                .method("POST")
                .endpoint("/api/v1/test")
                .consumerId("test-consumer")
                .result("SUCCESS")
                .httpStatus(200)
                .durationMs(100)
                .maskApplied(false)
                .build();

        auditService.saveSync(entry);

        Optional<AuditEntry> found = auditRepository.findByTraceId("nx-test1234");
        assertThat(found).isPresent();
        assertThat(found.get().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldCountByResult() {
        // Salva 2 SUCCESS e 1 ERROR
        for (int i = 0; i < 2; i++) {
            auditService.saveSync(AuditEntry.builder()
                    .traceId("nx-count" + i)
                    .timestampUtc(Instant.now())
                    .result("SUCCESS")
                    .httpStatus(200)
                    .maskApplied(false)
                    .build());
        }
        auditService.saveSync(AuditEntry.builder()
                .traceId("nx-error1")
                .timestampUtc(Instant.now())
                .result("ERROR")
                .httpStatus(500)
                .maskApplied(false)
                .build());

        long success = auditRepository.countByResult("SUCCESS");
        assertThat(success).isGreaterThanOrEqualTo(2);
    }
}
