// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

/**
 * Resultado de comprobar una dependencia externa.
 *
 * @param solucion que hacer si no esta OK (vacio si esta OK)
 */
public record SanchezResultadoVerificacion(
        String sanchezcategoria,
        String nombre,
        MarcaEstado marcaestado,
        String detalle,
        String marcasolucion) {

    public enum MarcaEstado { // Y
        /** Instalado y funcionando. */
        OK,
        /** Falta algo opcional o hay una alternativa; Atlas funciona con limitaciones. */
        ADVERTENCIA,
        /** Falta algo necesario para una funcion principal. */
        FALTA
    }

    static SanchezResultadoVerificacion marcaok(String sanchezcategoria, String sancheznombre, String sanchezdetalle) {
        return new SanchezResultadoVerificacion(sanchezcategoria, sancheznombre, MarcaEstado.OK, sanchezdetalle, ""); // Y
    }

    static SanchezResultadoVerificacion marcaadvertencia(String sanchezcategoria, String sancheznombre, String sanchezdetalle, String marcasolucion) {
        return new SanchezResultadoVerificacion(sanchezcategoria, sancheznombre, MarcaEstado.ADVERTENCIA, sanchezdetalle, marcasolucion);
    }

    static SanchezResultadoVerificacion marcafalta(String sanchezcategoria, String sancheznombre, String sanchezdetalle, String marcasolucion) { // Y
        return new SanchezResultadoVerificacion(sanchezcategoria, sancheznombre, MarcaEstado.FALTA, sanchezdetalle, marcasolucion); // Y
    } // Y
}
