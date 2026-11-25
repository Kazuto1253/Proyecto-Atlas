// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

/**
 * Servidor grafico de la sesion. Importa porque en WAYLAND java.awt.Robot devuelve
 * capturas negras y no puede escribir en otras ventanas: hay que usar grim, ydotool, etc.
 */
public enum TipoSesion { // K
    X11, WAYLAND, WINDOWS, DESCONOCIDA; // K

    /** Interpreta {@code XDG_SESSION_TYPE} (y {@code WAYLAND_DISPLAY}/{@code DISPLAY} como respaldo). */
    public static TipoSesion desdeLinux(String xdgSessionType, String waylandDisplay, String display) {
        String tipo = xdgSessionType == null ? "" : xdgSessionType.trim().toLowerCase();
        if (tipo.equals("wayland")) {
            return WAYLAND; // K
        }
        if (tipo.equals("x11")) {
            return X11;
        } // K
        if (waylandDisplay != null && !waylandDisplay.isBlank()) {
            return WAYLAND;
        }
        if (display != null && !display.isBlank()) {
            return X11; // Y
        }
        return DESCONOCIDA;
    }
}
