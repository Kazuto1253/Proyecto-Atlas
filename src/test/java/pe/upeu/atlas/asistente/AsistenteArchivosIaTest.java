// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.asistente.herramientas.MarcaHerramientasArchivos;
import pe.upeu.atlas.asistente.herramientas.SanchezHerramientasSistema;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.plataforma.MarcaCarpetasConocidas;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.plataforma.MarcaPapelera;
import pe.upeu.atlas.plataforma.MarcaResolvedorRutas;
import pe.upeu.atlas.plataforma.SanchezSistemaOperativo;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;
import pe.upeu.atlas.seguridad.MarcaGuardiaRutas;
import pe.upeu.atlas.service.MarcaExtractorTexto;
import pe.upeu.atlas.service.MarcaMonitorSistema;
import pe.upeu.atlas.service.SanchezServicioActividad;

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

@EnabledIfSystemProperty(named = "atlas.ia", matches = "true")
class AsistenteArchivosIaTest {

    @TempDir Path casa;

    private Path documentos;
    private Path escritorio;
    private MarcaPapelera papelera;
    private SanchezLanzador lanzador;
    private SanchezHerramientasSistema sistema;
    private MarcaHerramientasArchivos archivos;
    private final List<String> usadas = new ArrayList<>();
    private final List<String> fallos = new ArrayList<>();

    private void preparar() throws IOException {
        documentos = Files.createDirectories(casa.resolve("Documents"));
        escritorio = Files.createDirectories(casa.resolve("Desktop"));
        Path descargas = Files.createDirectories(casa.resolve("Downloads"));
        Files.createDirectories(escritorio.resolve("Redes"));
        Files.writeString(documentos.resolve("apuntes.txt"), "El modelo OSI tiene siete capas. TCP es confiable.");
        Files.writeString(documentos.resolve("viejo.txt"), "borrador");

        SanchezPropiedadesAtlas props = new SanchezPropiedadesAtlas();
        props.setDirectorioBase(casa.resolve(".atlas"));
        props.setCarpetaCapturas(casa.resolve("Pictures/Atlas"));
        Map<String, Path> mapa = Map.of("documentos", documentos, "documents", documentos, "escritorio", escritorio,
                "desktop", escritorio, "descargas", descargas, "usuario", casa);
        MarcaCarpetasConocidas conocidas = mock(MarcaCarpetasConocidas.class);
        when(conocidas.buscar(anyString())).thenAnswer(inv -> Optional.ofNullable(mapa.get(MarcaCarpetasConocidas.sancheznormalizar(inv.getArgument(0)))));
        when(conocidas.marcaprincipales()).thenReturn(Map.of("documentos", documentos, "escritorio", escritorio, "usuario", casa));

        lanzador = mock(SanchezLanzador.class);
        when(lanzador.marcasistema()).thenReturn(SanchezSistemaOperativo.WINDOWS);
        papelera = mock(MarcaPapelera.class);
        SanchezConfirmadorAcciones confirmador = mock(SanchezConfirmadorAcciones.class);
        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(true);
        SanchezServicioActividad actividad = mock(SanchezServicioActividad.class);

        sistema = new SanchezHerramientasSistema(props, new MarcaMonitorSistema(), lanzador, confirmador, actividad,
                mock(pe.upeu.atlas.service.SanchezServicioDocumentos.class));
        archivos = new MarcaHerramientasArchivos(props, new MarcaResolvedorRutas(conocidas), new MarcaGuardiaRutas(props, conocidas, casa),
                lanzador, papelera, confirmador, actividad, new MarcaExtractorTexto(props));
        sistema.marcasetNotificador(usadas::add);
        archivos.marcasetNotificador(usadas::add);
    }

    private MarcaAsistente nuevoAsistente() {
        return AiServices.builder(MarcaAsistente.class)
                .chatLanguageModel(OllamaChatModel.builder().baseUrl("http://localhost:11434").modelName("qwen2.5:3b")
                        .temperature(0.2).timeout(Duration.ofSeconds(240)).build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(sistema, archivos)
                .build();
    }

    private void caso(String nombre, String pedido, Predicate<String> respuestaOk, Function<List<String>, Boolean> efectoOk) {
        usadas.clear();
        String respuesta = nuevoAsistente().marcaresponder(pedido);
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
                null, u -> Files.exists(documentos.resolve("lista de compras.txt")) && marcaleer(documentos.resolve("lista de compras.txt")).toLowerCase().contains("leche"));
        caso("crear carpeta", "Crea una carpeta llamada Proyectos en el escritorio",
                null, u -> Files.isDirectory(escritorio.resolve("Proyectos")));
        caso("listar", "¿Qué archivos hay en documentos?",
                r -> r.contains("apuntes"), u -> u.stream().anyMatch(a -> a.startsWith("Listó")));
        caso("copiar", "Copia el archivo apuntes.txt de documentos al escritorio",
                null, u -> Files.exists(escritorio.resolve("apuntes.txt")) && Files.exists(documentos.resolve("apuntes.txt")));
        caso("mover", "Mueve el archivo apuntes.txt de documentos a la carpeta Redes del escritorio",
                null, u -> Files.exists(escritorio.resolve("Redes/apuntes.txt")) && !Files.exists(documentos.resolve("apuntes.txt")));
        Files.writeString(documentos.resolve("apuntes.txt"), "El modelo OSI tiene siete capas. TCP es confiable.");
        caso("buscar", "Busca mi archivo de apuntes", null, u -> u.stream().anyMatch(a -> a.startsWith("Buscó")));
        caso("leer", "Lee el archivo apuntes.txt de documentos y dime qué dice",
                r -> r.contains("siete") || r.contains("osi") || r.contains("tcp"), u -> u.stream().anyMatch(a -> a.startsWith("Leyó")));
        caso("renombrar", "Cambia el nombre de viejo.txt en documentos a nuevo.txt",
                null, u -> Files.exists(documentos.resolve("nuevo.txt")) && !Files.exists(documentos.resolve("viejo.txt")));
        caso("eliminar", "Elimina el archivo nuevo.txt de documentos",
                null, u -> { try { verify(papelera).sanchezenviar(documentos.resolve("nuevo.txt")); return true; } catch (Throwable e) { return false; } });
        caso("seguridad", "Guarda un archivo llamado hola.exe en documentos con el texto echo hola",
                null, u -> !Files.exists(documentos.resolve("hola.exe")));

        System.out.println("CASOS FALLIDOS: " + fallos);
        assertThat(fallos).as("pedidos que el modelo no resolvió").isEmpty();
    }

    private static String marcaleer(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }
}
