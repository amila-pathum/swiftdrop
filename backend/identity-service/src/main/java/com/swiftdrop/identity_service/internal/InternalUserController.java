package com.swiftdrop.identity_service.internal;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Contact details other services need, e.g. where to email a parcel update. */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserRepository users;

    public InternalUserController(UserRepository users) {
        this.users = users;
    }

    public record Contact(Long id, String name, String email, Role role, UserStatus status) {
        static Contact from(User user) {
            return new Contact(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus());
        }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Contact> contact(@PathVariable long id) {
        return users.findById(id).map(Contact::from).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Ids of every account with the role and status, oldest first. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Long> ids(@RequestParam Role role, @RequestParam(defaultValue = "ACTIVE") UserStatus status) {
        return users.findByRoleAndStatusOrderByIdAsc(role, status).stream().map(User::getId).toList();
    }
}
