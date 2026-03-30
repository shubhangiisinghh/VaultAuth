package dev.vaultauth.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.vaultauth.model.TotpSecret;
import dev.vaultauth.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TotpServiceTest {

    @Mock private TotpSecretRepository totpSecretRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private TotpService totpService;

    /**
     * A valid TOTP code generated right now should pass validation.
     */
    @Test
    void validCodePassesValidation() throws Exception {
        String secret = new dev.samstevens.totp.secret.DefaultSecretGenerator().generate();
        String validCode = generateCurrentCode(secret);

        User user = User.builder().id(1L).username("testuser").build();
        TotpSecret totpSecret = TotpSecret.builder()
                .user(user).secret(secret).confirmed(true).build();

        when(totpSecretRepository.findByUserAndConfirmedTrue(user))
                .thenReturn(Optional.of(totpSecret));

        assertThat(totpService.validate(user, validCode)).isTrue();
    }

    /**
     * A totally wrong code (000000) should fail.
     * This won't be a real code unless astronomically unlucky.
     */
    @Test
    void wrongCodeFailsValidation() {
        String secret = new dev.samstevens.totp.secret.DefaultSecretGenerator().generate();
        User user = User.builder().id(1L).username("testuser").build();
        TotpSecret totpSecret = TotpSecret.builder()
                .user(user).secret(secret).confirmed(true).build();

        when(totpSecretRepository.findByUserAndConfirmedTrue(user))
                .thenReturn(Optional.of(totpSecret));

        assertThat(totpService.validate(user, "000000")).isFalse();
    }

    /**
     * A user with no confirmed TOTP secret always fails validation.
     * This guards against the case where enrolment was started but not confirmed.
     */
    @Test
    void noConfirmedSecretAlwaysFails() {
        User user = User.builder().id(1L).username("testuser").build();
        when(totpSecretRepository.findByUserAndConfirmedTrue(user))
                .thenReturn(Optional.empty());

        assertThat(totpService.validate(user, "123456")).isFalse();
    }

    private String generateCurrentCode(String secret) throws Exception {
        CodeGenerator gen = new DefaultCodeGenerator();
        TimeProvider tp = new SystemTimeProvider();
        long bucket = Math.floorDiv(tp.getTime(), 30);
        return gen.generate(secret, bucket);
    }
}
