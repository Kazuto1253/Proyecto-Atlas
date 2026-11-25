// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorEntornoTest {

    @TempDir
    Path bin;

    private DetectorEntorno detector(Map<String, String> variables, String os) {
        return new DetectorEntorno(variables::get, os, "1.0");
    }

    @Test
    void detectaWaylandPorXdgSessionType() {
        var e = detector(Map.of("XDG_SESSION_TYPE", "wayland", "XDG_CURRENT_DESKTOP", "GNOME"), "Linux").entorno();
        assertThat(e.sistemaOperativo()).isEqualTo(SistemaOperativo.LINUX);
        assertThat(e.tipoSesion()).isEqualTo(TipoSesion.WAYLAND);
        assertThat(e.escritorio()).isEqualTo("GNOME");
    }

    @Test
    void detectaX11PorXdgSessionType() {
        assertThat(detector(Map.of("XDG_SESSION_TYPE", "x11"), "Linux").entorno().tipoSesion())
                .isEqualTo(TipoSesion.X11);
    }

    @Test
    void usaWaylandDisplayYDisplayComoRespaldo() {
        assertThat(TipoSesion.desdeLinux(null, "wayland-0", ":0")).isEqualTo(TipoSesion.WAYLAND);
        assertThat(TipoSesion.desdeLinux("", null, ":0")).isEqualTo(TipoSesion.X11);
        assertThat(TipoSesion.desdeLinux("tty", null, null)).isEqualTo(TipoSesion.DESCONOCIDA);
    }

    @Test
    void windowsTieneSuPropioTipoDeSesion() {
        var e = detector(Map.of(), "Windows 11").entorno();
        assertThat(e.sistemaOperativo()).isEqualTo(SistemaOperativo.WINDOWS);
        assertThat(e.tipoSesion()).isEqualTo(TipoSesion.WINDOWS);
        assertThat(e.comandos()).containsOnlyKeys("powershell", "explorer", "rundll32");
    }

    @Test
    void encuentraComandosEnElPathLinux() throws IOException {
        Path wmctrl = Files.createFile(bin.resolve("wmctrl"));
        wmctrl.toFile().setExecutable(true);
        Map<String, String> vars = new HashMap<>();
        vars.put("PATH", bin + File.pathSeparator + "/no/existe");
        vars.put("XDG_SESSION_TYPE", "x11");

        DetectorEntorno d = detector(vars, "Linux");
        assertThat(d.entorno().tieneComando("wmctrl")).isTrue();
        assertThat(d.entorno().tieneComando("xdotool")).isFalse();
        // En X11 no aplican las herramientas de Wayland
        assertThat(d.comandosAplicables()).extracting(ComandoSistema::nombre)
                .contains("wmctrl", "xdotool", "xdg-open")
                .doesNotContain("grim", "ydotool", "powershell");
    }

    @Test
    void enWindowsPruebaLasExtensionesDePathext() throws IOException {
        Files.createFile(bin.resolve("powershell.exe"));
        DetectorEntorno d = detector(Map.of("PATH", bin.toString(), "PATHEXT", ".COM;.EXE"), "Windows 10");
        assertThat(d.entorno().tieneComando("powershell")).isTrue();
        assertThat(d.entorno().tieneComando("explorer")).isFalse();
    }

    @Test
    void reconoceElSistemaOperativo() {
        assertThat(SistemaOperativo.desde("Linux")).isEqualTo(SistemaOperativo.LINUX);
        assertThat(SistemaOperativo.desde("Windows Server 2022")).isEqualTo(SistemaOperativo.WINDOWS);
        assertThat(SistemaOperativo.desde("Mac OS X")).isEqualTo(SistemaOperativo.MAC);
        assertThat(SistemaOperativo.desde(null)).isEqualTo(SistemaOperativo.OTRO);
    }
}
