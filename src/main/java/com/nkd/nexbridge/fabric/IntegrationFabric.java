package com.nkd.nexbridge.fabric;

import com.nkd.nexbridge.audit.AuditEntry;
import com.nkd.nexbridge.audit.AuditService;
import com.nkd.nexbridge.connector.ConnectorRegistry;
import com.nkd.nexbridge.connector.ConnectorRequest;
import com.nkd.nexbridge.connector.ConnectorResponse;
import com.nkd.nexbridge.connector.LegacyConnector;
import com.nkd.nexbridge.domain.MappingDefinition;
import com.nkd.nexbridge.domain.MappingDefinitionRepository;
import com.nkd.nexbridge.exception.ConnectorException;
import com.nkd.nexbridge.exception.MappingException;
import com.nkd.nexbridge.governance.LgpdMasker;
import com.nkd.nexbridge.mapper.FieldAction;
import com.nkd.nexbridge.mapper.FieldMapper;
import com.nkd.nexbridge.mapper.FieldMapping;
import com.nkd.nexbridge.mapper.MappingConfig;
import com.nkd.nexbridge.mapper.TransformRule;
import com.nkd.nexbridge.api.filter.TraceIdFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class IntegrationFabric {

    private final ConnectorRegistry connectorRegistry;
    private final MappingDefinitionRepository mappingRepository;
    private final FieldMapper fieldMapper;
    private final LgpdMasker lgpdMasker;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    private final Counter requestsTotal;
    private final Counter requestsSuccess;
    private final Counter requestsError;
    private final Counter lgpdMaskingsTotal;
    private final Timer requestLatency;

    @Value("${nexbridge.lgpd.auto-mask:true}")
    private boolean autoMask;

    public IntegrationFabric(ConnectorRegistry connectorRegistry,
                             MappingDefinitionRepository mappingRepository,
                             FieldMapper fieldMapper,
                             LgpdMasker lgpdMasker,
                             AuditService auditService,
                             MeterRegistry meterRegistry) {
        this.connectorRegistry = connectorRegistry;
        this.mappingRepository = mappingRepository;
        this.fieldMapper = fieldMapper;
        this.lgpdMasker = lgpdMasker;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;

        this.requestsTotal   = Counter.builder("nexbridge.requests.total")
                .description("Total de requisições processadas pelo IntegrationFabric")
                .register(meterRegistry);
        this.requestsSuccess = Counter.builder("nexbridge.requests.success")
                .description("Requisições com resultado SUCCESS")
                .register(meterRegistry);
        this.requestsError   = Counter.builder("nexbridge.requests.error")
                .description("Requisições com resultado ERROR")
                .register(meterRegistry);
        this.lgpdMaskingsTotal = Counter.builder("nexbridge.lgpd.maskings.total")
                .description("Total de campos mascarados pelo LgpdMasker")
                .register(meterRegistry);
        this.requestLatency  = Timer.builder("nexbridge.request.latency")
                .description("Latência das requisições processadas (ms)")
                .register(meterRegistry);
    }

    public FlowContext execute(RouteDefinition route, Map<String, Object> requestData,
                               String consumerId, String consumerIp) {
        String traceId = TraceIdFilter.current();
        Instant start = Instant.now();
        requestsTotal.increment();

        FlowContext.FlowContextBuilder ctx = FlowContext.builder()
                .traceId(traceId)
                .consumerId(consumerId)
                .consumerIp(consumerIp)
                .endpoint(route.getPath())
                .method(route.getMethod())
                .connectorId(route.getConnectorId())
                .mappingId(route.getMappingId())
                .mappingVersion(route.getMappingVersion())
                .startedAt(start)
                .requestData(requestData);

        try {
            MappingDefinition mappingDef = mappingRepository
                    .findByMappingIdAndVersion(route.getMappingId(), route.getMappingVersion())
                    .orElseThrow(() -> new MappingException("MAPPING_NOT_FOUND",
                            "Mapeamento não encontrado: " + route.getMappingId() + " v" + route.getMappingVersion()));

            MappingConfig mappingConfig = toMappingConfig(mappingDef);
            ctx.copybookId(mappingDef.getCopybookId());

            fieldMapper.validateRequest(requestData, mappingConfig);

            FieldMapper.MapResult requestResult = fieldMapper.mapRequest(requestData, mappingConfig);
            ctx.discardedFields(requestResult.discardedFields());

            LegacyConnector connector = connectorRegistry.get(route.getConnectorId());
            ctx.sourceSystem(connector.getType().name() + " / " + route.getConnectorId());

            ConnectorRequest connectorRequest = ConnectorRequest.builder()
                    .connectorId(route.getConnectorId())
                    .traceId(traceId)
                    .params(requestResult.output())
                    .timeoutMs(5000)
                    .build();

            ConnectorResponse connectorResponse = connector.send(connectorRequest);

            if (!connectorResponse.isSuccess()) {
                throw new ConnectorException(
                        connectorResponse.getErrorCode() != null ? connectorResponse.getErrorCode() : "CONNECTOR_ERROR",
                        connectorResponse.getErrorMessage() != null ? connectorResponse.getErrorMessage() : "Connector returned failure");
            }

            Map<String, Object> rawResponse = connectorResponse.getResultSet() != null
                    ? connectorResponse.getResultSet()
                    : Map.of();

            FieldMapper.MapResult responseResult = fieldMapper.mapResponse(rawResponse, mappingConfig);
            Map<String, Object> mappedResponse = new LinkedHashMap<>(responseResult.output());

            List<String> maskedFields = new ArrayList<>();
            if (autoMask) {
                LgpdMasker.MaskResult maskResult = lgpdMasker.mask(mappedResponse);
                maskedFields = maskResult.maskedFields();
                if (!maskedFields.isEmpty()) {
                    lgpdMaskingsTotal.increment(maskedFields.size());
                }
            }

            long durationMs = System.currentTimeMillis() - start.toEpochMilli();
            requestLatency.record(durationMs, TimeUnit.MILLISECONDS);
            requestsSuccess.increment();

            FlowContext result = ctx
                    .responseData(mappedResponse)
                    .maskedFields(maskedFields)
                    .durationMs((int) durationMs)
                    .success(true)
                    .build();

            auditService.save(buildAuditEntry(result, 200));
            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start.toEpochMilli();
            requestLatency.record(durationMs, TimeUnit.MILLISECONDS);
            requestsError.increment();
            log.error("IntegrationFabric error traceId={}: {}", traceId, e.getMessage());

            FlowContext errorCtx = ctx
                    .durationMs((int) durationMs)
                    .success(false)
                    .errorCode(e instanceof com.nkd.nexbridge.exception.NexBridgeException nbe ? nbe.getErrorCode() : "INTERNAL_ERROR")
                    .errorMessage(e.getMessage())
                    .build();

            auditService.save(buildAuditEntry(errorCtx, 500));
            throw e;
        }
    }

    private MappingConfig toMappingConfig(MappingDefinition def) {
        List<FieldMapping> requestFields = toFieldMappings(def.getRequestFields());
        List<FieldMapping> responseFields = toFieldMappings(def.getResponseFields());
        return MappingConfig.builder()
                .mappingId(def.getMappingId())
                .version(def.getVersion())
                .connectorId(def.getConnectorId())
                .copybookId(def.getCopybookId())
                .sourceFormat(def.getSourceFormat())
                .targetFormat(def.getTargetFormat())
                .requestFields(requestFields)
                .responseFields(responseFields)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<FieldMapping> toFieldMappings(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        return raw.stream().map(m -> {
            String actionStr = (String) m.getOrDefault("action", "MAP");
            String transformStr = (String) m.get("transform");
            return FieldMapping.builder()
                    .from((String) m.get("from"))
                    .to((String) m.get("to"))
                    .action(FieldAction.valueOf(actionStr))
                    .transform(transformStr != null ? TransformRule.valueOf(transformStr) : TransformRule.COPY)
                    .constantValue((String) m.get("constant_value"))
                    .required(Boolean.TRUE.equals(m.get("required")))
                    .padLength((Integer) m.get("pad_length"))
                    .padChar((String) m.get("pad_char"))
                    .decimalPlaces((Integer) m.get("decimal_places"))
                    .dateFormatIn((String) m.get("date_format_in"))
                    .dateFormatOut((String) m.get("date_format_out"))
                    .enumMap((Map<String, String>) m.get("enum_map"))
                    .auditNote((String) m.get("audit_note"))
                    .build();
        }).toList();
    }

    private AuditEntry buildAuditEntry(FlowContext ctx, int httpStatus) {
        return AuditEntry.builder()
                .traceId(ctx.getTraceId())
                .timestampUtc(ctx.getStartedAt())
                .method(ctx.getMethod())
                .endpoint(ctx.getEndpoint())
                .consumerId(ctx.getConsumerId())
                .consumerIp(ctx.getConsumerIp())
                .sourceSystem(ctx.getSourceSystem())
                .connectorId(ctx.getConnectorId())
                .httpStatus(httpStatus)
                .durationMs(ctx.getDurationMs())
                .sensitiveFields(ctx.getMaskedFields())
                .maskApplied(ctx.getMaskedFields() != null && !ctx.getMaskedFields().isEmpty())
                .discardedFields(ctx.getDiscardedFields())
                .result(ctx.isSuccess() ? "SUCCESS" : "ERROR")
                .errorCode(ctx.getErrorCode())
                .errorMessage(ctx.getErrorMessage())
                .mappingId(ctx.getMappingId())
                .copybookVersion(ctx.getCopybookId())
                .build();
    }
}
