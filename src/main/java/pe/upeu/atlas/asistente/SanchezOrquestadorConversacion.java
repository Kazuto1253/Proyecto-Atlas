// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.asistente.herramientas.MarcaHerramientasArchivos;
import pe.upeu.atlas.asistente.herramientas.SanchezHerramientasSistema;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.service.SanchezServicioHistorial;
import pe.upeu.atlas.voz.SanchezNormalizadorTranscripcion;
import pe.upeu.atlas.voz.MarcaReconocedorVoz;
import pe.upeu.atlas.voz.SanchezSintetizadorVoz;
import pe.upeu.atlas.voz.SanchezTranscripcion;

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
public class SanchezOrquestadorConversacion {

    public enum MarcaEstado { INACTIVO, CARGANDO, ESCUCHANDO, PENSANDO, HABLANDO }

    public interface MarcaOyente { // Y
        void marcaestado(MarcaEstado marcaestado);
        void marcatextoParcial(String marcatexto);
        void sanchezmensajeUsuario(String marcatexto);
        void sanchezmensajeAsistente(String marcatexto); // K
        void error(String sanchezmensaje); // K

        /** El modelo usó una herramienta (por ejemplo "Abrió la carpeta Descargas"). */
        default void marcaherramienta(String sanchezdescripcion) { }

        /** Lo entendido por voz tiene poca confianza: mostrarlo para que el usuario lo corrija y lo envíe. */
        default void sanchezconfirmarTexto(String marcatexto) { }

        /** El usuario interrumpió el turno; {@code pedido} es lo que había pedido (o {@code null} si aún no decía nada). */
        default void marcainterrumpido(String sanchezpedido) { }
    }

    /** Turno en curso: permite cancelarlo desde otro hilo. */
    private static final class MarcaTurno {
        final AtomicBoolean marcacancelado = new AtomicBoolean(false); // K
        volatile Future<?> modelo;
    }

    private final MarcaReconocedorVoz sanchezreconocedor;
    private final SanchezSintetizadorVoz marcasintetizador;
    private final ChatLanguageModel modelo;
    private final SanchezHerramientasSistema sanchezherramientas;
    private final MarcaHerramientasArchivos sanchezarchivos;
    private final SanchezServicioHistorial sanchezhistorial;
    private final SanchezPropiedadesAtlas marcapropiedades;
    private final int mensajesMemoria; // K
    private volatile MessageWindowChatMemory marcamemoria; // K

    private final ExecutorService marcahilo = Executors.newSingleThreadExecutor(marcar -> { // K
        Thread marcat = new Thread(marcar, "atlas-conversacion");
        marcat.setDaemon(true); // Y
        return marcat;
    });
    /** La llamada al modelo va en su propio hilo para poder abandonarla al interrumpir. */
    private final ExecutorService marcahiloModelo = Executors.newCachedThreadPool(marcar -> {
        Thread marcat = new Thread(marcar, "atlas-modelo");
        marcat.setDaemon(true);
        return marcat;
    });
    private final AtomicBoolean sanchezocupado = new AtomicBoolean(false);
    private final List<MarcaOyente> sanchezoyentes = new CopyOnWriteArrayList<>();
    private final MarcaOyente marcaoyente = new MarcaDifusor(); // K
    private volatile MarcaEstado marcaestado = MarcaEstado.INACTIVO; // Y
    private volatile MarcaTurno marcaturno;
    private volatile boolean marcavozActivada = true;

    public SanchezOrquestadorConversacion(MarcaReconocedorVoz sanchezreconocedor, SanchezSintetizadorVoz marcasintetizador,
                                   ChatLanguageModel marcamodelo, SanchezPropiedadesAtlas marcapropiedades,
                                   SanchezHerramientasSistema sanchezherramientas, MarcaHerramientasArchivos sanchezarchivos,
                                   SanchezServicioHistorial sanchezhistorial) { // Y
        this.sanchezhistorial = sanchezhistorial; // K
        this.marcapropiedades = marcapropiedades;
        this.sanchezreconocedor = sanchezreconocedor; // K
        this.marcasintetizador = marcasintetizador;
        this.modelo = marcamodelo; // K
        this.sanchezherramientas = sanchezherramientas;
        this.sanchezarchivos = sanchezarchivos; // K
        this.mensajesMemoria = marcapropiedades.getLlm().getMensajesMemoria();
        this.marcamemoria = sancheznuevaMemoria();
        this.sanchezherramientas.marcasetNotificador(marcad -> marcaoyente.marcaherramienta(marcad));
        this.sanchezarchivos.marcasetNotificador(marcad -> marcaoyente.marcaherramienta(marcad));
    }

