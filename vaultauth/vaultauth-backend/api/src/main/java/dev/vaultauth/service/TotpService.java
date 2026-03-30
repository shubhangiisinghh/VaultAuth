package dev.vaultauth.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.vaultauth.model.TotpSecret;
import dev.vaultauth.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Service
@RequiredArgsConstructor
public class TotpService {

    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final TotpSecretRepository totpSecretRepository;
    private final UserRepository userRepository;

    /**
     * Generate a new TOTP secret for the user and return the QR code data URI.
     * The secret is saved but NOT confirmed yet — confirmation happens when the
     * user submits their first valid code.
     */
    @Transactional
    public String generateSetup(User user) throws QrGenerationException {
        // remove any previous unconfirmed setup
        totpSecretRepository.findByUser(user).ifPresent(totpSecretRepository::delete);

        String secret = secretGenerator.generate();

        TotpSecret totpSecret = TotpSecret.builder()
                .user(user)
                .secret(secret)
                .confirmed(false)
                .build();
        totpSecretRepository.save(totpSecret);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("VaultAuth")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        return getDataUriForImage(qrGenerator.generate(qrData), qrGenerator.getImageMimeType());
    }

    /**
     * Confirm enrolment — validate the first code the user submits after scanning.
     */
    @Transactional
    public boolean confirmEnrolment(User user, String code) {
        Optional<TotpSecret> maybeSecret = totpSecretRepository.findByUser(user);
        if (maybeSecret.isEmpty()) return false;

        TotpSecret totpSecret = maybeSecret.get();
        if (!validateCode(totpSecret.getSecret(), code)) return false;

        totpSecret.setConfirmed(true);
        totpSecretRepository.save(totpSecret);

        user.setTwoFactorEnabled(true);
        user.setSecondFactorType(dev.vaultauth.model.SecondFactorType.TOTP);
        userRepository.save(user);

        return true;
    }

    /**
     * Validate a TOTP code against the user's confirmed secret.
     * Returns false if the user has no confirmed secret.
     */
    public boolean validate(User user, String code) {
        return totpSecretRepository.findByUserAndConfirmedTrue(user)
                .map(s -> validateCode(s.getSecret(), code))
                .orElse(false);
    }

    private boolean validateCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        return verifier.isValidCode(secret, code);
    }
}
