// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.upeu.atlas.asistente.OrquestadorConversacion.Estado;
import pe.upeu.atlas.asistente.OrquestadorConversacion.Oyente;
import pe.upeu.atlas.asistente.herramientas.HerramientasSistema;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.service.ServicioHistorial;
import pe.upeu.atlas.voz.ReconocedorVoz;
import pe.upeu.atlas.voz.SintetizadorVoz;
import pe.upeu.atlas.voz.Transcripcion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El usuario puede interrumpir a Atlas en cualquier momento y corregir su pedido. */
class OrquestadorInterrupcionTest {

    /** Modelo falso: puede tardar mucho (se interrumpe) y recuerda lo que recibió. */
    static class ModeloFalso implements ChatLanguageModel {
        volatile boolean lento;
        final CountDownLatch entro = new CountDownLatch(1);
        final List<List<String>> recibidos = new ArrayList<>();
        int respuestas;

        @Override
        public synchronized Response<AiMessage> generate(List<ChatMessage> mensajes) {
            List<String> textos = new ArrayList<>();
            for (ChatMessage m : mensajes) {
                if (m instanceof dev.langchain4j.data.message.SystemMessage) {
                    continue; // las instrucciones fijas mencionan palabras de ejemplo: no son la conversación
                }
                textos.add(m instanceof UserMessage u ? u.singleText() : String.valueOf(m));
            }
            recibidos.add(textos);
            entro.countDown();
            if (lento) {
                try {
                    wait(20_000); // se libera al interrumpir el hilo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrumpido", e);
                }
            }
            respuestas++;
            return Response.from(AiMessage.from("Respuesta " + respuestas));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> mensajes, List<ToolSpecification> herramientas) {
            return generate(mensajes);
        }
    }

    static class Grabador implements Oyente {
        final ConcurrentLinkedQueue<String> eventos = new ConcurrentLinkedQueue<>();
        volatile Estado estado = Estado.INACTIVO;

        @Override public void estado(Estado e) { estado = e; }
        @Override public void textoParcial(String t) { }
        @Override public void mensajeUsuario(String t) { eventos.add("usuario:" + t); }
        @Override public void mensajeAsistente(String t) { eventos.add("asistente:" + t); }
        @Override public void error(String m) { eventos.add("error:" + m); }
        @Override public void interrumpido(String p) { eventos.add("interrumpido:" + p); }
    }

    private ModeloFalso modelo;
    private ReconocedorVoz reconocedor;
    private ServicioHistorial historial;
    private Grabador oyente;
    private OrquestadorConversacion orquestador;

    @BeforeEach
    void preparar() {
        modelo = new ModeloFalso();
        reconocedor = mock(ReconocedorVoz.class);
        historial = mock(ServicioHistorial.class);
        oyente = new Grabador();
        orquestador = new OrquestadorConversacion(reconocedor, mock(SintetizadorVoz.class), modelo,
                new PropiedadesAtlas(), mock(HerramientasSistema.class),
                mock(pe.upeu.atlas.asistente.herramientas.HerramientasArchivos.class), historial);
        orquestador.setVozActivada(false);
        orquestador.agregarOyente(oyente);
    }

    @AfterEach
    void cerrar() {
        orquestador.liberar();
    }

    @Test
    void interrumpirMientrasPiensaDevuelveElPedidoYNoHaceNadaMas() throws Exception {
        modelo.lento = true;
        orquestador.turnoPorTexto("Abre la carpeta de descargas");
        assertThat(modelo.entro.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(orquestador.getEstado()).isEqualTo(Estado.PENSANDO);

        orquestador.interrumpir();

        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == Estado.INACTIVO);
        assertThat(oyente.eventos).contains("interrumpido:Abre la carpeta de descargas");
        assertThat(oyente.eventos).noneMatch(e -> e.startsWith("asistente:") || e.startsWith("error:"));
        verify(historial, never()).registrar(org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void elPedidoInterrumpidoNoContaminaLaConversacionSiguiente() throws Exception {
        modelo.lento = true;
        orquestador.turnoPorTexto("Abre la carpeta de descargas");
        assertThat(modelo.entro.await(5, TimeUnit.SECONDS)).isTrue();
        orquestador.interrumpir();
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == Estado.INACTIVO);

        modelo.lento = false;
        orquestador.turnoPorTexto("Abre la carpeta de documentos");
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.eventos.contains("asistente:Respuesta 1"));

        List<String> segundaLlamada = modelo.recibidos.get(modelo.recibidos.size() - 1);
        assertThat(segundaLlamada).anyMatch(t -> t.contains("documentos"));
        assertThat(segundaLlamada).noneMatch(t -> t.contains("descargas"));
    }

    @Test
    void unTurnoCompletoSiConservaLaMemoria() throws Exception {
        orquestador.turnoPorTexto("Hola Atlas");
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.eventos.contains("asistente:Respuesta 1"));
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == Estado.INACTIVO);

        orquestador.turnoPorTexto("¿Cómo te llamas?");
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.eventos.contains("asistente:Respuesta 2"));

        List<String> segunda = modelo.recibidos.get(modelo.recibidos.size() - 1);
        assertThat(segunda).anyMatch(t -> t.contains("Hola Atlas"));
        verify(historial).registrar(true, "Hola Atlas");
        verify(historial).registrar(false, "Respuesta 1");
    }

    @Test
    void interrumpirMientrasEscuchaCancelaSinEnviarNiMostrarErrores() throws Exception {
        CountDownLatch escuchando = new CountDownLatch(1);
        CountDownLatch cancelada = new CountDownLatch(1);
        when(reconocedor.escucharFrase(any(), any())).thenAnswer(inv -> {
            escuchando.countDown();
            cancelada.await(5, TimeUnit.SECONDS);
            return Transcripcion.VACIA;
        });
        doAnswer(inv -> {
            cancelada.countDown();
            return null;
        }).when(reconocedor).cancelar();

        orquestador.turnoPorVoz();
        assertThat(escuchando.await(5, TimeUnit.SECONDS)).isTrue();
        orquestador.interrumpir();

        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == Estado.INACTIVO);
        assertThat(oyente.eventos).containsExactly("interrumpido:null");
        assertThat(modelo.recibidos).isEmpty();
    }

    @Test
    void interrumpirSinNadaEnCursoNoHaceNada() {
        orquestador.interrumpir();
        assertThat(oyente.eventos).isEmpty();
        assertThat(orquestador.getEstado()).isEqualTo(Estado.INACTIVO);
    }

    @Test
    void todosLosOyentesRecibenLosAvisos() throws Exception {
        Grabador flotante = new Grabador();
        orquestador.agregarOyente(flotante);
        orquestador.agregarOyente(flotante); // no se duplica

        orquestador.turnoPorTexto("Hola");
        await().atMost(Duration.ofSeconds(5)).until(() -> flotante.eventos.contains("asistente:Respuesta 1"));

        assertThat(oyente.eventos).contains("asistente:Respuesta 1");
        assertThat(flotante.eventos.stream().filter(e -> e.equals("asistente:Respuesta 1"))).hasSize(1);
    }
}
