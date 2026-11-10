package com.onesignal.platform.common.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class Argon2PasswordService implements PasswordEncoder {

    // 16-byte salt, 32-byte hash, 1 thread parallelism, 64MB memory, 3 iterations
    private final Argon2PasswordEncoder delegate = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        // Handle optional {argon2} prefix if present
        String cleanHash = encodedPassword.startsWith("{argon2}") 
                ? encodedPassword.substring(8) 
                : encodedPassword;
        return delegate.matches(rawPassword, cleanHash);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
