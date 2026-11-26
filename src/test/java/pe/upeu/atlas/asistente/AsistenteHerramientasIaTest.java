// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.asistente.herramientas.SanchezHerramientasSistema;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.plataforma.SanchezSistemaOperativo;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;
import pe.upeu.atlas.service.MarcaMonitorSistema;
import pe.upeu.atlas.service.SanchezServicioActividad;

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

@EnabledIfSystemProperty(named = "atlas.ia", matches = "true")
class AsistenteHerramientasIaTest {

    @TempDir Path base;

    @Test
    void elModeloUsaLasHerramientasCorrectas() throws IOException {
        Path descargas = Files.createDirectories(base.resolve("Descargas"));
        SanchezPropiedadesAtlas props = new SanchezPropiedadesAtlas();
        props.setDirectorioBase(base.resolve(".atlas"));
        props.setCarpetaCapturas(base.resolve("capturas"));
        props.setCarpetasPermitidas(List.of(descargas));

        SanchezLanzador lanzador = mock(SanchezLanzador.class);
        when(lanzador.marcasistema()).thenReturn(SanchezSistemaOperativo.WINDOWS);
        SanchezHerramientasSistema herramientas = new SanchezHerramientasSistema(props, new MarcaMonitorSistema(), lanzador,
                mock(SanchezConfirmadorAcciones.class), mock(SanchezServicioActividad.class),
                mock(pe.upeu.atlas.service.SanchezServicioDocumentos.class));
        List<String> usadas = new ArrayList<>();
        herramientas.marcasetNotificador(usadas::add);

        MarcaAsistente asistente = AiServices.builder(MarcaAsistente.class)
                .chatLanguageModel(OllamaChatModel.builder().baseUrl("http://localhost:11434")
                        .modelName("qwen2.5:3b").temperature(0.3).timeout(Duration.ofSeconds(180)).build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(herramientas)
                .build();

        String cuenta = asistente.marcaresponder("¿Cuánto es 15 por 12 más 3?");
        System.out.println("CUENTA -> " + cuenta + " | herramientas: " + usadas);
        assertThat(cuenta).contains("183");

        usadas.clear();
        String hora = asistente.marcaresponder("¿Qué hora es?");
        System.out.println("HORA -> " + hora + " | herramientas: " + usadas);
        assertThat(usadas).isNotEmpty();

        usadas.clear();
        String charla = asistente.marcaresponder("Hola, ¿cómo te llamas?");
        System.out.println("CHARLA -> " + charla + " | herramientas: " + usadas);
        assertThat(usadas).isEmpty();
        verify(lanzador, org.mockito.Mockito.never()).sanchezejecutar(any(String[].class));
    }
}
