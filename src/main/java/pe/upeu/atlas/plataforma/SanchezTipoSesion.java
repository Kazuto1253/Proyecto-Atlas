// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

public enum SanchezTipoSesion { // K
    X11, WAYLAND, WINDOWS, DESCONOCIDA; // K

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
