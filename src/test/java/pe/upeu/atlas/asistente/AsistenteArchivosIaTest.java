// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.asistente.herramientas.HerramientasArchivos;
import pe.upeu.atlas.asistente.herramientas.HerramientasSistema;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.plataforma.CarpetasConocidas;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.plataforma.Papelera;
import pe.upeu.atlas.plataforma.ResolvedorRutas;
import pe.upeu.atlas.plataforma.SistemaOperativo;
import pe.upeu.atlas.seguridad.ConfirmadorAcciones;
import pe.upeu.atlas.seguridad.GuardiaRutas;
import pe.upeu.atlas.service.ExtractorTexto;
import pe.upeu.atlas.service.MonitorSistema;
import pe.upeu.atlas.service.ServicioActividad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pedidos reales de manejo de archivos con el Ollama real (qwen2.5:3b), sobre un usuario simulado en una carpeta
 * temporal. {@code mvnw test -Dtest=AsistenteArchivosIaTest -Datlas.ia=true}
 */
@EnabledIfSystemProperty(named = "atlas.ia", matches = "true")
class AsistenteArchivosIaTest {

    @TempDir Path casa;

    private Path documentos;
    private Path escritorio;
    private Papelera papelera;
    private Lanzador lanzador;
    private HerramientasSistema sistema;
    private HerramientasArchivos archivos;
    private final List<String> usadas = new ArrayList<>();
    private final List<String> fallos = new ArrayList<>();

    private void preparar() throws IOException {
        documentos = Files.createDirectories(casa.resolve("Documents"));
        escritorio = Files.createDirectories(casa.resolve("Desktop"));
        Path descargas = Files.createDirectories(casa.resolve("Downloads"));
        Files.createDirectories(escritorio.resolve("Redes"));
        Files.writeString(documentos.resolve("apuntes.txt"), "El modelo OSI tiene siete capas. TCP es confiable.");
        Files.writeString(documentos.resolve("viejo.txt"), "borrador");

        PropiedadesAtlas props = new PropiedadesAtlas();
        props.setDirectorioBase(casa.resolve(".atlas"));
        props.setCarpetaCapturas(casa.resolve("Pictures/Atlas"));
        Map<String, Path> mapa = Map.of("documentos", documentos, "documents", documentos, "escritorio", escritorio,
                "desktop", escritorio, "descargas", descargas, "usuario", casa);
        CarpetasConocidas conocidas = mock(CarpetasConocidas.class);
        when(conocidas.buscar(anyString())).thenAnswer(inv -> Optional.ofNullable(mapa.get(CarpetasConocidas.normalizar(inv.getArgument(0)))));
        when(conocidas.principales()).thenReturn(Map.of("documentos", documentos, "escritorio", escritorio, "usuario", casa));

        lanzador = mock(Lanzador.class);
        when(lanzador.sistema()).thenReturn(SistemaOperativo.WINDOWS);
        papelera = mock(Papelera.class);
        ConfirmadorAcciones confirmador = mock(ConfirmadorAcciones.class);
        when(confirmador.confirmar(anyString(), anyString())).thenReturn(true);
        ServicioActividad actividad = mock(ServicioActividad.class);

        sistema = new HerramientasSistema(props, new MonitorSistema(), lanzador, confirmador, actividad,
                mock(pe.upeu.atlas.service.ServicioDocumentos.class));
        archivos = new HerramientasArchivos(props, new ResolvedorRutas(conocidas), new GuardiaRutas(props, conocidas, casa),
                lanzador, papelera, confirmador, actividad, new ExtractorTexto(props));
        sistema.setNotificador(usadas::add);
        archivos.setNotificador(usadas::add);
    }

    private Asistente nuevoAsistente() {
        return AiServices.builder(Asistente.class)
                .chatLanguageModel(OllamaChatModel.builder().baseUrl("http://localhost:11434").modelName("qwen2.5:3b")
                        .temperature(0.2).timeout(Duration.ofSeconds(240)).build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(sistema, archivos)
                .build();
    }

    /** Cada pedido con un asistente nuevo: así se ve qué hace la herramienta, no la memoria. */
    private void caso(String nombre, String pedido, Predicate<String> respuestaOk, Function<List<String>, Boolean> efectoOk) {
        usadas.clear();
        String respuesta = nuevoAsistente().responder(pedido);
        boolean ok = efectoOk.apply(new ArrayList<>(usadas)) && (respuestaOk == null || respuestaOk.test(respuesta.toLowerCase()));
        System.out.println((ok ? "OK    " : "FALLA ") + nombre + " | «" + pedido + "» -> «" + respuesta.replace('\n', ' ')
                + "» | herramientas: " + usadas);
        if (!ok) {
            fallos.add(nombre);
        }
    }

    @Test
    void elModeloRealManejaArchivosComoPideElUsuario() throws Exception {
        preparar();

        caso("guardar", "Guarda en mi carpeta de documentos un archivo llamado lista de compras con leche, pan y huevos",
                null, u -> Files.exists(documentos.resolve("lista de compras.txt")) && leer(documentos.resolve("lista de compras.txt")).toLowerCase().contains("leche"));
        caso("crear carpeta", "Crea una carpeta llamada Proyectos en el escritorio",
                null, u -> Files.isDirectory(escritorio.resolve("Proyectos")));
        caso("listar", "¿Qué archivos hay en documentos?",
                r -> r.contains("apuntes"), u -> u.stream().anyMatch(a -> a.startsWith("Listó")));
        caso("copiar", "Copia el archivo apuntes.txt de documentos al escritorio",
                null, u -> Files.exists(escritorio.resolve("apuntes.txt")) && Files.exists(documentos.resolve("apuntes.txt")));
        caso("mover", "Mueve el archivo apuntes.txt de documentos a la carpeta Redes del escritorio",
                null, u -> Files.exists(escritorio.resolve("Redes/apuntes.txt")) && !Files.exists(documentos.resolve("apuntes.txt")));
        // el mover anterior dejó apuntes.txt en Redes; se repone para los siguientes casos
        Files.writeString(documentos.resolve("apuntes.txt"), "El modelo OSI tiene siete capas. TCP es confiable.");
        caso("buscar", "Busca mi archivo de apuntes", null, u -> u.stream().anyMatch(a -> a.startsWith("Buscó")));
        caso("leer", "Lee el archivo apuntes.txt de documentos y dime qué dice",
                r -> r.contains("siete") || r.contains("osi") || r.contains("tcp"), u -> u.stream().anyMatch(a -> a.startsWith("Leyó")));
        caso("renombrar", "Cambia el nombre de viejo.txt en documentos a nuevo.txt",
                null, u -> Files.exists(documentos.resolve("nuevo.txt")) && !Files.exists(documentos.resolve("viejo.txt")));
        caso("eliminar", "Elimina el archivo nuevo.txt de documentos",
                null, u -> { try { verify(papelera).enviar(documentos.resolve("nuevo.txt")); return true; } catch (Throwable e) { return false; } });
        caso("seguridad", "Guarda un archivo llamado hola.exe en documentos con el texto echo hola",
                null, u -> !Files.exists(documentos.resolve("hola.exe")));

        System.out.println("CASOS FALLIDOS: " + fallos);
        assertThat(fallos).as("pedidos que el modelo no resolvió").isEmpty();
    }

    private static String leer(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }
}
