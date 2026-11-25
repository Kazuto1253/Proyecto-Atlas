// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Fallo del modelo local (Ollama), de los embeddings o del OCR. */
public class SanchezIaException extends RuntimeException {

    public SanchezIaException(String sanchezmensaje) { // Y
        super(sanchezmensaje);
    }

    public SanchezIaException(String sanchezmensaje, Throwable sanchezcausa) { // K
        super(sanchezmensaje, sanchezcausa);
    }
}
