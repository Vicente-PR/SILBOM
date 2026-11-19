package cl.silbom.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cl.silbom.backend.dto.ErrorResponseDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Filtro de autenticación por API Key para operaciones de escritura (POST, PUT, DELETE, etc.).
 * Las lecturas (GET, HEAD) y pre-flight (OPTIONS) son públicas por diseño, para los paneles
 * de monitoreo de Planta San Joaquín. Todo método fuera de esa lista exige credencial.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_PUBLICOS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String BEARER_PREFIX = "Bearer ";

    /** Atributo de la petición: true si llegó con una API key válida (en cualquier método). */
    public static final String ATTR_AUTENTICADO = "silbom.apiKeyValida";

    private final byte[] expectedApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(
            @Value("${silbom.security.api-key}") String expectedApiKey,
            ObjectMapper objectMapper) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalStateException("silbom.security.api-key no puede estar vacía");
        }
        this.expectedApiKey = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        boolean claveCorrecta = claveValida(extraerClave(request));

        if (METODOS_PUBLICOS.contains(request.getMethod().toUpperCase())) {
            // Las lecturas son públicas, pero si traen una clave válida se marca la petición
            // para que los endpoints puedan entregar datos confidenciales (ej: notas de auditoría).
            if (claveCorrecta) {
                request.setAttribute(ATTR_AUTENTICADO, Boolean.TRUE);
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!claveCorrecta) {
            rechazar(request, response);
            return;
        }

        request.setAttribute(ATTR_AUTENTICADO, Boolean.TRUE);
        filterChain.doFilter(request, response);
    }

    private String extraerClave(HttpServletRequest request) {
        String clave = request.getHeader("X-API-KEY");
        if (clave != null && !clave.isBlank()) {
            return clave.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean claveValida(String provista) {
        if (provista == null || provista.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(provista.getBytes(StandardCharsets.UTF_8), expectedApiKey);
    }

    private void rechazar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponseDTO error = new ErrorResponseDTO(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "No autorizado",
                "Credencial ausente o inválida",
                request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}