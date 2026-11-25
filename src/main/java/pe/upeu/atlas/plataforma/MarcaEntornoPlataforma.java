// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import java.util.Map;

/**
 * Foto del entorno detectado al arrancar.
 *
 * @param comandos comando del SO → si esta disponible en el PATH
 */
public record MarcaEntornoPlataforma(
        SanchezSistemaOperativo sanchezsistemaOperativo,
        String marcaversionSistema,
        SanchezTipoSesion sancheztipoSesion,
        String sanchezescritorio,
        Map<String, Boolean> marcacomandos) {

    public boolean marcatieneComando(String sanchezcomando) { // K
        return marcacomandos.getOrDefault(sanchezcomando, false);
    }

    public String sanchezdescripcion() {
        String sanchezbase = sanchezsistemaOperativo + " " + marcaversionSistema;
        if (sanchezsistemaOperativo == SanchezSistemaOperativo.LINUX) { // Y
            sanchezbase += " · " + sancheztipoSesion + (sanchezescritorio.isBlank() ? "" : " · " + sanchezescritorio);
        }
        return sanchezbase;
    }
}
