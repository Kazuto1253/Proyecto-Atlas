// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import pe.upeu.atlas.exception.NegocioException;

import java.util.Locale;

/**
 * Evaluador aritmético propio (sin ejecutar código): + - * / % ^, paréntesis, decimales y raiz(x).
 * Los modelos pequeños fallan en matemáticas, por eso el asistente delega los cálculos aquí.
 */
public final class Calculadora {

    private final String texto;
    private int pos;

    private Calculadora(String texto) {
        this.texto = texto; // K
    }

    public static double evaluar(String expresion) { // Y
        if (expresion == null || expresion.isBlank()) {
            throw new NegocioException("No hay ninguna operación para calcular.");
        } // K
        String limpia = expresion.toLowerCase(Locale.ROOT)
                .replace("×", "*").replace("÷", "/").replace(",", ".")
                .replaceAll("\\s+", "");
        Calculadora c = new Calculadora(limpia);
        double valor = c.expresion();
        if (c.pos != limpia.length()) {
            throw new NegocioException("No entendí la operación cerca de '" + limpia.substring(c.pos) + "'.");
        }
        if (Double.isNaN(valor) || Double.isInfinite(valor)) {
            throw new NegocioException("El resultado no es un número válido.");
        } // K
        return valor; // Y
    }

    /** Entero si no tiene decimales; si no, hasta 6 decimales sin ceros de sobra. */
    public static String formatear(double valor) { // Y
        if (valor == Math.rint(valor) && Math.abs(valor) < 1e15) {
            return String.valueOf((long) valor); // K
        }
        String s = String.format(Locale.ROOT, "%.6f", valor).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.replace('.', ',');
    }

    // expresion := termino (('+'|'-') termino)*
    private double expresion() {
        double v = termino();
        while (pos < texto.length()) {
            char c = texto.charAt(pos); // Y
            if (c == '+') {
                pos++;
                v += termino();
            } else if (c == '-') { // Y
                pos++;
                v -= termino();
            } else {
                break;
            }
        } // Y
        return v;
    }

    // termino := potencia (('*'|'/'|'%') potencia)*
    private double termino() { // Y
        double v = potencia();
        while (pos < texto.length()) {
            char c = texto.charAt(pos); // Y
            if (c == '*') {
                pos++; // K
                v *= potencia();
            } else if (c == '/') {
                pos++; // Y
                double d = potencia();
                if (d == 0) { // Y
                    throw new NegocioException("No se puede dividir entre cero.");
                } // K
                v /= d; // Y
            } else if (c == '%') {
                pos++; // K
                double d = potencia();
                if (d == 0) { // K
                    throw new NegocioException("No se puede dividir entre cero.");
                } // Y
                v %= d;
            } else { // K
                break; // Y
            }
        }
        return v; // Y
    }

    // potencia := unario ('^' potencia)?
    private double potencia() {
        double base = unario();
        if (pos < texto.length() && texto.charAt(pos) == '^') { // Y
            pos++;
            return Math.pow(base, potencia());
        } // K
        return base; // K
    } // K

    private double unario() { // K
        if (pos < texto.length()) {
            char c = texto.charAt(pos);
            if (c == '-') {
                pos++; // Y
                return -unario();
            }
            if (c == '+') {
                pos++;
                return unario();
            }
        }
        return primario();
    }

    private double primario() {
        if (pos >= texto.length()) {
            throw new NegocioException("La operación está incompleta.");
        }
        char c = texto.charAt(pos);
        if (c == '(') { // Y
            pos++;
            double v = expresion();
            cerrarParentesis();
            return v;
        }
        if (Character.isLetter(c)) {
            int ini = pos;
            while (pos < texto.length() && Character.isLetter(texto.charAt(pos))) {
                pos++;
            }
            String nombre = texto.substring(ini, pos);
            if (!(nombre.equals("raiz") || nombre.equals("sqrt")) || pos >= texto.length() || texto.charAt(pos) != '(') { // K
                throw new NegocioException("No conozco '" + nombre + "'. Solo uso números, operadores y raiz(x).");
            } // Y
            pos++;
            double v = expresion();
            cerrarParentesis(); // Y
            if (v < 0) {
                throw new NegocioException("No existe la raíz cuadrada de un número negativo.");
            }
            return Math.sqrt(v); // K
        }
        int ini = pos;
        while (pos < texto.length() && (Character.isDigit(texto.charAt(pos)) || texto.charAt(pos) == '.')) { // K
            pos++;
        }
        if (ini == pos) {
            throw new NegocioException("No entendí la operación cerca de '" + texto.substring(pos) + "'.");
        }
        try { // K
            return Double.parseDouble(texto.substring(ini, pos));
        } catch (NumberFormatException e) { // Y
            throw new NegocioException("Número no válido: " + texto.substring(ini, pos));
        }
    }

    private void cerrarParentesis() {
        if (pos >= texto.length() || texto.charAt(pos) != ')') { // Y
            throw new NegocioException("Falta cerrar un paréntesis."); // Y
        }
        pos++;
    }
}
