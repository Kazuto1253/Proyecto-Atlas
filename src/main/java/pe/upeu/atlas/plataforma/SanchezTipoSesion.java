package pe.upeu.atlas.plataforma;

public enum SanchezTipoSesion {
    X11, WAYLAND, WINDOWS, DESCONOCIDA;

    public static SanchezTipoSesion sanchezdesdeLinux(String sanchezxdgSessionType, String marcawaylandDisplay, String marcadisplay) {
        String marcatipo = sanchezxdgSessionType == null ? "" : sanchezxdgSessionType.trim().toLowerCase();
        if (marcatipo.equals("wayland")) {
            return WAYLAND;
        }
        if (marcatipo.equals("x11")) {
            return X11;
        }
        if (marcawaylandDisplay != null && !marcawaylandDisplay.isBlank()) {
            return WAYLAND;
        }
        if (marcadisplay != null && !marcadisplay.isBlank()) {
            return X11;
        }
        return DESCONOCIDA;
    }
}
