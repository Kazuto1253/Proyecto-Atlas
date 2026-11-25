// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.ia.ResultadoVerificacion.Estado;
import pe.upeu.atlas.plataforma.DetectorEntorno;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin Ollama real: un HttpServer del JDK simula /api/tags. */
class VerificadorDependenciasTest {

    @TempDir
    Path base;

    private HttpServer ollamaFalso;
    private PropiedadesAtlas propiedades;

    @BeforeEach
    void preparar() throws IOException {
        ollamaFalso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollamaFalso.createContext("/api/tags", ex -> {
            byte[] cuerpo = """
                    {"models":[{"name":"qwen2.5:3b","size":1},{"name":"nomic-embed-text:latest","size":2}]}
                    """.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        ollamaFalso.start();

        propiedades = new PropiedadesAtlas();
        propiedades.setDirectorioBase(base);
        propiedades.setDirectorioModelos(base.resolve("modelos"));
        propiedades.setCarpetaCapturas(base.resolve("capturas"));
        propiedades.getLlm().setUrl("http://127.0.0.1:" + ollamaFalso.getAddress().getPort());
        propiedades.getVoz().setModeloVosk(base.resolve("modelos/vosk/modelo"));
        propiedades.getVoz().setEjecutablePiper(base.resolve("modelos/piper/piper"));
        propiedades.getVoz().setVozPiper(base.resolve("modelos/voces/voz.onnx"));
        propiedades.getOcr().setDirectorioTessdata(base.resolve("modelos/tessdata"));
    }

    @AfterEach
    void cerrar() {
        ollamaFalso.stop(0);
    }

    private ResultadoVerificacion buscar(List<ResultadoVerificacion> r, String nombre) {
        return r.stream().filter(x -> x.nombre().startsWith(nombre)).findFirst().orElseThrow();
    }

    @Test
    void reportaOllamaYModelosInstalados() {
        var r = new VerificadorDependencias(propiedades, new DetectorEntorno()).verificarTodo();

        assertThat(buscar(r, "Ollama").estado()).isEqualTo(Estado.OK);
        assertThat(buscar(r, "Modelo de lenguaje").estado()).isEqualTo(Estado.OK);
        assertThat(buscar(r, "Modelo de embeddings").estado()).isEqualTo(Estado.OK);
        // La vision es opcional: falta, pero solo como advertencia, con el comando para instalarla
        var vision = buscar(r, "Modelo de visión");
        assertThat(vision.estado()).isEqualTo(Estado.ADVERTENCIA);
        assertThat(vision.solucion()).isEqualTo("ollama pull qwen2.5vl:3b");
    }

    @Test
    void indicaComoInstalarLosModelosLocalesQueFaltan() {
        var r = new VerificadorDependencias(propiedades, new DetectorEntorno()).verificarTodo();

        for (String nombre : List.of("Modelo Vosk", "Piper", "Voz de Piper", "Tesseract")) {
            var res = buscar(r, nombre);
            assertThat(res.estado()).as(nombre).isEqualTo(Estado.FALTA);
            assertThat(res.solucion()).as(nombre).contains("instalar-dependencias");
        }
    }

    @Test
    void detectaLosModelosLocalesCuandoExisten() throws IOException {
        Files.createDirectories(base.resolve("modelos/vosk/modelo/am"));
        Files.createDirectories(base.resolve("modelos/piper"));
        Files.createFile(propiedades.getVoz().ejecutablePiperResuelto());
        Files.createDirectories(base.resolve("modelos/voces"));
        Files.createFile(base.resolve("modelos/voces/voz.onnx"));
        Files.createFile(base.resolve("modelos/voces/voz.onnx.json"));
        Files.createDirectories(base.resolve("modelos/tessdata"));
        Files.createFile(base.resolve("modelos/tessdata/spa.traineddata"));

        var r = new VerificadorDependencias(propiedades, new DetectorEntorno()).verificarTodo();

        for (String nombre : List.of("Modelo Vosk", "Piper", "Voz de Piper", "Tesseract", "Carpeta de datos")) {
            assertThat(buscar(r, nombre).estado()).as(nombre).isEqualTo(Estado.OK);
        }
    }

    @Test
    void siOllamaNoRespondeLoDiceSinFallar() {
        ollamaFalso.stop(0);
        var r = new VerificadorDependencias(propiedades, new DetectorEntorno()).verificarTodo();

        var ollama = buscar(r, "Ollama");
        assertThat(ollama.estado()).isEqualTo(Estado.FALTA);
        assertThat(ollama.solucion()).contains("ollama.com");
        assertThat(r).noneMatch(x -> x.nombre().startsWith("Modelo de lenguaje"));
    }

    @Test
    void comparaNombresDeModeloConEtiquetaLatest() {
        var instalados = List.of("nomic-embed-text:latest", "qwen2.5:3b");
        assertThat(VerificadorDependencias.contieneModelo(instalados, "nomic-embed-text")).isTrue();
        assertThat(VerificadorDependencias.contieneModelo(instalados, "qwen2.5:3b")).isTrue();
        assertThat(VerificadorDependencias.contieneModelo(instalados, "qwen2.5:7b")).isFalse();
    }
}
