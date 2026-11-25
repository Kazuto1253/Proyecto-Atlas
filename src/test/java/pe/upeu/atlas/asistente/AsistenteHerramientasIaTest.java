// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.asistente.herramientas.HerramientasSistema;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.plataforma.SistemaOperativo;
import pe.upeu.atlas.seguridad.ConfirmadorAcciones;
import pe.upeu.atlas.service.MonitorSistema;
import pe.upeu.atlas.service.ServicioActividad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba de integración con el Ollama real (qwen2.5:3b). No corre por defecto:
 * {@code mvnw test -Dtest=AsistenteHerramientasIaTest -Datlas.ia=true}
 */
@EnabledIfSystemProperty(named = "atlas.ia", matches = "true")
class AsistenteHerramientasIaTest {

    @TempDir Path base;

    @Test
    void elModeloUsaLasHerramientasCorrectas() throws IOException {
        Path descargas = Files.createDirectories(base.resolve("Descargas"));
        PropiedadesAtlas props = new PropiedadesAtlas();
        props.setDirectorioBase(base.resolve(".atlas"));
        props.setCarpetaCapturas(base.resolve("capturas"));
        props.setCarpetasPermitidas(List.of(descargas));

        Lanzador lanzador = mock(Lanzador.class);
        when(lanzador.sistema()).thenReturn(SistemaOperativo.WINDOWS);
        HerramientasSistema herramientas = new HerramientasSistema(props, new MonitorSistema(), lanzador,
                mock(ConfirmadorAcciones.class), mock(ServicioActividad.class),
                mock(pe.upeu.atlas.service.ServicioDocumentos.class));
        List<String> usadas = new ArrayList<>();
        herramientas.setNotificador(usadas::add);

        Asistente asistente = AiServices.builder(Asistente.class)
                .chatLanguageModel(OllamaChatModel.builder().baseUrl("http://localhost:11434")
                        .modelName("qwen2.5:3b").temperature(0.3).timeout(Duration.ofSeconds(180)).build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(herramientas)
                .build();

        String cuenta = asistente.responder("¿Cuánto es 15 por 12 más 3?");
        System.out.println("CUENTA -> " + cuenta + " | herramientas: " + usadas);
        assertThat(cuenta).contains("183");

        usadas.clear();
        String hora = asistente.responder("¿Qué hora es?");
        System.out.println("HORA -> " + hora + " | herramientas: " + usadas);
        assertThat(usadas).isNotEmpty();

        // Sin herramienta: conversación normal
        usadas.clear();
        String charla = asistente.responder("Hola, ¿cómo te llamas?");
        System.out.println("CHARLA -> " + charla + " | herramientas: " + usadas);
        assertThat(usadas).isEmpty();
        verify(lanzador, org.mockito.Mockito.never()).ejecutar(any(String[].class));
    }
}
