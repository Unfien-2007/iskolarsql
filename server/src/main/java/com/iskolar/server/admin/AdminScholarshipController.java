package com.iskolar.server.admin;

import com.iskolar.server.admin.AdminDtos.ScholarshipWrite;
import com.iskolar.server.auth.AuthPrincipal;
import com.iskolar.server.common.ApiException;
import com.iskolar.server.config.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only catalog writes. Each write bumps {@code catalog_version} so
 * desktop clients detect and pull the new snapshot.
 */
@RestController
@RequestMapping("/api/admin/scholarships")
public class AdminScholarshipController {

    private final ScholarshipAdminRepository repo;

    public AdminScholarshipController(ScholarshipAdminRepository repo) {
        this.repo = repo;
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

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody ScholarshipWrite write,
                                    HttpServletRequest request) {
        AuthPrincipal principal = requireAdmin(request);
        validateDates(write);
        long id = repo.insert(write, principal.userId());
        repo.bumpCatalogVersion();
        return Map.of("scholarshipId", id);
    }

    @PutMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable long id,
                       @Valid @RequestBody ScholarshipWrite write,
                       HttpServletRequest request) {
        requireAdmin(request);
        validateDates(write);
        repo.update(id, write);
        repo.bumpCatalogVersion();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, HttpServletRequest request) {
        requireAdmin(request);
        repo.delete(id);
        repo.bumpCatalogVersion();
    }

    private void validateDates(ScholarshipWrite w) {
        if (w.openDate() != null && w.openDate().isAfter(w.deadlineDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Open date must not be after the deadline.");
        }
    }
}
