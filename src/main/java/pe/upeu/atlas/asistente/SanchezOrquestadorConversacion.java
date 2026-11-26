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

@Slf4j
@Service
public class SanchezOrquestadorConversacion {

    public enum MarcaEstado { INACTIVO, CARGANDO, ESCUCHANDO, PENSANDO, HABLANDO }

    public interface MarcaOyente {
        void marcaestado(MarcaEstado marcaestado);
        void marcatextoParcial(String marcatexto);
        void sanchezmensajeUsuario(String marcatexto);
        void sanchezmensajeAsistente(String marcatexto);
        void error(String sanchezmensaje);

        default void marcaherramienta(String sanchezdescripcion) { }

        default void sanchezconfirmarTexto(String marcatexto) { }

        default void marcainterrumpido(String sanchezpedido) { }
    }

    private static final class MarcaTurno {
        final AtomicBoolean marcacancelado = new AtomicBoolean(false);
        volatile Future<?> modelo;
    }

    private final MarcaReconocedorVoz sanchezreconocedor;
    private final SanchezSintetizadorVoz marcasintetizador;
    private final ChatLanguageModel modelo;
    private final SanchezHerramientasSistema sanchezherramientas;
    private final MarcaHerramientasArchivos sanchezarchivos;
    private final SanchezServicioHistorial sanchezhistorial;
    private final SanchezPropiedadesAtlas marcapropiedades;
    private final int mensajesMemoria;
    private volatile MessageWindowChatMemory marcamemoria;

    private final ExecutorService marcahilo = Executors.newSingleThreadExecutor(marcar -> {
        Thread marcat = new Thread(marcar, "atlas-conversacion");
        marcat.setDaemon(true);
        return marcat;
    });
    private final ExecutorService marcahiloModelo = Executors.newCachedThreadPool(marcar -> {
        Thread marcat = new Thread(marcar, "atlas-modelo");
        marcat.setDaemon(true);
        return marcat;
    });
    private final AtomicBoolean sanchezocupado = new AtomicBoolean(false);
    private final List<MarcaOyente> sanchezoyentes = new CopyOnWriteArrayList<>();
    private final MarcaOyente marcaoyente = new MarcaDifusor();
    private volatile MarcaEstado marcaestado = MarcaEstado.INACTIVO;
    private volatile MarcaTurno marcaturno;
    private volatile boolean marcavozActivada = true;

    public SanchezOrquestadorConversacion(MarcaReconocedorVoz sanchezreconocedor, SanchezSintetizadorVoz marcasintetizador,
                                   ChatLanguageModel marcamodelo, SanchezPropiedadesAtlas marcapropiedades,
                                   SanchezHerramientasSistema sanchezherramientas, MarcaHerramientasArchivos sanchezarchivos,
                                   SanchezServicioHistorial sanchezhistorial) {
        this.sanchezhistorial = sanchezhistorial;
        this.marcapropiedades = marcapropiedades;
        this.sanchezreconocedor = sanchezreconocedor;
        this.marcasintetizador = marcasintetizador;
        this.modelo = marcamodelo;
        this.sanchezherramientas = sanchezherramientas;
        this.sanchezarchivos = sanchezarchivos;
        this.mensajesMemoria = marcapropiedades.getLlm().getMensajesMemoria();
        this.marcamemoria = sancheznuevaMemoria();
        this.sanchezherramientas.marcasetNotificador(marcad -> marcaoyente.marcaherramienta(marcad));
        this.sanchezarchivos.marcasetNotificador(marcad -> marcaoyente.marcaherramienta(marcad));
    }

    private MessageWindowChatMemory sancheznuevaMemoria() {
        return MessageWindowChatMemory.withMaxMessages(mensajesMemoria);
    }

    private MarcaAsistente sanchezconstruirAsistente(MessageWindowChatMemory sanchezmemoriaDelTurno) {
        return AiServices.builder(MarcaAsistente.class)
                .chatLanguageModel(modelo)
                .chatMemory(sanchezmemoriaDelTurno)
                .tools(sanchezherramientas, sanchezarchivos)
                .build();
    }

