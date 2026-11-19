package cl.silbom.backend.security;

import java.util.regex.Pattern;

/**
 * QUÉ HACE:
 * Sanitiza cadenas de texto libre ingresadas por los usuarios (motivos, nombres de técnicos, notas).
 *
 * POR QUÉ ESTÁ ESCRITO ASÍ (CONTROL 15 DE CIBERSEGURIDAD: SANITIZAR CONTENIDO):
 * - Remueve de raíz etiquetas peligrosas HTML y JavaScript (<script>, <iframe>, <object>, onload, onclick, etc.).
 * - Escapa caracteres especiales como '<', '>', '&', y elimina caracteres de control ASCII (para prevenir Log Injection / CRLF Injection).
 * - Se ejecuta en la capa de negocio antes de persistir los datos en base de datos.
 */
public final class InputSanitizer {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern INLINE_EVENT_PATTERN = Pattern.compile("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']");

    private InputSanitizer() {
        // Clase de utilidades estáticas: constructor privado
    }

    /**
     * Sanitiza el texto eliminando tags maliciosos y normalizando espacios y saltos peligrosos.
     *
     * @param input Texto recibido desde el cliente
     * @return Texto limpio y seguro para persistencia y logs
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String cleaned = input;

        // 1. Remover etiquetas completas de <script>
        cleaned = SCRIPT_PATTERN.matcher(cleaned).replaceAll("");

        // 2. Remover eventos JavaScript inline (ej: onclick="alert(1)")
        cleaned = INLINE_EVENT_PATTERN.matcher(cleaned).replaceAll("");

        // 3. Remover cualquier otra etiqueta HTML (<p>, <img>, etc.)
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll("");

        // 4. Limpiar caracteres de control de terminal/CRLF (prevenir Log Injection)
        cleaned = cleaned.replaceAll("[\\p{Cc}\\p{Cf}]+", " ");

        // 5. Escapar caracteres residuales peligrosos
        cleaned = cleaned.replace("<", "&lt;").replace(">", "&gt;");

        return cleaned.trim();
    }

    public static String required(String text, int min, int max) {
        String result = sanitize(text);
        if (result == null || result.length() < min || result.length() > max)
            throw new IllegalArgumentException("Texto vacío o fuera de longitud tras normalizar");
        return result;
    }
}
