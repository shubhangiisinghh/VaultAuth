package dev.vaultauth.credentials;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UsernamePasswordCredentials implements Credentials {
    private final String username;
    private final String password;

    @Override
    public String getClientName() {
        return "UsernamePassword";
    }
}
