package com.diseconstech.ordenes.seguridad;

import com.diseconstech.ordenes.dto.AuthDtos.LoginResponse;
import com.diseconstech.ordenes.dto.AuthDtos.UsuarioDto;
import com.diseconstech.ordenes.error.ApiException;
import com.diseconstech.ordenes.modelo.AccionBitacora;
import com.diseconstech.ordenes.modelo.Parametro;
import com.diseconstech.ordenes.modelo.Usuario;
import com.diseconstech.ordenes.repositorio.UsuarioRepository;
import com.diseconstech.ordenes.servicio.BitacoraService;
import com.diseconstech.ordenes.servicio.ParametroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Login único: el tipo_autenticacion del usuario decide contra qué se valida.
 * LDAP solo responde "la contraseña es correcta"; roles, límite y departamento
 * siempre salen de la BD de la app (autenticación ≠ autorización).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ENTIDAD = "usuarios";
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final LdapAuthenticator ldap;
    private final TokenService tokens;
    private final BitacoraService bitacora;
    private final ParametroService parametros;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, LdapAuthenticator ldap,
                       TokenService tokens, BitacoraService bitacora, ParametroService parametros) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.ldap = ldap;
        this.tokens = tokens;
        this.bitacora = bitacora;
        this.parametros = parametros;
    }

    // noRollbackFor: el intento fallido y su registro en bitácora deben guardarse aunque el login falle
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String usernameIngresado, String password) {
        String username = usernameIngresado.trim();
        Usuario u = usuarios.findByUsernameIgnoreCaseAndEliminadoFalse(username).orElse(null);

        // Mismo mensaje para "no existe", "inactivo" y "contraseña mala": no revelamos qué usuarios existen
        if (u == null || !u.isActivo()) {
            fallido(u == null ? null : u.getId(), username, u == null ? "usuario no existe" : "usuario inactivo");
            throw ApiException.noAutenticado(CREDENCIALES_INVALIDAS);
        }
        if (u.estaBloqueado()) {
            fallido(u.getId(), u.getUsername(), "cuenta bloqueada");
            throw ApiException.bloqueado("Cuenta bloqueada por intentos fallidos. Intenta de nuevo después de las "
                    + u.getBloqueadoHasta().atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        boolean valida = u.esInterno() ? validarEnLdap(u.getUsername(), password)
                : passwordEncoder.matches(password, u.getPasswordHash());
        if (!valida) {
            if (u.esInterno()) {
                // El bloqueo de internos lo aplica la política del directorio corporativo, no la app
                fallido(u.getId(), u.getUsername(), "contraseña incorrecta (LDAP)");
            } else {
                registrarIntentoFallido(u);
            }
            throw ApiException.noAutenticado(CREDENCIALES_INVALIDAS);
        }

        // Armar token y DTO antes del update (que limpia el contexto de persistencia)
        TokenService.TokenEmitido token = tokens.emitir(u);
        UsuarioDto dto = UsuarioDto.de(u);
        usuarios.registrarAccesoExitoso(u.getId(), OffsetDateTime.now());
        bitacora.registrarComo(u.getId(), u.getUsername(), AccionBitacora.LOGIN, ENTIDAD, u.getId(), null, null);
        return new LoginResponse(token.valor(), "Bearer", token.expira(), dto);
    }

    @Transactional(readOnly = true)
    public UsuarioDto yo(Long id) {
        return usuarios.findById(id).map(UsuarioDto::de)
                .orElseThrow(() -> ApiException.noAutenticado("Sesión no válida"));
    }

    private boolean validarEnLdap(String username, String password) {
        try {
            ldap.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            return true;
        } catch (InternalAuthenticationServiceException | org.springframework.ldap.NamingException e) {
            log.error("Error de comunicación con el directorio LDAP", e);
            throw ApiException.noDisponible("El directorio corporativo no responde. Intenta más tarde.");
        } catch (AuthenticationException e) {
            return false; // credenciales malas o usuario inexistente en el directorio
        }
    }

    private void registrarIntentoFallido(Usuario u) {
        int max = parametros.entero(Parametro.MAX_INTENTOS_LOGIN, 5);
        int minutos = parametros.entero(Parametro.MINUTOS_BLOQUEO, 15);
        int intentos = u.getIntentosFallidos() + 1;

        if (intentos >= max) {
            OffsetDateTime hasta = OffsetDateTime.now().plusMinutes(minutos);
            usuarios.registrarIntentoFallido(u.getId(), (short) 0, hasta);
            bitacora.registrarComo(u.getId(), u.getUsername(), AccionBitacora.BLOQUEO, ENTIDAD, u.getId(), null,
                    Map.of("intentos", intentos, "bloqueadoHasta", hasta));
        } else {
            usuarios.registrarIntentoFallido(u.getId(), (short) intentos, null);
            fallido(u.getId(), u.getUsername(), "contraseña incorrecta (intento " + intentos + " de " + max + ")");
        }
    }

    private void fallido(Long usuarioId, String username, String motivo) {
        bitacora.registrarComo(usuarioId, username, AccionBitacora.LOGIN_FALLIDO, ENTIDAD, usuarioId, null,
                Map.of("motivo", motivo));
    }
}
