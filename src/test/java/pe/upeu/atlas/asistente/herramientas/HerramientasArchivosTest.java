// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.plataforma.MarcaCarpetasConocidas;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.plataforma.MarcaPapelera;
import pe.upeu.atlas.plataforma.MarcaResolvedorRutas;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;
import pe.upeu.atlas.seguridad.MarcaGuardiaRutas;
import pe.upeu.atlas.service.MarcaExtractorTexto;
import pe.upeu.atlas.service.SanchezServicioActividad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El asistente maneja archivos de verdad: todo sobre un "usuario" simulado en una carpeta temporal. */
class HerramientasArchivosTest {

    @TempDir Path casa;

    private SanchezPropiedadesAtlas props;
    private SanchezLanzador lanzador;
    private MarcaPapelera papelera;
    private SanchezConfirmadorAcciones confirmador;
    private MarcaHerramientasArchivos h;
    private Path documentos;
    private Path escritorio;

    @BeforeEach
    void preparar() throws IOException {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"), "Pruebas pensadas para Windows");
        documentos = Files.createDirectories(casa.resolve("Documents"));
        escritorio = Files.createDirectories(casa.resolve("Desktop"));
        Path descargas = Files.createDirectories(casa.resolve("Downloads"));

        props = new SanchezPropiedadesAtlas();
        props.setDirectorioBase(casa.resolve(".atlas"));
        props.setCarpetaCapturas(casa.resolve("Pictures/Atlas"));

        Map<String, Path> mapa = Map.of("documentos", documentos, "documents", documentos, "escritorio", escritorio,
                "desktop", escritorio, "descargas", descargas, "usuario", casa);
        MarcaCarpetasConocidas conocidas = mock(MarcaCarpetasConocidas.class);
        when(conocidas.buscar(anyString())).thenAnswer(inv -> Optional.ofNullable(mapa.get(MarcaCarpetasConocidas.sancheznormalizar(inv.getArgument(0)))));
        when(conocidas.marcaprincipales()).thenReturn(Map.of("documentos", documentos, "escritorio", escritorio, "usuario", casa));

        lanzador = mock(SanchezLanzador.class);
        papelera = mock(MarcaPapelera.class);
        confirmador = mock(SanchezConfirmadorAcciones.class);
        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(true);
        h = new MarcaHerramientasArchivos(props, new MarcaResolvedorRutas(conocidas), new MarcaGuardiaRutas(props, conocidas, casa), lanzador,
                papelera, confirmador, mock(SanchezServicioActividad.class), new MarcaExtractorTexto(props));
    }

    // ===== Guardar =====

    @Test
    void guardaUnArchivoConSuContenidoEnLaCarpetaPedida() throws IOException {
        String r = h.marcaguardarArchivo("documentos", "lista-compras", "leche\npan\nhuevos");

        assertThat(r).contains("Guardé").contains("lista-compras.txt");
        assertThat(Files.readString(documentos.resolve("lista-compras.txt"))).isEqualTo("leche\npan\nhuevos");
        verify(confirmador, never()).sanchezconfirmar(anyString(), anyString());
    }

    @Test
    void creaLasCarpetasQueFaltenAlGuardar() throws IOException {
        h.marcaguardarArchivo("escritorio/redes/semana1", "apuntes.md", "# Redes");
        assertThat(Files.readString(escritorio.resolve("redes/semana1/apuntes.md"))).isEqualTo("# Redes");
    }

    @Test
    void pideConfirmacionParaReemplazarYRespetaUnNo() throws IOException {
        Files.writeString(documentos.resolve("a.txt"), "original");
        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(false);

        assertThat(h.marcaguardarArchivo("documentos", "a.txt", "nuevo")).contains("No reemplacé");
        assertThat(Files.readString(documentos.resolve("a.txt"))).isEqualTo("original");

        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(true);
        assertThat(h.marcaguardarArchivo("documentos", "a.txt", "nuevo")).contains("Reemplacé");
        assertThat(Files.readString(documentos.resolve("a.txt"))).isEqualTo("nuevo");
    }

