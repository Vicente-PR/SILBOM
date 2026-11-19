package cl.silbom.backend.security;
import cl.silbom.backend.dto.TecnicoCreateDTO;
import cl.silbom.backend.exception.ForbiddenOperationException;
import cl.silbom.backend.model.Rol;
import cl.silbom.backend.repository.*;
import cl.silbom.backend.service.TecnicoService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TecnicoServiceTests {
    @Test void servicioRechazaNoSupervisorAntesDePersistir() {
        var repo=mock(TecnicoRepository.class);var bloqueos=mock(BloqueoLotoRepository.class);
        var encoder=mock(PasswordEncoder.class);var actor=mock(ActorActual.class);
        doThrow(new ForbiddenOperationException("No autorizado")).when(actor).exigirSupervisor();
        var service=new TecnicoService(repo,bloqueos,encoder,actor,"PLANTA");
        assertThrows(ForbiddenOperationException.class,()->service.crear(new TecnicoCreateDTO("prueba","Nombre","test-only-password",Rol.TECNICO)));
        verifyNoInteractions(repo,bloqueos,encoder);
    }
}
