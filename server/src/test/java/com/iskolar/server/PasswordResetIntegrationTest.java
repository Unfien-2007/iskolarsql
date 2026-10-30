package com.iskolar.server;

import com.iskolar.server.auth.MailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Password-reset flow against an embedded PostgreSQL: the forgot endpoint
 * never leaks which emails exist, the reset token is single-use, and the
 * new password replaces the old one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    static EmbeddedPostgres postgres;

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

    @MockitoBean
    private MailService mailService;

    private String loginStatus(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse();
        return String.valueOf(response.getStatus());
    }

    private String requestToken(String email) throws Exception {
        mvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendResetToken(eq(email), captor.capture());
        return captor.getValue();
    }

    @Test
    void forgotPasswordSendsTokenOnlyForKnownActiveAccounts() throws Exception {
        String known = mvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@iskolar.app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andReturn().getResponse().getContentAsString();

        String unknown = mvc.perform(post("/api/auth/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@iskolar.app\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Identical answers — the endpoint must not leak which emails exist.
        JsonNode knownNode = objectMapper.readTree(known);
        JsonNode unknownNode = objectMapper.readTree(unknown);
        assertEquals(knownNode.path("message").asText(),
                unknownNode.path("message").asText());
        assertNotEquals("", knownNode.path("message").asText());

        verify(mailService).sendResetToken(eq("admin@iskolar.app"), anyString());
        verify(mailService, never()).sendResetToken(eq("nobody@iskolar.app"), anyString());
    }

    @Test
    void resetTokenChangesPassword() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resetme@iskolar.app\",\"password\":\"oldpassword1\"}"))
                .andExpect(status().isCreated());

        String token = requestToken("resetme@iskolar.app");
        assertEquals("200", loginStatus("resetme@iskolar.app", "oldpassword1"));

        mvc.perform(post("/api/auth/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"brand-new-pass-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertEquals("401", loginStatus("resetme@iskolar.app", "oldpassword1"));
        assertEquals("200", loginStatus("resetme@iskolar.app", "brand-new-pass-1"));
    }

    @Test
    void resetTokenIsSingleUse() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"onetime@iskolar.app\",\"password\":\"oldpassword1\"}"))
                .andExpect(status().isCreated());

        String token = requestToken("onetime@iskolar.app");

        mvc.perform(post("/api/auth/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"firstnewpass1\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"secondnewpass1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This reset link has already been used."));
    }

    @Test
    void resetRejectsInvalidTokenAndShortPassword() throws Exception {
        mvc.perform(post("/api/auth/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage-token\",\"newPassword\":\"longenough1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This reset link is invalid or has expired."));

        mvc.perform(post("/api/auth/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"garbage-token\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
