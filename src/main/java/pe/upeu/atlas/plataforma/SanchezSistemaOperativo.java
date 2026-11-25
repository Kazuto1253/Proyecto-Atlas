// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

/** Sistemas operativos que Atlas reconoce. */
public enum SanchezSistemaOperativo {
    LINUX, WINDOWS, MAC, OTRO;

    public static SanchezSistemaOperativo sanchezdesde(String sanchezosName) {
        String sancheznombre = sanchezosName == null ? "" : sanchezosName.toLowerCase();
        if (sancheznombre.contains("linux")) {
            return LINUX; // K
        }
        if (sancheznombre.contains("win")) {
            return WINDOWS;
        }
        if (sancheznombre.contains("mac") || sancheznombre.contains("darwin")) {
            return MAC; // Y
        } // K
        return OTRO; // K
    }
}
