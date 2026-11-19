package cl.silbom.backend.security;

import cl.silbom.backend.exception.*;
import cl.silbom.backend.model.Rol;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * QUÉ HACE: Centraliza el acceso a la identidad individual autenticada y las comprobaciones de rol
 * y propiedad.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Los controles de autenticación y autorización (6 y 7) también deben
 * aplicarse en servicios: el nombre enviado por el cliente no acredita identidad.
 */
@Component
public class ActorActual {

    /**
     * QUÉ HACE: Extrae el TecnicoPrincipal autenticado de SecurityContextHolder.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Rechaza contextos ausentes o incompatibles con 401; no acepta
     * identidades declaradas en el JSON.
     */
    public TecnicoPrincipal obtener() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof TecnicoPrincipal t)) {
            throw new SecurityAuthException("Se requiere la identidad individual del técnico");
        }
        return t;
    }

    /**
     * QUÉ HACE: Exige el rol SUPERVISOR antes de una operación administrativa.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Implementa RBAC para máquinas y técnicos; un técnico autenticado
     * no obtiene por ello permisos administrativos.
     */
    public void exigirSupervisor() {
        if (obtener().rol() != Rol.SUPERVISOR) {
            throw new ForbiddenOperationException("Operación reservada a supervisores");
        }
    }

    /**
     * QUÉ HACE: Comprueba que el técnico autenticado sea el dueño del ticket digital antes de
     * liberarlo.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: La regla del sistema protege el registro personal e impide
     * liberaciones ajenas incluso por supervisores. El rechazo es 403; no certifica cumplimiento
     * del DS 594 ni demuestra quién retiró físicamente un candado.
     */
    public void exigirPropietario(Long tecnicoId) {
        if (!obtener().id().equals(tecnicoId)) {
            throw new ForbiddenOperationException(
                    "Solo el técnico responsable puede liberar este bloqueo; los registros legados"
                        + " requieren un procedimiento supervisado");
        }
    }
}
