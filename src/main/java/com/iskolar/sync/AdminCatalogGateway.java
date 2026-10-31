package com.iskolar.sync;

import com.iskolar.dto.ScholarshipDraft;
import com.iskolar.model.Scholarship;
import com.iskolar.model.ScholarshipCriteria;
import com.iskolar.service.SyncService;
import com.iskolar.sync.CatalogDtos.CriteriaWriteDto;
import com.iskolar.sync.CatalogDtos.ScholarshipWriteDto;
import com.iskolar.sync.CatalogDtos.TagRequirementDto;
import com.iskolar.view.ServerAuthDialog;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Routes admin catalog edits to the central server. Admins edit online only:
 * an unreachable server aborts the edit with a clear message. The first write
 * of a session (or after a token rejection) prompts for the server password,
 * exchanges it for a JWT via {@code POST /api/auth/login}, and caches it in
 * {@link TokenStore}. After every successful write the local catalog is
 * re-synced so the dashboard shows the server's state.
 */
public final class AdminCatalogGateway {

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogGateway.class);

    private final SyncService sync;

    public AdminCatalogGateway(SyncService sync) {
        this.sync = sync;
    }

    public long create(Window owner, String adminEmail, ScholarshipDraft draft) {
        String token = requireToken(owner, adminEmail);
        long id = withRetryOnUnauthorized(token, owner, adminEmail,
                t -> sync.client().createScholarship(t, toWrite(draft)));
        refreshLocalCatalog();
        return id;
    }

    public void update(Window owner, String adminEmail, long scholarshipId, ScholarshipDraft draft) {
        String token = requireToken(owner, adminEmail);
        withRetryOnUnauthorized(token, owner, adminEmail,
                t -> {
                    sync.client().updateScholarship(t, scholarshipId, toWrite(draft));
                    return null;
                });
        refreshLocalCatalog();
    }

    public void delete(Window owner, String adminEmail, long scholarshipId) {
        String token = requireToken(owner, adminEmail);
        withRetryOnUnauthorized(token, owner, adminEmail,
                t -> {
                    sync.client().deleteScholarship(t, scholarshipId);
                    return null;
                });
        refreshLocalCatalog();
    }

    /** Lists server accounts (admin portal Users tab). */
    public List<CatalogDtos.AdminUserDto> listUsers(Window owner, String adminEmail) {
        String token = requireToken(owner, adminEmail);
        return withRetryOnUnauthorized(token, owner, adminEmail,
                sync.client()::listUsers);
    }

    /** Changes a role and/or the active flag; returns the updated account. */
    public CatalogDtos.AdminUserDto updateUser(Window owner, String adminEmail,
                                               long userId, String role, Boolean isActive) {
        String token = requireToken(owner, adminEmail);
        return withRetryOnUnauthorized(token, owner, adminEmail,
                t -> sync.client().updateUser(t, userId, role, isActive));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private String requireToken(Window owner, String adminEmail) {
        // Offline check first — admin edits require the server.
        try {
            sync.client().fetchVersion();
        } catch (SyncException e) {
            throw new SyncException(
                    "Admin edits require an internet connection. The catalog is stored centrally — "
                            + "reconnect and try again.");
        }

        if (TokenStore.token().isPresent() && adminEmail.equalsIgnoreCase(TokenStore.email())) {
            return TokenStore.token().get();
        }
        return signIn(owner, adminEmail);
    }

    /** Prompts for the server password and logs in; returns the JWT or throws. */
    private String signIn(Window owner, String adminEmail) {
        var password = ServerAuthDialog.promptPassword(owner, adminEmail);
        if (password.isEmpty()) {
            throw new SyncException("Sign-in cancelled — the edit was not saved.");
        }
        var auth = sync.client().login(adminEmail, password.get());
        TokenStore.save(auth.token(), auth.email());
        return auth.token();
    }

    /**
     * Runs the write with {@code token}; on a 401 clears the token, re-prompts
     * once, and retries.
     */
    private <T> T withRetryOnUnauthorized(String token, Window owner, String adminEmail,
                                          TokenConsumer<T> write) {
        try {
            return write.run(token);
        } catch (SyncException.UnauthorizedException e) {
            TokenStore.clear();
            String fresh = signIn(owner, adminEmail);
            return write.run(fresh);
        }
    }

    private void refreshLocalCatalog() {
        var result = sync.syncNow();
        if (!result.success()) {
            log.warn("Admin write succeeded but catalog re-sync failed: {}", result.error());
        }
    }

    /** Maps the local admin draft onto the server's write payload. */
    static ScholarshipWriteDto toWrite(ScholarshipDraft draft) {
        Scholarship s = draft.scholarship();
        ScholarshipCriteria c = draft.criteria();
        return new ScholarshipWriteDto(
                s.title(),
                s.providerName(),
                s.providerType().name(),
                s.description(),
                s.tuitionBenefit(),
                s.monthlyStipend(),
                s.returnServiceYears(),
                s.applicationUrl(),
                s.openDate(),
                s.deadlineDate(),
                s.isActive(),
                new CriteriaWriteDto(
                        c.minGwaPercentage(),
                        c.allowFailingGrade(),
                        c.maxAnnualIncome(),
                        c.allowedSchoolType() == null ? null : c.allowedSchoolType().name(),
                        c.requiredProvince(),
                        c.requiredCity()),
                draft.programIds().stream().map(Integer::longValue).toList(),
                List.copyOf(draft.yearLevels()),
                draft.tagRequirements().stream()
                        .map(r -> new TagRequirementDto(r.tag().tagId(), r.mandatory()))
                        .toList(),
                draft.documentIds().stream().map(Integer::longValue).toList());
    }

    @FunctionalInterface
    private interface TokenConsumer<T> {
        T run(String token);
    }

    /** Utility shared by admin screens to surface gateway errors. */
    public static void showError(Window owner, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setTitle("Admin edit failed");
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.showAndWait();
        });
    }
}
