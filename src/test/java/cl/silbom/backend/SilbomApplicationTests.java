package cl.silbom.backend;

import cl.silbom.backend.dto.*;
import cl.silbom.backend.model.*;
import cl.silbom.backend.repository.*;
import cl.silbom.backend.security.TecnicoPrincipal;
import cl.silbom.backend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.concurrent.atomic.AtomicInteger;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithUserDetails("supervisor-test")
class SilbomApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired MaquinaService maquinas;
    @Autowired BloqueoService bloqueos;
    @Autowired TecnicoService tecnicos;
    @Autowired TecnicoRepository tecnicoRepository;
    MockMvc mvc;
    static AtomicInteger address = new AtomicInteger();
    @BeforeEach void setup() {
        String ip="198.51.100."+address.incrementAndGet();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
            .defaultRequest(get("/").header("User-Agent","TestRunner/1.0").with(r->{r.setRemoteAddr(ip);return r;})).build();
    }
    String json(Object o) throws Exception { return mapper.writeValueAsString(o); }
    MaquinaCreateDTO maquina(String id,int fallas) { return new MaquinaCreateDTO(id,"Máquina de pruebas","Área de pruebas","X",100.0,fallas,null); }
    BloqueoCreateDTO bloqueo(String id,TipoIntervencion tipo) {
        return new BloqueoCreateDTO(id,tipo,"Revisión de máquina","Nombre enviado no confiable",true,"nota de prueba",null,
            tipo==TipoIntervencion.CORRECTIVA?"FALLA-001":null);
    }
    TecnicoPrincipal crearTecnico(String username) {
        tecnicos.crear(new TecnicoCreateDTO(username,"Técnico de prueba","test-only-password-123",Rol.TECNICO));
        return new TecnicoPrincipal(tecnicoRepository.findByUsernameAndPlantaId(username,"PLANTA-SAN-JOAQUIN").orElseThrow());
    }
    @Test void cargaInicial() throws Exception {
        mvc.perform(get("/api/v1/maquinas").with(anonymous())).andExpect(status().isOk())
            .andExpect(jsonPath("$",hasSize(6)))
            .andExpect(jsonPath("$[?(@.id == 'EQ-ENV-01')].mtbf",contains(233.8)));
    }
    @Test void autenticacionIndividualYNoApiKeyCompartida() throws Exception {
        mvc.perform(post("/api/v1/maquinas").with(anonymous()).header("X-API-KEY","legacy-key")
            .contentType(MediaType.APPLICATION_JSON).content(json(maquina("EQ-TEST-01",1))))
            .andExpect(status().isUnauthorized()).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/v1/bloqueos").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tecnicos").with(anonymous()).with(httpBasic("supervisor-test","wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tecnicos").with(anonymous()).with(httpBasic("supervisor-test","test-only-password-123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
    @Test void crudMaquinasYMtbfNoCalculable() throws Exception {
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON).content(json(maquina("EQ-TEST-01",0))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.mtbf",nullValue()));
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON).content(json(maquina("EQ-TEST-01",0))))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/maquinas/EQ-TEST-01").contentType(MediaType.APPLICATION_JSON)
            .content(json(new MaquinaUpdateDTO("Nombre actualizado","Nueva área","X",80.0,2))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mtbf",is(40.0)));
        mvc.perform(delete("/api/v1/maquinas/EQ-TEST-01")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/maquinas/EQ-TEST-01")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON).content(json(maquina("EQ-TEST-01",0))))
            .andExpect(status().isConflict());
    }
    @Test void declaracionCandadoRequerida() throws Exception {
        var dto=new BloqueoCreateDTO("EQ-ENV-01",TipoIntervencion.CORRECTIVA,"Motivo de prueba","Técnico",false,null,null,"F-01");
        mvc.perform(post("/api/v1/bloqueos").contentType(MediaType.APPLICATION_JSON).content(json(dto)))
            .andExpect(status().isBadRequest());
    }
    @Test void multiLotoYLiberacionRepetida() throws Exception {
        var uno=bloqueos.crearBloqueo(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA));
        var dos=bloqueos.crearBloqueo(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA));
        assertEquals(2,maquinas.obtenerPorId("EQ-PR-04").bloqueosActivos());
        bloquesCerrar(uno.id());
        assertTrue(maquinas.obtenerPorId("EQ-PR-04").bloqueada());
        mvc.perform(put("/api/v1/bloqueos/"+uno.id()+"/liberar")).andExpect(status().isConflict());
        assertEquals(1,maquinas.obtenerPorId("EQ-PR-04").bloqueosActivos());
        bloquesCerrar(dos.id());
        assertFalse(maquinas.obtenerPorId("EQ-PR-04").bloqueada());
    }
    void bloquesCerrar(Long id) {
        org.springframework.security.core.context.SecurityContextHolder.setContext(org.springframework.security.test.context.TestSecurityContextHolder.getContext());
        bloqueos.liberarBloqueo(id);
    }
    @Test void mismaFallaSeCuentaUnaVez() {
        maquinas.crearMaquina(maquina("EQ-TEST-02",0));
        var a=bloqueos.crearBloqueo(bloqueo("EQ-TEST-02",TipoIntervencion.CORRECTIVA));
        var b=bloqueos.crearBloqueo(bloqueo("EQ-TEST-02",TipoIntervencion.CORRECTIVA));
        assertEquals(a.fallaId(),b.fallaId());
        assertEquals(1,maquinas.obtenerPorId("EQ-TEST-02").fallasCorrectivas());
        assertEquals(100.0,maquinas.obtenerPorId("EQ-TEST-02").mtbf());
        bloqueos.crearBloqueo(bloqueo("EQ-TEST-02",TipoIntervencion.PREVENTIVA));
        assertEquals(1,maquinas.obtenerPorId("EQ-TEST-02").fallasCorrectivas());
    }
    @Test void historialYAuditoria() throws Exception {
        var b=bloqueos.crearBloqueo(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA));
        mvc.perform(delete("/api/v1/bloqueos/"+b.id())).andExpect(status().isConflict());
        bloquesCerrar(b.id());
        mvc.perform(delete("/api/v1/bloqueos/"+b.id())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/bloqueos/"+b.id()+"/eventos")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].accion",contains("CREADO","LIBERADO")));
    }
    @Test void tecnicoNoPuedeLiberarNiLeerNotasAjenas() throws Exception {
        var otro=crearTecnico("otro-tecnico");
        var b=bloqueos.crearBloqueo(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA));
        mvc.perform(put("/api/v1/bloqueos/"+b.id()+"/liberar").with(user(otro))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/bloqueos/"+b.id()).with(user(otro))).andExpect(status().isOk())
            .andExpect(jsonPath("$.notasAuditoria",nullValue()));
        assertEquals(1,maquinas.obtenerPorId("EQ-PR-04").bloqueosActivos());
        mvc.perform(post("/api/v1/maquinas").with(user(otro)).contentType(MediaType.APPLICATION_JSON).content(json(maquina("EQ-TEST-09",0))))
            .andExpect(status().isForbidden());
    }
    @Test void identidadDelResponsableNoProvieneDelJson() throws Exception {
        var t=crearTecnico("tecnico-real");
        mvc.perform(post("/api/v1/bloqueos").with(user(t)).contentType(MediaType.APPLICATION_JSON)
            .content(json(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.tecnicoId",is(t.id().intValue())))
            .andExpect(jsonPath("$.nombreTecnico",is("Técnico de prueba")));
    }
    @Test void crudTecnicosBajaLogicaYPermisos() throws Exception {
        var t=crearTecnico("crud-tecnico");
        mvc.perform(get("/api/v1/tecnicos/"+t.id())).andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/v1/tecnicos/"+t.id()).contentType(MediaType.APPLICATION_JSON)
            .content(json(new TecnicoUpdateDTO("Nombre cambiado",Rol.TECNICO,null)))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tecnicos").with(user(t))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/tecnicos/"+t.id())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tecnicos/"+t.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/bloqueos").with(anonymous()).with(httpBasic("crud-tecnico","test-only-password-123")))
            .andExpect(status().isUnauthorized());
    }
    @Test void noDarBajaTecnicoConBloqueoActivo() throws Exception {
        var t=crearTecnico("tecnico-activo");
        mvc.perform(post("/api/v1/bloqueos").with(user(t)).contentType(MediaType.APPLICATION_JSON)
            .content(json(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA)))).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/tecnicos/"+t.id())).andExpect(status().isConflict());
    }
    @Test void noBorrarMaquinaBloqueada() throws Exception {
        bloqueos.crearBloqueo(bloqueo("EQ-PR-04",TipoIntervencion.PREVENTIVA));
        mvc.perform(delete("/api/v1/maquinas/EQ-PR-04")).andExpect(status().isConflict());
    }
    @Test void entradasInvalidasYMetodos() throws Exception {
        mvc.perform(get("/api/v1/maquinas?bloqueada=INVALIDO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/bloqueos/abc")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/maquinas/EQ-PR-04")).andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"));
        mvc.perform(get("/api/v1/no-existe")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON)
            .content(json(new MaquinaCreateDTO("EQ-TEST-03","<b></b>","Área","X",0.0,0,null))))
            .andExpect(status().isBadRequest());
    }
    @Test void referenciaCorrectivaObligatoria() throws Exception {
        var dto=new BloqueoCreateDTO("EQ-PR-04",TipoIntervencion.CORRECTIVA,"Motivo válido","Técnico",true,null,null,null);
        mvc.perform(post("/api/v1/bloqueos").contentType(MediaType.APPLICATION_JSON).content(json(dto)))
            .andExpect(status().isBadRequest());
    }
    @Test void corsYCabeceras() throws Exception {
        mvc.perform(get("/api/v1/maquinas").secure(true)).andExpect(header().string("Strict-Transport-Security",containsString("max-age=")))
            .andExpect(header().string("X-Frame-Options","DENY")).andExpect(header().string("Content-Security-Policy","default-src 'self'"));
        mvc.perform(get("/api/v1/maquinas")).andExpect(header().doesNotExist("Strict-Transport-Security"));
        mvc.perform(options("/api/v1/maquinas").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","GET"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(options("/api/v1/maquinas").header("Origin","https://no-autorizado.example").header("Access-Control-Request-Method","GET"))
            .andExpect(status().isForbidden());
    }
    @Test void rateLimitRealSinDeshabilitarFiltro() throws Exception {
        for(int i=0;i<60;i++) mvc.perform(get("/api/v1/maquinas")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/maquinas")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
    @Test void validacionesAdicionalesYTipoDeContenido() throws Exception {
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.TEXT_PLAIN).content("no-json"))
            .andExpect(status().isUnsupportedMediaType());
        var bad=new MaquinaCreateDTO("EQ-TEST-08","Máquina","Área","X",100.0,1,"bot");
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON).content(json(bad)))
            .andExpect(status().isBadRequest());
        var note=new BloqueoCreateDTO("EQ-PR-04",TipoIntervencion.PREVENTIVA,"Motivo válido",null,true,"x".repeat(2001),null,null);
        mvc.perform(post("/api/v1/bloqueos").contentType(MediaType.APPLICATION_JSON).content(json(note)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/maquinas").header("User-Agent","" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/maquinas").header("User-Agent","sqlmap")).andExpect(status().isForbidden());
    }
    @Test void passwordUtf8SinTruncamientoYProteccionSupervisor() throws Exception {
        var principal=(TecnicoPrincipal)org.springframework.security.test.context.TestSecurityContextHolder.getContext().getAuthentication().getPrincipal();
        mvc.perform(delete("/api/v1/tecnicos/"+principal.id())).andExpect(status().isConflict());
        var dto=new TecnicoCreateDTO("unicode-pass","Técnico prueba","á".repeat(40),Rol.TECNICO);
        mvc.perform(post("/api/v1/tecnicos").contentType(MediaType.APPLICATION_JSON).content(json(dto)))
            .andExpect(status().isBadRequest());
    }

    // Regresión: el POST y el GET autorizado deben devolver el texto normalizado persistido.
    @Test void notasSeNormalizanAntesDeCifrarYResponder() throws Exception {
        var dto = new BloqueoCreateDTO("EQ-PR-04", TipoIntervencion.PREVENTIVA,
            "Prueba de notas", null, true,
            "<b>Revisión</b>\n<script>alert(1)</script>\u0000pendiente", null, null);
        var result = mvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON).content(json(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.notasAuditoria", is("Revisión pendiente")))
            .andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/v1/bloqueos/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notasAuditoria", is("Revisión pendiente")));
    }

    // Una nota ausente o vacía tras normalizar no debe persistirse como otro valor.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \r\n\t ", "<b></b>"})
    void notasSinContenidoSonNullEnAltaYConsulta(String nota) throws Exception {
        var dto = new BloqueoCreateDTO("EQ-PR-04", TipoIntervencion.PREVENTIVA,
            "Prueba de nota vacía", null, true, nota, null, null);
        var result = mvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON).content(json(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.notasAuditoria", nullValue()))
            .andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/v1/bloqueos/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notasAuditoria", nullValue()));
    }
}
