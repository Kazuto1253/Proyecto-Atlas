package pe.upeu.atlas.exception;

public class MarcaVozException extends RuntimeException {

    public MarcaVozException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public MarcaVozException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
