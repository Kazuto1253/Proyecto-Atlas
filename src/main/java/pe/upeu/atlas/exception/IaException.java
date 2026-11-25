// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Fallo del modelo local (Ollama), de los embeddings o del OCR. */
public class IaException extends RuntimeException {

    public IaException(String mensaje) { // Y
        super(mensaje);
    }

    public IaException(String mensaje, Throwable causa) { // K
        super(mensaje, causa);
    }
}
