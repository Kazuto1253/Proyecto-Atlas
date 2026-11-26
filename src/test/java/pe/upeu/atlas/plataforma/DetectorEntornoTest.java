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

    private SanchezDetectorEntorno detector(Map<String, String> variables, String os) {
        return new SanchezDetectorEntorno(variables::get, os, "1.0");
    }

    @Test
    void detectaWaylandPorXdgSessionType() {
        var e = detector(Map.of("XDG_SESSION_TYPE", "wayland", "XDG_CURRENT_DESKTOP", "GNOME"), "Linux").sanchezentorno();
        assertThat(e.sanchezsistemaOperativo()).isEqualTo(SanchezSistemaOperativo.LINUX);
        assertThat(e.sancheztipoSesion()).isEqualTo(SanchezTipoSesion.WAYLAND);
        assertThat(e.sanchezescritorio()).isEqualTo("GNOME");
    }

    @Test
    void detectaX11PorXdgSessionType() {
        assertThat(detector(Map.of("XDG_SESSION_TYPE", "x11"), "Linux").sanchezentorno().sancheztipoSesion())
                .isEqualTo(SanchezTipoSesion.X11);
    }

    @Test
    void usaWaylandDisplayYDisplayComoRespaldo() {
        assertThat(SanchezTipoSesion.sanchezdesdeLinux(null, "wayland-0", ":0")).isEqualTo(SanchezTipoSesion.WAYLAND);
        assertThat(SanchezTipoSesion.sanchezdesdeLinux("", null, ":0")).isEqualTo(SanchezTipoSesion.X11);
        assertThat(SanchezTipoSesion.sanchezdesdeLinux("tty", null, null)).isEqualTo(SanchezTipoSesion.DESCONOCIDA);
    }

    @Test
    void windowsTieneSuPropioTipoDeSesion() {
        var e = detector(Map.of(), "Windows 11").sanchezentorno();
        assertThat(e.sanchezsistemaOperativo()).isEqualTo(SanchezSistemaOperativo.WINDOWS);
        assertThat(e.sancheztipoSesion()).isEqualTo(SanchezTipoSesion.WINDOWS);
        assertThat(e.marcacomandos()).containsOnlyKeys("powershell", "explorer", "rundll32");
    }

    @Test
    void encuentraComandosEnElPathLinux() throws IOException {
        Path wmctrl = Files.createFile(bin.resolve("wmctrl"));
        wmctrl.toFile().setExecutable(true);
        Map<String, String> vars = new HashMap<>();
        vars.put("PATH", bin + File.pathSeparator + "/no/existe");
        vars.put("XDG_SESSION_TYPE", "x11");

        SanchezDetectorEntorno d = detector(vars, "Linux");
        assertThat(d.sanchezentorno().marcatieneComando("wmctrl")).isTrue();
        assertThat(d.sanchezentorno().marcatieneComando("xdotool")).isFalse();
        assertThat(d.marcacomandosAplicables()).extracting(SanchezComandoSistema::nombre)
                .contains("wmctrl", "xdotool", "xdg-open")
                .doesNotContain("grim", "ydotool", "powershell");
    }

    @Test
    void enWindowsPruebaLasExtensionesDePathext() throws IOException {
        Files.createFile(bin.resolve("powershell.exe"));
        SanchezDetectorEntorno d = detector(Map.of("PATH", bin.toString(), "PATHEXT", ".COM;.EXE"), "Windows 10");
        assertThat(d.sanchezentorno().marcatieneComando("powershell")).isTrue();
        assertThat(d.sanchezentorno().marcatieneComando("explorer")).isFalse();
    }

    @Test
    void reconoceElSistemaOperativo() {
        assertThat(SanchezSistemaOperativo.sanchezdesde("Linux")).isEqualTo(SanchezSistemaOperativo.LINUX);
        assertThat(SanchezSistemaOperativo.sanchezdesde("Windows Server 2022")).isEqualTo(SanchezSistemaOperativo.WINDOWS);
        assertThat(SanchezSistemaOperativo.sanchezdesde("Mac OS X")).isEqualTo(SanchezSistemaOperativo.MAC);
        assertThat(SanchezSistemaOperativo.sanchezdesde(null)).isEqualTo(SanchezSistemaOperativo.OTRO);
    }
}
