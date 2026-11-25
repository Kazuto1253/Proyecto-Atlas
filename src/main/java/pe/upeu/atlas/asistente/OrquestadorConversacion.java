// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.asistente.herramientas.HerramientasArchivos;
import pe.upeu.atlas.asistente.herramientas.HerramientasSistema;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.service.ServicioHistorial;
import pe.upeu.atlas.voz.NormalizadorTranscripcion;
import pe.upeu.atlas.voz.ReconocedorVoz;
import pe.upeu.atlas.voz.SintetizadorVoz;
import pe.upeu.atlas.voz.Transcripcion;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Une el ciclo escuchar -> pensar -> hablar. Todo corre en un hilo propio; los avances se informan a los
 * {@link Oyente} registrados (la UI debe pasar a su hilo).
 *
 * <p><b>Interrupción:</b> {@link #interrumpir()} corta el turno en cualquier punto (escuchando, pensando o
 * hablando). La llamada al modelo corre en un hilo aparte que se abandona al instante, las herramientas
 * comprueban la cancelación antes de actuar y la memoria de la conversación solo se actualiza si el turno
 * termina bien, así el pedido interrumpido no contamina el siguiente.
 */
@Slf4j
@Service
public class OrquestadorConversacion {

    public enum Estado { INACTIVO, CARGANDO, ESCUCHANDO, PENSANDO, HABLANDO }

    public interface Oyente { // Y
        void estado(Estado estado);
        void textoParcial(String texto);
        void mensajeUsuario(String texto);
        void mensajeAsistente(String texto); // K
        void error(String mensaje); // K

        /** El modelo usó una herramienta (por ejemplo "Abrió la carpeta Descargas"). */
        default void herramienta(String descripcion) { }

        /** Lo entendido por voz tiene poca confianza: mostrarlo para que el usuario lo corrija y lo envíe. */
        default void confirmarTexto(String texto) { }

        /** El usuario interrumpió el turno; {@code pedido} es lo que había pedido (o {@code null} si aún no decía nada). */
        default void interrumpido(String pedido) { }
    }

    /** Turno en curso: permite cancelarlo desde otro hilo. */
    private static final class Turno {
        final AtomicBoolean cancelado = new AtomicBoolean(false); // K
        volatile Future<?> modelo;
    }