    private MessageWindowChatMemory sancheznuevaMemoria() { // K
        return MessageWindowChatMemory.withMaxMessages(mensajesMemoria);
    }

    private MarcaAsistente sanchezconstruirAsistente(MessageWindowChatMemory sanchezmemoriaDelTurno) {
        return AiServices.builder(MarcaAsistente.class)
                .chatLanguageModel(modelo)
                .chatMemory(sanchezmemoriaDelTurno)
                .tools(sanchezherramientas, sanchezarchivos)
                .build();
    }

    /** Copia de la memoria actual: el turno trabaja sobre la copia y solo se adopta si termina bien. */
    private MessageWindowChatMemory marcacopiarMemoria() {
        MessageWindowChatMemory marcacopia = sancheznuevaMemoria(); // Y
        for (ChatMessage sanchezm : marcamemoria.messages()) { // Y
            marcacopia.add(sanchezm);
        }
        return marcacopia;
    }

    /** Olvida la conversación (por ejemplo al cerrar sesión, para que otro usuario no la vea). */
    public void sancheznuevaConversacion() {
        this.marcamemoria = sancheznuevaMemoria();
        sanchezhistorial.marcaterminarConversacion();
    } // Y

    /** Registra quién escucha los avances (ventana principal, modo flotante...). */
    public void sanchezagregarOyente(MarcaOyente sanchezo) {
        if (sanchezo != null && !sanchezoyentes.contains(sanchezo)) { // K
            sanchezoyentes.add(sanchezo);
        }
    } // Y

    public void marcaquitarOyente(MarcaOyente sanchezo) {
        sanchezoyentes.remove(sanchezo); // K
    }

    /** Deja un único oyente (compatibilidad). */
    public void marcasetOyente(MarcaOyente sanchezo) { // K
        sanchezoyentes.clear();
        sanchezagregarOyente(sanchezo); // Y
    }

    public void marcasetVozActivada(boolean marcavozActivada) {
        this.marcavozActivada = marcavozActivada;
    }

    public MarcaEstado marcagetEstado() {
        return marcaestado;
    }

    /** Carga el modelo de voz en segundo plano para que el primer turno sea rapido. */
    public void sanchezprecargar() {
        if (!sanchezocupado.compareAndSet(false, true)) {
            return;
        }
        marcahilo.submit(() -> {
            try {
                sanchezcambiarEstado(MarcaEstado.CARGANDO);
                sanchezreconocedor.marcacargarModelo(); // Y
            } catch (Exception sancheze) {
                log.error("No se pudo cargar el reconocedor de voz", sancheze);
                marcaoyente.error(sancheze.getMessage()); // K
            } finally {
                marcaterminarTurno();
            }
        }); // Y
    }

