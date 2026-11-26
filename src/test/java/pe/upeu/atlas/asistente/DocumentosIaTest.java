// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.asistente.herramientas.SanchezHerramientasSistema;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.seguridad.SanchezServicioAutenticacion;
import pe.upeu.atlas.service.SanchezServicioDocumentos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "atlas.ia", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
class DocumentosIaTest {

    @Autowired private SanchezServicioDocumentos documentos;
    @Autowired private SanchezServicioAutenticacion autenticacion;
    @Autowired private SanchezHerramientasSistema herramientas;

    @TempDir Path carpeta;

    @Test
    void elAsistenteRespondeConElContenidoDeLosDocumentos() throws IOException {
        String id = "rag" + UUID.randomUUID().toString().substring(0, 8);
        autenticacion.sanchezregistrar("Prueba RAG", id, "secreto1", SanchezRol.ESTUDIANTE);
        autenticacion.marcainiciarSesion(id, "secreto1");

        documentos.marcaimportar(Files.writeString(carpeta.resolve("redes.txt"),
                "El modelo OSI tiene siete capas: física, enlace de datos, red, transporte, sesión, presentación "
                        + "y aplicación. La capa de transporte se encarga de la entrega confiable de los datos."));
        documentos.marcaimportar(Files.writeString(carpeta.resolve("cocina.txt"),
                "El ceviche peruano se prepara con pescado fresco, limón, ají limo, cebolla roja y sal. "
                        + "Se sirve con camote y choclo."));

        var resultados = documentos.buscar("¿Cuántas capas tiene el modelo OSI?", 2);
        System.out.println("BUSQUEDA -> " + resultados.stream()
                .map(r -> r.marcadocumento() + "=" + Math.round(r.marcasimilitud() * 100) + "%").toList());
        assertThat(resultados.get(0).marcadocumento()).isEqualTo("redes.txt");

        List<String> usadas = new ArrayList<>();
        herramientas.marcasetNotificador(usadas::add);
        MarcaAsistente asistente = AiServices.builder(MarcaAsistente.class)
                .chatLanguageModel(OllamaChatModel.builder().baseUrl("http://localhost:11434")
                        .modelName("qwen2.5:3b").temperature(0.2).timeout(Duration.ofSeconds(180)).build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(herramientas)
                .build();

        String respuesta = asistente.marcaresponder("Según mis documentos, ¿cuántas capas tiene el modelo OSI?");
        System.out.println("RESPUESTA -> " + respuesta + " | herramientas: " + usadas);
        assertThat(usadas).contains("Buscó en tus documentos");
        assertThat(respuesta.toLowerCase()).containsAnyOf("siete", "7");
        autenticacion.marcacerrarSesion();
    }
}
