// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import lombok.extern.slf4j.Slf4j;
import org.vosk.Model;
import org.vosk.Recognizer;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procesa el audio de UNA frase, bloque a bloque, y decide cuándo terminó. No toca el micrófono: recibe
 * PCM de 16 bits mono, así que se puede probar con grabaciones.
 *
 * <p>Lo que hace, en orden, con cada bloque:
 * <ol>
 *   <li>Quita componente continua y rumbo grave (filtro paso alto).</li>
 *   <li>Mide el ruido de fondo al inicio y fija un umbral de voz que se adapta (o usa el configurado).</li>
 *   <li>Amplifica tu voz hacia un nivel cómodo para el reconocedor (control automático de ganancia).</li>
 *   <li>Alimenta el reconocedor libre y el de comandos, y <b>conserva todos los segmentos</b> que Vosk
 *       va cerrando (antes se perdían y Atlas decía «No te escuché»).</li>
 *   <li>Termina cuando hay {@code milisegundosSilencio} de silencio tras hablar, al llegar al máximo, o
 *       antes si ya reconoció un comando completo.</li>
 * </ol>
 */
@Slf4j
public final class SanchezProcesadorFrase implements AutoCloseable {

    /** Cuánto se espera a que el usuario empiece a hablar. */
    static final int MS_ESPERA_INICIO = 8000;
    /** Los primeros ms de un micrófono suelen traer un "pop" de arranque: no cuentan como voz. */
    static final int MS_ARRANQUE = 450;
    /** Nivel RMS al que se intenta llevar tu voz. */
    static final double OBJETIVO_RMS = 2200; // K
    /** Silencio tras un comando completo para responder sin esperar todo el tiempo de silencio. */
    static final int MS_SILENCIO_COMANDO = 600; // Y
    static final double CONFIANZA_COMANDO = 0.75;
    /** Con esta confianza un comando se acepta aunque el reconocedor libre haya entendido algo distinto. */
    static final double CONFIANZA_COMANDO_SEGURA = 0.9;
    private static final Set<String> FRASES_CONOCIDAS = new HashSet<>(SanchezGramaticaComandos.marcafrases());

