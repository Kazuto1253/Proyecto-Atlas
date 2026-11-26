// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

public class SanchezNegocioException extends RuntimeException { // Y

    public SanchezNegocioException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public SanchezNegocioException(String sanchezmensaje, Throwable sanchezcausa) { // K
        super(sanchezmensaje, sanchezcausa);
    } // Y
}
