package com.swiftdrop.identity_service.repository;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() { }

    public static Specification<User> withFilter(
            String search,
            String name,
            String email,
            String phone,
            Role role,
            UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                Predicate phoneMatch = cb.and(cb.isNotNull(root.get("phone")),
                        cb.like(cb.lower(root.get("phone")), pattern));
                predicates.add(cb.or(nameMatch, emailMatch, phoneMatch));
            }

            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + name.trim().toLowerCase(Locale.ROOT) + "%"));
            }

            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")),
                        "%" + email.trim().toLowerCase(Locale.ROOT) + "%"));
            }

            if (phone != null && !phone.isBlank()) {
                predicates.add(cb.and(cb.isNotNull(root.get("phone")),
                        cb.like(cb.lower(root.get("phone")),
                                "%" + phone.trim().toLowerCase(Locale.ROOT) + "%")));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

