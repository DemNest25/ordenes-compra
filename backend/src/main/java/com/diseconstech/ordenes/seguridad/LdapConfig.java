package com.diseconstech.ordenes.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;

/**
 * Valida la contraseña de los internos contra el directorio:
 * 1) busca el DN del usuario con el filtro (uid={0} aquí, sAMAccountName={0} en AD),
 * 2) intenta un "bind" con ese DN y la contraseña. Si el directorio lo acepta, es válida.
 * La app nunca ve ni guarda la contraseña de un interno.
 * El LdapContextSource lo arma Spring Boot (embebido o spring.ldap.urls).
 */
@Configuration
public class LdapConfig {

    @Bean
    LdapAuthenticator ldapAuthenticator(LdapContextSource contextSource, AppProperties props) {
        var busqueda = new FilterBasedLdapUserSearch(
                props.ldap().busquedaBase(), props.ldap().filtroUsuario(), contextSource);
        var authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(busqueda);
        return authenticator;
    }
}
