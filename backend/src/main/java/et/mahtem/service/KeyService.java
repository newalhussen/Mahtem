package et.mahtem.service;

import et.mahtem.crypto.Ed25519;
import et.mahtem.crypto.Hashing;
import et.mahtem.crypto.KeyVault;
import et.mahtem.domain.KeyStatus;
import et.mahtem.domain.Organization;
import et.mahtem.domain.SigningKey;
import et.mahtem.repo.SigningKeyRepository;
import et.mahtem.util.ApiException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, rotates and uses organizations' Ed25519 signing keys. Private keys exist in memory only while signing. */
@Service
public class KeyService {

    private static final int KEY_VALIDITY_YEARS = 1;

    private final SigningKeyRepository keys;
    private final KeyVault vault;

    public KeyService(SigningKeyRepository keys, KeyVault vault) {
        this.keys = keys;
        this.vault = vault;
    }

    /** The organization's active key, created on first use. */
    @Transactional
    public SigningKey activeKey(Organization org) {
        return keys.findByOrganizationIdAndStatus(org.getId(), KeyStatus.ACTIVE).orElseGet(() -> create(org));
    }

    /** Retire the active key (it keeps verifying what it signed) and start a new one. */
    @Transactional
    public SigningKey rotate(Organization org) {
        keys.findByOrganizationIdAndStatus(org.getId(), KeyStatus.ACTIVE).ifPresent(k -> {
            k.retire(LocalDate.now());
            keys.saveAndFlush(k); // free the unique "one active key" slot before inserting the next
        });
        return create(org);
    }

    public List<SigningKey> list(UUID orgId) {
        return keys.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    public SigningKey get(UUID id) {
        return keys.findById(id).orElseThrow(() -> ApiException.notFound("Signing key"));
    }

    public byte[] sign(SigningKey key, byte[] message) {
        byte[] pkcs8 = vault.decrypt(key.getPrivateKeyEnc(), aad(key.getId(), key.getOrganizationId()));
        try {
            return Ed25519.sign(message, pkcs8);
        } finally {
            java.util.Arrays.fill(pkcs8, (byte) 0);
        }
    }

    public static String shortFingerprint(String fingerprint) {
        String f = fingerprint.toUpperCase();
        return f.substring(0, 4) + " " + f.substring(4, 8) + " … " + f.substring(f.length() - 8, f.length() - 4) + " " + f.substring(f.length() - 4);
    }

    private SigningKey create(Organization org) {
        Ed25519.Pair pair = Ed25519.generate();
        UUID id = UUID.randomUUID();
        byte[] enc = vault.encrypt(pair.privateKey(), aad(id, org.getId()));
        java.util.Arrays.fill(pair.privateKey(), (byte) 0);
        LocalDate today = LocalDate.now();
        String label = org.getCode() + " signing key " + today.getYear();
        String fingerprint = Hashing.sha256Hex(pair.publicKey());
        return keys.save(new SigningKey(id, org.getId(), label, pair.publicKey(), enc, fingerprint, today, today.plusYears(KEY_VALIDITY_YEARS)));
    }

    /** Binds the ciphertext to this key row and organization. */
    private static String aad(UUID keyId, UUID orgId) {
        return "signing-key:" + orgId + ":" + keyId;
    }

    public static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