    @Test
    void elNombreNoPuedeSalirseDeLaCarpetaNiTenerCaracteresInvalidos() throws IOException {
        h.marcaguardarArchivo("documentos", "../../evil:*?.txt", "x");
        try (var s = Files.list(documentos)) {
            assertThat(s.toList()).hasSize(1).allSatisfy(p -> assertThat(p.getFileName().toString()).doesNotContain("/", "\\", ":", "*", "?"));
        }
        assertThat(Files.list(casa).filter(Files::isRegularFile).count()).isZero();
    }

    @Test
    void noCreaProgramasNiScriptsNiEscribeEnZonasProtegidas() {
        for (String nombre : new String[]{"virus.exe", "tarea.ps1", "run.bat", "x.vbs"}) {
            assertThat(h.marcaguardarArchivo("documentos", nombre, "echo hola")).as(nombre).contains("programas o scripts");
            assertThat(documentos.resolve(nombre)).doesNotExist();
        }
        assertThat(h.marcaguardarArchivo(casa.resolve("AppData/Local").toString(), "a.txt", "x")).contains("protegida");
        assertThat(h.marcaguardarArchivo(casa.resolve(".ssh").toString(), "config", "x")).contains("protegida");
        assertThat(h.marcaguardarArchivo("C:\\Windows", "a.txt", "x")).contains("protegida");
        assertThat(h.sanchezcrearCarpeta("C:\\Windows\\NuevaCarpeta")).contains("protegida");
    }

    @Test
    void agregaTextoAlFinalYCreaElArchivoSiNoExiste() throws IOException {
        h.marcaagregarAArchivo("documentos", "lista.txt", "leche");
        h.marcaagregarAArchivo("documentos", "lista.txt", "pan");
        assertThat(Files.readString(documentos.resolve("lista.txt")).lines().toList()).containsExactly("leche", "pan");
    }

    // ===== Ver =====

    @Test
    void creaCarpetasAnidadas() {
        assertThat(h.sanchezcrearCarpeta("escritorio/redes/tareas")).contains("Creé");
        assertThat(escritorio.resolve("redes/tareas")).isDirectory();
        assertThat(h.sanchezcrearCarpeta("escritorio/redes")).contains("ya existía");
    }

    @Test
    void listaCarpetasPrimeroConTamanos() throws IOException {
        Files.writeString(documentos.resolve("b.txt"), "12345");
        Files.createDirectories(documentos.resolve("Clase"));
        Files.writeString(documentos.resolve("desktop.ini"), "x");

        String r = h.marcalistarContenido("documentos");

        assertThat(r).contains("hay 2 elementos").contains("Clase (carpeta)").contains("b.txt");
        assertThat(r.indexOf("Clase")).isLessThan(r.indexOf("b.txt"));
        assertThat(r).doesNotContain("desktop.ini");
        assertThat(h.marcalistarContenido("documentos/Clase")).contains("vacía");
        assertThat(h.marcalistarContenido("documentos/nada")).contains("No existe");
    }

    @Test
    void buscaPorPartesDelNombreSinTildesYOmiteAppData() throws IOException {
        Files.createDirectories(documentos.resolve("Clases/Redes"));
        Files.writeString(documentos.resolve("Clases/Redes/Tarea Redés 1.txt"), "x");
        Files.writeString(documentos.resolve("otra.txt"), "x");
        Files.createDirectories(casa.resolve("AppData/Local"));
        Files.writeString(casa.resolve("AppData/Local/tarea redes cache.txt"), "x");

        String r = h.sanchezbuscarArchivos("tarea redes", "");

        assertThat(r).contains("Tarea Redés 1.txt").doesNotContain("otra.txt").doesNotContain("cache.txt");
        assertThat(h.sanchezbuscarArchivos("inexistente", "documentos")).contains("No encontré");
        assertThat(h.sanchezbuscarArchivos("tarea", "escritorio")).contains("No encontré");
    }

