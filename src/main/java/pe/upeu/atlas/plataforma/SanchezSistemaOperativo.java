package pe.upeu.atlas.plataforma;

public enum SanchezSistemaOperativo {
    LINUX, WINDOWS, MAC, OTRO;

    public static SanchezSistemaOperativo sanchezdesde(String sanchezosName) {
        String sancheznombre = sanchezosName == null ? "" : sanchezosName.toLowerCase();
        if (sancheznombre.contains("linux")) {
            return LINUX;
        }
        if (sancheznombre.contains("win")) {
            return WINDOWS;
        }
        if (sancheznombre.contains("mac") || sancheznombre.contains("darwin")) {
            return MAC;
        }
        return OTRO;
    }
}
