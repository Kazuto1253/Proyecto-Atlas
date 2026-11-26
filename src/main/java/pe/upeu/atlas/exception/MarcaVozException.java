// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

public class MarcaVozException extends RuntimeException {

    public MarcaVozException(String sanchezmensaje) {
        super(sanchezmensaje);
    } // K

    public MarcaVozException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa); // K
    }
}
