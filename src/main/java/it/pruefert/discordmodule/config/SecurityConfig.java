package it.pruefert.discordmodule.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API key filter — all routes under /api/* require a Bearer token matching
 * {@code AETHERA_API_KEY}. Health/actuator endpoints are always allowed.
 */
@Configuration
public class SecurityConfig {

    @Value("${aethera.api-key:}")
    private String apiKey;

    @Bean
    public OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String path = request.getRequestURI();

                // Allow health/actuator endpoints without auth
                if (path.startsWith("/actuator")) {
                    chain.doFilter(request, response);
                    return;
                }

                // Skip auth if no API key is configured
                if (apiKey == null || apiKey.isBlank()) {
                    chain.doFilter(request, response);
                    return;
                }

                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (apiKey.equals(token)) {
                        chain.doFilter(request, response);
                        return;
                    }
                }

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
            }
        };
    }
}
