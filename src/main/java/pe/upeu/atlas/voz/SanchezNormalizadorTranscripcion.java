package pe.upeu.atlas.voz;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SanchezNormalizadorTranscripcion {

    private static final Map<String, String> INTERROGATIVAS = Map.ofEntries(
            Map.entry("que", "Qué"), Map.entry("cual", "Cuál"), Map.entry("cuales", "Cuáles"),
            Map.entry("como", "Cómo"), Map.entry("cuando", "Cuándo"), Map.entry("donde", "Dónde"),
            Map.entry("quien", "Quién"), Map.entry("quienes", "Quiénes"), Map.entry("cuanto", "Cuánto"),
            Map.entry("cuanta", "Cuánta"), Map.entry("cuantos", "Cuántos"), Map.entry("cuantas", "Cuántas"));

    private static final Set<String> TRAS_COMO = Set.of("esta", "estas", "estan", "va", "vas");

    private SanchezNormalizadorTranscripcion() { }

    public static String sanchezformatear(String marcatexto) {
        if (marcatexto == null) {
            return "";
        }
        String marcat = marcatexto.strip().replaceAll("\\s+", " ");
        if (marcat.isEmpty()) {
            return "";
        }
        String[] marcapalabras = marcat.split(" ");
        String marcaprimera = marcapalabras[0].toLowerCase(Locale.ROOT);

        boolean marcapregunta = INTERROGATIVAS.containsKey(marcaprimera)
                || (marcapalabras.length > 2 && (marcaprimera.equals("por") || marcaprimera.equals("para")) && marcapalabras[1].equals("que"));
        if (marcapregunta) {
            if (INTERROGATIVAS.containsKey(marcaprimera)) {
                marcapalabras[0] = INTERROGATIVAS.get(marcaprimera);
                if (marcaprimera.equals("como") && marcapalabras.length > 1 && TRAS_COMO.contains(marcapalabras[1])) {
                    marcapalabras[1] = marcapalabras[1].equals("va") ? "va" : sanchezacentuar(marcapalabras[1]);
                }
            } else {
                marcapalabras[0] = Character.toUpperCase(marcapalabras[0].charAt(0)) + marcapalabras[0].substring(1);
                marcapalabras[1] = "qué";
            }
            return "¿" + String.join(" ", marcapalabras) + "?";
        }
        marcapalabras[0] = Character.toUpperCase(marcapalabras[0].charAt(0)) + marcapalabras[0].substring(1);
        return String.join(" ", marcapalabras);
    }

    private static String sanchezacentuar(String marcaverbo) {
        return switch (marcaverbo) {
            case "esta" -> "está";
            case "estas" -> "estás";
            case "estan" -> "están";
            default -> marcaverbo;
        };
    }
}
