package com.nkd.nexbridge.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Properties;

@Service
@Slf4j
public class VaultService {

    private static final String VAULT_PREFIX = "vault://";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final byte[] SALT = "NexBridgeVaultSalt".getBytes(StandardCharsets.UTF_8);

    @Value("${nexbridge.vault.master-key}")
    private String masterKey;

    @Value("${nexbridge.vault.store-path}")
    private String storePath;

    private SecretKey secretKey;
    private final Properties store = new Properties();

    @PostConstruct
    void init() {
        try {
            secretKey = deriveKey(masterKey);
            Path path = Path.of(storePath);
            if (Files.exists(path)) {
                load(path);
            }
            log.info("VaultService initialized. Store: {}", storePath);
        } catch (Exception e) {
            log.warn("VaultService init warning: {}", e.getMessage());
        }
    }

    public String get(String vaultRef) {
        if (!vaultRef.startsWith(VAULT_PREFIX)) {
            return vaultRef;
        }
        String key = vaultRef.substring(VAULT_PREFIX.length()).replace("/", ".");
        String value = store.getProperty(key);
        if (value == null) {
            log.warn("Vault key not found: {}", key);
            return null;
        }
        return value;
    }

    public void put(String vaultRef, String value) {
        String key = vaultRef.startsWith(VAULT_PREFIX)
                ? vaultRef.substring(VAULT_PREFIX.length()).replace("/", ".")
                : vaultRef;
        store.setProperty(key, value);
        try {
            save(Path.of(storePath));
        } catch (Exception e) {
            log.error("Failed to persist vault store", e);
        }
    }

    private void load(Path path) throws Exception {
        byte[] encrypted = Files.readAllBytes(path);
        byte[] decrypted = decrypt(encrypted);
        store.load(new ByteArrayInputStream(decrypted));
    }

    private void save(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        store.store(bos, "NexBridge Vault");
        byte[] encrypted = encrypt(bos.toByteArray());
        Files.write(path, encrypted);
    }

    private byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);
        byte[] result = new byte[16 + encrypted.length];
        System.arraycopy(iv, 0, result, 0, 16);
        System.arraycopy(encrypted, 0, result, 16, encrypted.length);
        return result;
    }

    private byte[] decrypt(byte[] data) throws Exception {
        byte[] iv = new byte[16];
        System.arraycopy(data, 0, iv, 0, 16);
        byte[] encrypted = new byte[data.length - 16];
        System.arraycopy(data, 16, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher.doFinal(encrypted);
    }

    private SecretKey deriveKey(String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, 65536, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