    private MessageWindowChatMemory marcacopiarMemoria() {
        MessageWindowChatMemory marcacopia = sancheznuevaMemoria();
        for (ChatMessage sanchezm : marcamemoria.messages()) {
            marcacopia.add(sanchezm);
        }
        return marcacopia;
    }

    public void sancheznuevaConversacion() {
        this.marcamemoria = sancheznuevaMemoria();
        sanchezhistorial.marcaterminarConversacion();
    }

    public void sanchezagregarOyente(MarcaOyente sanchezo) {
        if (sanchezo != null && !sanchezoyentes.contains(sanchezo)) {
            sanchezoyentes.add(sanchezo);
        }
    }

    public void marcaquitarOyente(MarcaOyente sanchezo) {
        sanchezoyentes.remove(sanchezo);
    }

    public void marcasetOyente(MarcaOyente sanchezo) {
        sanchezoyentes.clear();
        sanchezagregarOyente(sanchezo);
    }

    public void marcasetVozActivada(boolean marcavozActivada) {
        this.marcavozActivada = marcavozActivada;
    }

    public MarcaEstado marcagetEstado() {
        return marcaestado;
    }

    public void sanchezprecargar() {
        if (!sanchezocupado.compareAndSet(false, true)) {
            return;
        }
        marcahilo.submit(() -> {
            try {
                sanchezcambiarEstado(MarcaEstado.CARGANDO);
                sanchezreconocedor.marcacargarModelo();
            } catch (Exception sancheze) {
                log.error("No se pudo cargar el reconocedor de voz", sancheze);
                marcaoyente.error(sancheze.getMessage());
            } finally {
                marcaterminarTurno();
            }
        });
    }

