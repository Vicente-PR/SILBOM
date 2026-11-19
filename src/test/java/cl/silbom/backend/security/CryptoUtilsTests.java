package cl.silbom.backend.security;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTests {
    String key=Base64.getEncoder().encodeToString(new byte[32]); // clave pública solo de prueba
    @Test void roundTripConIVDistinto() {
        var crypto=new CryptoUtils(key);var a=crypto.encrypt("nota");var b=crypto.encrypt("nota");
        assertNotEquals(a,b);assertEquals("nota",crypto.decrypt(a));assertEquals("nota",crypto.decrypt(b));
    }
    @Test void detectaManipulacionSinExponerDato() {
        var crypto=new CryptoUtils(key);var bytes=Base64.getDecoder().decode(crypto.encrypt("confidencial"));
        bytes[bytes.length-1]^=1;
        assertEquals("[nota no disponible: fallo de integridad]",crypto.decrypt(Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void rechazaClaveInvalida() {
        assertThrows(IllegalStateException.class,()->new CryptoUtils("not-base64"));
        assertThrows(IllegalStateException.class,()->new CryptoUtils(Base64.getEncoder().encodeToString(new byte[16])));
    }
    @Test void normalizacionNoAceptaTextoVacioNiControles() {
        assertThrows(IllegalArgumentException.class,()->InputSanitizer.required("<b></b>",3,100));
        assertEquals("a b",InputSanitizer.sanitize("a\u0000b"));
        assertEquals("texto",InputSanitizer.sanitize("<script>alert(1)</script>texto"));
    }
}
