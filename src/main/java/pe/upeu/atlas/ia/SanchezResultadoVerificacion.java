package pe.upeu.atlas.ia;

public record SanchezResultadoVerificacion(
        String sanchezcategoria,
        String nombre,
        MarcaEstado marcaestado,
        String detalle,
        String marcasolucion) {

    public enum MarcaEstado {
        OK,
        ADVERTENCIA,
        FALTA
    }

    static SanchezResultadoVerificacion marcaok(String sanchezcategoria, String sancheznombre, String sanchezdetalle) {
        return new SanchezResultadoVerificacion(sanchezcategoria, sancheznombre, MarcaEstado.OK, sanchezdetalle, "");
    }

    static SanchezResultadoVerificacion marcaadvertencia(String sanchezcategoria, String sancheznombre, String sanchezdetalle, String marcasolucion) {
        return new SanchezResultadoVerificacion(sanchezcategoria, sancheznombre, MarcaEstado.ADVERTENCIA, sanchezdetalle, marcasolucion);
    }

    static SanchezResultadoVerificacion marcafalta(String sanchezcategoria, String sancheznombre, String sanchezdetalle, String marcasolucion) {
        return new SanchezResultadoVerificacion(sanchezcategoria, sancheznombre, MarcaEstado.FALTA, sanchezdetalle, marcasolucion);
    }
}