    private static final Pattern TEXTO = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\""); // K
    private static final Pattern PARCIAL = Pattern.compile("\"partial\"\\s*:\\s*\"(.*?)\""); // K
    private static final Pattern CONFIANZA = Pattern.compile("\"conf\"\\s*:\\s*([0-9.]+)");

    /** Frase cerrada por Vosk: texto y suma de confianzas de sus palabras. */
    private record MarcaSegmento(String texto, double sanchezsumaConfianza, int marcapalabras) {
        boolean marcavacio() {
            return texto.isBlank();
        }
    } // Y

    private final Recognizer marcalibre;
    private final Recognizer marcacomandos;
    private final double sanchezfrecuencia; // K
    private final int sanchezumbralFijo;
    private final double gananciaMaxima;
    private final int sanchezmsSilencioFin;
    private final int sanchezmsMaximo;

    private final List<MarcaSegmento> sanchezsegLibre = new ArrayList<>();
    private final List<MarcaSegmento> sanchezsegComandos = new ArrayList<>();
    private String marcaparcial = ""; // Y

    private final java.io.ByteArrayOutputStream sanchezaudioAcondicionado = new java.io.ByteArrayOutputStream();
    private final java.util.ArrayDeque<Double> marcarecientes = new java.util.ArrayDeque<>(); // K
    private double sanchezruido = -1;
    private double sanchezpico;
    private double marcaganancia = 1.0;
    private double sanchezxPrevia;
    private double marcayPrevia;
    private double marcamsTotal;
    private double marcamsSilencio;
    private int sanchezbloquesVozSeguidos;
    private boolean sanchezhayVoz;
    private double marcaultimoRms; // K

    /**
     * @param gramatica JSON de frases para el reconocedor de comandos, o {@code null} para no usarlo
     */
    public SanchezProcesadorFrase(Model marcamodelo, SanchezPropiedadesAtlas.SanchezVoz marcaconfig, String sanchezgramatica) throws IOException {
        this.sanchezfrecuencia = marcaconfig.getFrecuenciaMuestreo();
        this.sanchezumbralFijo = marcaconfig.getUmbralSilencio(); // K
        this.gananciaMaxima = marcaconfig.getGananciaMaxima();
        this.sanchezmsSilencioFin = marcaconfig.getMilisegundosSilencio();
        this.sanchezmsMaximo = marcaconfig.getSegundosMaximosFrase() * 1000;
        this.marcalibre = new Recognizer(marcamodelo, (float) sanchezfrecuencia);
        this.marcalibre.setWords(true); // Y
        if (sanchezgramatica != null) {
            this.marcacomandos = new Recognizer(marcamodelo, (float) sanchezfrecuencia, sanchezgramatica); // K
            this.marcacomandos.setWords(true);
        } else {
            this.marcacomandos = null; // Y
        }
    } // K

    /**
     * Procesa un bloque de audio.
     *
     * @return {@code true} cuando la frase terminó y hay que llamar a {@link #finalizar()}
     */
    public boolean sanchezalimentar(byte[] sanchezdatos, int marcalongitud) {
        int marcan = marcalongitud / 2;
        if (marcan == 0) {
            return false;
        }
        double marcams = marcan * 1000.0 / sanchezfrecuencia;

        // 0) El clic del ratón al pulsar el micrófono y el "pop" de arranque caen en los primeros ms:
        //    no deben llegar al reconocedor (inventa palabras con ellos) ni a la medición del ruido
        if (marcamsTotal < MS_ARRANQUE) { // K
            marcamsTotal += marcams;
            return false;
        } // K

        // 1) Paso alto de primer orden: quita la componente continua y el rumbo grave
        double[] marcaf = new double[marcan];
        double marcasuma = 0; // K
        for (int sanchezi = 0; sanchezi < marcan; sanchezi++) {
            double marcax = (short) ((sanchezdatos[2 * sanchezi + 1] << 8) | (sanchezdatos[2 * sanchezi] & 0xFF));
            double sanchezy = marcax - sanchezxPrevia + 0.995 * marcayPrevia; // K
            sanchezxPrevia = marcax;
            marcayPrevia = sanchezy; // Y
            marcaf[sanchezi] = sanchezy;
            marcasuma += sanchezy * sanchezy;
        } // Y
        double marcarms = Math.sqrt(marcasuma / marcan); // K
        marcaultimoRms = marcarms;

        // 2) Ruido de fondo (el nivel más bajo de los últimos ~2 s: así no depende de que el inicio sea
        //    silencioso, porque el usuario suele hablar nada más pulsar) y detección de voz
        marcarecientes.addLast(marcarms); // K
        if (marcarecientes.size() > 20) { // K
            marcarecientes.removeFirst();
        } // Y
        sanchezruido = marcarecientes.stream().mapToDouble(Double::doubleValue).min().orElse(marcarms);
        sanchezpico = Math.max(sanchezpico, marcarms);
        boolean sanchezvoz = marcarms >= sanchezumbral();
        if (sanchezvoz) { // Y
            sanchezbloquesVozSeguidos++; // Y
            marcamsSilencio = 0;
            // Dos bloques seguidos: un golpe o un clic no cuentan como voz
            if (sanchezbloquesVozSeguidos >= 2) {
                sanchezhayVoz = true;
            }
            // 3) Control automático de ganancia: solo mientras hay voz
            double sanchezdeseada = Math.max(1.0, Math.min(gananciaMaxima, OBJETIVO_RMS / Math.max(marcarms, 1)));
            marcaganancia = 0.7 * marcaganancia + 0.3 * sanchezdeseada;
        } else {
            sanchezbloquesVozSeguidos = 0;
            marcamsSilencio += marcams;
        }
        marcamsTotal += marcams;

        // 4) Reconocimiento
        byte[] marcasalida = new byte[marcan * 2];
        for (int sanchezi = 0; sanchezi < marcan; sanchezi++) {
            int marcav = (int) Math.max(-32768, Math.min(32767, Math.round(marcaf[sanchezi] * marcaganancia)));
            marcasalida[2 * sanchezi] = (byte) marcav;
            marcasalida[2 * sanchezi + 1] = (byte) (marcav >> 8);
        }
        sanchezaudioAcondicionado.write(marcasalida, 0, marcasalida.length); // K
        if (marcalibre.acceptWaveForm(marcasalida, marcasalida.length)) {
            sanchezanotar(sanchezsegLibre, marcalibre.getResult());
        } else {
            String marcap = sanchezextraer(PARCIAL, marcalibre.getPartialResult());
            if (!marcap.isEmpty()) {
                marcaparcial = marcap;
            }
        }
        if (marcacomandos != null && marcacomandos.acceptWaveForm(marcasalida, marcasalida.length)) {
            sanchezanotar(sanchezsegComandos, marcacomandos.getResult()); // K
        }
        if (!sanchezsegLibre.isEmpty()) { // K
            sanchezhayVoz = true;
        }

        // 5) ¿Terminó la frase?
        if (!sanchezhayVoz && marcamsTotal >= MS_ESPERA_INICIO) {
            return true;
        }
        if (sanchezhayVoz && marcamsSilencio >= sanchezmsSilencioFin) {
            return true;
        } // K
        if (sanchezhayVoz && marcamsSilencio >= MS_SILENCIO_COMANDO && marcacomandoValido(sanchezsegComandos, sanchezsegLibre) != null) {
            return true;
        }
        return marcamsTotal >= sanchezmsMaximo;
    }

    /** {@code true} si se detectó voz (evita pedirle a Whisper que "oiga" silencio, donde inventa texto). */
    public boolean sanchezhuboVoz() {
        return sanchezhayVoz;
    }

    /** Audio de la frase ya filtrado y amplificado (PCM 16 bits mono), sin el arranque del micrófono. */
    public byte[] marcaaudio() {
        return sanchezaudioAcondicionado.toByteArray();
    } // Y

    /** Texto provisional de lo que se va entendiendo (para mostrarlo mientras se habla). */
    public String marcaparcial() {
        return marcaparcial;
    }

    public double sanchezruido() {
        return Math.max(sanchezruido, 0);
    } // Y

    /** Nivel por debajo del cual se considera silencio: sube con el ruido de fondo y con lo fuerte que hablas. */
    public double sanchezumbral() {
        if (sanchezumbralFijo > 0) {
            return sanchezumbralFijo;
        }
        double marcaporRuido = Math.min(Math.max(sanchezruido, 0), 100) * 3 + 15; // K
        double marcaporPico = sanchezpico * 0.02; // K
        return Math.max(40, Math.min(400, Math.max(marcaporRuido, marcaporPico)));
    } // Y

    public double marcaganancia() {
        return marcaganancia;
    }

    public double marcaultimoRms() { // Y
        return marcaultimoRms;
    }

    public double sanchezmilisegundos() {
        return marcamsTotal;
    } // K

    /** Cierra la frase y decide el texto final: comando conocido si lo hay; si no, lo entendido libremente. */
    public SanchezTranscripcion marcafinalizar() { // Y
        sanchezanotar(sanchezsegLibre, marcalibre.getFinalResult());
        if (marcacomandos != null) {
            sanchezanotar(sanchezsegComandos, marcacomandos.getFinalResult());
        }
        MarcaSegmento sanchezlibreUnido = marcaunir(sanchezsegLibre);
        MarcaSegmento sanchezcomando = marcacomandoValido(sanchezsegComandos, sanchezsegLibre); // K
        if (sanchezcomando != null) { // Y
            return new SanchezTranscripcion(sanchezcomando.texto(), sanchezpromedio(sanchezcomando), true, sanchezlibreUnido.texto());
        }
        return new SanchezTranscripcion(sanchezlibreUnido.texto(), sanchezpromedio(sanchezlibreUnido), false, sanchezlibreUnido.texto()); // Y
    }

    /**
     * El comando reconocido, solo si es EXACTAMENTE una frase conocida (el modo de vocabulario acotado a veces
     * "inventa" combinaciones de palabras), tiene confianza suficiente y no contradice lo que oyó el
     * reconocedor libre.
     */
    private static MarcaSegmento marcacomandoValido(List<MarcaSegmento> marcacomandos, List<MarcaSegmento> sanchezlibres) {
        MarcaSegmento sanchezs = marcaunir(marcacomandos);
        if (sanchezs.marcavacio() || sanchezs.texto().contains("[unk]") || !FRASES_CONOCIDAS.contains(sanchezs.texto())) {
            return null; // Y
        } // Y
        double sanchezconfianza = sanchezpromedio(sanchezs);
        if (sanchezconfianza < CONFIANZA_COMANDO) { // K
            return null; // K
        }
        MarcaSegmento marcalibre = marcaunir(sanchezlibres);
        if (marcalibre.marcavacio() || sanchezconfianza >= CONFIANZA_COMANDO_SEGURA) {
            return sanchezs;
        }
        // Debe compartir al menos un tercio de sus palabras con lo que se oyó libremente
        Set<String> marcaoidas = new HashSet<>(List.of(marcalibre.texto().split(" ")));
        String[] marcapalabras = sanchezs.texto().split(" ");
        long marcacomunes = java.util.Arrays.stream(marcapalabras).filter(marcaoidas::contains).count(); // Y
        return marcacomunes * 3 >= marcapalabras.length ? sanchezs : null; // K
    }

    private static MarcaSegmento marcaunir(List<MarcaSegmento> sanchezsegmentos) {
        StringBuilder marcatexto = new StringBuilder();
        double marcasuma = 0;
        int marcapalabras = 0; // Y
        for (MarcaSegmento sanchezs : sanchezsegmentos) { // K
            if (sanchezs.marcavacio()) {
                continue;
            }
            if (marcatexto.length() > 0) {
                marcatexto.append(' ');
            }
            marcatexto.append(sanchezs.texto()); // K
            marcasuma += sanchezs.sanchezsumaConfianza();
            marcapalabras += sanchezs.marcapalabras();
        }
        return new MarcaSegmento(marcatexto.toString().strip(), marcasuma, marcapalabras);
    }

    private static double sanchezpromedio(MarcaSegmento sanchezs) {
        return sanchezs.marcapalabras() == 0 ? 0 : sanchezs.sanchezsumaConfianza() / sanchezs.marcapalabras(); // K
    } // K

    private static void sanchezanotar(List<MarcaSegmento> sanchezlista, String marcajson) {
        String marcatexto = sanchezextraer(TEXTO, marcajson);
        if (marcatexto.isEmpty()) {
            return; // K
        }
        double marcasuma = 0; // K
        int marcapalabras = 0; // K
        Matcher sanchezm = CONFIANZA.matcher(marcajson); // K
        while (sanchezm.find()) {
            marcasuma += Double.parseDouble(sanchezm.group(1));
            marcapalabras++;
        }
        sanchezlista.add(new MarcaSegmento(marcatexto, marcasuma, marcapalabras));
    }

    static String sanchezextraer(Pattern marcapatron, String marcajson) {
        Matcher sanchezm = marcapatron.matcher(marcajson);
        return sanchezm.find() ? sanchezm.group(1).trim() : "";
    } // Y

    @Override
    public void close() { // K
        marcalibre.close();
        if (marcacomandos != null) {
            marcacomandos.close();
        }
    }
}
