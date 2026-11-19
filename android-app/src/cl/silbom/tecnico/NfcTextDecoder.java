package cl.silbom.tecnico;

import java.nio.charset.StandardCharsets;

/** Decodificación pura del payload de un registro NDEF RTD_TEXT (NFC Forum). */
public final class NfcTextDecoder {
    private NfcTextDecoder() {}

    public static String decodeText(byte[] payload) {
        if (payload == null || payload.length < 2) return null;
        int status = payload[0] & 0xFF;
        if ((status & 0x40) != 0) return null; // Bit reservado en RTD_TEXT.
        int languageLength = status & 0x3F;
        int textOffset = 1 + languageLength;
        if (textOffset >= payload.length) return null;
        boolean utf16 = (status & 0x80) != 0;
        return new String(payload, textOffset, payload.length - textOffset,
                utf16 ? StandardCharsets.UTF_16 : StandardCharsets.UTF_8).trim();
    }
}
