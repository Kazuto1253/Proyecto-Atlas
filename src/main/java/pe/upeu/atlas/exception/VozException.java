// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Fallo en la captura de audio, el reconocimiento o la sintesis de voz. */
public class VozException extends RuntimeException {

    public VozException(String mensaje) {
        super(mensaje);
    } // K

    public VozException(String mensaje, Throwable causa) {
        super(mensaje, causa); // K
    }
}
