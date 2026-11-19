package cl.silbom.backend.security;

import cl.silbom.backend.model.Rol;
import cl.silbom.backend.model.Tecnico;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * QUÉ HACE: Adapta Tecnico al contrato UserDetails de Spring Security, con identidad, hash, rol,
 * planta y estado activo.
 *
 * <p>POR QUÉ ESTÁ ESCRITO ASÍ: HTTP Basic aporta la credencial y Spring compara la contraseña con
 * BCrypt. Este objeto interno no es un DTO HTTP; toString omite el hash para evitar su exposición
 * accidental en logs.
 */
public record TecnicoPrincipal(
    Long id,

    String username,

    String password,

    Rol rol,

    String plantaId,

    boolean activo
)
        implements UserDetails {

    public TecnicoPrincipal(Tecnico t) {
        this(
                t.getId(),
                t.getUsername(),
                t.getPasswordHash(),
                t.getRol(),
                t.getPlantaId(),
                t.isActivo());
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * QUÉ HACE: Entrega el hash BCrypt al proveedor de autenticación de Spring.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: No representa la contraseña original ni debe serializarse como
     * respuesta de API.
     */
    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    /**
     * QUÉ HACE: Transforma el rol persistido en una autoridad ROLE_TECNICO o ROLE_SUPERVISOR.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Spring Security utiliza ese prefijo al evaluar hasRole; no se
     * toman roles del JSON de una petición.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    /**
     * QUÉ HACE: Representa la identidad sin incluir el hash de contraseña.
     *
     * <p>POR QUÉ ESTÁ ESCRITO ASÍ: Los logs de diagnóstico no deben divulgar material de
     * autenticación (Controles 1 y 17).
     */
    @Override
    public String toString() {
        return "TecnicoPrincipal[" + username + "]";
    }
}
