// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import pe.upeu.atlas.exception.SanchezNegocioException;

import java.util.Locale;

public final class SanchezCalculadora {

    private final String texto;
    private int marcapos;

    private SanchezCalculadora(String marcatexto) {
        this.texto = marcatexto; // K
    }

    public static double marcaevaluar(String sanchezexpresion) { // Y
        if (sanchezexpresion == null || sanchezexpresion.isBlank()) {
            throw new SanchezNegocioException("No hay ninguna operación para calcular.");
        } // K
        String marcalimpia = sanchezexpresion.toLowerCase(Locale.ROOT)
                .replace("×", "*").replace("÷", "/").replace(",", ".")
                .replaceAll("\\s+", "");
        SanchezCalculadora sanchezc = new SanchezCalculadora(marcalimpia);
        double marcavalor = sanchezc.sanchezexpresion();
        if (sanchezc.marcapos != marcalimpia.length()) {
            throw new SanchezNegocioException("No entendí la operación cerca de '" + marcalimpia.substring(sanchezc.marcapos) + "'.");
        }
        if (Double.isNaN(marcavalor) || Double.isInfinite(marcavalor)) {
            throw new SanchezNegocioException("El resultado no es un número válido.");
        } // K
        return marcavalor; // Y
    }

    public static String sanchezformatear(double marcavalor) { // Y
        if (marcavalor == Math.rint(marcavalor) && Math.abs(marcavalor) < 1e15) {
            return String.valueOf((long) marcavalor); // K
        }
        String sanchezs = String.format(Locale.ROOT, "%.6f", marcavalor).replaceAll("0+$", "").replaceAll("\\.$", "");
        return sanchezs.replace('.', ',');
    }

    private double sanchezexpresion() {
        double marcav = marcatermino();
        while (marcapos < texto.length()) {
            char sanchezc = texto.charAt(marcapos); // Y
            if (sanchezc == '+') {
                marcapos++;
                marcav += marcatermino();
            } else if (sanchezc == '-') { // Y
                marcapos++;
                marcav -= marcatermino();
            } else {
                break;
            }
        } // Y
        return marcav;
    }

    private double marcatermino() { // Y
        double marcav = sanchezpotencia();
        while (marcapos < texto.length()) {
            char sanchezc = texto.charAt(marcapos); // Y
            if (sanchezc == '*') {
                marcapos++; // K
                marcav *= sanchezpotencia();
            } else if (sanchezc == '/') {
                marcapos++; // Y
                double marcad = sanchezpotencia();
                if (marcad == 0) { // Y
                    throw new SanchezNegocioException("No se puede dividir entre cero.");
                } // K
                marcav /= marcad; // Y
            } else if (sanchezc == '%') {
                marcapos++; // K
                double marcad = sanchezpotencia();
                if (marcad == 0) { // K
                    throw new SanchezNegocioException("No se puede dividir entre cero.");
                } // Y
                marcav %= marcad;
            } else { // K
                break; // Y
            }
        }
        return marcav; // Y
    }

    private double sanchezpotencia() {
        double sanchezbase = marcaunario();
        if (marcapos < texto.length() && texto.charAt(marcapos) == '^') { // Y
            marcapos++;
            return Math.pow(sanchezbase, sanchezpotencia());
        } // K
        return sanchezbase; // K
    } // K

    private double marcaunario() { // K
        if (marcapos < texto.length()) {
            char sanchezc = texto.charAt(marcapos);
            if (sanchezc == '-') {
                marcapos++; // Y
                return -marcaunario();
            }
            if (sanchezc == '+') {
                marcapos++;
                return marcaunario();
            }
        }
        return sanchezprimario();
    }

    private double sanchezprimario() {
        if (marcapos >= texto.length()) {
            throw new SanchezNegocioException("La operación está incompleta.");
        }
        char sanchezc = texto.charAt(marcapos);
        if (sanchezc == '(') { // Y
            marcapos++;
            double marcav = sanchezexpresion();
            sanchezcerrarParentesis();
            return marcav;
        }
        if (Character.isLetter(sanchezc)) {
            int marcaini = marcapos;
            while (marcapos < texto.length() && Character.isLetter(texto.charAt(marcapos))) {
                marcapos++;
            }
            String sancheznombre = texto.substring(marcaini, marcapos);
            if (!(sancheznombre.equals("raiz") || sancheznombre.equals("sqrt")) || marcapos >= texto.length() || texto.charAt(marcapos) != '(') { // K
                throw new SanchezNegocioException("No conozco '" + sancheznombre + "'. Solo uso números, operadores y raiz(x).");
            } // Y
            marcapos++;
            double marcav = sanchezexpresion();
            sanchezcerrarParentesis(); // Y
            if (marcav < 0) {
                throw new SanchezNegocioException("No existe la raíz cuadrada de un número negativo.");
            }
            return Math.sqrt(marcav); // K
        }
        int marcaini = marcapos;
        while (marcapos < texto.length() && (Character.isDigit(texto.charAt(marcapos)) || texto.charAt(marcapos) == '.')) { // K
            marcapos++;
        }
        if (marcaini == marcapos) {
            throw new SanchezNegocioException("No entendí la operación cerca de '" + texto.substring(marcapos) + "'.");
        }
        try { // K
            return Double.parseDouble(texto.substring(marcaini, marcapos));
        } catch (NumberFormatException sancheze) { // Y
            throw new SanchezNegocioException("Número no válido: " + texto.substring(marcaini, marcapos));
        }
    }

    private void sanchezcerrarParentesis() {
        if (marcapos >= texto.length() || texto.charAt(marcapos) != ')') { // Y
            throw new SanchezNegocioException("Falta cerrar un paréntesis."); // Y
        }
        marcapos++;
    }
}
