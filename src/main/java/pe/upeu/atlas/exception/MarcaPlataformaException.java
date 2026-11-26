package pe.upeu.atlas.exception;

public class MarcaPlataformaException extends RuntimeException {

    public MarcaPlataformaException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public MarcaPlataformaException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
