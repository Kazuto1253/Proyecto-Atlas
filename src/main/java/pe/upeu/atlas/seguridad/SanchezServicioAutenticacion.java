package pe.upeu.atlas.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.repository.MarcaUsuarioRepository;
import pe.upeu.atlas.service.SanchezServicioActividad;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SanchezServicioAutenticacion {

    static final int MAX_INTENTOS = 5;
    static final Duration BLOQUEO = Duration.ofSeconds(60);
    static final int CLAVE_MINIMA = 6;
    private static final Pattern PATRON_USUARIO = Pattern.compile("[A-Za-z0-9._-]{3,30}");
    private static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";

    private record MarcaIntentos(int marcafallidos, Instant sanchezbloqueadoHasta) { }

    private final MarcaUsuarioRepository sanchezusuarios;
    private final PasswordEncoder sanchezcodificador;
    private final SanchezSesionActual sanchezsesion;
    private final SanchezServicioActividad sanchezactividad;
    private final Map<String, MarcaIntentos> marcaintentos = new ConcurrentHashMap<>();

    public MarcaUsuario marcainiciarSesion(String sancheznombreUsuario, String sanchezclave) {
        String sanchezclavePlano = sanchezclave == null ? "" : sanchezclave;
        String sanchezid = sancheznormalizar(sancheznombreUsuario);
        if (sanchezid.isEmpty() || sanchezclavePlano.isEmpty()) {
            throw new SanchezNegocioException("Escribe tu usuario y tu contraseña.");
        }
        marcaverificarBloqueo(sanchezid);

        MarcaUsuario sanchezu = sanchezusuarios.findByUsuarioIgnoreCase(sanchezid).orElse(null);
        boolean marcacoincide = sanchezcodificador.matches(sanchezclavePlano, sanchezu == null ? "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinva" : sanchezu.getContrasenaHash());
        if (sanchezu == null || !marcacoincide) {
            sanchezregistrarFallo(sanchezid);
            throw new SanchezNegocioException(MENSAJE_CREDENCIALES);
        }
        if (!sanchezu.isActivo()) {
            throw new SanchezNegocioException("Tu cuenta está desactivada. Habla con un administrador.");
        }
        marcaintentos.remove(sanchezid);
        sanchezu.setUltimoAcceso(LocalDateTime.now());
        sanchezu = sanchezusuarios.save(sanchezu);
        sanchezsesion.sancheziniciar(sanchezu);
        sanchezactividad.sanchezregistrarComo(sanchezu.getUsuario(), "Inicio de sesión", "Rol: " + sanchezu.getRol().marcaetiqueta());
        return sanchezu;
    }

    public MarcaUsuario sanchezregistrar(String marcanombreCompleto, String sancheznombreUsuario, String sanchezclave, SanchezRol sanchezrol) {
        String sancheznombre = marcanombreCompleto == null ? "" : marcanombreCompleto.strip();
        String sanchezid = sancheznormalizar(sancheznombreUsuario);
        if (sancheznombre.length() < 3) {
            throw new SanchezNegocioException("Escribe tu nombre completo (mínimo 3 letras).");
        }
        if (!PATRON_USUARIO.matcher(sanchezid).matches()) {
            throw new SanchezNegocioException("El usuario debe tener de 3 a 30 caracteres: letras, números, punto, guion o guion bajo.");
        }
        marcavalidarClave(sanchezclave);
        if (sanchezusuarios.existsByUsuarioIgnoreCase(sanchezid)) {
            throw new SanchezNegocioException("Ese usuario ya existe. Elige otro.");
        }
        MarcaUsuario sanchezu = new MarcaUsuario();
        sanchezu.setNombreCompleto(sancheznombre);
        sanchezu.setUsuario(sanchezid);
        sanchezu.setContrasenaHash(sanchezcodificador.encode(sanchezclave));
        sanchezu.setRol(sanchezrol == null ? SanchezRol.ESTUDIANTE : sanchezrol);
        sanchezu = sanchezusuarios.save(sanchezu);
        sanchezactividad.sanchezregistrarComo(sanchezid, "Cuenta creada", "Rol: " + sanchezu.getRol().marcaetiqueta());
        return sanchezu;
    }

    public void marcacerrarSesion() {
        sanchezsesion.marcaactual().ifPresent(sanchezu -> sanchezactividad.sanchezregistrarComo(sanchezu.getUsuario(), "Cierre de sesión", null));
        sanchezsesion.sanchezcerrar();
    }

    static void marcavalidarClave(String sanchezclave) {
        if (sanchezclave == null || sanchezclave.length() < CLAVE_MINIMA) {
            throw new SanchezNegocioException("La contraseña debe tener al menos " + CLAVE_MINIMA + " caracteres.");
        }
    }

    private void marcaverificarBloqueo(String sanchezid) {
        MarcaIntentos sanchezi = marcaintentos.get(sanchezid);
        if (sanchezi != null && sanchezi.sanchezbloqueadoHasta() != null) {
            if (Instant.now().isBefore(sanchezi.sanchezbloqueadoHasta())) {
                long sanchezseg = Duration.between(Instant.now(), sanchezi.sanchezbloqueadoHasta()).toSeconds() + 1;
                throw new SanchezNegocioException("Demasiados intentos. Acceso bloqueado por " + sanchezseg + " s.");
            }
            marcaintentos.remove(sanchezid);
        }
    }

    private void sanchezregistrarFallo(String sanchezid) {
        marcaintentos.merge(sanchezid, new MarcaIntentos(1, null), (sancheza, marcab) -> {
            int marcan = sancheza.marcafallidos() + 1;
            return new MarcaIntentos(marcan, marcan >= MAX_INTENTOS ? Instant.now().plus(BLOQUEO) : null);
        });
    }

    private static String sancheznormalizar(String sanchezs) {
        return sanchezs == null ? "" : sanchezs.strip().toLowerCase();
    }
}
