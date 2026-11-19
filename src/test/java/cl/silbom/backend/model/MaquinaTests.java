package cl.silbom.backend.model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MaquinaTests {
    Maquina maquina(double horas,int fallas) { return new Maquina("EQ-TEST-01","Prueba","Área","X",horas,fallas,"PLANTA-SAN-JOAQUIN"); }
    @Test void sinFallasNoCalculableInclusoConCeroHoras() {
        assertNull(maquina(100,0).getMtbf()); assertNull(maquina(0,0).getMtbf());
    }
    @Test void calculaYRedondea() { assertEquals(233.8,maquina(701.5,3).getMtbf()); }
    @Test void ceroHorasConFallasEsCero() { assertEquals(0.0,maquina(0,2).getMtbf()); }
    @Test void preventivaNoAumentaFallas() {
        var m=maquina(100,0); m.registrarEntradaBloqueo(false);
        assertEquals(0,m.getFallasCorrectivas()); assertNull(m.getMtbf()); assertTrue(m.getBloqueada());
    }
    @Test void unaFallaConVariosTecnicos() {
        var m=maquina(100,0); m.registrarEntradaBloqueo(true); m.registrarEntradaBloqueo(false);
        assertEquals(1,m.getFallasCorrectivas()); assertEquals(2,m.getBloqueosActivos()); assertEquals(100.0,m.getMtbf());
        m.registrarSalidaBloqueo(); assertTrue(m.getBloqueada());
        m.registrarSalidaBloqueo(); assertFalse(m.getBloqueada());
        assertThrows(IllegalStateException.class,m::registrarSalidaBloqueo);
    }
    @Test void fallasDistintasSeCuentan() {
        var m=maquina(100,0); m.registrarEntradaBloqueo(true); m.registrarEntradaBloqueo(true);
        assertEquals(2,m.getFallasCorrectivas()); assertEquals(50.0,m.getMtbf());
    }
    @Test void noAvanzaHorasPorTiempoDeReloj() {
        var m=maquina(100,1); m.registrarEntradaBloqueo(false);m.registrarSalidaBloqueo();
        assertEquals(100.0,m.getHorasOperativas());
    }
    @ParameterizedTest @ValueSource(doubles={-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1e20})
    void rechazaHorasInvalidas(double horas) { assertThrows(IllegalArgumentException.class,()->maquina(horas,1)); }
    @Test void rechazaConteosNegativos() { assertThrows(IllegalArgumentException.class,()->maquina(1,-1)); }
    @Test void noDesbordaContadores() {
        var m=maquina(1,Integer.MAX_VALUE);assertThrows(IllegalStateException.class,()->m.registrarEntradaBloqueo(true));
        assertEquals(0,m.getBloqueosActivos());
    }
}
