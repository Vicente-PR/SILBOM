package cl.silbom.backend;

import cl.silbom.backend.dto.*;
import cl.silbom.backend.exception.BusinessConflictException;
import cl.silbom.backend.model.*;
import cl.silbom.backend.security.*;
import cl.silbom.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL real, rol no propietario y transacciones independientes; no usa H2 ni Docker. */
@SpringBootTest @ActiveProfiles("postgres-test") @Timeout(60)
class PostgresIT {
    @Autowired MaquinaService maquinas;
    @Autowired BloqueoService bloqueos;
    @Autowired TecnicoService tecnicos;
    @Autowired TecnicoUserDetailsService users;
    @Autowired DataSource datasource;
    static AtomicInteger sequence=new AtomicInteger();
    TecnicoPrincipal supervisor;
    @BeforeEach void login() { supervisor=(TecnicoPrincipal)users.loadUserByUsername("supervisor-test"); as(supervisor); }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
    static void as(TecnicoPrincipal t) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(t,null,t.getAuthorities()));
    }
    String nuevaMaquina() {
        String id="EQ-IT-"+String.format("%02d",sequence.incrementAndGet());
        maquinas.crearMaquina(new MaquinaCreateDTO(id,"Prueba PostgreSQL","Área pruebas","X",100.0,0,null));return id;
    }
    TecnicoPrincipal tecnico() {
        String user="it-tecnico-"+sequence.incrementAndGet();
        tecnicos.crear(new TecnicoCreateDTO(user,"Técnico integración","test-only-password-123",Rol.TECNICO));
        return (TecnicoPrincipal)users.loadUserByUsername(user);
    }
    BloqueoCreateDTO dto(String id) {
        return new BloqueoCreateDTO(id,TipoIntervencion.CORRECTIVA,"Misma falla observada","Nombre no confiable",true,"nota PostgreSQL",null,"FALLA-COMPARTIDA");
    }
    <T> List<T> simultaneas(TecnicoPrincipal a,TecnicoPrincipal b,Supplier<T> uno,Supplier<T> dos) throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var f1=pool.submit(()->ejecutar(a,barrier,uno));var f2=pool.submit(()->ejecutar(b,barrier,dos));
            return List.of(f1.get(40,TimeUnit.SECONDS),f2.get(40,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    <T> T ejecutar(TecnicoPrincipal p,CyclicBarrier barrier,Supplier<T> action) throws Exception {
        try { as(p);barrier.await(10,TimeUnit.SECONDS);return action.get(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    long scalar(Connection c,String query) throws SQLException {
        try(var st=c.createStatement();var rs=st.executeQuery(query)) { rs.next();return rs.getLong(1); }
    }
    Connection conexion(String planta) throws SQLException {
        Connection c=datasource.getConnection();c.setAutoCommit(false);
        if(planta!=null) try(var st=c.prepareStatement("select set_config('app.current_planta_id',?,true)")) {st.setString(1,planta);st.execute();}
        return c;
    }
    @Test void dosTecnicosSimultaneosMismaFallaYCierreConcurrente() throws Exception {
        String id=nuevaMaquina();var a=tecnico();var b=tecnico();
        var tickets=simultaneas(a,b,()->bloqueos.crearBloqueo(dto(id)),()->bloqueos.crearBloqueo(dto(id)));
        assertNotEquals(tickets.get(0).id(),tickets.get(1).id());
        assertEquals(tickets.get(0).fallaId(),tickets.get(1).fallaId());
        var m=maquinas.obtenerPorId(id);assertEquals(2,m.bloqueosActivos());assertEquals(1,m.fallasCorrectivas());assertEquals(100.0,m.mtbf());
        simultaneas(a,b,()->bloqueos.liberarBloqueo(tickets.get(0).id()),()->bloqueos.liberarBloqueo(tickets.get(1).id()));
        assertFalse(maquinas.obtenerPorId(id).bloqueada());assertEquals(0,maquinas.obtenerPorId(id).bloqueosActivos());
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            assertEquals(4,scalar(c,"select count(*) from eventos_loto where bloqueo_id in ("+tickets.get(0).id()+","+tickets.get(1).id()+")"));
            assertEquals(1,scalar(c,"select count(*) from fallas where maquina_id='"+id+"'"));
            c.rollback();
        }
    }
    @Test void dobleLiberacionConcurrenteNoDescuentaDosVeces() throws Exception {
        String id=nuevaMaquina();var a=tecnico();as(a);var ticket=bloqueos.crearBloqueo(dto(id));
        Supplier<String> release=()->{try {bloqueos.liberarBloqueo(ticket.id());return "ok";}catch(BusinessConflictException e){return "conflict";}};
        var results=simultaneas(a,a,release,release);
        assertEquals(1,Collections.frequency(results,"ok"));assertEquals(1,Collections.frequency(results,"conflict"));
        assertEquals(0,maquinas.obtenerPorId(id).bloqueosActivos());
    }
    @Test void rlsRealDeniegaOtraPlantaYSinContextoYNoFiltraPool() throws Exception {
        try(var c=conexion(null)) {
            assertEquals(0,scalar(c,"select count(*) from maquinas"));
            assertEquals(0,scalar(c,"select count(*) from bloqueos_loto"));
            assertEquals(0,scalar(c,"select count(*) from pg_roles where rolname=current_user and (rolsuper or rolbypassrls)"));
            assertEquals(0,scalar(c,"select count(*) from pg_tables where schemaname='public' and tableowner=current_user"));
            c.rollback();
        }
        try(var c=conexion("PLANTA-OTRA")) {
            assertEquals(0,scalar(c,"select count(*) from maquinas"));
            assertEquals(0,scalar(c,"select count(*) from bloqueos_loto"));c.rollback();
        }
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            assertTrue(scalar(c,"select count(*) from maquinas")>=6);c.commit();
            assertEquals(0,scalar(c,"select count(*) from maquinas"));c.rollback();
        }
        try(var c=conexion("PLANTA-OTRA")) {
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("insert into maquinas(id,nombre,area,icono,planta_id) values('EQ-OTR-99','Prueba','Área','X','PLANTA-SAN-JOAQUIN')"));
            assertEquals("42501",ex.getSQLState());c.rollback();
        }
    }
    @Test void historialProtegidoPorBaseYNotasCifradas() throws Exception {
        String id=nuevaMaquina();var b=bloqueos.crearBloqueo(dto(id));
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            try(var st=c.prepareStatement("select notas_auditoria_cifradas from bloqueos_loto where id=?")) {
                st.setLong(1,b.id());try(var rs=st.executeQuery()) {assertTrue(rs.next());assertNotEquals("nota PostgreSQL",rs.getString(1));}
            }
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("delete from bloqueos_loto where id="+b.id()));
            assertEquals("42501",ex.getSQLState());c.rollback();
        }
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("update bloqueos_loto set motivo='cambio no autorizado' where id="+b.id()));
            assertEquals("23514",ex.getSQLState());c.rollback();
        }
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("update eventos_loto set accion='LIBERADO' where bloqueo_id="+b.id()));
            assertEquals("42501",ex.getSQLState());c.rollback();
        }
    }
    @Test void bajaLogicaConservaTicketsYSusReferencias() throws Exception {
        String id=nuevaMaquina();var b=bloqueos.crearBloqueo(dto(id));bloqueos.liberarBloqueo(b.id());maquinas.eliminarMaquina(id);
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            assertEquals(1,scalar(c,"select count(*) from maquinas where id='"+id+"' and activa=false"));
            assertEquals(1,scalar(c,"select count(*) from bloqueos_loto where id="+b.id()));c.rollback();
        }
    }
    @Test void falloDeDominioRevierteFallaYTicketEnLaTransaccion() throws Exception {
        String id="EQ-IT-"+String.format("%02d",sequence.incrementAndGet());
        maquinas.crearMaquina(new MaquinaCreateDTO(id,"Prueba rollback","Área pruebas","X",100.0,Integer.MAX_VALUE,null));
        assertThrows(IllegalStateException.class,()->bloqueos.crearBloqueo(dto(id)));
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            assertEquals(0,scalar(c,"select count(*) from fallas where maquina_id='"+id+"'"));
            assertEquals(0,scalar(c,"select count(*) from bloqueos_loto where maquina_id='"+id+"'"));c.rollback();
        }
    }
    @Test void restriccionesFisicasYReferenciasSeVerificanEnPostgres() throws Exception {
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("insert into bloqueos_loto(maquina_id,tipo_intervencion,motivo,nombre_tecnico,confirmacion_candado_ds594,tecnico_id) values('EQ-PR-04','PREVENTIVA','Motivo prueba','Prueba',true,9223372036854775807)"));
            assertEquals("23503",ex.getSQLState());c.rollback();
        }
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("insert into maquinas(id,nombre,area,icono,horas_operativas) values('EQ-NEG-99','Prueba','Área','X',-1)"));
            assertEquals("23514",ex.getSQLState());c.rollback();
        }
        try(var c=conexion("PLANTA-SAN-JOAQUIN")) {
            var ex=assertThrows(SQLException.class,()->c.createStatement().executeUpdate("update maquinas set bloqueada=true,bloqueos_activos=0 where id='EQ-PR-04'"));
            assertEquals("23514",ex.getSQLState());c.rollback();
        }
    }
}
