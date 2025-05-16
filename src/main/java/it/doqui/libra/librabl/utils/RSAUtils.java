package it.doqui.libra.librabl.utils;

import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import org.apache.commons.codec.binary.Base64;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RSAUtils {

    private RSAUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static PublicKey decodePublicKeyFromBase64(String publicKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] decoded = new Base64().decode(publicKey);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    public static PrivateKey decodePrivateKeyFromBase64(String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] decoded = new Base64().decode(privateKey);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    public static boolean verify(String plainText, byte[] signature, PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(plainText.getBytes());
        return publicSignature.verify(signature);
    }

    public static boolean verify(String plainText, String signature, String publicKey) {
        byte[] signatureBytes = new Base64().decode(signature);
        try {
            var pk = decodePublicKeyFromBase64(publicKey);
            return verify(plainText, signatureBytes, pk);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new SystemException(e);
        }
    }

    public static byte[] sign(String plainText, PrivateKey privateKey)
        throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(plainText.getBytes());
        return privateSignature.sign();
    }
}
