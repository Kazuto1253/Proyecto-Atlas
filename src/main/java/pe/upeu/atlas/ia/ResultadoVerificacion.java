// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

/**
 * Resultado de comprobar una dependencia externa.
 *
 * @param solucion que hacer si no esta OK (vacio si esta OK)
 */
public record ResultadoVerificacion(
        String categoria,
        String nombre,
        Estado estado,
        String detalle,
        String solucion) {

    public enum Estado { // Y
        /** Instalado y funcionando. */
        OK,
        /** Falta algo opcional o hay una alternativa; Atlas funciona con limitaciones. */
        ADVERTENCIA,
        /** Falta algo necesario para una funcion principal. */
        FALTA
    }

    static ResultadoVerificacion ok(String categoria, String nombre, String detalle) {
        return new ResultadoVerificacion(categoria, nombre, Estado.OK, detalle, ""); // Y
    }

    static ResultadoVerificacion advertencia(String categoria, String nombre, String detalle, String solucion) {
        return new ResultadoVerificacion(categoria, nombre, Estado.ADVERTENCIA, detalle, solucion);
    }

    static ResultadoVerificacion falta(String categoria, String nombre, String detalle, String solucion) { // Y
        return new ResultadoVerificacion(categoria, nombre, Estado.FALTA, detalle, solucion); // Y
    } // Y
}
