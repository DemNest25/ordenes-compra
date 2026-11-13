package com.diseconstech.ordenes.seguridad;

import com.diseconstech.ordenes.modelo.Rol;
import com.diseconstech.ordenes.modelo.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

    public static final String EMISOR = "ordenes-compra";
    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_NOMBRE = "nombre";
    public static final String CLAIM_ROLES = "roles";

    private final JwtEncoder encoder;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public record TokenEmitido(String valor, Instant expira) {
    }

    public TokenEmitido emitir(Usuario u) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(props.jwt().expiracion());
        List<String> roles = u.getRoles().stream().map(Rol::getCodigo).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(u.getUsername())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(CLAIM_UID, u.getId())
                .claim(CLAIM_NOMBRE, u.getNombreCompleto())
                .claim(CLAIM_ROLES, roles)
                .build();
        String valor = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenEmitido(valor, expira);
    }
}
