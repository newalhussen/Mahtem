package et.mahtem.service;

import et.mahtem.util.ApiException;

/** Minimum password rules for new passwords. Length matters more than composition. */
final class PasswordPolicy {

    private PasswordPolicy() {}

    static void check(String password) {
        if (password == null || password.length() < 10) {
            throw ApiException.invalid("Use at least 10 characters.", "password");
        }
        if (password.length() > 128) {
            throw ApiException.invalid("Keep your password under 128 characters.", "password");
        }
        String lower = password.toLowerCase();
        if (lower.contains("password") || lower.matches("(.)\\1+") || lower.equals("1234567890")) {
            throw ApiException.invalid("Choose something harder to guess.", "password");
        }
    }
}
