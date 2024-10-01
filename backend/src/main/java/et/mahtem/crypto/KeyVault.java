package et.mahtem.crypto;

import et.mahtem.config.MahtemProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets (organization signing keys, TOTP secrets) at rest with AES-256-GCM.
 * The master key comes from the environment, never from the database. Each ciphertext is bound to
 * its owner through GCM additional authenticated data, so a blob copied onto another row fails to decrypt.
 *
 * <p>Layout: 12-byte random nonce || ciphertext+tag.
 */
@Component
public class KeyVault {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public KeyVault(MahtemProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.security().masterKey().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MAHTEM_MASTER_KEY must be valid base64.", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("MAHTEM_MASTER_KEY must decode to exactly 32 bytes.");
        }
        this.masterKey = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(byte[] plaintext, String aad) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(nonce.length + ct.length).put(nonce).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] blob, String aad) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(blob);
            byte[] nonce = new byte[NONCE_BYTES];
            buf.get(nonce);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ct);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed (wrong master key or tampered data)", e);
        }
    }
}
