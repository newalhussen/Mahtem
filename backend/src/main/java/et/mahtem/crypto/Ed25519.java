package et.mahtem.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Ed25519 key generation, signing and verification using the JDK's built-in provider. */
public final class Ed25519 {

    private Ed25519() {}

    /** A fresh key pair: public key as X.509, private key as PKCS#8. */
    public record Pair(byte[] publicKey, byte[] privateKey) {}

    public static Pair generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return new Pair(pair.getPublic().getEncoded(), pair.getPrivate().getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 is unavailable", e);
        }
    }

    public static byte[] sign(byte[] message, byte[] pkcs8PrivateKey) {
        try {
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8PrivateKey));
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(key);
            sig.update(message);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    /** Returns false (never throws) for malformed keys or signatures so verification can report INVALID. */
    public static boolean verify(byte[] message, byte[] signature, byte[] x509PublicKey) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509PublicKey));
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(message);
            return sig.verify(signature);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }
}
