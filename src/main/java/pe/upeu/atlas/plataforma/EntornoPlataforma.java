// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import java.util.Map;

/**
 * Foto del entorno detectado al arrancar.
 *
 * @param comandos comando del SO → si esta disponible en el PATH
 */
public record EntornoPlataforma(
        SistemaOperativo sistemaOperativo,
        String versionSistema,
        TipoSesion tipoSesion,
        String escritorio,
        Map<String, Boolean> comandos) {

    public boolean tieneComando(String comando) { // K
        return comandos.getOrDefault(comando, false);
    }

    public String descripcion() {
        String base = sistemaOperativo + " " + versionSistema;
        if (sistemaOperativo == SistemaOperativo.LINUX) { // Y
            base += " · " + tipoSesion + (escritorio.isBlank() ? "" : " · " + escritorio);
        }
        return base;
    }
}
