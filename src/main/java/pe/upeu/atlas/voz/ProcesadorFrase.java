// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import lombok.extern.slf4j.Slf4j;
import org.vosk.Model;
import org.vosk.Recognizer;
import pe.upeu.atlas.config.PropiedadesAtlas;

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
public final class ProcesadorFrase implements AutoCloseable {

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
    private static final Set<String> FRASES_CONOCIDAS = new HashSet<>(GramaticaComandos.frases());

    private static final Pattern TEXTO = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\""); // K
    private static final Pattern PARCIAL = Pattern.compile("\"partial\"\\s*:\\s*\"(.*?)\""); // K
    private static final Pattern CONFIANZA = Pattern.compile("\"conf\"\\s*:\\s*([0-9.]+)");

    /** Frase cerrada por Vosk: texto y suma de confianzas de sus palabras. */
    private record Segmento(String texto, double sumaConfianza, int palabras) {
        boolean vacio() {
            return texto.isBlank();
        }
    } // Y

    private final Recognizer libre;
    private final Recognizer comandos;
    private final double frecuencia; // K
    private final int umbralFijo;
    private final double gananciaMaxima;
    private final int msSilencioFin;
    private final int msMaximo;

    private final List<Segmento> segLibre = new ArrayList<>();
    private final List<Segmento> segComandos = new ArrayList<>();
    private String parcial = ""; // Y

    private final java.io.ByteArrayOutputStream audioAcondicionado = new java.io.ByteArrayOutputStream();
    private final java.util.ArrayDeque<Double> recientes = new java.util.ArrayDeque<>(); // K
    private double ruido = -1;
    private double pico;
    private double ganancia = 1.0;
    private double xPrevia;
    private double yPrevia;
    private double msTotal;
    private double msSilencio;
    private int bloquesVozSeguidos;
    private boolean hayVoz;
    private double ultimoRms; // K

    /**
     * @param gramatica JSON de frases para el reconocedor de comandos, o {@code null} para no usarlo
     */
    public ProcesadorFrase(Model modelo, PropiedadesAtlas.Voz config, String gramatica) throws IOException {
        this.frecuencia = config.getFrecuenciaMuestreo();
        this.umbralFijo = config.getUmbralSilencio(); // K
        this.gananciaMaxima = config.getGananciaMaxima();
        this.msSilencioFin = config.getMilisegundosSilencio();
        this.msMaximo = config.getSegundosMaximosFrase() * 1000;
        this.libre = new Recognizer(modelo, (float) frecuencia);
        this.libre.setWords(true); // Y
        if (gramatica != null) {
            this.comandos = new Recognizer(modelo, (float) frecuencia, gramatica); // K
            this.comandos.setWords(true);
        } else {
            this.comandos = null; // Y
        }
    } // K

    /**
     * Procesa un bloque de audio.
     *
     * @return {@code true} cuando la frase terminó y hay que llamar a {@link #finalizar()}
     */
    public boolean alimentar(byte[] datos, int longitud) {
        int n = longitud / 2;
        if (n == 0) {
            return false;
        }
        double ms = n * 1000.0 / frecuencia;

        // 0) El clic del ratón al pulsar el micrófono y el "pop" de arranque caen en los primeros ms:
        //    no deben llegar al reconocedor (inventa palabras con ellos) ni a la medición del ruido
        if (msTotal < MS_ARRANQUE) { // K
            msTotal += ms;
            return false;
        } // K

        // 1) Paso alto de primer orden: quita la componente continua y el rumbo grave
        double[] f = new double[n];
        double suma = 0; // K
        for (int i = 0; i < n; i++) {
            double x = (short) ((datos[2 * i + 1] << 8) | (datos[2 * i] & 0xFF));
            double y = x - xPrevia + 0.995 * yPrevia; // K
            xPrevia = x;
            yPrevia = y; // Y
            f[i] = y;
            suma += y * y;
        } // Y
        double rms = Math.sqrt(suma / n); // K
        ultimoRms = rms;

        // 2) Ruido de fondo (el nivel más bajo de los últimos ~2 s: así no depende de que el inicio sea
        //    silencioso, porque el usuario suele hablar nada más pulsar) y detección de voz
        recientes.addLast(rms); // K
        if (recientes.size() > 20) { // K
            recientes.removeFirst();
        } // Y
        ruido = recientes.stream().mapToDouble(Double::doubleValue).min().orElse(rms);
        pico = Math.max(pico, rms);
        boolean voz = rms >= umbral();
        if (voz) { // Y
            bloquesVozSeguidos++; // Y
            msSilencio = 0;
            // Dos bloques seguidos: un golpe o un clic no cuentan como voz
            if (bloquesVozSeguidos >= 2) {
                hayVoz = true;
            }
            // 3) Control automático de ganancia: solo mientras hay voz
            double deseada = Math.max(1.0, Math.min(gananciaMaxima, OBJETIVO_RMS / Math.max(rms, 1)));
            ganancia = 0.7 * ganancia + 0.3 * deseada;
        } else {
            bloquesVozSeguidos = 0;
            msSilencio += ms;
        }
        msTotal += ms;

        // 4) Reconocimiento
        byte[] salida = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int v = (int) Math.max(-32768, Math.min(32767, Math.round(f[i] * ganancia)));
            salida[2 * i] = (byte) v;
            salida[2 * i + 1] = (byte) (v >> 8);
        }
        audioAcondicionado.write(salida, 0, salida.length); // K
        if (libre.acceptWaveForm(salida, salida.length)) {
            anotar(segLibre, libre.getResult());
        } else {
            String p = extraer(PARCIAL, libre.getPartialResult());
            if (!p.isEmpty()) {
                parcial = p;
            }
        }
        if (comandos != null && comandos.acceptWaveForm(salida, salida.length)) {
            anotar(segComandos, comandos.getResult()); // K
        }
        if (!segLibre.isEmpty()) { // K
            hayVoz = true;
        }

        // 5) ¿Terminó la frase?
        if (!hayVoz && msTotal >= MS_ESPERA_INICIO) {
            return true;
        }
        if (hayVoz && msSilencio >= msSilencioFin) {
            return true;
        } // K
        if (hayVoz && msSilencio >= MS_SILENCIO_COMANDO && comandoValido(segComandos, segLibre) != null) {
            return true;
        }
        return msTotal >= msMaximo;
    }

    /** {@code true} si se detectó voz (evita pedirle a Whisper que "oiga" silencio, donde inventa texto). */
    public boolean huboVoz() {
        return hayVoz;
    }

    /** Audio de la frase ya filtrado y amplificado (PCM 16 bits mono), sin el arranque del micrófono. */
    public byte[] audio() {
        return audioAcondicionado.toByteArray();
    } // Y

    /** Texto provisional de lo que se va entendiendo (para mostrarlo mientras se habla). */
    public String parcial() {
        return parcial;
    }

    public double ruido() {
        return Math.max(ruido, 0);
    } // Y

    /** Nivel por debajo del cual se considera silencio: sube con el ruido de fondo y con lo fuerte que hablas. */
    public double umbral() {
        if (umbralFijo > 0) {
            return umbralFijo;
        }
        double porRuido = Math.min(Math.max(ruido, 0), 100) * 3 + 15; // K
        double porPico = pico * 0.02; // K
        return Math.max(40, Math.min(400, Math.max(porRuido, porPico)));
    } // Y

    public double ganancia() {
        return ganancia;
    }

    public double ultimoRms() { // Y
        return ultimoRms;
    }

    public double milisegundos() {
        return msTotal;
    } // K

    /** Cierra la frase y decide el texto final: comando conocido si lo hay; si no, lo entendido libremente. */
    public Transcripcion finalizar() { // Y
        anotar(segLibre, libre.getFinalResult());
        if (comandos != null) {
            anotar(segComandos, comandos.getFinalResult());
        }
        Segmento libreUnido = unir(segLibre);
        Segmento comando = comandoValido(segComandos, segLibre); // K
        if (comando != null) { // Y
            return new Transcripcion(comando.texto(), promedio(comando), true, libreUnido.texto());
        }
        return new Transcripcion(libreUnido.texto(), promedio(libreUnido), false, libreUnido.texto()); // Y
    }

    /**
     * El comando reconocido, solo si es EXACTAMENTE una frase conocida (el modo de vocabulario acotado a veces
     * "inventa" combinaciones de palabras), tiene confianza suficiente y no contradice lo que oyó el
     * reconocedor libre.
     */
    private static Segmento comandoValido(List<Segmento> comandos, List<Segmento> libres) {
        Segmento s = unir(comandos);
        if (s.vacio() || s.texto().contains("[unk]") || !FRASES_CONOCIDAS.contains(s.texto())) {
            return null; // Y
        } // Y
        double confianza = promedio(s);
        if (confianza < CONFIANZA_COMANDO) { // K
            return null; // K
        }
        Segmento libre = unir(libres);
        if (libre.vacio() || confianza >= CONFIANZA_COMANDO_SEGURA) {
            return s;
        }
        // Debe compartir al menos un tercio de sus palabras con lo que se oyó libremente
        Set<String> oidas = new HashSet<>(List.of(libre.texto().split(" ")));
        String[] palabras = s.texto().split(" ");
        long comunes = java.util.Arrays.stream(palabras).filter(oidas::contains).count(); // Y
        return comunes * 3 >= palabras.length ? s : null; // K
    }

    private static Segmento unir(List<Segmento> segmentos) {
        StringBuilder texto = new StringBuilder();
        double suma = 0;
        int palabras = 0; // Y
        for (Segmento s : segmentos) { // K
            if (s.vacio()) {
                continue;
            }
            if (texto.length() > 0) {
                texto.append(' ');
            }
            texto.append(s.texto()); // K
            suma += s.sumaConfianza();
            palabras += s.palabras();
        }
        return new Segmento(texto.toString().strip(), suma, palabras);
    }

    private static double promedio(Segmento s) {
        return s.palabras() == 0 ? 0 : s.sumaConfianza() / s.palabras(); // K
    } // K

    private static void anotar(List<Segmento> lista, String json) {
        String texto = extraer(TEXTO, json);
        if (texto.isEmpty()) {
            return; // K
        }
        double suma = 0; // K
        int palabras = 0; // K
        Matcher m = CONFIANZA.matcher(json); // K
        while (m.find()) {
            suma += Double.parseDouble(m.group(1));
            palabras++;
        }
        lista.add(new Segmento(texto, suma, palabras));
    }

    static String extraer(Pattern patron, String json) {
        Matcher m = patron.matcher(json);
        return m.find() ? m.group(1).trim() : "";
    } // Y

    @Override
    public void close() { // K
        libre.close();
        if (comandos != null) {
            comandos.close();
        }
    }
}
