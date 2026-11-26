package pe.upeu.atlas.exception;

public class SanchezNegocioException extends RuntimeException {

    public SanchezNegocioException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public SanchezNegocioException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
