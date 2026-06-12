package com.nkd.nexbridge.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditStatsDto {
    private long total;
    private long sucesso;
    private long erro;
    private long aviso;
    private long bloqueado;
    @JsonProperty("latencia_media_ms")
    private long latenciaMediaMs;
    @JsonProperty("dados_mascarados")
    private long dadosMascarados;
    @JsonProperty("dados_expostos")
    private long dadosExpostos;
}
