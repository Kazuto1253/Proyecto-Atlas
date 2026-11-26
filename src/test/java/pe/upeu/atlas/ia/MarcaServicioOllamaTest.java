// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarcaServicioOllamaTest {

    @TempDir Path carpeta;

    @Test
    void prefiereElOllamaIncluidoEnElProyecto() throws IOException {
        Path delProyecto = crear(carpeta.resolve("proyecto/herramientas/ollama/ollama.exe"));
        crear(carpeta.resolve("local/Programs/Ollama/ollama.exe"));

        assertThat(MarcaServicioOllama.marcabuscarEjecutable(carpeta.resolve("proyecto"),
                Map.of("LOCALAPPDATA", carpeta.resolve("local").toString()))).contains(delProyecto);
    }

    @Test
    void usaElInstaladoSiElProyectoNoLoTrae() throws IOException {
        Path instalado = crear(carpeta.resolve("local/Programs/Ollama/ollama.exe"));

        assertThat(MarcaServicioOllama.marcabuscarEjecutable(carpeta.resolve("proyecto"),
                Map.of("LOCALAPPDATA", carpeta.resolve("local").toString()))).contains(instalado);
    }

    @Test
    void noEncuentraNadaSiNoEstaInstalado() {
        assertThat(MarcaServicioOllama.marcabuscarEjecutable(carpeta, Map.of("Path", ""))).isEmpty();
    }

    @Test
    void soloArrancaOllamaEnEsteEquipo() {
        assertThat(MarcaServicioOllama.marcaesLocal(URI.create("http://localhost:11434"))).isTrue();
        assertThat(MarcaServicioOllama.marcaesLocal(URI.create("http://127.0.0.1:11434"))).isTrue();
        assertThat(MarcaServicioOllama.marcaesLocal(URI.create("http://192.168.1.20:11434"))).isFalse();
    }

    private static Path crear(Path archivo) throws IOException {
        Files.createDirectories(archivo.getParent());
        return Files.createFile(archivo);
    }
}
