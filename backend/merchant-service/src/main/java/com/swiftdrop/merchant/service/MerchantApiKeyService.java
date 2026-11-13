package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.dto.response.CreatedMerchantApiKeyResponse;
import com.swiftdrop.merchant.dto.response.MerchantApiKeyResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiKey;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class MerchantApiKeyService {
    private final MerchantRepository merchants;
    private final MerchantApiKeyRepository keys;
    private final SecureRandom random = new SecureRandom();

    public MerchantApiKeyService(MerchantRepository merchants, MerchantApiKeyRepository keys) {
        this.merchants = merchants;
        this.keys = keys;
    }

    @Transactional
    public CreatedMerchantApiKeyResponse create(Long userId, String keyName) {
        Merchant merchant = approvedMerchant(userId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String plaintext = "sd_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        MerchantApiKey key = new MerchantApiKey();
        key.setMerchant(merchant);
        key.setKeyName(keyName.trim());
        key.setKeyPrefix(plaintext.substring(0, 12));
        key.setKeyHash(sha256(plaintext));
        MerchantApiKey saved = keys.saveAndFlush(key);
        return new CreatedMerchantApiKeyResponse(MerchantApiKeyResponse.from(saved), plaintext);
    }

    @Transactional(readOnly = true)
    public List<MerchantApiKeyResponse> list(Long userId) {
        Merchant merchant = approvedMerchant(userId);
        return keys.findByMerchantIdOrderByCreatedAtDesc(merchant.getId())
                .stream().map(MerchantApiKeyResponse::from).toList();
    }

    @Transactional
    public void revoke(Long userId, Long keyId) {
        Merchant merchant = approvedMerchant(userId);
        MerchantApiKey key = keys.findByIdAndMerchantId(keyId, merchant.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        if (key.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "API key already revoked");
        }
        key.setRevokedAt(OffsetDateTime.now());
    }

    private Merchant approvedMerchant(Long userId) {
        Merchant merchant = merchants.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Merchant profile not found"));
        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Merchant must be approved to manage API keys");
        }
        return merchant;
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
