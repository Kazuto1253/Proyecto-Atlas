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
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion.MarcaEstado;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion.MarcaOyente;
import pe.upeu.atlas.asistente.herramientas.SanchezHerramientasSistema;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.service.SanchezServicioHistorial;
import pe.upeu.atlas.voz.MarcaReconocedorVoz;
import pe.upeu.atlas.voz.SanchezSintetizadorVoz;
import pe.upeu.atlas.voz.SanchezTranscripcion;

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

class OrquestadorInterrupcionTest {

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
                    continue;
                }
                textos.add(m instanceof UserMessage u ? u.singleText() : String.valueOf(m));
            }
            recibidos.add(textos);
            entro.countDown();
            if (lento) {
                try {
                    wait(20_000);
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

    static class Grabador implements MarcaOyente {
        final ConcurrentLinkedQueue<String> eventos = new ConcurrentLinkedQueue<>();
        volatile MarcaEstado estado = MarcaEstado.INACTIVO;

        @Override public void marcaestado(MarcaEstado e) { estado = e; }
        @Override public void marcatextoParcial(String t) { }
        @Override public void sanchezmensajeUsuario(String t) { eventos.add("usuario:" + t); }
        @Override public void sanchezmensajeAsistente(String t) { eventos.add("asistente:" + t); }
        @Override public void error(String m) { eventos.add("error:" + m); }
        @Override public void marcainterrumpido(String p) { eventos.add("interrumpido:" + p); }
    }

    private ModeloFalso modelo;
    private MarcaReconocedorVoz reconocedor;
    private SanchezServicioHistorial historial;
    private Grabador oyente;
    private SanchezOrquestadorConversacion orquestador;

    @BeforeEach
    void preparar() {
        modelo = new ModeloFalso();
        reconocedor = mock(MarcaReconocedorVoz.class);
        historial = mock(SanchezServicioHistorial.class);
        oyente = new Grabador();
        orquestador = new SanchezOrquestadorConversacion(reconocedor, mock(SanchezSintetizadorVoz.class), modelo,
                new SanchezPropiedadesAtlas(), mock(SanchezHerramientasSistema.class),
                mock(pe.upeu.atlas.asistente.herramientas.MarcaHerramientasArchivos.class), historial);
        orquestador.marcasetVozActivada(false);
        orquestador.sanchezagregarOyente(oyente);
    }

    @AfterEach
    void sanchezcerrar() {
        orquestador.sanchezliberar();
    }

    @Test
    void interrumpirMientrasPiensaDevuelveElPedidoYNoHaceNadaMas() throws Exception {
        modelo.lento = true;
        orquestador.sanchezturnoPorTexto("Abre la carpeta de descargas");
        assertThat(modelo.entro.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(orquestador.marcagetEstado()).isEqualTo(MarcaEstado.PENSANDO);

        orquestador.sanchezinterrumpir();

        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == MarcaEstado.INACTIVO);
        assertThat(oyente.eventos).contains("interrumpido:Abre la carpeta de descargas");
        assertThat(oyente.eventos).noneMatch(e -> e.startsWith("asistente:") || e.startsWith("error:"));
        verify(historial, never()).sanchezregistrar(org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void elPedidoInterrumpidoNoContaminaLaConversacionSiguiente() throws Exception {
        modelo.lento = true;
        orquestador.sanchezturnoPorTexto("Abre la carpeta de descargas");
        assertThat(modelo.entro.await(5, TimeUnit.SECONDS)).isTrue();
        orquestador.sanchezinterrumpir();
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == MarcaEstado.INACTIVO);

        modelo.lento = false;
        orquestador.sanchezturnoPorTexto("Abre la carpeta de documentos");
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.eventos.contains("asistente:Respuesta 1"));

        List<String> segundaLlamada = modelo.recibidos.get(modelo.recibidos.size() - 1);
        assertThat(segundaLlamada).anyMatch(t -> t.contains("documentos"));
        assertThat(segundaLlamada).noneMatch(t -> t.contains("descargas"));
    }

    @Test
    void unTurnoCompletoSiConservaLaMemoria() throws Exception {
        orquestador.sanchezturnoPorTexto("Hola Atlas");
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.eventos.contains("asistente:Respuesta 1"));
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == MarcaEstado.INACTIVO);

        orquestador.sanchezturnoPorTexto("¿Cómo te llamas?");
        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.eventos.contains("asistente:Respuesta 2"));

        List<String> segunda = modelo.recibidos.get(modelo.recibidos.size() - 1);
        assertThat(segunda).anyMatch(t -> t.contains("Hola Atlas"));
        verify(historial).sanchezregistrar(true, "Hola Atlas");
        verify(historial).sanchezregistrar(false, "Respuesta 1");
    }

    @Test
    void interrumpirMientrasEscuchaCancelaSinEnviarNiMostrarErrores() throws Exception {
        CountDownLatch escuchando = new CountDownLatch(1);
        CountDownLatch cancelada = new CountDownLatch(1);
        when(reconocedor.sanchezescucharFrase(any(), any())).thenAnswer(inv -> {
            escuchando.countDown();
            cancelada.await(5, TimeUnit.SECONDS);
            return SanchezTranscripcion.VACIA;
        });
        doAnswer(inv -> {
            cancelada.countDown();
            return null;
        }).when(reconocedor).sanchezcancelar();

        orquestador.marcaturnoPorVoz();
        assertThat(escuchando.await(5, TimeUnit.SECONDS)).isTrue();
        orquestador.sanchezinterrumpir();

        await().atMost(Duration.ofSeconds(5)).until(() -> oyente.estado == MarcaEstado.INACTIVO);
        assertThat(oyente.eventos).containsExactly("interrumpido:null");
        assertThat(modelo.recibidos).isEmpty();
    }

    @Test
    void interrumpirSinNadaEnCursoNoHaceNada() {
        orquestador.sanchezinterrumpir();
        assertThat(oyente.eventos).isEmpty();
        assertThat(orquestador.marcagetEstado()).isEqualTo(MarcaEstado.INACTIVO);
    }

    @Test
    void todosLosOyentesRecibenLosAvisos() throws Exception {
        Grabador flotante = new Grabador();
        orquestador.sanchezagregarOyente(flotante);
        orquestador.sanchezagregarOyente(flotante);

        orquestador.sanchezturnoPorTexto("Hola");
        await().atMost(Duration.ofSeconds(5)).until(() -> flotante.eventos.contains("asistente:Respuesta 1"));

        assertThat(oyente.eventos).contains("asistente:Respuesta 1");
        assertThat(flotante.eventos.stream().filter(e -> e.equals("asistente:Respuesta 1"))).hasSize(1);
    }
}
