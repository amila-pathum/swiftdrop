package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.dto.request.UpdateMerchantWebhookRequest;
import com.swiftdrop.merchant.dto.response.MerchantWebhookResponse;
import com.swiftdrop.merchant.dto.response.MerchantWebhookWithSecretResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.entity.MerchantWebhookSettings;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.repository.MerchantWebhookSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Service
public class MerchantWebhookService {
    private final MerchantRepository merchants;
    private final MerchantWebhookSettingsRepository settings;
    private final SecureRandom random = new SecureRandom();
    private final String encryptionKey;

    public MerchantWebhookService(MerchantRepository merchants,
                                  MerchantWebhookSettingsRepository settings,
                                  @Value("${webhook.encryption-key:}") String encryptionKey) {
        this.merchants = merchants;
        this.settings = settings;
        this.encryptionKey = encryptionKey;
    }

    @Transactional(readOnly = true)
    public MerchantWebhookResponse get(Long userId) {
        Merchant merchant = approvedMerchant(userId);
        return settings.findByMerchantId(merchant.getId())
                .map(MerchantWebhookResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Webhook settings not configured"));
    }

    @Transactional
    public MerchantWebhookWithSecretResponse configure(Long userId,
                                                       UpdateMerchantWebhookRequest request) {
        Merchant merchant = approvedMerchant(userId);
        validateHttpsUrl(request.url());
        MerchantWebhookSettings config = settings.findByMerchantId(merchant.getId()).orElse(null);
        String newSecret = null;
        if (config == null) {
            config = new MerchantWebhookSettings();
            config.setMerchant(merchant);
            newSecret = generateSecret();
            config.setEncryptedSigningSecret(encrypt(newSecret));
        }
        config.setWebhookUrl(request.url().trim());
        config.setEnabled(request.enabled());
        config.markUpdated();
        MerchantWebhookSettings saved = settings.saveAndFlush(config);
        return new MerchantWebhookWithSecretResponse(MerchantWebhookResponse.from(saved), newSecret);
    }

    @Transactional
    public MerchantWebhookWithSecretResponse rotateSecret(Long userId) {
        Merchant merchant = approvedMerchant(userId);
        MerchantWebhookSettings config = settings.findByMerchantId(merchant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Webhook settings not configured"));
        String newSecret = generateSecret();
        config.setEncryptedSigningSecret(encrypt(newSecret));
        config.markUpdated();
        return new MerchantWebhookWithSecretResponse(
                MerchantWebhookResponse.from(settings.saveAndFlush(config)), newSecret);
    }

    private Merchant approvedMerchant(Long userId) {
        Merchant merchant = merchants.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Merchant profile not found"));
        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Merchant must be approved");
        }
        return merchant;
    }

    private void validateHttpsUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    uri.getHost() == null || uri.getRawUserInfo() != null ||
                    uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid URL");
            }
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Webhook URL must be a valid HTTPS URL without credentials or fragment");
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encrypt(String plaintext) {
        try {
            byte[] rawKey = Base64.getDecoder().decode(encryptionKey);
            if (rawKey.length != 32) throw new IllegalStateException("Invalid key length");
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(rawKey, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = Arrays.copyOf(iv, iv.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WEBHOOK_ENCRYPTION_KEY must be base64 32 bytes", e);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook encryption failed; check WEBHOOK_ENCRYPTION_KEY", e);
        }
    }
    @Transactional(readOnly = true)
    public InternalWebhookSettings getInternalSettings(Long merchantId) {
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Merchant not found"));

        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            return new InternalWebhookSettings(null, null, false);
        }

        return settings.findByMerchantId(merchantId)
                .filter(MerchantWebhookSettings::isEnabled)
                .map(config -> new InternalWebhookSettings(
                        config.getWebhookUrl(),
                        decrypt(config.getEncryptedSigningSecret()),
                        true
                ))
                .orElseGet(() ->
                        new InternalWebhookSettings(null, null, false));
    }

    public record InternalWebhookSettings(
            String url,
            String signingSecret,
            boolean enabled
    ) {
        @Override
        public String toString() {
            return "InternalWebhookSettings[enabled=" + enabled + "]";
        }
    }

    private String decrypt(String encryptedValue) {
        try {
            byte[] rawKey = Base64.getDecoder().decode(encryptionKey);
            if (rawKey.length != 32) {
                throw new IllegalStateException("Invalid encryption key length");
            }

            byte[] packed = Base64.getDecoder().decode(encryptedValue);
            if (packed.length < 28) {
                throw new IllegalStateException("Invalid encrypted secret");
            }

            byte[] iv = Arrays.copyOfRange(packed, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(packed, 12, packed.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(rawKey, "AES"),
                    new GCMParameterSpec(128, iv)
            );

            return new String(
                    cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Webhook signing secret could not be decrypted"
            );
        }
    }
}
