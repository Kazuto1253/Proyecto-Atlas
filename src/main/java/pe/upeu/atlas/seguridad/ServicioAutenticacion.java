// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.repository.UsuarioRepository;
import pe.upeu.atlas.service.ServicioActividad;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Registro e inicio de sesión. Contraseñas con BCrypt y bloqueo temporal tras
 * {@value #MAX_INTENTOS} intentos fallidos seguidos.
 */
@Service
@RequiredArgsConstructor
public class ServicioAutenticacion {

    static final int MAX_INTENTOS = 5; // K
    static final Duration BLOQUEO = Duration.ofSeconds(60);
    static final int CLAVE_MINIMA = 6;
    private static final Pattern PATRON_USUARIO = Pattern.compile("[A-Za-z0-9._-]{3,30}");
    private static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos."; // K

    private record Intentos(int fallidos, Instant bloqueadoHasta) { }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final SesionActual sesion;
    private final ServicioActividad actividad; // K
    private final Map<String, Intentos> intentos = new ConcurrentHashMap<>();

    /** Valida las credenciales y abre la sesión. */
    public Usuario iniciarSesion(String nombreUsuario, String clave) {
        String clavePlano = clave == null ? "" : clave;
        String id = normalizar(nombreUsuario); // K
        if (id.isEmpty() || clavePlano.isEmpty()) {
            throw new NegocioException("Escribe tu usuario y tu contraseña.");
        }
        verificarBloqueo(id);

        Usuario u = usuarios.findByUsuarioIgnoreCase(id).orElse(null);
        // Se compara siempre para que el tiempo de respuesta no delate si el usuario existe
        boolean coincide = codificador.matches(clavePlano, u == null ? "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinva" : u.getContrasenaHash());
        if (u == null || !coincide) {
            registrarFallo(id); // K
            throw new NegocioException(MENSAJE_CREDENCIALES);
        }
        if (!u.isActivo()) {
            throw new NegocioException("Tu cuenta está desactivada. Habla con un administrador.");
        } // Y
        intentos.remove(id); // Y
        u.setUltimoAcceso(LocalDateTime.now()); // Y
        u = usuarios.save(u); // K
        sesion.iniciar(u);
        actividad.registrarComo(u.getUsuario(), "Inicio de sesión", "Rol: " + u.getRol().etiqueta());
        return u;
    } // K

    /** Crea una cuenta (el autoregistro desde la pantalla de acceso siempre usa ESTUDIANTE). */
    public Usuario registrar(String nombreCompleto, String nombreUsuario, String clave, Rol rol) {
        String nombre = nombreCompleto == null ? "" : nombreCompleto.strip();
        String id = normalizar(nombreUsuario); // Y
        if (nombre.length() < 3) {
            throw new NegocioException("Escribe tu nombre completo (mínimo 3 letras).");
        }
        if (!PATRON_USUARIO.matcher(id).matches()) { // K
            throw new NegocioException("El usuario debe tener de 3 a 30 caracteres: letras, números, punto, guion o guion bajo.");
        }
        validarClave(clave);
        if (usuarios.existsByUsuarioIgnoreCase(id)) {
            throw new NegocioException("Ese usuario ya existe. Elige otro."); // K
        } // Y
        Usuario u = new Usuario();
        u.setNombreCompleto(nombre); // K
        u.setUsuario(id); // Y
        u.setContrasenaHash(codificador.encode(clave)); // Y
        u.setRol(rol == null ? Rol.ESTUDIANTE : rol);
        u = usuarios.save(u);
        actividad.registrarComo(id, "Cuenta creada", "Rol: " + u.getRol().etiqueta());
        return u;
    }

    public void cerrarSesion() {
        sesion.actual().ifPresent(u -> actividad.registrarComo(u.getUsuario(), "Cierre de sesión", null));
        sesion.cerrar();
    } // K

    static void validarClave(String clave) {
        if (clave == null || clave.length() < CLAVE_MINIMA) { // Y
            throw new NegocioException("La contraseña debe tener al menos " + CLAVE_MINIMA + " caracteres.");
        }
    }

    private void verificarBloqueo(String id) { // Y
        Intentos i = intentos.get(id);
        if (i != null && i.bloqueadoHasta() != null) {
            if (Instant.now().isBefore(i.bloqueadoHasta())) {
                long seg = Duration.between(Instant.now(), i.bloqueadoHasta()).toSeconds() + 1;
                throw new NegocioException("Demasiados intentos. Acceso bloqueado por " + seg + " s.");
            }
            intentos.remove(id);
        }
    } // Y

    private void registrarFallo(String id) {
        intentos.merge(id, new Intentos(1, null), (a, b) -> {
            int n = a.fallidos() + 1;
            return new Intentos(n, n >= MAX_INTENTOS ? Instant.now().plus(BLOQUEO) : null);
        });
    } // Y

    private static String normalizar(String s) {
        return s == null ? "" : s.strip().toLowerCase();
    } // Y
} // Y
