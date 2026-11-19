package cl.silbom.tecnico;

import java.nio.charset.StandardCharsets;

public final class NfcTextDecoderTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] payload(byte status, String language, byte[] text) {
        byte[] lang = language.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[1 + lang.length + text.length];
        result[0] = status;
        System.arraycopy(lang, 0, result, 1, lang.length);
        System.arraycopy(text, 0, result, 1 + lang.length, text.length);
        return result;
    }

    public static void main(String[] args) {
        check("EQ-COM-03".equals(NfcTextDecoder.decodeText(payload(
                (byte) 2, "en", "EQ-COM-03".getBytes(StandardCharsets.UTF_8)))), "UTF-8 en");
        check("EQ-ENV-01".equals(NfcTextDecoder.decodeText(payload(
                (byte) 2, "es", "EQ-ENV-01".getBytes(StandardCharsets.UTF_8)))), "UTF-8 es");
        check("EQ-EXT-02".equals(NfcTextDecoder.decodeText(payload(
                (byte) 0x82, "en", "EQ-EXT-02".getBytes(StandardCharsets.UTF_16)))), "UTF-16 en");
        check(NfcTextDecoder.decodeText(new byte[]{0x02, 'e'}) == null, "vacío");
        check(NfcTextDecoder.decodeText(null) == null, "null");
        check(NfcTextDecoder.decodeText(new byte[]{0x40, 'X'}) == null, "reserved bit");
        System.out.println("OK: NDEF RTD_TEXT UTF-8/UTF-16, en/es, vacío, inválido");
    }
}
