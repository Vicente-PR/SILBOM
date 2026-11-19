package cl.silbom.backend.model;

/**
 * QUÉ HACE: Enumera los perfiles TECNICO y SUPERVISOR usados en autenticación y autorización.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Un conjunto cerrado evita roles arbitrarios; el servicio determina
 * qué acciones permite cada perfil.
 */
public enum Rol {
    TECNICO,
    SUPERVISOR
}
