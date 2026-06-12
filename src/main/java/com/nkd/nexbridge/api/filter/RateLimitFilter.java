package com.nkd.nexbridge.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkd.nexbridge.api.dto.NexError;
import com.nkd.nexbridge.api.dto.NexMeta;
import com.nkd.nexbridge.api.dto.NexResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.nkd.nexbridge.config.NexBridgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${nexbridge.rate-limit.default-requests-per-minute:1000}")
    private int defaultRequestsPerMinute;

    private final ObjectMapper objectMapper;
    private final NexBridgeProperties properties;

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String consumerId = resolveConsumerId(request);
        RateLimiter limiter = limiters.computeIfAbsent(consumerId,
                k -> new RateLimiter(defaultRequestsPerMinute));

        if (!limiter.tryAcquire()) {
            log.warn("Rate limit exceeded for consumer {}", consumerId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            var error = NexError.builder()
                    .code("RATE_LIMIT_EXCEEDED")
                    .message("Taxa de requisições excedida. Tente novamente em breve.")
                    .httpStatus(429)
                    .build();
            var meta = NexMeta.builder()
                    .traceId(TraceIdFilter.current())
                    .timestamp(Instant.now().toString())
                    .nexbridgeVersion(properties.getVersion())
                    .build();
            objectMapper.writeValue(response.getWriter(), NexResponse.error(error, meta));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveConsumerId(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String principal) {
            return principal;
        }
        return request.getRemoteAddr();
    }

    private static class RateLimiter {
        private final int maxRequests;
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        RateLimiter(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                counter.set(0);
                windowStart = now;
            }
            return counter.incrementAndGet() <= maxRequests;
        }
    }
}
