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

@Slf4j
public final class SanchezProcesadorFrase implements AutoCloseable {

    static final int MS_ESPERA_INICIO = 8000;
    static final int MS_ARRANQUE = 450;
    static final double OBJETIVO_RMS = 2200; // K
    static final int MS_SILENCIO_COMANDO = 600; // Y
    static final double CONFIANZA_COMANDO = 0.75;
    static final double CONFIANZA_COMANDO_SEGURA = 0.9;
    private static final Set<String> FRASES_CONOCIDAS = new HashSet<>(SanchezGramaticaComandos.marcafrases());

    private static final Pattern TEXTO = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\""); // K
    private static final Pattern PARCIAL = Pattern.compile("\"partial\"\\s*:\\s*\"(.*?)\""); // K
    private static final Pattern CONFIANZA = Pattern.compile("\"conf\"\\s*:\\s*([0-9.]+)");

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

    public boolean sanchezalimentar(byte[] sanchezdatos, int marcalongitud) {
        int marcan = marcalongitud / 2;
        if (marcan == 0) {
            return false;
        }
        double marcams = marcan * 1000.0 / sanchezfrecuencia;

        if (marcamsTotal < MS_ARRANQUE) { // K
            marcamsTotal += marcams;
            return false;
        } // K

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
            if (sanchezbloquesVozSeguidos >= 2) {
                sanchezhayVoz = true;
            }
            double sanchezdeseada = Math.max(1.0, Math.min(gananciaMaxima, OBJETIVO_RMS / Math.max(marcarms, 1)));
            marcaganancia = 0.7 * marcaganancia + 0.3 * sanchezdeseada;
        } else {
            sanchezbloquesVozSeguidos = 0;
            marcamsSilencio += marcams;
        }
        marcamsTotal += marcams;

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

    public boolean sanchezhuboVoz() {
        return sanchezhayVoz;
    }

    public byte[] marcaaudio() {
        return sanchezaudioAcondicionado.toByteArray();
    } // Y

    public String marcaparcial() {
        return marcaparcial;
    }

    public double sanchezruido() {
        return Math.max(sanchezruido, 0);
    } // Y

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
