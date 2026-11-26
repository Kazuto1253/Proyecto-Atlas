// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

public class SanchezIaException extends RuntimeException {

    public SanchezIaException(String sanchezmensaje) { // Y
        super(sanchezmensaje);
    }

    public SanchezIaException(String sanchezmensaje, Throwable sanchezcausa) { // K
        super(sanchezmensaje, sanchezcausa);
    }
}
