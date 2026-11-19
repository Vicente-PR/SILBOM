package cl.silbom.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Control 18: Cabeceras defensivas HTTP
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "default-src 'self'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // HSTS solo en una conexión que ya es segura; no configura TLS.
        if (request.isSecure())
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        filterChain.doFilter(request, response);
    }
}
