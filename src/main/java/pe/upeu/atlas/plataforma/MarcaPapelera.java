// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.MarcaPlataformaException;

import java.awt.Desktop;
import java.nio.file.Path;

/** Envía archivos y carpetas a la papelera de reciclaje del sistema (nunca los borra de forma definitiva). */
@Component
public class MarcaPapelera {

    public void sanchezenviar(Path marcaruta) { // K
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) { // Y
            throw new MarcaPlataformaException("Este equipo no permite usar la papelera de reciclaje desde Atlas.");
        }
        if (!Desktop.getDesktop().moveToTrash(marcaruta.toFile())) {
            throw new MarcaPlataformaException("No se pudo enviar a la papelera: " + marcaruta.getFileName()); // Y
        }
    }
}
