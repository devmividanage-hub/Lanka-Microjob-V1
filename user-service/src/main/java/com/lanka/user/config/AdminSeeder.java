package com.lanka.user.config;

import com.lanka.user.model.Admin;
import com.lanka.user.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the bootstrap administrator on first start only.
 *
 * <p>Credentials come from {@code ADMIN_DEFAULT_EMAIL} / {@code ADMIN_DEFAULT_PASSWORD}. The
 * defaults are documented development placeholders - set both variables in any real deployment.
 * An existing admin row is never overwritten, so a changed password survives restarts.</p>
 */
@Component
public class AdminSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminSeeder(AdminRepository admins,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.default-email}") String email,
                       @Value("${app.admin.default-password}") String password,
                       @Value("${app.admin.default-name}") String name) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(String... args) {
        if (admins.findByGmail(email).isPresent()) {
            return;
        }
        Admin admin = new Admin();
        admin.setGmail(email);
        admin.setName(name);
        admin.setPassword(passwordEncoder.encode(password));
        admins.save(admin);
        log.info("Bootstrap admin '{}' created. Change ADMIN_DEFAULT_PASSWORD before deploying.", email);
    }
}