    /** Turno por voz: escucha una frase, la responde y la lee en voz alta. */
    public void marcaturnoPorVoz() {
        if (!sanchezocupado.compareAndSet(false, true)) {
            return;
        }
        MarcaTurno marcat = new MarcaTurno();
        marcaturno = marcat;
        marcahilo.submit(() -> { // Y
            try {
                sanchezcambiarEstado(MarcaEstado.ESCUCHANDO);
                SanchezTranscripcion marcatr = sanchezreconocedor.sanchezescucharFrase(marcap -> marcaoyente.marcatextoParcial(marcap), () -> {
                    marcaoyente.marcatextoParcial(""); // Y
                    sanchezcambiarEstado(MarcaEstado.PENSANDO);
                });
                marcaoyente.marcatextoParcial("");
                if (marcat.marcacancelado.get()) {
                    marcaoyente.marcainterrumpido(null);
                    return;
                }
                if (marcatr.marcavacia()) {
                    marcaoyente.error("No te escuché. Habla cerca del micrófono y pulsa de nuevo; "
                            + "si el problema sigue, revisa el volumen del micrófono en Windows.");
                    return;
                } // K
                String marcatexto = SanchezNormalizadorTranscripcion.sanchezformatear(marcatr.texto());
                // Un comando conocido es fiable; el texto libre con poca confianza se deja corregir antes de enviarlo
                if (!marcatr.sanchezcomando() && marcatr.sanchezconfianza() < marcapropiedades.getVoz().getConfianzaMinima()) {
                    log.info("Confianza baja ({}%): se pide confirmar «{}»", Math.round(marcatr.sanchezconfianza() * 100), marcatexto); // Y
                    marcaoyente.sanchezconfirmarTexto(marcatexto);
                    return; // Y
                }
                sanchezprocesar(marcatexto, marcat); // K
            } catch (Exception sancheze) { // K
                if (marcat.marcacancelado.get()) {
                    marcaoyente.marcainterrumpido(null);
                } else {
                    log.error("Fallo el turno por voz", sancheze); // K
                    marcaoyente.error(marcamensajeDe(sancheze));
                }
            } finally {
                marcaterminarTurno();
            }
        });
    } // Y

    /** Turno escrito: igual que por voz pero sin microfono. */
    public void sanchezturnoPorTexto(String marcatexto) {
        if (marcatexto == null || marcatexto.isBlank() || !sanchezocupado.compareAndSet(false, true)) {
            return;
        }
        MarcaTurno marcat = new MarcaTurno();
        marcaturno = marcat; // Y
        marcahilo.submit(() -> {
            try { // K
                sanchezprocesar(marcatexto.strip(), marcat);
            } catch (Exception sancheze) {
                if (marcat.marcacancelado.get()) {
                    marcaoyente.marcainterrumpido(marcatexto.strip());
                } else { // K
                    log.error("Fallo el turno escrito", sancheze);
                    marcaoyente.error(marcamensajeDe(sancheze));
                }
            } finally {
                marcaterminarTurno(); // K
            }
        });
    }

    /** Termina de escuchar (y transcribe) o calla la respuesta en curso. */
    public void sanchezdetener() { // K
        sanchezreconocedor.sanchezdetener(); // K
        marcasintetizador.sanchezdetener(); // K
    }

    /**
     * Interrumpe lo que Atlas esté haciendo (escuchar, pensar o hablar) sin enviar ni ejecutar nada más.
     * Si ya había un pedido, la interfaz lo recibe en {@link Oyente#interrumpido(String)} para que el usuario lo corrija.
     */
    public void sanchezinterrumpir() {
        MarcaTurno marcat = marcaturno;
        if (marcat == null || marcaestado == MarcaEstado.INACTIVO) {
            return;
        }
        log.info("El usuario interrumpió a Atlas (estado: {})", marcaestado);
        marcat.marcacancelado.set(true);
        sanchezreconocedor.sanchezcancelar(); // K
        marcasintetizador.sanchezdetener();
        Future<?> marcaf = marcat.modelo;
        if (marcaf != null) {
            marcaf.cancel(true);
        }
    }

