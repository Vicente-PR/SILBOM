package cl.silbom.backend.exception;

import cl.silbom.backend.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * QUÉ HACE: Traduce excepciones del backend a los contratos y códigos HTTP existentes.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: El Control 17 exige separar mensajes de negocio de errores internos:
 * las respuestas de integridad y errores inesperados no revelan SQL, credenciales ni trazas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.NOT_FOUND.value(),
                        "Recurso No Encontrado",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceAlreadyExists(
            ResourceAlreadyExistsException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.CONFLICT.value(),
                        "Conflicto de Identificador",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusinessConflict(
            BusinessConflictException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.CONFLICT.value(),
                        "Conflicto de Regla de Negocio LOTO",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbidden(
            ForbiddenOperationException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.FORBIDDEN.value(),
                        "Acceso Prohibido (Anti-IDOR)",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(SecurityAuthException.class)
    public ResponseEntity<ErrorResponseDTO> handleSecurityAuth(
            SecurityAuthException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.UNAUTHORIZED.value(),
                        "No Autorizado",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(BotDetectionException.class)
    public ResponseEntity<ErrorResponseDTO> handleBotDetection(
            BotDetectionException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Petición Rechazada por Seguridad",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detalleErrores =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(FieldError::getDefaultMessage)
                        .collect(Collectors.joining("; "));
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Error de Validación de Entrada (Control 14)",
                        detalleErrores,
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Cuerpo de Petición Malformado",
                        "El formato JSON enviado es inválido o contiene tipos de datos"
                            + " incompatibles",
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Argumento Inválido",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.CONFLICT.value(),
                        "Estado Inválido de Operación",
                        ex.getMessage(),
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * QUÉ HACE: Devuelve 409 para restricciones de datos o fallos de adquisición de locks.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No devuelve el mensaje SQL interno; el cliente debe consultar el
     * estado actual antes de decidir un reintento.
     */
    @ExceptionHandler({
        org.springframework.dao.DataIntegrityViolationException.class,
        org.springframework.dao.PessimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleDatabaseConflict(
            Exception ex, HttpServletRequest request) {
        return error(
                409,
                "Conflicto de datos",
                "La operación viola una restricción o existe una operación concurrente; revise el"
                    + " estado actual",
                request);
    }

    @ExceptionHandler(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleParameter(
            Exception ex, HttpServletRequest request) {
        return error(
                400, "Parámetro inválido", "Revise el tipo de los parámetros enviados", request);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResource(
            Exception ex, HttpServletRequest request) {
        return error(404, "Recurso no encontrado", "Ruta no encontrada", request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMedia(Exception ex, HttpServletRequest request) {
        return error(
                415,
                "Tipo de contenido no admitido",
                "Use application/json para el cuerpo de la petición",
                request);
    }

    /**
     * QUÉ HACE: Devuelve 405 y la cabecera Allow con los métodos admitidos.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Una ruta existente con verbo incorrecto no debe convertirse en
     * un error 500.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethod(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(405)
                .allow(
                        ex.getSupportedHttpMethods()
                                .toArray(org.springframework.http.HttpMethod[]::new))
                .body(
                        new ErrorResponseDTO(
                                LocalDateTime.now(),
                                405,
                                "Método no permitido",
                                "Verbo HTTP no admitido para este recurso",
                                request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccess(Exception ex, HttpServletRequest request) {
        return error(403, "Acceso prohibido", "Operación no autorizada", request);
    }

    /**
     * QUÉ HACE: Construye respuestas uniformes con estado, título, mensaje y ruta.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Evita duplicar la estructura sin alterar los códigos HTTP ni
     * exponer detalles internos.
     */
    private ResponseEntity<ErrorResponseDTO> error(
            int status, String title, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(
                        new ErrorResponseDTO(
                                LocalDateTime.now(),
                                status,
                                title,
                                message,
                                request.getRequestURI()));
    }

    /**
     * QUÉ HACE: Responde 500 con un mensaje genérico ante un fallo inesperado.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No serializa la excepción ni su stack trace; la API conserva su
     * contrato de errores.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(
            Exception ex, HttpServletRequest request) {
        // En log interno se puede registrar el error, pero al cliente NUNCA se expone el stack
        // trace
        ErrorResponseDTO error =
                new ErrorResponseDTO(
                        LocalDateTime.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error Interno del Servidor",
                        "Ocurrió un error inesperado al procesar la solicitud. Contacte al"
                            + " administrador.",
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
