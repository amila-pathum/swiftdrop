package com.swiftdrop.identity_service.config;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Creates the first administrator from the environment, because no API can:
 * there is no public way to become an ADMIN, and creating one through the API
 * needs an existing ADMIN.
 *
 * <p>Runs only when both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD are
 * set; startup fails if only one is, or if the password is too short. It never
 * changes an existing account. An existing ADMIN is left untouched; an
 * existing non-admin causes startup to fail because silently continuing would
 * leave the installation without the administrator the operator configured.
 * Remove the two variables once the administrator exists.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int PASSWORD_MIN = 12;
    static final int PASSWORD_MAX = 72;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${swiftdrop.admin.bootstrap-email:}") String email,
            @Value("${swiftdrop.admin.bootstrap-password:}") String password,
            @Value("${swiftdrop.admin.bootstrap-name:SwiftDrop Administrator}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasEmail = StringUtils.hasText(email);
        boolean hasPassword = StringUtils.hasText(password);
        if (!hasEmail && !hasPassword) {
            return;
        }
        if (!hasEmail || !hasPassword) {
            throw new IllegalStateException(
                "ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD must be set together");
        }
        if (!email.contains("@") || email.length() > 255) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must be an email address");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD must be "
                + PASSWORD_MIN + " to " + PASSWORD_MAX + " characters");
        }

        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_EMAIL belongs to an existing non-admin account; "
                    + "choose a different email or resolve the account explicitly");
            }
            log.info("Admin bootstrap skipped: the administrator account already exists");
            return;
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Admin bootstrap skipped: an administrator account already exists");
            return;
        }
        userRepository.save(new User(name.trim(), email, passwordEncoder.encode(password),
            Role.ADMIN, UserStatus.ACTIVE));
        log.info("Admin bootstrap: created the initial administrator account");
    }
}
