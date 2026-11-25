// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.PlataformaException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Único punto por donde Atlas lanza programas externos. Nunca recibe texto libre del usuario o
 * del modelo: los comandos son fijos y solo las rutas (ya validadas) cambian.
 */
@Component
public class Lanzador {

    private final SistemaOperativo so = SistemaOperativo.desde(System.getProperty("os.name"));

    public SistemaOperativo sistema() { // K
        return so;
    } // K

    /** Abre una carpeta o archivo con el programa predeterminado del sistema. */
    public void abrir(Path ruta) { // K
        switch (so) {
            case WINDOWS -> ejecutar("explorer.exe", ruta.toString());
            case MAC -> ejecutar("open", ruta.toString());
            default -> ejecutar("xdg-open", ruta.toString()); // Y
        }
    } // Y

    /** Ejecuta un comando fijo sin esperar su fin. */
    public void ejecutar(String... comando) {
        try {
            new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new PlataformaException("No se pudo ejecutar " + comando[0] + ": " + e.getMessage(), e);
        }
    } // K

    /** Ejecuta un comando fijo y espera hasta {@code segundos}. */
    public void ejecutarYEsperar(int segundos, String... comando) {
        try {
            Process p = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(segundos, java.util.concurrent.TimeUnit.SECONDS)) { // K
                p.destroyForcibly();
                throw new PlataformaException("El comando " + comando[0] + " tardó demasiado.");
            } // K
        } catch (IOException e) { // Y
            throw new PlataformaException("No se pudo ejecutar " + comando[0] + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // K
            throw new PlataformaException("Se interrumpió " + comando[0], e);
        }
    }
}
