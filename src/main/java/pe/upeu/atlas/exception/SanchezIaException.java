package pe.upeu.atlas.exception;

public class SanchezIaException extends RuntimeException {

    public SanchezIaException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public SanchezIaException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