    private final ReconocedorVoz reconocedor;
    private final SintetizadorVoz sintetizador;
    private final ChatLanguageModel modelo;
    private final HerramientasSistema herramientas;
    private final HerramientasArchivos archivos;
    private final ServicioHistorial historial;
    private final PropiedadesAtlas propiedades;
    private final int mensajesMemoria; // K
    private volatile MessageWindowChatMemory memoria; // K

    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> { // K
        Thread t = new Thread(r, "atlas-conversacion");
        t.setDaemon(true); // Y
        return t;
    });
    /** La llamada al modelo va en su propio hilo para poder abandonarla al interrumpir. */
    private final ExecutorService hiloModelo = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "atlas-modelo");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean ocupado = new AtomicBoolean(false);
    private final List<Oyente> oyentes = new CopyOnWriteArrayList<>();
    private final Oyente oyente = new Difusor(); // K
    private volatile Estado estado = Estado.INACTIVO; // Y
    private volatile Turno turno;
    private volatile boolean vozActivada = true;

    public OrquestadorConversacion(ReconocedorVoz reconocedor, SintetizadorVoz sintetizador,
                                   ChatLanguageModel modelo, PropiedadesAtlas propiedades,
                                   HerramientasSistema herramientas, HerramientasArchivos archivos,
                                   ServicioHistorial historial) { // Y
        this.historial = historial; // K
        this.propiedades = propiedades;
        this.reconocedor = reconocedor; // K
        this.sintetizador = sintetizador;
        this.modelo = modelo; // K
        this.herramientas = herramientas;
        this.archivos = archivos; // K
        this.mensajesMemoria = propiedades.getLlm().getMensajesMemoria();
        this.memoria = nuevaMemoria();
        this.herramientas.setNotificador(d -> oyente.herramienta(d));
        this.archivos.setNotificador(d -> oyente.herramienta(d));
    }

    private MessageWindowChatMemory nuevaMemoria() { // K
        return MessageWindowChatMemory.withMaxMessages(mensajesMemoria);
    }

    private Asistente construirAsistente(MessageWindowChatMemory memoriaDelTurno) {
        return AiServices.builder(Asistente.class)
                .chatLanguageModel(modelo)
                .chatMemory(memoriaDelTurno)
                .tools(herramientas, archivos)
                .build();
    }

    /** Copia de la memoria actual: el turno trabaja sobre la copia y solo se adopta si termina bien. */
    private MessageWindowChatMemory copiarMemoria() {
        MessageWindowChatMemory copia = nuevaMemoria(); // Y
        for (ChatMessage m : memoria.messages()) { // Y
            copia.add(m);
        }
        return copia;
    }

    /** Olvida la conversación (por ejemplo al cerrar sesión, para que otro usuario no la vea). */
    public void nuevaConversacion() {
        this.memoria = nuevaMemoria();
        historial.terminarConversacion();
    } // Y

    /** Registra quién escucha los avances (ventana principal, modo flotante...). */
    public void agregarOyente(Oyente o) {
        if (o != null && !oyentes.contains(o)) { // K
            oyentes.add(o);
        }
    } // Y

    public void quitarOyente(Oyente o) {
        oyentes.remove(o); // K
    }

    /** Deja un único oyente (compatibilidad). */
    public void setOyente(Oyente o) { // K
        oyentes.clear();
        agregarOyente(o); // Y
    }

    public void setVozActivada(boolean vozActivada) {
        this.vozActivada = vozActivada;
    }

    public Estado getEstado() {
        return estado;
    }

    /** Carga el modelo de voz en segundo plano para que el primer turno sea rapido. */
    public void precargar() {
        if (!ocupado.compareAndSet(false, true)) {
            return;
        }
        hilo.submit(() -> {
            try {
                cambiarEstado(Estado.CARGANDO);
                reconocedor.cargarModelo(); // Y
            } catch (Exception e) {
                log.error("No se pudo cargar el reconocedor de voz", e);
                oyente.error(e.getMessage()); // K
            } finally {
                terminarTurno();
            }
        }); // Y
    }

    /** Turno por voz: escucha una frase, la responde y la lee en voz alta. */
    public void turnoPorVoz() {
        if (!ocupado.compareAndSet(false, true)) {
            return;
        }
        Turno t = new Turno();
        turno = t;
        hilo.submit(() -> { // Y
            try {
                cambiarEstado(Estado.ESCUCHANDO);
                Transcripcion tr = reconocedor.escucharFrase(p -> oyente.textoParcial(p), () -> {
                    oyente.textoParcial(""); // Y
                    cambiarEstado(Estado.PENSANDO);
                });
                oyente.textoParcial("");
                if (t.cancelado.get()) {
                    oyente.interrumpido(null);
                    return;
                }
                if (tr.vacia()) {
                    oyente.error("No te escuché. Habla cerca del micrófono y pulsa de nuevo; "
                            + "si el problema sigue, revisa el volumen del micrófono en Windows.");
                    return;
                } // K
                String texto = NormalizadorTranscripcion.formatear(tr.texto());
                // Un comando conocido es fiable; el texto libre con poca confianza se deja corregir antes de enviarlo
                if (!tr.comando() && tr.confianza() < propiedades.getVoz().getConfianzaMinima()) {
                    log.info("Confianza baja ({}%): se pide confirmar «{}»", Math.round(tr.confianza() * 100), texto); // Y
                    oyente.confirmarTexto(texto);
                    return; // Y
                }
                procesar(texto, t); // K
            } catch (Exception e) { // K
                if (t.cancelado.get()) {
                    oyente.interrumpido(null);
                } else {
                    log.error("Fallo el turno por voz", e); // K
                    oyente.error(mensajeDe(e));
                }
            } finally {
                terminarTurno();
            }
        });
    } // Y

    /** Turno escrito: igual que por voz pero sin microfono. */
    public void turnoPorTexto(String texto) {
        if (texto == null || texto.isBlank() || !ocupado.compareAndSet(false, true)) {
            return;
        }
        Turno t = new Turno();
        turno = t; // Y
        hilo.submit(() -> {
            try { // K
                procesar(texto.strip(), t);
            } catch (Exception e) {
                if (t.cancelado.get()) {
                    oyente.interrumpido(texto.strip());
                } else { // K
                    log.error("Fallo el turno escrito", e);
                    oyente.error(mensajeDe(e));
                }
            } finally {
                terminarTurno(); // K
            }
        });
    }

    /** Termina de escuchar (y transcribe) o calla la respuesta en curso. */
    public void detener() { // K
        reconocedor.detener(); // K
        sintetizador.detener(); // K
    }

    /**
     * Interrumpe lo que Atlas esté haciendo (escuchar, pensar o hablar) sin enviar ni ejecutar nada más.
     * Si ya había un pedido, la interfaz lo recibe en {@link Oyente#interrumpido(String)} para que el usuario lo corrija.
     */
    public void interrumpir() {
        Turno t = turno;
        if (t == null || estado == Estado.INACTIVO) {
            return;
        }
        log.info("El usuario interrumpió a Atlas (estado: {})", estado);
        t.cancelado.set(true);
        reconocedor.cancelar(); // K
        sintetizador.detener();
        Future<?> f = t.modelo;
        if (f != null) {
            f.cancel(true);
        }
    }

    private void procesar(String texto, Turno t) throws Exception {
        oyente.mensajeUsuario(texto); // K
        cambiarEstado(Estado.PENSANDO); // K

        MessageWindowChatMemory copia = copiarMemoria(); // Y
        Asistente asistente = construirAsistente(copia); // K
        Future<String> llamada = hiloModelo.submit(() -> { // K
            HerramientasSistema.TURNO.set(t.cancelado); // K
            try {
                return asistente.responder(texto);
            } finally {
                HerramientasSistema.TURNO.remove(); // K
            } // Y
        });
        t.modelo = llamada;
        if (t.cancelado.get()) {
            llamada.cancel(true);
        }

        String respuesta;
        try {
            respuesta = llamada.get();
        } catch (CancellationException e) {
            oyente.interrumpido(texto);
            return;
        } catch (ExecutionException e) { // Y
            if (t.cancelado.get()) {
                oyente.interrumpido(texto);
                return;
            } // K
            Throwable causa = e.getCause();
            throw causa instanceof Exception ex ? ex : e; // Y
        }
        if (t.cancelado.get()) {
            oyente.interrumpido(texto); // Y
            return;
        }

        memoria = copia; // el turno terminó bien: esta memoria pasa a ser la de la conversación
        String limpia = limpiarParaVoz(respuesta);
        oyente.mensajeAsistente(limpia);
        historial.registrar(true, texto);
        historial.registrar(false, limpia); // Y
        if (vozActivada && !t.cancelado.get()) {
            cambiarEstado(Estado.HABLANDO);
            sintetizador.hablar(limpia);
        }
    } // K

    /** Quita marcas de markdown que el modelo pueda colar y que suenan mal al leerse. */
    static String limpiarParaVoz(String texto) {
        return texto
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("[*_#`>]+", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("[ \\t]+", " ")
                .strip();
    }

    private String mensajeDe(Exception e) { // Y
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("Connection refused") || m.contains("ConnectException") || m.contains("Failed to connect")) { // K
            return "No pude conectar con Ollama. Verifica que esté abierto.";
        }
        return m;
    } // Y

    private void cambiarEstado(Estado nuevo) { // Y
        estado = nuevo; // K
        oyente.estado(nuevo);
    }

    private void terminarTurno() {
        turno = null;
        cambiarEstado(Estado.INACTIVO);
        ocupado.set(false);
    }

    @PreDestroy
    void liberar() { // K
        detener(); // K
        hilo.shutdownNow();
        hiloModelo.shutdownNow(); // K
    }

    /** Reparte cada aviso a todos los oyentes; el fallo de uno no afecta a los demás. */
    private final class Difusor implements Oyente {
        private void paraTodos(java.util.function.Consumer<Oyente> accion) {
            for (Oyente o : oyentes) {
                try { // K
                    accion.accept(o);
                } catch (RuntimeException e) {
                    log.warn("Un oyente falló", e);
                }
            }
        }

        @Override public void estado(Estado e) { paraTodos(o -> o.estado(e)); }
        @Override public void textoParcial(String t) { paraTodos(o -> o.textoParcial(t)); }
        @Override public void mensajeUsuario(String t) { paraTodos(o -> o.mensajeUsuario(t)); }
        @Override public void mensajeAsistente(String t) { paraTodos(o -> o.mensajeAsistente(t)); }
        @Override public void error(String m) { paraTodos(o -> o.error(m)); }
        @Override public void herramienta(String d) { paraTodos(o -> o.herramienta(d)); }
        @Override public void confirmarTexto(String t) { paraTodos(o -> o.confirmarTexto(t)); }
        @Override public void interrumpido(String p) { paraTodos(o -> o.interrumpido(p)); }
    }
}
