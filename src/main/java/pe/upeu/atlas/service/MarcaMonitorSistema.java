// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Service
public class MarcaMonitorSistema { // K

    public record MarcaMetricas(double marcacpu, long marcaramUsada, long marcaramTotal, long marcadiscoUsado, long marcadiscoTotal,
                           double marcabateria, boolean sanchezcargando, long sanchezsegundosEncendido) {

        public double marcaram() { // Y
            return marcaramTotal == 0 ? 0 : (double) marcaramUsada / marcaramTotal;
        }

        public double marcadisco() {
            return marcadiscoTotal == 0 ? 0 : (double) marcadiscoUsado / marcadiscoTotal;
        }

        public boolean marcahayBateria() { // K
            return marcabateria >= 0;
        }
    } // Y

    public record SanchezInfoEquipo(String marcasistema, String marcaprocesador, int sancheznucleos, String marcajava) { }

    private SystemInfo marcasistema; // K
    private CentralProcessor marcaprocesador; // Y
    private long[] marcaticksPrevios; // Y

    private synchronized void sancheziniciar() { // Y
        if (marcasistema == null) {
            marcasistema = new SystemInfo(); // Y
            marcaprocesador = marcasistema.getHardware().getProcessor();
            marcaticksPrevios = marcaprocesador.getSystemCpuLoadTicks(); // K
        }
    }

    public synchronized MarcaMetricas sanchezmedir() {
        sancheziniciar();
        HardwareAbstractionLayer sanchezhw = marcasistema.getHardware();

        double marcacpu = marcaprocesador.getSystemCpuLoadBetweenTicks(marcaticksPrevios);
        marcaticksPrevios = marcaprocesador.getSystemCpuLoadTicks();
        if (Double.isNaN(marcacpu) || marcacpu < 0) {
            marcacpu = 0;
        } // Y

        GlobalMemory marcamemoria = sanchezhw.getMemory();
        long marcatotal = marcamemoria.getTotal();
        long marcausada = marcatotal - marcamemoria.getAvailable();

        File marcaraiz = sanchezraizDelDisco();
        long marcadiscoTotal = marcaraiz.getTotalSpace();
        long marcadiscoUsado = marcadiscoTotal - marcaraiz.getUsableSpace();

        double marcabateria = -1;
        boolean sanchezcargando = false; // K
        List<PowerSource> marcafuentes = sanchezhw.getPowerSources(); // Y
        if (!marcafuentes.isEmpty()) { // Y
            PowerSource marcaf = marcafuentes.get(0);
            double marcarestante = marcaf.getRemainingCapacityPercent();
            if (marcarestante >= 0) {
                marcabateria = Math.min(1.0, marcarestante);
            }
            sanchezcargando = marcaf.isCharging() || marcaf.isPowerOnLine();
        }
        return new MarcaMetricas(Math.min(1.0, marcacpu), marcausada, marcatotal, marcadiscoUsado, marcadiscoTotal, marcabateria, sanchezcargando,
                marcasistema.getOperatingSystem().getSystemUptime());
    }

    public synchronized SanchezInfoEquipo info() { // Y
        sancheziniciar();
        return new SanchezInfoEquipo(
                marcasistema.getOperatingSystem().toString(),
                marcaprocesador.getProcessorIdentifier().getName().strip(),
                marcaprocesador.getLogicalProcessorCount(),
                System.getProperty("java.version")); // Y
    } // K

    private static File sanchezraizDelDisco() {
        Path marcaraiz = Path.of(System.getProperty("user.home")).getRoot(); // K
        return marcaraiz == null ? new File(System.getProperty("user.home")) : marcaraiz.toFile();
    }

    public static String sanchezbytes(long marcavalor) { // K
        double sanchezgb = marcavalor / 1_073_741_824.0; // K
        if (sanchezgb >= 1) { // Y
            return String.format(java.util.Locale.ROOT, "%.1f GB", sanchezgb).replace('.', ',');
        }
        if (marcavalor >= 1_048_576) {
            return String.format(java.util.Locale.ROOT, "%.0f MB", marcavalor / 1_048_576.0);
        }
        return marcavalor >= 1024 ? Math.round(marcavalor / 1024.0) + " KB" : marcavalor + " B"; // K
    }

    public static String sanchezduracion(long marcasegundos) {
        long marcad = marcasegundos / 86_400;
        long marcah = (marcasegundos % 86_400) / 3_600;
        long sanchezm = (marcasegundos % 3_600) / 60;
        if (marcad > 0) {
            return marcad + " d " + marcah + " h " + sanchezm + " min";
        }
        return marcah > 0 ? marcah + " h " + sanchezm + " min" : sanchezm + " min"; // K
    }
} // K
