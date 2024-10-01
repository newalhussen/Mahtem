package et.mahtem;

import static org.assertj.core.api.Assertions.assertThat;

import et.mahtem.crypto.Ed25519;
import et.mahtem.crypto.Hashing;
import et.mahtem.crypto.PublicIds;
import et.mahtem.crypto.Totp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import org.junit.jupiter.api.Test;

class CryptoTest {

    @Test
    void ed25519SignsAndRejectsTampering() {
        Ed25519.Pair pair = Ed25519.generate();
        byte[] message = "MHT-AAU-26-K7Q4-8TZ2|Selamawit".getBytes(StandardCharsets.UTF_8);
        byte[] signature = Ed25519.sign(message, pair.privateKey());
        assertThat(Ed25519.verify(message, signature, pair.publicKey())).isTrue();

        byte[] changed = "MHT-AAU-26-K7Q4-8TZ2|Selamawlt".getBytes(StandardCharsets.UTF_8);
        assertThat(Ed25519.verify(changed, signature, pair.publicKey())).isFalse();

        byte[] badSig = signature.clone();
        badSig[0] ^= 1;
        assertThat(Ed25519.verify(message, badSig, pair.publicKey())).isFalse();

        Ed25519.Pair other = Ed25519.generate();
        assertThat(Ed25519.verify(message, signature, other.publicKey())).isFalse();
    }

    @Test
    void sha256MatchesKnownVector() {
        assertThat(Hashing.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void totpMatchesRfc6238Vector() {
        // RFC 6238 appendix B, SHA-1, T=59s -> 94287082; the 6-digit code is its last six digits.
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.code(secret, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.verify(secret, "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(Totp.verify(secret, "287083", Instant.ofEpochSecond(59))).isFalse();
        assertThat(Totp.verify(secret, "12345", Instant.ofEpochSecond(59))).isFalse();
        // codes from far in the past are rejected
        assertThat(Totp.verify(secret, "287082", Instant.ofEpochSecond(59 + 600))).isFalse();
    }

    @Test
    void publicIdsAreWellFormedAndNormalised() {
        String id = PublicIds.next("AAU", Year.of(2026));
        assertThat(id).matches("MHT-AAU-26-[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(PublicIds.isWellFormed(id)).isTrue();
        assertThat(PublicIds.isWellFormed("not-an-id")).isFalse();
        assertThat(PublicIds.normalize("  mht-aau-26-k7q4-8tz2 ")).isEqualTo("MHT-AAU-26-K7Q4-8TZ2");
    }
}
