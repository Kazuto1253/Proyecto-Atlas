// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import java.util.List;
import java.util.Set;

/**
 * Lista blanca de comandos externos que Atlas puede llegar a usar, con el paquete que
 * los instala. Ningun otro programa se ejecuta (ver AdaptadorPlataforma, Fase 6).
 *
 * @param sesiones   sesiones donde aplica (vacio = todas las del SO)
 * @param obligatorio si su falta impide una funcion basica (si no, hay alternativas)
 */
public record ComandoSistema(
        String nombre,
        String funcion,
        String paqueteApt,
        String paqueteDnf,
        SistemaOperativo sistema,
        Set<TipoSesion> sesiones,
        boolean obligatorio) { // Y

    public boolean aplicaA(SistemaOperativo so, TipoSesion sesion) {
        return sistema == so && (sesiones.isEmpty() || sesiones.contains(sesion));
    }

    public String sugerenciaInstalacion() { // Y
        if (sistema != SistemaOperativo.LINUX) {
            return "Viene con Windows; revisa que esté en el PATH"; // Y
        } // Y
        return "sudo apt install " + paqueteApt + "   (Fedora: sudo dnf install " + paqueteDnf + ")";
    }

    private static ComandoSistema linux(String nombre, String funcion, String apt, String dnf,
                                        boolean obligatorio, TipoSesion... sesiones) {
        return new ComandoSistema(nombre, funcion, apt, dnf, SistemaOperativo.LINUX, Set.of(sesiones), obligatorio);
    }

    private static ComandoSistema windows(String nombre, String funcion) {
        return new ComandoSistema(nombre, funcion, "", "", SistemaOperativo.WINDOWS, Set.of(), true);
    }

    /** Catalogo completo. */
    public static final List<ComandoSistema> CATALOGO = List.of(
            linux("xdg-open", "Abrir archivos y carpetas", "xdg-utils", "xdg-utils", true),
            linux("gio", "Enviar a la papelera", "libglib2.0-bin", "glib2", true),
            linux("loginctl", "Bloquear la pantalla", "systemd", "systemd", true),
            linux("pactl", "Controlar el volumen (PulseAudio/PipeWire)", "pulseaudio-utils", "pulseaudio-utils", false),
            linux("wpctl", "Controlar el volumen (WirePlumber)", "wireplumber", "wireplumber", false),
            linux("brightnessctl", "Controlar el brillo", "brightnessctl", "brightnessctl", false),
            linux("wmctrl", "Listar y enfocar ventanas", "wmctrl", "wmctrl", false, TipoSesion.X11),
            linux("xdotool", "Escribir texto y ventana activa", "xdotool", "xdotool", false, TipoSesion.X11),
            linux("grim", "Captura de pantalla (wlroots)", "grim", "grim", false, TipoSesion.WAYLAND),
            linux("slurp", "Seleccionar área (wlroots)", "slurp", "slurp", false, TipoSesion.WAYLAND),
            linux("gnome-screenshot", "Captura de pantalla (GNOME)", "gnome-screenshot", "gnome-screenshot", false, TipoSesion.WAYLAND),
            linux("spectacle", "Captura de pantalla (KDE)", "kde-spectacle", "spectacle", false, TipoSesion.WAYLAND),
            linux("ydotool", "Escribir texto en Wayland", "ydotool", "ydotool", false, TipoSesion.WAYLAND),
            linux("wtype", "Escribir texto en Wayland (wlroots)", "wtype", "wtype", false, TipoSesion.WAYLAND),
            linux("wl-copy", "Portapapeles en Wayland", "wl-clipboard", "wl-clipboard", false, TipoSesion.WAYLAND),
            windows("powershell", "Volumen, ventanas, bloqueo y apagado"),
            windows("explorer", "Abrir archivos y carpetas"),
            windows("rundll32", "Bloquear la pantalla")
    );
}
