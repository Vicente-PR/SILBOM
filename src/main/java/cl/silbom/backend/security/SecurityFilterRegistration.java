package cl.silbom.backend.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QUÉ HACE:
 * Evita que Spring Boot registre por segunda vez, como filtros de servlet independientes, los filtros
 * de seguridad que ya se insertan explícitamente en el SecurityFilterChain (SecurityConfig).
 *
 * POR QUÉ: así el orden de ejecución queda definido en un solo lugar (SecurityConfig) y no
 * depende del orden de registro automático de beans @Component.
 */
@Configuration
public class SecurityFilterRegistration {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration(SecurityHeadersFilter filter) {
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

}
