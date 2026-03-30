package dev.vaultauth.credentials;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TotpCredentials implements Credentials {
    private final String code;

    @Override
    public String getClientName() {
        return "TOTP";
    }
}
