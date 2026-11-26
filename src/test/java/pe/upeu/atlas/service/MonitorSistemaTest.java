// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorSistemaTest {

    @Test
    void midePorcentajesCoherentesYCpuQueReaccionaALaCarga() throws Exception {
        MarcaMonitorSistema monitor = new MarcaMonitorSistema();
        monitor.sanchezmedir();

        long fin = System.currentTimeMillis() + 2000;
        Thread[] hilos = new Thread[Runtime.getRuntime().availableProcessors()];
        for (int i = 0; i < hilos.length; i++) {
            hilos[i] = new Thread(() -> {
                double x = 0;
                while (System.currentTimeMillis() < fin) {
                    x += Math.sqrt(x + 1);
                }
            });
            hilos[i].start();
        }
        Thread.sleep(1500);
        MarcaMonitorSistema.MarcaMetricas m = monitor.sanchezmedir();
        for (Thread h : hilos) {
            h.join();
        }
        System.out.println("MEDIDA bajo carga -> cpu=" + Math.round(m.marcacpu() * 100) + "% ram=" + Math.round(m.marcaram() * 100)
                + "% disco=" + Math.round(m.marcadisco() * 100) + "% bateria=" + m.marcabateria());

        assertThat(m.marcaramTotal()).isPositive();
        assertThat(m.marcaram()).isBetween(0.01, 1.0);
        assertThat(m.marcadiscoTotal()).isPositive();
        assertThat(m.marcacpu()).as("el procesador debe reflejar la carga artificial").isGreaterThan(0.15);
        assertThat(monitor.info().sancheznucleos()).isPositive();
    }

    @Test
    void formateaBytesYDuraciones() {
        assertThat(MarcaMonitorSistema.sanchezbytes(8L * 1_073_741_824L)).isEqualTo("8,0 GB");
        assertThat(MarcaMonitorSistema.sanchezbytes(512L * 1_048_576L)).isEqualTo("512 MB");
        assertThat(MarcaMonitorSistema.sanchezbytes(350 * 1024L)).isEqualTo("350 KB");
        assertThat(MarcaMonitorSistema.sanchezbytes(12)).isEqualTo("12 B");
        assertThat(MarcaMonitorSistema.sanchezduracion(90_061)).isEqualTo("1 d 1 h 1 min");
        assertThat(MarcaMonitorSistema.sanchezduracion(125)).isEqualTo("2 min");
    }
}
