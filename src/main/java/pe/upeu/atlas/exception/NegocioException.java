// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Violacion de una regla de negocio; el mensaje se muestra o se dice al usuario. */
public class NegocioException extends RuntimeException { // Y

    public NegocioException(String mensaje) {
        super(mensaje);
    }

    public NegocioException(String mensaje, Throwable causa) { // K
        super(mensaje, causa);
    } // Y
}
