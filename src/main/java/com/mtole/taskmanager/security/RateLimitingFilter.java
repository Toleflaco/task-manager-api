package com.mtole.taskmanager.security;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {


    private final RateLimitingProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    public RateLimitingFilter(RateLimitingProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 1. ¿Aplica este filtro a esta request?
        //    Si no → filterChain.doFilter(...) y salir
        if (!"/auth/login".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        // 2. Obtener IP del cliente.
        String ipClient = getClientIp(request);
        // 3. Buscar o crear bucket para esa IP.
        Bucket bucket = buckets.computeIfAbsent(ipClient, ip -> newBucket());
        // 4. Intentar consumir 1 ficha.

        // 5a. Si consumió → filterChain.doFilter(...)
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            // 5b. Si no consumió → 429 con cuerpo idiomático
            String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again later.\",\"instance\":\"/auth/login\"}";
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(properties.login().refillPeriod().toSeconds()));
            response.getWriter().write(body);
            log.warn("Rate limit exceeded for ip={}", ipClient);

        }

    }


    // construir bucket con capacity + refill desde properties
    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(properties.login().capacity())
                        .refillGreedy(1, properties.login().refillPeriod()))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}


