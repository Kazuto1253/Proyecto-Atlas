// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import java.util.List;
import java.util.Set;

public record SanchezComandoSistema(
        String nombre,
        String marcafuncion,
        String marcapaqueteApt,
        String sanchezpaqueteDnf,
        SanchezSistemaOperativo marcasistema,
        Set<SanchezTipoSesion> sanchezsesiones,
        boolean sanchezobligatorio) { // Y

    public boolean sanchezaplicaA(SanchezSistemaOperativo marcaso, SanchezTipoSesion sanchezsesion) {
        return marcasistema == marcaso && (sanchezsesiones.isEmpty() || sanchezsesiones.contains(sanchezsesion));
    }

    public String sanchezsugerenciaInstalacion() { // Y
        if (marcasistema != SanchezSistemaOperativo.LINUX) {
            return "Viene con Windows; revisa que esté en el PATH"; // Y
        } // Y
        return "sudo apt install " + marcapaqueteApt + "   (Fedora: sudo dnf install " + sanchezpaqueteDnf + ")";
    }

    private static SanchezComandoSistema marcalinux(String sancheznombre, String marcafuncion, String sanchezapt, String marcadnf,
                                        boolean sanchezobligatorio, SanchezTipoSesion... sanchezsesiones) {
        return new SanchezComandoSistema(sancheznombre, marcafuncion, sanchezapt, marcadnf, SanchezSistemaOperativo.LINUX, Set.of(sanchezsesiones), sanchezobligatorio);
    }

    private static SanchezComandoSistema sanchezwindows(String sancheznombre, String marcafuncion) {
        return new SanchezComandoSistema(sancheznombre, marcafuncion, "", "", SanchezSistemaOperativo.WINDOWS, Set.of(), true);
    }

    public static final List<SanchezComandoSistema> CATALOGO = List.of(
            marcalinux("xdg-open", "Abrir archivos y carpetas", "xdg-utils", "xdg-utils", true),
            marcalinux("gio", "Enviar a la papelera", "libglib2.0-bin", "glib2", true),
            marcalinux("loginctl", "Bloquear la pantalla", "systemd", "systemd", true),
            marcalinux("pactl", "Controlar el volumen (PulseAudio/PipeWire)", "pulseaudio-utils", "pulseaudio-utils", false),
            marcalinux("wpctl", "Controlar el volumen (WirePlumber)", "wireplumber", "wireplumber", false),
            marcalinux("brightnessctl", "Controlar el brillo", "brightnessctl", "brightnessctl", false),
            marcalinux("wmctrl", "Listar y enfocar ventanas", "wmctrl", "wmctrl", false, SanchezTipoSesion.X11),
            marcalinux("xdotool", "Escribir texto y ventana activa", "xdotool", "xdotool", false, SanchezTipoSesion.X11),
            marcalinux("grim", "Captura de pantalla (wlroots)", "grim", "grim", false, SanchezTipoSesion.WAYLAND),
            marcalinux("slurp", "Seleccionar área (wlroots)", "slurp", "slurp", false, SanchezTipoSesion.WAYLAND),
            marcalinux("gnome-screenshot", "Captura de pantalla (GNOME)", "gnome-screenshot", "gnome-screenshot", false, SanchezTipoSesion.WAYLAND),
            marcalinux("spectacle", "Captura de pantalla (KDE)", "kde-spectacle", "spectacle", false, SanchezTipoSesion.WAYLAND),
            marcalinux("ydotool", "Escribir texto en Wayland", "ydotool", "ydotool", false, SanchezTipoSesion.WAYLAND),
            marcalinux("wtype", "Escribir texto en Wayland (wlroots)", "wtype", "wtype", false, SanchezTipoSesion.WAYLAND),
            marcalinux("wl-copy", "Portapapeles en Wayland", "wl-clipboard", "wl-clipboard", false, SanchezTipoSesion.WAYLAND),
            sanchezwindows("powershell", "Volumen, ventanas, bloqueo y apagado"),
            sanchezwindows("explorer", "Abrir archivos y carpetas"),
            sanchezwindows("rundll32", "Bloquear la pantalla")
    );
}
