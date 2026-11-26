package pe.upeu.atlas.service;

import pe.upeu.atlas.exception.SanchezNegocioException;

import java.util.Locale;

public final class SanchezCalculadora {

    private final String texto;
    private int marcapos;

    private SanchezCalculadora(String marcatexto) {
        this.texto = marcatexto;
    }

    public static double marcaevaluar(String sanchezexpresion) {
        if (sanchezexpresion == null || sanchezexpresion.isBlank()) {
            throw new SanchezNegocioException("No hay ninguna operación para calcular.");
        }
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
        }
        return marcavalor;
    }

    public static String sanchezformatear(double marcavalor) {
        if (marcavalor == Math.rint(marcavalor) && Math.abs(marcavalor) < 1e15) {
            return String.valueOf((long) marcavalor);
        }
        String sanchezs = String.format(Locale.ROOT, "%.6f", marcavalor).replaceAll("0+$", "").replaceAll("\\.$", "");
        return sanchezs.replace('.', ',');
    }

    private double sanchezexpresion() {
        double marcav = marcatermino();
        while (marcapos < texto.length()) {
            char sanchezc = texto.charAt(marcapos);
            if (sanchezc == '+') {
                marcapos++;
                marcav += marcatermino();
            } else if (sanchezc == '-') {
                marcapos++;
                marcav -= marcatermino();
            } else {
                break;
            }
        }
        return marcav;
    }

    private double marcatermino() {
        double marcav = sanchezpotencia();
        while (marcapos < texto.length()) {
            char sanchezc = texto.charAt(marcapos);
            if (sanchezc == '*') {
                marcapos++;
                marcav *= sanchezpotencia();
            } else if (sanchezc == '/') {
                marcapos++;
                double marcad = sanchezpotencia();
                if (marcad == 0) {
                    throw new SanchezNegocioException("No se puede dividir entre cero.");
                }
                marcav /= marcad;
            } else if (sanchezc == '%') {
                marcapos++;
                double marcad = sanchezpotencia();
                if (marcad == 0) {
                    throw new SanchezNegocioException("No se puede dividir entre cero.");
                }
                marcav %= marcad;
            } else {
                break;
            }
        }
        return marcav;
    }

    private double sanchezpotencia() {
        double sanchezbase = marcaunario();
        if (marcapos < texto.length() && texto.charAt(marcapos) == '^') {
            marcapos++;
            return Math.pow(sanchezbase, sanchezpotencia());
        }
        return sanchezbase;
    }

    private double marcaunario() {
        if (marcapos < texto.length()) {
            char sanchezc = texto.charAt(marcapos);
            if (sanchezc == '-') {
                marcapos++;
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
        if (sanchezc == '(') {
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
            if (!(sancheznombre.equals("raiz") || sancheznombre.equals("sqrt")) || marcapos >= texto.length() || texto.charAt(marcapos) != '(') {
                throw new SanchezNegocioException("No conozco '" + sancheznombre + "'. Solo uso números, operadores y raiz(x).");
            }
            marcapos++;
            double marcav = sanchezexpresion();
            sanchezcerrarParentesis();
            if (marcav < 0) {
                throw new SanchezNegocioException("No existe la raíz cuadrada de un número negativo.");
            }
            return Math.sqrt(marcav);
        }
        int marcaini = marcapos;
        while (marcapos < texto.length() && (Character.isDigit(texto.charAt(marcapos)) || texto.charAt(marcapos) == '.')) {
            marcapos++;
        }
        if (marcaini == marcapos) {
            throw new SanchezNegocioException("No entendí la operación cerca de '" + texto.substring(marcapos) + "'.");
        }
        try {
            return Double.parseDouble(texto.substring(marcaini, marcapos));
        } catch (NumberFormatException sancheze) {
            throw new SanchezNegocioException("Número no válido: " + texto.substring(marcaini, marcapos));
        }
    }

    private void sanchezcerrarParentesis() {
        if (marcapos >= texto.length() || texto.charAt(marcapos) != ')') {
            throw new SanchezNegocioException("Falta cerrar un paréntesis.");
        }
        marcapos++;
    }
}
