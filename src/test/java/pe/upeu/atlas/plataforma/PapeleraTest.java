// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import org.junit.jupiter.api.Test;
import pe.upeu.atlas.exception.PlataformaException;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PapeleraTest {

    @Test
    void enviaUnArchivoRealALaPapeleraSinBorrarloDeFormaDefinitiva() throws Exception {
        assumeTrue(!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH), "Sin papelera disponible");
        Path archivo = Files.createTempFile("atlas-papelera-", ".txt");
        Files.writeString(archivo, "prueba");

        new Papelera().enviar(archivo);

        assertThat(archivo).doesNotExist();
    }

    @Test
    void siElArchivoNoExisteDaUnErrorClaro() {
        assumeTrue(!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH), "Sin papelera disponible");
        assertThatThrownBy(() -> new Papelera().enviar(Path.of("D:\\no-existe-atlas-xyz\\a.txt")))
                .isInstanceOf(RuntimeException.class);
    }
}