    public void marcaturnoPorVoz() {
        if (!sanchezocupado.compareAndSet(false, true)) {
            return;
        }
        MarcaTurno marcat = new MarcaTurno();
        marcaturno = marcat;
        marcahilo.submit(() -> {
            try {
                sanchezcambiarEstado(MarcaEstado.ESCUCHANDO);
                SanchezTranscripcion marcatr = sanchezreconocedor.sanchezescucharFrase(marcap -> marcaoyente.marcatextoParcial(marcap), () -> {
                    marcaoyente.marcatextoParcial("");
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
                }
                String marcatexto = SanchezNormalizadorTranscripcion.sanchezformatear(marcatr.texto());
                if (!marcatr.sanchezcomando() && marcatr.sanchezconfianza() < marcapropiedades.getVoz().getConfianzaMinima()) {
                    log.info("Confianza baja ({}%): se pide confirmar «{}»", Math.round(marcatr.sanchezconfianza() * 100), marcatexto);
                    marcaoyente.sanchezconfirmarTexto(marcatexto);
                    return;
                }
                sanchezprocesar(marcatexto, marcat);
            } catch (Exception sancheze) {
                if (marcat.marcacancelado.get()) {
                    marcaoyente.marcainterrumpido(null);
                } else {
                    log.error("Fallo el turno por voz", sancheze);
                    marcaoyente.error(marcamensajeDe(sancheze));
                }
            } finally {
                marcaterminarTurno();
            }
        });
    }

    public void sanchezturnoPorTexto(String marcatexto) {
        if (marcatexto == null || marcatexto.isBlank() || !sanchezocupado.compareAndSet(false, true)) {
            return;
        }
        MarcaTurno marcat = new MarcaTurno();
        marcaturno = marcat;
        marcahilo.submit(() -> {
            try {
                sanchezprocesar(marcatexto.strip(), marcat);
            } catch (Exception sancheze) {
                if (marcat.marcacancelado.get()) {
                    marcaoyente.marcainterrumpido(marcatexto.strip());
                } else {
                    log.error("Fallo el turno escrito", sancheze);
                    marcaoyente.error(marcamensajeDe(sancheze));
                }
            } finally {
                marcaterminarTurno();
            }
        });
    }

    public void sanchezdetener() {
        sanchezreconocedor.sanchezdetener();
        marcasintetizador.sanchezdetener();
    }

    public void sanchezinterrumpir() {
        MarcaTurno marcat = marcaturno;
        if (marcat == null || marcaestado == MarcaEstado.INACTIVO) {
            return;
        }
        log.info("El usuario interrumpió a Atlas (estado: {})", marcaestado);
        marcat.marcacancelado.set(true);
        sanchezreconocedor.sanchezcancelar();
        marcasintetizador.sanchezdetener();
        Future<?> marcaf = marcat.modelo;
        if (marcaf != null) {
            marcaf.cancel(true);
        }
    }

    private void sanchezprocesar(String marcatexto, MarcaTurno marcat) throws Exception {
        marcaoyente.sanchezmensajeUsuario(marcatexto);
        sanchezcambiarEstado(MarcaEstado.PENSANDO);

        MessageWindowChatMemory marcacopia = marcacopiarMemoria();
        MarcaAsistente marcaasistente = sanchezconstruirAsistente(marcacopia);
        Future<String> marcallamada = marcahiloModelo.submit(() -> {
            SanchezHerramientasSistema.TURNO.set(marcat.marcacancelado);
            try {
                return marcaasistente.marcaresponder(marcatexto);
            } finally {
                SanchezHerramientasSistema.TURNO.remove();
            }
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
        } catch (ExecutionException sancheze) {
            if (marcat.marcacancelado.get()) {
                marcaoyente.marcainterrumpido(marcatexto);
                return;
            }
            Throwable sanchezcausa = sancheze.getCause();
            throw sanchezcausa instanceof Exception sanchezex ? sanchezex : sancheze;
        }
        if (marcat.marcacancelado.get()) {
            marcaoyente.marcainterrumpido(marcatexto);
            return;
        }

        marcamemoria = marcacopia;
        String marcalimpia = sanchezlimpiarParaVoz(marcarespuesta);
        marcaoyente.sanchezmensajeAsistente(marcalimpia);
        sanchezhistorial.sanchezregistrar(true, marcatexto);
        sanchezhistorial.sanchezregistrar(false, marcalimpia);
        if (marcavozActivada && !marcat.marcacancelado.get()) {
            sanchezcambiarEstado(MarcaEstado.HABLANDO);
            marcasintetizador.marcahablar(marcalimpia);
        }
    }

    static String sanchezlimpiarParaVoz(String marcatexto) {
        return marcatexto
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("[*_#`>]+", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("[ \\t]+", " ")
                .strip();
    }

    private String marcamensajeDe(Exception sancheze) {
        String sanchezm = sancheze.getMessage() == null ? sancheze.getClass().getSimpleName() : sancheze.getMessage();
        if (sanchezm.contains("Connection refused") || sanchezm.contains("ConnectException") || sanchezm.contains("Failed to connect")) {
            return "No pude conectar con Ollama. Verifica que esté abierto.";
        }
        return sanchezm;
    }

    private void sanchezcambiarEstado(MarcaEstado sancheznuevo) {
        marcaestado = sancheznuevo;
        marcaoyente.marcaestado(sancheznuevo);
    }

    private void marcaterminarTurno() {
        marcaturno = null;
        sanchezcambiarEstado(MarcaEstado.INACTIVO);
        sanchezocupado.set(false);
    }

    @PreDestroy
    void sanchezliberar() {
        sanchezdetener();
        marcahilo.shutdownNow();
        marcahiloModelo.shutdownNow();
    }

    private final class MarcaDifusor implements MarcaOyente {
        private void sanchezparaTodos(java.util.function.Consumer<MarcaOyente> sanchezaccion) {
            for (MarcaOyente sanchezo : sanchezoyentes) {
                try {
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
