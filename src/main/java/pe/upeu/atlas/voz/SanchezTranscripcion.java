// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

/**
 * Resultado de reconocer una frase hablada.
 *
 * @param texto     texto reconocido (sin tildes ni signos: así lo entrega Vosk)
 * @param confianza de 0 a 1, promedio de las palabras
 * @param comando   {@code true} si coincidió con un comando conocido (vocabulario acotado, muy fiable)
 * @param libre     lo que entendió el reconocedor libre (para depuración)
 */
public record SanchezTranscripcion(String texto, double sanchezconfianza, boolean sanchezcomando, String marcalibre) {

    public static final SanchezTranscripcion VACIA = new SanchezTranscripcion("", 0, false, "");

    public boolean marcavacia() {
        return texto == null || texto.isBlank(); // K
    }
} // K