    @Test
    void leeTextoPeroNoBinariosNiTiposDesconocidos() throws IOException {
        Files.writeString(documentos.resolve("apuntes.txt"), "El modelo OSI tiene siete capas");
        Files.write(documentos.resolve("raro.txt"), new byte[]{1, 2, 0, 3, 0, 0});
        Files.writeString(documentos.resolve("datos.xyz"), "abc");

        assertThat(h.marcaleerArchivo("documentos/apuntes.txt")).contains("siete capas");
        assertThat(h.marcaleerArchivo("documentos/raro.txt")).contains("no es de texto");
        assertThat(h.marcaleerArchivo("documentos/datos.xyz")).contains("No sé leer");
        assertThat(h.marcaleerArchivo("documentos")).contains("es una carpeta");
        assertThat(h.marcaleerArchivo("documentos/nada.txt")).contains("No existe");
    }

    @Test
    void recortaLosArchivosMuyLargos() throws IOException {
        Files.writeString(documentos.resolve("largo.txt"), "palabra ".repeat(2000));
        String r = h.marcaleerArchivo("documentos/largo.txt");
        assertThat(r).contains("primeros 3500 de").hasSizeLessThan(3700);
    }

    @Test
    void abreCarpetasYArchivosPeroNuncaProgramas() throws IOException {
        Files.writeString(documentos.resolve("tarea.pdf"), "x");
        Files.writeString(documentos.resolve("setup.exe"), "x");

        assertThat(h.marcaabrirRuta("documentos")).contains("Abrí la carpeta");
        verify(lanzador).marcaabrir(documentos);
        assertThat(h.marcaabrirRuta("documentos/tarea.pdf")).contains("Abrí el archivo");
        assertThat(h.marcaabrirRuta("documentos/setup.exe")).contains("programas o scripts");
        assertThat(h.marcaabrirRuta("documentos/no-existe")).contains("No existe");
        assertThat(h.marcaabrirRuta("C:\\Windows")).contains("protegida");
        verify(lanzador, never()).marcaabrir(documentos.resolve("setup.exe"));
    }

    // ===== Copiar, mover, renombrar =====

    @Test
    void copiaSinBorrarElOriginalYNoPisaNada() throws IOException {
        Files.writeString(documentos.resolve("tarea.txt"), "contenido");

        assertThat(h.marcacopiar("documentos/tarea.txt", "escritorio")).contains("Copié");
        assertThat(h.marcacopiar("documentos/tarea.txt", "escritorio")).contains("tarea (2).txt");

        assertThat(documentos.resolve("tarea.txt")).exists();
        assertThat(Files.readString(escritorio.resolve("tarea.txt"))).isEqualTo("contenido");
        assertThat(Files.readString(escritorio.resolve("tarea (2).txt"))).isEqualTo("contenido");
    }

    @Test
    void copiaCarpetasCompletas() throws IOException {
        Files.createDirectories(documentos.resolve("Clase/sub"));
        Files.writeString(documentos.resolve("Clase/a.txt"), "A");
        Files.writeString(documentos.resolve("Clase/sub/b.txt"), "B");

        assertThat(h.marcacopiar("documentos/Clase", "escritorio")).contains("Copié");

        assertThat(Files.readString(escritorio.resolve("Clase/a.txt"))).isEqualTo("A");
        assertThat(Files.readString(escritorio.resolve("Clase/sub/b.txt"))).isEqualTo("B");
        assertThat(documentos.resolve("Clase/sub/b.txt")).exists();
    }

    @Test
    void muevePeroNoUnaCarpetaDentroDeSiMisma() throws IOException {
        Files.writeString(documentos.resolve("a.txt"), "A");
        Files.createDirectories(documentos.resolve("Clase"));

        assertThat(h.sanchezmover("documentos/a.txt", "documentos/Clase")).contains("Moví");
        assertThat(documentos.resolve("a.txt")).doesNotExist();
        assertThat(documentos.resolve("Clase/a.txt")).exists();
        assertThat(h.sanchezmover("documentos/Clase", "documentos/Clase/sub")).contains("dentro de sí misma");
        assertThat(h.sanchezmover("documentos/fantasma.txt", "escritorio")).contains("No existe");
    }

