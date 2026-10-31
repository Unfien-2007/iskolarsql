package com.iskolar.server.admin;

import com.iskolar.server.admin.AdminDtos.AdminUserDto;
import com.iskolar.server.admin.AdminDtos.UserUpdateRequest;
import com.iskolar.server.auth.AccountStatusService;
import com.iskolar.server.auth.AuthPrincipal;
import com.iskolar.server.auth.UserRepository;
import com.iskolar.server.common.ApiException;
import com.iskolar.server.config.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Admin-only account management: list accounts, promote/demote, disable and
 * re-enable. Guardrails prevent an admin from locking themselves out and keep
 * at least one active administrator.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final AccountStatusService statuses;

    public AdminUserController(UserRepository users, AccountStatusService statuses) {
        this.users = users;
        this.statuses = statuses;
    }

    @GetMapping
    public List<AdminUserDto> list(HttpServletRequest request) {
        requireAdmin(request);
        return users.findAllForAdmin().stream()
                .map(AdminUserController::toDto)
                .toList();
    }

    @PatchMapping("/{id}")
    public AdminUserDto update(@PathVariable long id,
                               @RequestBody UserUpdateRequest update,
                               HttpServletRequest request) {
        AuthPrincipal principal = requireAdmin(request);
        if (update.role() == null && update.isActive() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to update.");
        }
        UserRepository.UserRow target = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        String newRole = update.role() == null
                ? target.role()
                : update.role().trim().toUpperCase(Locale.ROOT);
        if (!newRole.equals("STUDENT") && !newRole.equals("ADMIN")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role must be STUDENT or ADMIN.");
        }
        boolean newActive = update.isActive() == null ? target.isActive() : update.isActive();
        boolean self = target.userId() == principal.userId();

        if (self && !newRole.equals(target.role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot change your own role.");
        }
        if (self && !newActive) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot disable your own account.");
        }

        boolean losesActiveAdmin = "ADMIN".equals(target.role()) && target.isActive()
                && (!"ADMIN".equals(newRole) || !newActive);
        if (losesActiveAdmin && users.countActiveAdminsExcluding(target.userId()) < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "At least one active admin must remain.");
        }

        if (!newRole.equals(target.role())) {
            users.updateRole(target.userId(), newRole);
        }
        if (newActive != target.isActive()) {
            users.updateActive(target.userId(), newActive);
        }
        statuses.invalidate(target.userId());

        UserRepository.UserListRow updated = users.findAllForAdmin().stream()
                .filter(row -> row.userId() == target.userId())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        return toDto(updated);
    }

    private AuthPrincipal requireAdmin(HttpServletRequest request) {
        Object attr = request.getAttribute(JwtAuthFilter.AUTH_ATTRIBUTE);
        if (!(attr instanceof AuthPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        if (Boolean.FALSE.equals(request.getAttribute(JwtAuthFilter.ACTIVE_ATTRIBUTE))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "This account has been disabled.");
        }
        if (!principal.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
        return principal;
    }

    private static AdminUserDto toDto(UserRepository.UserListRow row) {
        return new AdminUserDto(row.userId(), row.email(), row.role(),
                row.isActive(), row.createdAt());
    }
}
