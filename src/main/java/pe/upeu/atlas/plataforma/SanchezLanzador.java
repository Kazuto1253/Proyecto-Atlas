// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.MarcaPlataformaException;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class SanchezLanzador {

    private final SanchezSistemaOperativo marcaso = SanchezSistemaOperativo.sanchezdesde(System.getProperty("os.name"));

    public SanchezSistemaOperativo marcasistema() { // K
        return marcaso;
    } // K

    public void marcaabrir(Path marcaruta) { // K
        switch (marcaso) {
            case WINDOWS -> sanchezejecutar("explorer.exe", marcaruta.toString());
            case MAC -> sanchezejecutar("open", marcaruta.toString());
            default -> sanchezejecutar("xdg-open", marcaruta.toString()); // Y
        }
    } // Y

    public void sanchezejecutar(String... sanchezcomando) {
        try {
            new ProcessBuilder(sanchezcomando)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException sancheze) {
            throw new MarcaPlataformaException("No se pudo ejecutar " + sanchezcomando[0] + ": " + sancheze.getMessage(), sancheze);
        }
    } // K

    public void marcaejecutarYEsperar(int marcasegundos, String... sanchezcomando) {
        try {
            Process marcap = new ProcessBuilder(sanchezcomando)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!marcap.waitFor(marcasegundos, java.util.concurrent.TimeUnit.SECONDS)) { // K
                marcap.destroyForcibly();
                throw new MarcaPlataformaException("El comando " + sanchezcomando[0] + " tardó demasiado.");
            } // K
        } catch (IOException sancheze) { // Y
            throw new MarcaPlataformaException("No se pudo ejecutar " + sanchezcomando[0] + ": " + sancheze.getMessage(), sancheze);
        } catch (InterruptedException sancheze) {
            Thread.currentThread().interrupt(); // K
            throw new MarcaPlataformaException("Se interrumpió " + sanchezcomando[0], sancheze);
        }
    }
}