    @Test
    void renombraConservandoLaExtension() throws IOException {
        Files.writeString(documentos.resolve("borrador.docx"), "x");
        Files.writeString(documentos.resolve("final.docx"), "y");

        assertThat(h.marcarenombrar("documentos/borrador.docx", "informe de redes")).contains("informe de redes.docx");
        assertThat(documentos.resolve("informe de redes.docx")).exists();
        assertThat(h.marcarenombrar("documentos/informe de redes.docx", "final.docx")).contains("Ya existe");
        assertThat(h.marcarenombrar("documentos/final.docx", "malo.exe")).contains("programas o scripts");
    }

    // ===== Eliminar =====

    @Test
    void eliminarSoloEnviaALaPapeleraTrasConfirmar() throws IOException {
        Path archivo = Files.writeString(documentos.resolve("viejo.txt"), "x");

        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(false);
        assertThat(h.sanchezeliminar("documentos/viejo.txt")).contains("No eliminé nada");
        verify(papelera, never()).sanchezenviar(any());
        assertThat(archivo).exists();

        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(true);
        assertThat(h.sanchezeliminar("documentos/viejo.txt")).contains("papelera");
        verify(papelera).sanchezenviar(archivo);
    }

    @Test
    void nuncaBorraCarpetasPrincipalesNiElSistema() {
        assertThat(h.sanchezeliminar("documentos")).contains("carpetas principales");
        assertThat(h.sanchezeliminar("usuario")).contains("carpetas principales");
        assertThat(h.sanchezeliminar("C:\\Windows\\System32")).contains("protegida");
        verify(papelera, never()).sanchezenviar(any());
        verify(confirmador, never()).sanchezconfirmar(anyString(), anyString());
    }

    // ===== Modo solo lectura y cancelación =====

    @Test
    void modoSoloLecturaBloqueaLoQueModificaPeroNoLoQueLee() throws IOException {
        Files.writeString(documentos.resolve("a.txt"), "A");
        props.setModoSoloLectura(true);

        assertThat(h.marcaguardarArchivo("documentos", "n.txt", "x")).contains("solo lectura");
        assertThat(h.sanchezcrearCarpeta("escritorio/x")).contains("solo lectura");
        assertThat(h.marcacopiar("documentos/a.txt", "escritorio")).contains("solo lectura");
        assertThat(h.sanchezmover("documentos/a.txt", "escritorio")).contains("solo lectura");
        assertThat(h.marcarenombrar("documentos/a.txt", "b")).contains("solo lectura");
        assertThat(h.sanchezeliminar("documentos/a.txt")).contains("solo lectura");
        assertThat(h.marcaagregarAArchivo("documentos", "a.txt", "x")).contains("solo lectura");
        assertThat(documentos.resolve("a.txt")).exists();

        assertThat(h.marcalistarContenido("documentos")).contains("a.txt");
        assertThat(h.marcaleerArchivo("documentos/a.txt")).contains("A");
    }

    @Test
    void siElUsuarioInterrumpioNingunaHerramientaDeArchivosActua() throws IOException {
        Files.writeString(documentos.resolve("a.txt"), "A");
        SanchezHerramientasSistema.TURNO.set(new AtomicBoolean(true));
        try {
            assertThat(h.marcaguardarArchivo("documentos", "n.txt", "x")).contains("interrumpió");
            assertThat(h.sanchezeliminar("documentos/a.txt")).contains("interrumpió");
            assertThat(h.sanchezmover("documentos/a.txt", "escritorio")).contains("interrumpió");
            assertThat(h.marcalistarContenido("documentos")).contains("interrumpió");
            verify(papelera, never()).sanchezenviar(any());
            verify(confirmador, never()).sanchezconfirmar(anyString(), anyString());
        } finally {
            SanchezHerramientasSistema.TURNO.remove();
        }
        assertThat(documentos.resolve("n.txt")).doesNotExist();
        assertThat(documentos.resolve("a.txt")).exists();
    }
}
