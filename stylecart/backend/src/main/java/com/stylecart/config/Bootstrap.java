package com.stylecart.config;
import com.stylecart.model.*;
import com.stylecart.repo.*;
import com.stylecart.service.SecretBox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
@Component
public class Bootstrap implements CommandLineRunner {
    private final UserRepository users;private final SettingsRepository settings;private final PasswordEncoder passwords;private final SecretBox secrets;
    @Value("${stylecart.admin.email}") String email;@Value("${stylecart.admin.password}") String password;
    @Value("${stylecart.gateway}") String gateway;@Value("${stylecart.api-key}") String apiKey;@Value("${stylecart.webhook-secret}") String secret;
    public Bootstrap(UserRepository users,SettingsRepository settings,PasswordEncoder passwords,SecretBox secrets){this.users=users;this.settings=settings;this.passwords=passwords;this.secrets=secrets;}
    @Override @Transactional public void run(String...args){
        if(users.findByEmailIgnoreCase(email).isEmpty()){
            if(password.trim().length()<12||password.length()>72)throw new IllegalStateException("Set STYLECART_ADMIN_PASSWORD to 12–72 characters.");
            var admin=new ShopUser();admin.name="StyleCart Admin";admin.email=email.trim().toLowerCase(java.util.Locale.ROOT);
            admin.role="ADMIN";admin.passwordHash=passwords.encode(password);users.save(admin);
        }
        if(settings.findById(1L).isEmpty()){
            var config=new IntegrationSettings();config.gatewayUrl=gateway;config.apiKeyCipher=secrets.encrypt(apiKey);config.secretCipher=secrets.encrypt(secret);settings.save(config);
        }
    }
}
