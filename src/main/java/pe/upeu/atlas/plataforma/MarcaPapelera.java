package pe.upeu.atlas.plataforma;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.MarcaPlataformaException;

import java.awt.Desktop;
import java.nio.file.Path;

@Component
public class MarcaPapelera {

    public void sanchezenviar(Path marcaruta) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            throw new MarcaPlataformaException("Este equipo no permite usar la papelera de reciclaje desde Atlas.");
        }
        if (!Desktop.getDesktop().moveToTrash(marcaruta.toFile())) {
            throw new MarcaPlataformaException("No se pudo enviar a la papelera: " + marcaruta.getFileName());
        }
    }
}
