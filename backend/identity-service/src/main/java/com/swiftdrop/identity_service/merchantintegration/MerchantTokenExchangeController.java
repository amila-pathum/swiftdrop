package com.swiftdrop.identity_service.merchantintegration;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class MerchantTokenExchangeController {
    private final UserRepository users;
    private final JwtService tokens;
    public MerchantTokenExchangeController(UserRepository users, JwtService tokens) { this.users = users; this.tokens = tokens; }

    @PostMapping("/internal/merchant-order-tokens") @Transactional(readOnly = true)
    public ResponseEntity<Token> exchange(@Valid @RequestBody Request request) {
        var user = users.findById(request.userId()).orElse(null);
        if (user == null || user.getRole() != Role.MERCHANT || user.getStatus() != UserStatus.ACTIVE) {
            return ResponseEntity.status(403).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Token(user.getId(), tokens.generateMerchantIntegrationToken(user), 60));
    }
    public record Request(@NotNull @Positive Long userId) { }
    public record Token(Long userId, String accessToken, int expiresIn) {
        @Override public String toString() { return "Token[userId=" + userId + ", expiresIn=" + expiresIn + "]"; }
    }
}
