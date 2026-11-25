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

/** Lectura de CPU, memoria, disco y batería con OSHI. Las llamadas bloquean: no usarlas en el hilo de JavaFX. */
@Service
public class MonitorSistema { // K

    /** Fracciones de 0 a 1; {@code bateria} es -1 si el equipo no tiene batería. */
    public record Metricas(double cpu, long ramUsada, long ramTotal, long discoUsado, long discoTotal,
                           double bateria, boolean cargando, long segundosEncendido) {

        public double ram() { // Y
            return ramTotal == 0 ? 0 : (double) ramUsada / ramTotal;
        }

        public double disco() {
            return discoTotal == 0 ? 0 : (double) discoUsado / discoTotal;
        }

        public boolean hayBateria() { // K
            return bateria >= 0;
        }
    } // Y

    public record InfoEquipo(String sistema, String procesador, int nucleos, String java) { }

    private SystemInfo sistema; // K
    private CentralProcessor procesador; // Y
    private long[] ticksPrevios; // Y

    private synchronized void iniciar() { // Y
        if (sistema == null) {
            sistema = new SystemInfo(); // Y
            procesador = sistema.getHardware().getProcessor();
            ticksPrevios = procesador.getSystemCpuLoadTicks(); // K
        }
    }

    public synchronized Metricas medir() {
        iniciar();
        HardwareAbstractionLayer hw = sistema.getHardware();

        double cpu = procesador.getSystemCpuLoadBetweenTicks(ticksPrevios);
        ticksPrevios = procesador.getSystemCpuLoadTicks();
        if (Double.isNaN(cpu) || cpu < 0) {
            cpu = 0;
        } // Y

        GlobalMemory memoria = hw.getMemory();
        long total = memoria.getTotal();
        long usada = total - memoria.getAvailable();

        File raiz = raizDelDisco();
        long discoTotal = raiz.getTotalSpace();
        long discoUsado = discoTotal - raiz.getUsableSpace();

        double bateria = -1;
        boolean cargando = false; // K
        List<PowerSource> fuentes = hw.getPowerSources(); // Y
        if (!fuentes.isEmpty()) { // Y
            PowerSource f = fuentes.get(0);
            double restante = f.getRemainingCapacityPercent();
            if (restante >= 0) {
                bateria = Math.min(1.0, restante);
            }
            cargando = f.isCharging() || f.isPowerOnLine();
        }
        return new Metricas(Math.min(1.0, cpu), usada, total, discoUsado, discoTotal, bateria, cargando,
                sistema.getOperatingSystem().getSystemUptime());
    }

    public synchronized InfoEquipo info() { // Y
        iniciar();
        return new InfoEquipo(
                sistema.getOperatingSystem().toString(),
                procesador.getProcessorIdentifier().getName().strip(),
                procesador.getLogicalProcessorCount(),
                System.getProperty("java.version")); // Y
    } // K

    private static File raizDelDisco() {
        Path raiz = Path.of(System.getProperty("user.home")).getRoot(); // K
        return raiz == null ? new File(System.getProperty("user.home")) : raiz.toFile();
    }

    /** Texto breve de bytes: "7,8 GB". */
    public static String bytes(long valor) { // K
        double gb = valor / 1_073_741_824.0; // K
        // Coma decimal fija (no depende de la configuración regional de Java)
        if (gb >= 1) { // Y
            return String.format(java.util.Locale.ROOT, "%.1f GB", gb).replace('.', ',');
        }
        if (valor >= 1_048_576) {
            return String.format(java.util.Locale.ROOT, "%.0f MB", valor / 1_048_576.0);
        }
        return valor >= 1024 ? Math.round(valor / 1024.0) + " KB" : valor + " B"; // K
    }

    /** "3 d 4 h 12 min" a partir de segundos. */
    public static String duracion(long segundos) {
        long d = segundos / 86_400;
        long h = (segundos % 86_400) / 3_600;
        long m = (segundos % 3_600) / 60;
        if (d > 0) {
            return d + " d " + h + " h " + m + " min";
        }
        return h > 0 ? h + " h " + m + " min" : m + " min"; // K
    }
} // K
