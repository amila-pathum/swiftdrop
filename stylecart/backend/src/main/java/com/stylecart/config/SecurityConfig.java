package com.stylecart.config;
import com.stylecart.repo.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService userDetails(UserRepository users){return email->users.findByEmailIgnoreCase(email.trim())
        .map(u->User.withUsername(u.email).password(u.passwordHash).roles(u.role).build())
        .orElseThrow(()->new UsernameNotFoundException("Account not found"));}
    @Bean AuthenticationManager authenticationManager(UserDetailsService details,PasswordEncoder passwords){
        var provider=new DaoAuthenticationProvider(details);provider.setPasswordEncoder(passwords);return new ProviderManager(provider);
    }
    @Bean CookieCsrfTokenRepository csrfTokens(){return CookieCsrfTokenRepository.withHttpOnlyFalse();}
    @Bean SecurityContextRepository contexts(){return new HttpSessionSecurityContextRepository();}
    @Bean SecurityFilterChain security(HttpSecurity http,CookieCsrfTokenRepository csrf,SecurityContextRepository contexts) throws Exception {
        return http.csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()).ignoringRequestMatchers("/webhooks/swiftdrop"))
            .securityContext(c->c.securityContextRepository(contexts))
            .httpBasic(b->b.disable()).formLogin(f->f.disable())
            .authorizeHttpRequests(a->a
                // An authenticated stream may finish after logout invalidates its session.
                // Allow only its internal completion dispatch; new connections still require the role below.
                .requestMatchers(request->request.getDispatcherType()==DispatcherType.ASYNC
                    && (request.getRequestURI().equals(request.getContextPath()+"/api/admin/live")
                        || request.getRequestURI().equals(request.getContextPath()+"/api/customer/live"))).permitAll()
                .requestMatchers("/api/catalog","/api/auth/csrf","/api/auth/login","/api/auth/register","/webhooks/swiftdrop","/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/customer/**").hasRole("CUSTOMER").anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->{r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"error\":\"Please sign in.\"}");})
                .accessDeniedHandler((q,r,x)->{r.setStatus(403);r.setContentType("application/json");r.getWriter().write("{\"error\":\"Access denied or session token expired. Refresh and try again.\"}");}))
            .logout(l->l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID").logoutSuccessHandler((q,r,a)->r.setStatus(204)))
            .build();
    }
}
