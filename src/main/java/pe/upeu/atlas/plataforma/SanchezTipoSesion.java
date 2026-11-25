// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

/**
 * Servidor grafico de la sesion. Importa porque en WAYLAND java.awt.Robot devuelve
 * capturas negras y no puede escribir en otras ventanas: hay que usar grim, ydotool, etc.
 */
public enum SanchezTipoSesion { // K
    X11, WAYLAND, WINDOWS, DESCONOCIDA; // K

    /** Interpreta {@code XDG_SESSION_TYPE} (y {@code WAYLAND_DISPLAY}/{@code DISPLAY} como respaldo). */
    public static SanchezTipoSesion sanchezdesdeLinux(String sanchezxdgSessionType, String marcawaylandDisplay, String marcadisplay) {
        String marcatipo = sanchezxdgSessionType == null ? "" : sanchezxdgSessionType.trim().toLowerCase();
        if (marcatipo.equals("wayland")) {
            return WAYLAND; // K
        }
        if (marcatipo.equals("x11")) {
            return X11;
        } // K
        if (marcawaylandDisplay != null && !marcawaylandDisplay.isBlank()) {
            return WAYLAND;
        }
        if (marcadisplay != null && !marcadisplay.isBlank()) {
            return X11; // Y
        }
        return DESCONOCIDA;
    }
}