    private void sanchezprocesar(String marcatexto, MarcaTurno marcat) throws Exception {
        marcaoyente.sanchezmensajeUsuario(marcatexto); // K
        sanchezcambiarEstado(MarcaEstado.PENSANDO); // K

        MessageWindowChatMemory marcacopia = marcacopiarMemoria(); // Y
        MarcaAsistente marcaasistente = sanchezconstruirAsistente(marcacopia); // K
        Future<String> marcallamada = marcahiloModelo.submit(() -> { // K
            SanchezHerramientasSistema.TURNO.set(marcat.marcacancelado); // K
            try {
                return marcaasistente.marcaresponder(marcatexto);
            } finally {
                SanchezHerramientasSistema.TURNO.remove(); // K
            } // Y
        });
        marcat.modelo = marcallamada;
        if (marcat.marcacancelado.get()) {
            marcallamada.cancel(true);
        }

        String marcarespuesta;
        try {
            marcarespuesta = marcallamada.get();
        } catch (CancellationException sancheze) {
            marcaoyente.marcainterrumpido(marcatexto);
            return;
        } catch (ExecutionException sancheze) { // Y
            if (marcat.marcacancelado.get()) {
                marcaoyente.marcainterrumpido(marcatexto);
                return;
            } // K
            Throwable sanchezcausa = sancheze.getCause();
            throw sanchezcausa instanceof Exception sanchezex ? sanchezex : sancheze; // Y
        }
        if (marcat.marcacancelado.get()) {
            marcaoyente.marcainterrumpido(marcatexto); // Y
            return;
        }

        marcamemoria = marcacopia; // el turno terminó bien: esta memoria pasa a ser la de la conversación
        String marcalimpia = sanchezlimpiarParaVoz(marcarespuesta);
        marcaoyente.sanchezmensajeAsistente(marcalimpia);
        sanchezhistorial.sanchezregistrar(true, marcatexto);
        sanchezhistorial.sanchezregistrar(false, marcalimpia); // Y
        if (marcavozActivada && !marcat.marcacancelado.get()) {
            sanchezcambiarEstado(MarcaEstado.HABLANDO);
            marcasintetizador.marcahablar(marcalimpia);
        }
    } // K

    /** Quita marcas de markdown que el modelo pueda colar y que suenan mal al leerse. */
    static String sanchezlimpiarParaVoz(String marcatexto) {
        return marcatexto
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("[*_#`>]+", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("[ \\t]+", " ")
                .strip();
    }

    private String marcamensajeDe(Exception sancheze) { // Y
        String sanchezm = sancheze.getMessage() == null ? sancheze.getClass().getSimpleName() : sancheze.getMessage();
        if (sanchezm.contains("Connection refused") || sanchezm.contains("ConnectException") || sanchezm.contains("Failed to connect")) { // K
            return "No pude conectar con Ollama. Verifica que esté abierto.";
        }
        return sanchezm;
    } // Y

    private void sanchezcambiarEstado(MarcaEstado sancheznuevo) { // Y
        marcaestado = sancheznuevo; // K
        marcaoyente.marcaestado(sancheznuevo);
    }

    private void marcaterminarTurno() {
        marcaturno = null;
        sanchezcambiarEstado(MarcaEstado.INACTIVO);
        sanchezocupado.set(false);
    }

    @PreDestroy
    void sanchezliberar() { // K
        sanchezdetener(); // K
        marcahilo.shutdownNow();
        marcahiloModelo.shutdownNow(); // K
    }

    /** Reparte cada aviso a todos los oyentes; el fallo de uno no afecta a los demás. */
    private final class MarcaDifusor implements MarcaOyente {
        private void sanchezparaTodos(java.util.function.Consumer<MarcaOyente> sanchezaccion) {
            for (MarcaOyente sanchezo : sanchezoyentes) {
                try { // K
                    sanchezaccion.accept(sanchezo);
                } catch (RuntimeException sancheze) {
                    log.warn("Un oyente falló", sancheze);
                }
            }
        }

        @Override public void marcaestado(MarcaEstado sancheze) { sanchezparaTodos(sanchezo -> sanchezo.marcaestado(sancheze)); }
        @Override public void marcatextoParcial(String marcat) { sanchezparaTodos(sanchezo -> sanchezo.marcatextoParcial(marcat)); }
        @Override public void sanchezmensajeUsuario(String marcat) { sanchezparaTodos(sanchezo -> sanchezo.sanchezmensajeUsuario(marcat)); }
        @Override public void sanchezmensajeAsistente(String marcat) { sanchezparaTodos(sanchezo -> sanchezo.sanchezmensajeAsistente(marcat)); }
        @Override public void error(String sanchezm) { sanchezparaTodos(sanchezo -> sanchezo.error(sanchezm)); }
        @Override public void marcaherramienta(String marcad) { sanchezparaTodos(sanchezo -> sanchezo.marcaherramienta(marcad)); }
        @Override public void sanchezconfirmarTexto(String marcat) { sanchezparaTodos(sanchezo -> sanchezo.sanchezconfirmarTexto(marcat)); }
        @Override public void marcainterrumpido(String marcap) { sanchezparaTodos(sanchezo -> sanchezo.marcainterrumpido(marcap)); }
    }
}
