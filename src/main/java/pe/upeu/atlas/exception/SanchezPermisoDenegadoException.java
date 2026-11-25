// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Ruta fuera de las carpetas permitidas o accion no autorizada. */
public class SanchezPermisoDenegadoException extends RuntimeException {

    public SanchezPermisoDenegadoException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public SanchezPermisoDenegadoException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
