// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorSistemaTest {

    @Test
    void midePorcentajesCoherentesYCpuQueReaccionaALaCarga() throws Exception {
        MonitorSistema monitor = new MonitorSistema();
        monitor.medir(); // la primera lectura solo fija la referencia

        // Carga artificial en todos los núcleos durante ~2 s
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
        MonitorSistema.Metricas m = monitor.medir();
        for (Thread h : hilos) {
            h.join();
        }
        System.out.println("MEDIDA bajo carga -> cpu=" + Math.round(m.cpu() * 100) + "% ram=" + Math.round(m.ram() * 100)
                + "% disco=" + Math.round(m.disco() * 100) + "% bateria=" + m.bateria());

        assertThat(m.ramTotal()).isPositive();
        assertThat(m.ram()).isBetween(0.01, 1.0);
        assertThat(m.discoTotal()).isPositive();
        assertThat(m.cpu()).as("el procesador debe reflejar la carga artificial").isGreaterThan(0.15);
        assertThat(monitor.info().nucleos()).isPositive();
    }

    @Test
    void formateaBytesYDuraciones() {
        assertThat(MonitorSistema.bytes(8L * 1_073_741_824L)).isEqualTo("8,0 GB");
        assertThat(MonitorSistema.bytes(512L * 1_048_576L)).isEqualTo("512 MB");
        assertThat(MonitorSistema.bytes(350 * 1024L)).isEqualTo("350 KB");
        assertThat(MonitorSistema.bytes(12)).isEqualTo("12 B");
        assertThat(MonitorSistema.duracion(90_061)).isEqualTo("1 d 1 h 1 min");
        assertThat(MonitorSistema.duracion(125)).isEqualTo("2 min");
    }
}
