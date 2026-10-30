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
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests against a REAL PostgreSQL instance (embedded, spun up
 * by zonky), with Flyway applying the production migrations so the tested
 * schema is exactly what Neon will run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServerIntegrationTest {

    private static EmbeddedPostgres postgres;

    @TestConfiguration(proxyBeanMethods = false)
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

    private static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String tokenFor(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    // ------------------------------------------------------------------ auth

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void registerLoginAndMeFlow() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("new.student@email.com", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Email comparison is case-insensitive on login.
        String token = tokenFor("NEW.STUDENT@email.com", "password123");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.student@email.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void duplicateRegistrationIsConflict() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("dupe@email.com", "password123")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("DUPE@email.com", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmail() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin@iskolar.app", "wrong-password")))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nobody@email.com", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededAdminCanLogIn() throws Exception {
        String token = tokenFor("admin@iskolar.app", "password");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationRejectsBadEmailAndShortPassword() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("not-an-email", "password123")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("short@email.com", "short")))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- catalog

    @Test
    void catalogVersionIsOne() throws Exception {
        mvc.perform(get("/api/catalog/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void catalogSnapshotHasExpectedCounts() throws Exception {
        MvcResult result = mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(root.get("version").asInt()).isEqualTo(1);
        assertThat(root.get("degreePrograms")).hasSize(16);
        assertThat(root.get("tags")).hasSize(10);
        assertThat(root.get("documentTypes")).hasSize(8);
        assertThat(root.get("scholarships")).hasSize(14);

        int active = 0;
        for (var s : root.get("scholarships")) {
            if (s.get("isActive").asBoolean()) {
                active++;
            }
        }
        assertThat(active).isEqualTo(13);
    }

    @Test
    void dostScholarshipCarriesProgramsTagsAndDocuments() throws Exception {
        MvcResult result = mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(result.getResponse().getContentAsString());
        var dost = root.get("scholarships").get(0); // ordered by scholarship_id

        assertThat(dost.get("scholarshipId").asLong()).isEqualTo(1);
        assertThat(dost.get("title").asText()).contains("DOST-SEI");
        assertThat(dost.get("allowedProgramIds")).hasSize(8);
        assertThat(dost.get("allowedYearLevels")).hasSize(1);
        assertThat(dost.get("requiredTags")).hasSize(1);
        assertThat(dost.get("requiredTags").get(0).get("tagId").asLong()).isEqualTo(5);
        assertThat(dost.get("requiredTags").get(0).get("mandatory").asBoolean()).isTrue();
        assertThat(dost.get("requiredDocumentIds")).hasSize(6);
        assertThat(dost.get("criteria").get("minGwaPercentage").asDouble()).isEqualTo(83.0);
        assertThat(dost.get("criteria").get("allowFailingGrade").asBoolean()).isFalse();
        assertThat(dost.get("deadlineDate").isNull()).isFalse();
    }

    @Test
    void cebuLguCarriesResidencyCriteria() throws Exception {
        MvcResult result = mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(result.getResponse().getContentAsString());

        var cebu = root.get("scholarships").get(4); // scholarship_id = 5
        assertThat(cebu.get("scholarshipId").asLong()).isEqualTo(5);
        assertThat(cebu.get("criteria").get("requiredProvince").asText()).isEqualTo("CEBU");
        assertThat(cebu.get("criteria").get("requiredCity").asText()).isEqualTo("CEBU CITY");
        assertThat(cebu.get("criteria").get("allowedSchoolType").asText()).isEqualTo("PUBLIC");
        assertThat(cebu.get("criteria").get("allowFailingGrade").asBoolean()).isTrue();
    }
}
