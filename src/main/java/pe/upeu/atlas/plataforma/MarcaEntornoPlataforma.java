package pe.upeu.atlas.plataforma;

import java.util.Map;

public record MarcaEntornoPlataforma(
        SanchezSistemaOperativo sanchezsistemaOperativo,
        String marcaversionSistema,
        SanchezTipoSesion sancheztipoSesion,
        String sanchezescritorio,
        Map<String, Boolean> marcacomandos) {

    public boolean marcatieneComando(String sanchezcomando) {
        return marcacomandos.getOrDefault(sanchezcomando, false);
    }

    public String sanchezdescripcion() {
        String sanchezbase = sanchezsistemaOperativo + " " + marcaversionSistema;
        if (sanchezsistemaOperativo == SanchezSistemaOperativo.LINUX) {
            sanchezbase += " · " + sancheztipoSesion + (sanchezescritorio.isBlank() ? "" : " · " + sanchezescritorio);
        }
        return sanchezbase;
    }
}
