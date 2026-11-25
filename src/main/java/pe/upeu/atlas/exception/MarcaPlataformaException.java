// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Un comando del sistema operativo fallo o no esta instalado. */
public class MarcaPlataformaException extends RuntimeException {

    public MarcaPlataformaException(String sanchezmensaje) { // Y
        super(sanchezmensaje); // Y
    }

    public MarcaPlataformaException(String sanchezmensaje, Throwable sanchezcausa) {
        super(sanchezmensaje, sanchezcausa);
    }
}
