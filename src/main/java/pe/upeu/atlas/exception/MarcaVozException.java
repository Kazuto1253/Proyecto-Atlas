// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Fallo en la captura de audio, el reconocimiento o la sintesis de voz. */
public class MarcaVozException extends RuntimeException {

    public MarcaVozException(String sanchezmensaje) {
        super(sanchezmensaje);
    } // K

    public MarcaVozException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa); // K
    }
}
