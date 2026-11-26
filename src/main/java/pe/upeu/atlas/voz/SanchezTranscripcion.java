// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

public record SanchezTranscripcion(String texto, double sanchezconfianza, boolean sanchezcomando, String marcalibre) {

    public static final SanchezTranscripcion VACIA = new SanchezTranscripcion("", 0, false, "");

    public boolean marcavacia() {
        return texto == null || texto.isBlank(); // K
    }
} // K
