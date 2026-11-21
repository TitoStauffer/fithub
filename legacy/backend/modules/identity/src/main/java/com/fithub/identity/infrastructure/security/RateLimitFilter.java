package com.fithub.identity.infrastructure.security;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter para endpoints de autenticação.
 * Limite: 5 requisições por minuto por IP.
 * Previne força bruta em /auth/login e /auth/register.
 *
 * Implementação simples sem dependências externas:
 * - Armazena timestamps em ConcurrentHashMap
 * - Limpa entradas antigas periodicamente
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long TIME_WINDOW_MS = 60_000; // 1 minuto

    private final Map<String, RequestRecord> requestRecords = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Rate limit ONLY em endpoints de autenticação
        if (isAuthEndpoint(request)) {
            String clientIp = getClientIp(request);

            if (!isWithinRateLimit(clientIp)) {
                response.setStatus(429); // HTTP 429 Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Too many requests. Please try again later.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/auth/login") || path.startsWith("/auth/register");
    }

    private boolean isWithinRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        RequestRecord record = requestRecords.get(clientIp);

        if (record == null || now - record.getWindowStart() > TIME_WINDOW_MS) {
            // Nova janela de tempo
            requestRecords.put(clientIp, new RequestRecord(now, 1));
            cleanupOldRecords(now);
            return true;
        }

        if (record.getCount() < MAX_REQUESTS_PER_MINUTE) {
            record.incrementCount();
            return true;
        }

        // Limite atingido
        return false;
    }

    private void cleanupOldRecords(long now) {
        // Remove registros mais antigos que 2 minutos (limpeza periódica)
        requestRecords.entrySet().removeIf(entry ->
            now - entry.getValue().getWindowStart() > TIME_WINDOW_MS * 2
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ========== Inner Class ==========

    private static class RequestRecord {
        private final long windowStart;
        private int count;

        RequestRecord(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        long getWindowStart() {
            return windowStart;
        }

        int getCount() {
            return count;
        }

        void incrementCount() {
            this.count++;
        }
    }
}
