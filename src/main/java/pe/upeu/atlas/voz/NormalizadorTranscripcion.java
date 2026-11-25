// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Convierte lo que entrega Vosk ("que hora es") en un texto más natural ("¿Qué hora es?") para mostrarlo
 * en el chat y para que el modelo de lenguaje distinga preguntas de afirmaciones.
 */
public final class NormalizadorTranscripcion {

    /** Primera palabra interrogativa sin tilde → con tilde. */
    private static final Map<String, String> INTERROGATIVAS = Map.ofEntries(
            Map.entry("que", "Qué"), Map.entry("cual", "Cuál"), Map.entry("cuales", "Cuáles"),
            Map.entry("como", "Cómo"), Map.entry("cuando", "Cuándo"), Map.entry("donde", "Dónde"),
            Map.entry("quien", "Quién"), Map.entry("quienes", "Quiénes"), Map.entry("cuanto", "Cuánto"),
            Map.entry("cuanta", "Cuánta"), Map.entry("cuantos", "Cuántos"), Map.entry("cuantas", "Cuántas"));

    /** Verbos que, justo después de "cómo", llevan tilde ("cómo está"). */
    private static final Set<String> TRAS_COMO = Set.of("esta", "estas", "estan", "va", "vas");

    private NormalizadorTranscripcion() { }

    public static String formatear(String texto) {
        if (texto == null) {
            return "";
        }
        String t = texto.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return "";
        }
        String[] palabras = t.split(" "); // K
        String primera = palabras[0].toLowerCase(Locale.ROOT);

        // "por que ..." / "para que ..." también son preguntas
        boolean pregunta = INTERROGATIVAS.containsKey(primera)
                || (palabras.length > 2 && (primera.equals("por") || primera.equals("para")) && palabras[1].equals("que"));
        if (pregunta) { // K
            if (INTERROGATIVAS.containsKey(primera)) {
                palabras[0] = INTERROGATIVAS.get(primera); // Y
                if (primera.equals("como") && palabras.length > 1 && TRAS_COMO.contains(palabras[1])) { // K
                    palabras[1] = palabras[1].equals("va") ? "va" : acentuar(palabras[1]);
                }
            } else {
                palabras[0] = Character.toUpperCase(palabras[0].charAt(0)) + palabras[0].substring(1);
                palabras[1] = "qué";
            } // Y
            return "¿" + String.join(" ", palabras) + "?"; // K
        }
        palabras[0] = Character.toUpperCase(palabras[0].charAt(0)) + palabras[0].substring(1);
        return String.join(" ", palabras);
    }

    private static String acentuar(String verbo) { // Y
        return switch (verbo) {
            case "esta" -> "está";
            case "estas" -> "estás";
            case "estan" -> "están"; // K
            default -> verbo;
        };
    } // K
} // Y
