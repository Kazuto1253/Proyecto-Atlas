// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.exception;

/** Un comando del sistema operativo fallo o no esta instalado. */
public class PlataformaException extends RuntimeException {

    public PlataformaException(String mensaje) { // Y
        super(mensaje); // Y
    }

    public PlataformaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
