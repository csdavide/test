package it.doqui.libra.librabl.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.doqui.libra.librabl.foundation.exceptions.SystemException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class EncryptionService {

    // AES-GCM richiede un IV (Initialization Vector) di 12 byte
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // 128 bit auth tag
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    // Iniettiamo la chiave segreta da application.properties
    // La chiave deve essere una stringa Base64 di 32 byte (256 bit)
    @ConfigProperty(name = "libra.encryption.secret")
    Optional<String> secretKeyString;

    @Inject
    ObjectMapper objectMapper;

    public String encryptObject(Object object) {
        if (object == null) {
            return null;
        }

        try {
            return encryptText(objectMapper.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new SystemException(e);
        }
    }

    public Object decryptAsObject(String text) {
        if (text == null) {
            return null;
        }

        try {
            return objectMapper.readValue(decryptText(text), Object.class);
        } catch (JsonProcessingException e) {
            throw new SystemException(e);
        }
    }

    private SecretKey getSecretKey() {
        return secretKeyString.map(secret -> {
            byte[] decodedKey = Base64.getDecoder().decode(secret);
            return new SecretKeySpec(decodedKey, "AES");
        }).orElseThrow(() -> new SystemException("Missing encryption secret"));
    }

    public String encryptText(String plaintext) {
        if (StringUtils.isBlank(plaintext) || secretKeyString.isEmpty()) {
            return plaintext;
        }

        try {
            // 1. Genera un IV casuale (diverso per ogni criptazione!)
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // 2. Configura il Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), parameterSpec);

            // 3. Cripta
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 4. Unisci IV + CipherText (L'IV serve per decriptare, non è segreto)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // 5. Ritorna tutto come stringa Base64 per il DB
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            throw new SystemException("Unable to crypt", e);
        }
    }

    public String decryptText(String encryptedText) {
        if (StringUtils.isBlank(encryptedText) || secretKeyString.isEmpty()) {
            return encryptedText;
        }

        try {
            // 1. Decodifica da Base64
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

            // 2. Estrai l'IV (i primi 12 byte) e il messaggio cifrato
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // 3. Configura il Cipher per decriptare
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), parameterSpec);

            // 4. Decripta
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new SystemException("Unable to decrypt", e);
        }
    }
}