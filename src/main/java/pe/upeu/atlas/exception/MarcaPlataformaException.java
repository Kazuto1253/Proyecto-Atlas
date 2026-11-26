// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

public class MarcaPlataformaException extends RuntimeException {

    public MarcaPlataformaException(String sanchezmensaje) { // Y
        super(sanchezmensaje); // Y
    }

    public MarcaPlataformaException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
