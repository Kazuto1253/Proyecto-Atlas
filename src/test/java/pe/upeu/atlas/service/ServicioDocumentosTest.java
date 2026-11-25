// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Documento;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.seguridad.ServicioAutenticacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/** Importar, buscar y borrar documentos, con embeddings simulados (sin Ollama). */
@SpringBootTest
@ActiveProfiles("test")
class ServicioDocumentosTest {

    private static final List<String> TEMAS = List.of("redes", "cocina", "java", "musica");

    @MockBean private EmbeddingModel embeddings;
    @Autowired private ServicioDocumentos documentos;
    @Autowired private ServicioAutenticacion autenticacion;

    @TempDir Path carpeta;

    /** Vector de conteo de palabras clave: textos del mismo tema quedan cerca. */
    private static float[] vector(String texto) {
        String t = texto.toLowerCase();
        float[] v = new float[TEMAS.size()];
        for (int i = 0; i < v.length; i++) {
            int desde = 0;
            while ((desde = t.indexOf(TEMAS.get(i), desde)) >= 0) {
                v[i]++;
                desde += TEMAS.get(i).length();
            }
        }
        return v;
    }

    private void simularEmbeddings() {
        when(embeddings.embedAll(anyList())).thenAnswer(inv -> {
            List<TextSegment> segmentos = inv.getArgument(0);
            return Response.from(segmentos.stream().map(s -> new Embedding(vector(s.text()))).toList());
        });
    }

    private String entrar(String base) {
        String id = base + UUID.randomUUID().toString().substring(0, 8);
        autenticacion.registrar("Usuario Docs", id, "secreto1", Rol.ESTUDIANTE);
        autenticacion.iniciarSesion(id, "secreto1");
        return id;
    }

    @Test
    void importaBuscaPorSignificadoYElimina() throws IOException {
        simularEmbeddings();
        entrar("doc");
        Path redes = Files.writeString(carpeta.resolve("redes.txt"),
                "Las redes conectan equipos. Una red LAN es local. Las redes redes redes son importantes.");
        Path cocina = Files.writeString(carpeta.resolve("cocina.txt"),
                "La cocina peruana usa aji. La cocina cocina cocina es cultura.");

        Documento d1 = documentos.importar(redes);
        documentos.importar(cocina);
        assertThat(documentos.listar()).extracting(Documento::getNombre).containsExactlyInAnyOrder("redes.txt", "cocina.txt");
        assertThat(d1.getFragmentos()).isEqualTo(1);

        List<ServicioDocumentos.Resultado> r = documentos.buscar("¿Qué son las redes?", 2);
        assertThat(r).hasSize(2);
        assertThat(r.get(0).documento()).isEqualTo("redes.txt");
        assertThat(r.get(0).similitud()).isGreaterThan(r.get(1).similitud());

        documentos.eliminar(d1.getId());
        assertThat(documentos.listar()).extracting(Documento::getNombre).containsExactly("cocina.txt");
        autenticacion.cerrarSesion();
    }

    @Test
    void cadaUsuarioVeSoloSusDocumentos() throws IOException {
        simularEmbeddings();
        entrar("ana");
        Documento mio = documentos.importar(Files.writeString(carpeta.resolve("a.txt"), "apuntes de java java"));
        autenticacion.cerrarSesion();

        entrar("beto");
        assertThat(documentos.listar()).isEmpty();
        assertThat(documentos.buscar("java", 3)).isEmpty();
        assertThatThrownBy(() -> documentos.eliminar(mio.getId())).isInstanceOf(PermisoDenegadoException.class);
        autenticacion.cerrarSesion();
    }

    @Test
    void rechazaTiposInvalidosArchivosVaciosYSinSesion() throws IOException {
        simularEmbeddings();
        Path exe = Files.writeString(carpeta.resolve("virus.exe"), "MZ");
        Path vacio = Files.writeString(carpeta.resolve("vacio.txt"), "   \n  ");

        assertThatThrownBy(() -> documentos.importar(exe)).isInstanceOf(PermisoDenegadoException.class); // sin sesión
        entrar("tipo");
        assertThatThrownBy(() -> documentos.importar(exe)).isInstanceOf(NegocioException.class).hasMessageContaining("no admitido");
        assertThatThrownBy(() -> documentos.importar(vacio)).isInstanceOf(NegocioException.class).hasMessageContaining("texto");
        assertThatThrownBy(() -> documentos.importar(carpeta.resolve("no-existe.txt"))).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> documentos.buscar("  ", 3)).isInstanceOf(NegocioException.class);
        autenticacion.cerrarSesion();
    }
}
