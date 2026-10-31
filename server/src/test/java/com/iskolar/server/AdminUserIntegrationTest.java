package com.iskolar.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin user management: listing, role changes, disabling. Role/active changes
 * must take effect immediately (the JWT filter reads them from the database,
 * not from the token claims).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest {

    private static EmbeddedPostgres postgres;

    @TestConfiguration
    static class EmbeddedPgConfig {
        @Bean
        @Primary
        DataSource testDataSource() throws IOException {
            if (postgres == null) {
                postgres = EmbeddedPostgres.builder().start();
            }
            return postgres.getPostgresDatabase();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    @Test
    void adminListsAccountsWithRolesAndStatus() throws Exception {
        register("listed@iskolar.app", "listing-pass-1");
        String token = tokenFor("admin@iskolar.app", "password");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem("admin@iskolar.app")))
                .andExpect(jsonPath("$[*].email", hasItem("listed@iskolar.app")))
                .andExpect(jsonPath("$[?(@.email == 'admin@iskolar.app')].role", hasItem("ADMIN")))
                .andExpect(jsonPath("$[?(@.email == 'admin@iskolar.app')].isActive", hasItem(true)))
                .andExpect(jsonPath("$[?(@.email == 'listed@iskolar.app')].role", hasItem("STUDENT")));
    }

    @Test
    void studentsAndGuestsAreRejected() throws Exception {
        register("plain@iskolar.app", "plain-pass-1");
        String studentToken = tokenFor("plain@iskolar.app", "plain-pass-1");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Admin access required.")));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Authentication required.")));
    }

    // ------------------------------------------------------------------
    // Role changes
    // ------------------------------------------------------------------

    @Test
    void promoteAndDemoteTakeEffectImmediately() throws Exception {
        long id = register("promote@iskolar.app", "promote-pass-1");
        String userToken = tokenFor("promote@iskolar.app", "promote-pass-1");
        String adminToken = tokenFor("admin@iskolar.app", "password");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        patchUser(id, "{\"role\":\"ADMIN\"}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ADMIN")));

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        patchUser(id, "{\"role\":\"STUDENT\"}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("STUDENT")));

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRoleIsRejected() throws Exception {
        long id = register("roleless@iskolar.app", "roleless-pass-1");
        String adminToken = tokenFor("admin@iskolar.app", "password");

        patchUser(id, "{\"role\":\"SUPERUSER\"}", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Role must be STUDENT or ADMIN.")));
    }

    // ------------------------------------------------------------------
    // Disabling accounts
    // ------------------------------------------------------------------

    @Test
    void disablingAccountBlocksAccessAndMeReportsDisabled() throws Exception {
        long id = register("victim@iskolar.app", "victim-pass-1");
        String victimToken = tokenFor("victim@iskolar.app", "victim-pass-1");
        String adminToken = tokenFor("admin@iskolar.app", "password");

        patchUser(id, "{\"isActive\":false}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + victimToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("This account has been disabled.")));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + victimToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("This account has been disabled.")));

        // A disabled account cannot log in again either.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"victim@iskolar.app\",\"password\":\"victim-pass-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("This account has been disabled.")));

        // Re-enabling restores access.
        patchUser(id, "{\"isActive\":true}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(true)));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + victimToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Guardrails
    // ------------------------------------------------------------------

    @Test
    void adminsCannotDemoteOrDisableThemselves() throws Exception {
        String adminToken = tokenFor("admin@iskolar.app", "password");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        long adminId = objectMapper.readTree(
                        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + adminToken))
                                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("userId").asLong();

        patchUser(adminId, "{\"role\":\"STUDENT\"}", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("You cannot change your own role.")));

        patchUser(adminId, "{\"isActive\":false}", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("You cannot disable your own account.")));
    }

    @Test
    void emptyUpdatesAndUnknownUsersAreRejected() throws Exception {
        long id = register("noop@iskolar.app", "noop-pass-1");
        String adminToken = tokenFor("admin@iskolar.app", "password");

        patchUser(id, "{}", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Nothing to update.")));

        patchUser(999_999L, "{\"isActive\":false}", adminToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("User not found.")));
    }

    // ------------------------------------------------------------------
    // Presence
    // ------------------------------------------------------------------

    @Test
    void presenceMarksRecentlyActiveAccountsOnline() throws Exception {
        register("presence@iskolar.app", "presence-pass-1");
        String adminToken = tokenFor("admin@iskolar.app", "password");

        // A brand-new account that has never signed in is not online yet.
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'admin@iskolar.app')].online", hasItem(true)))
                .andExpect(jsonPath("$[?(@.email == 'presence@iskolar.app')].online", hasItem(false)));

        // Signing in records presence, and an authenticated call refreshes it.
        String studentToken = tokenFor("presence@iskolar.app", "presence-pass-1");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'presence@iskolar.app')].online", hasItem(true)))
                .andExpect(jsonPath("$[?(@.email == 'presence@iskolar.app')].lastSeenAt",
                        hasItem(org.hamcrest.Matchers.notNullValue())));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long register(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("userId").asLong();
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("token").asText();
    }

    private ResultActions patchUser(long id, String body, String token) throws Exception {
        return mvc.perform(patch("/api/admin/users/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token));
    }
}
