package pe.upeu.atlas.exception;

public class SanchezPermisoDenegadoException extends RuntimeException {

    public SanchezPermisoDenegadoException(String sanchezmensaje) {
        super(sanchezmensaje);
    }

    public SanchezPermisoDenegadoException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
