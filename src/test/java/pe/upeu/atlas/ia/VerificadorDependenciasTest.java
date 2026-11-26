package pe.upeu.atlas.ia;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.ia.SanchezResultadoVerificacion.MarcaEstado;
import pe.upeu.atlas.plataforma.SanchezDetectorEntorno;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerificadorDependenciasTest {

    @TempDir
    Path base;

    private HttpServer ollamaFalso;
    private SanchezPropiedadesAtlas propiedades;

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

        propiedades = new SanchezPropiedadesAtlas();
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
    void sanchezcerrar() {
        ollamaFalso.stop(0);
    }

    private SanchezResultadoVerificacion buscar(List<SanchezResultadoVerificacion> r, String nombre) {
        return r.stream().filter(x -> x.nombre().startsWith(nombre)).findFirst().orElseThrow();
    }

    @Test
    void reportaOllamaYModelosInstalados() {
        var r = new SanchezVerificadorDependencias(propiedades, new SanchezDetectorEntorno()).sanchezverificarTodo();

        assertThat(buscar(r, "Ollama").marcaestado()).isEqualTo(MarcaEstado.OK);
        assertThat(buscar(r, "Modelo de lenguaje").marcaestado()).isEqualTo(MarcaEstado.OK);
        assertThat(buscar(r, "Modelo de embeddings").marcaestado()).isEqualTo(MarcaEstado.OK);
        var vision = buscar(r, "Modelo de visión");
        assertThat(vision.marcaestado()).isEqualTo(MarcaEstado.ADVERTENCIA);
        assertThat(vision.marcasolucion()).isEqualTo("ollama pull qwen2.5vl:3b");
    }

    @Test
    void indicaComoInstalarLosModelosLocalesQueFaltan() {
        var r = new SanchezVerificadorDependencias(propiedades, new SanchezDetectorEntorno()).sanchezverificarTodo();

        for (String nombre : List.of("Modelo Vosk", "Piper", "Voz de Piper", "Tesseract")) {
            var res = buscar(r, nombre);
            assertThat(res.marcaestado()).as(nombre).isEqualTo(MarcaEstado.FALTA);
            assertThat(res.marcasolucion()).as(nombre).contains("instalar-dependencias");
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

        var r = new SanchezVerificadorDependencias(propiedades, new SanchezDetectorEntorno()).sanchezverificarTodo();

        for (String nombre : List.of("Modelo Vosk", "Piper", "Voz de Piper", "Tesseract", "Carpeta de datos")) {
            assertThat(buscar(r, nombre).marcaestado()).as(nombre).isEqualTo(MarcaEstado.OK);
        }
    }

    @Test
    void siOllamaNoRespondeLoDiceSinFallar() {
        ollamaFalso.stop(0);
        var r = new SanchezVerificadorDependencias(propiedades, new SanchezDetectorEntorno()).sanchezverificarTodo();

        var ollama = buscar(r, "Ollama");
        assertThat(ollama.marcaestado()).isEqualTo(MarcaEstado.FALTA);
        assertThat(ollama.marcasolucion()).contains("ollama.com");
        assertThat(r).noneMatch(x -> x.nombre().startsWith("Modelo de lenguaje"));
    }

    @Test
    void comparaNombresDeModeloConEtiquetaLatest() {
        var instalados = List.of("nomic-embed-text:latest", "qwen2.5:3b");
        assertThat(SanchezVerificadorDependencias.sanchezcontieneModelo(instalados, "nomic-embed-text")).isTrue();
        assertThat(SanchezVerificadorDependencias.sanchezcontieneModelo(instalados, "qwen2.5:3b")).isTrue();
        assertThat(SanchezVerificadorDependencias.sanchezcontieneModelo(instalados, "qwen2.5:7b")).isFalse();
    }
}
