// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Ruta fuera de las carpetas permitidas o accion no autorizada. */
public class PermisoDenegadoException extends RuntimeException {

    public PermisoDenegadoException(String mensaje) {
        super(mensaje);
    }

    public PermisoDenegadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
