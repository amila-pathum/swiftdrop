package com.stylecart.web;
import com.stylecart.model.ShopUser;
import com.stylecart.repo.UserRepository;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;private final PasswordEncoder passwords;private final AuthenticationManager auth;
    private final SecurityContextRepository contexts;private final CookieCsrfTokenRepository csrf;
    public AuthController(UserRepository users,PasswordEncoder passwords,AuthenticationManager auth,SecurityContextRepository contexts,CookieCsrfTokenRepository csrf){this.users=users;this.passwords=passwords;this.auth=auth;this.contexts=contexts;this.csrf=csrf;}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/me") public UserView me(){return view(current());}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public UserView register(@Valid @RequestBody Registration body){
        String email=body.email().trim().toLowerCase(Locale.ROOT);
        if(users.findByEmailIgnoreCase(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"That email already has an account.");
        var u=new ShopUser();u.name=body.name().trim();u.email=email;u.passwordHash=passwords.encode(body.password());u.role="CUSTOMER";
        return view(users.saveAndFlush(u));
    }
    @PostMapping("/login") public UserView login(@Valid @RequestBody Login body,HttpServletRequest request,HttpServletResponse response){
        try{
            var authenticated=auth.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.email().trim().toLowerCase(Locale.ROOT),body.password()));
            request.getSession(true);request.changeSessionId();
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);csrf.saveToken(null,request,response);
            return view(current());
        }catch(org.springframework.security.core.AuthenticationException e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");}
    }
    public ShopUser current(){return users.findByEmailIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())
        .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in."));}
    public static UserView view(ShopUser u){return new UserView(u.id,u.name,u.email,u.role);}
    public record UserView(Long id,String name,String email,String role){}
    public record Registration(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(min=8,max=72) String password){@Override public String toString(){return "Registration[REDACTED]";}}
    public record Login(@NotBlank @Email String email,@NotBlank @Size(max=72) String password){@Override public String toString(){return "Login[REDACTED]";}}
}
