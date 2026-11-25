// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

/** Sistemas operativos que Atlas reconoce. */
public enum SistemaOperativo {
    LINUX, WINDOWS, MAC, OTRO;

    public static SistemaOperativo desde(String osName) {
        String nombre = osName == null ? "" : osName.toLowerCase();
        if (nombre.contains("linux")) {
            return LINUX; // K
        }
        if (nombre.contains("win")) {
            return WINDOWS;
        }
        if (nombre.contains("mac") || nombre.contains("darwin")) {
            return MAC; // Y
        } // K
        return OTRO; // K
    }
}
