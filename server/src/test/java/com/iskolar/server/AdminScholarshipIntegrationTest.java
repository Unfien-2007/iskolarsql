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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin write-path tests against their own embedded PostgreSQL instance so
 * catalog mutations never interfere with the read-path suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminScholarshipIntegrationTest {

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

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private int versionNow() throws Exception {
        MvcResult result = mvc.perform(get("/api/catalog/version"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("version").asInt();
    }

    private String scholarshipJson(String title) {
        return "{\"title\":\"" + title + "\",\"providerName\":\"Test Provider\","
                + "\"providerType\":\"PRIVATE_CORPORATE\",\"deadlineDate\":\"2099-12-31\","
                + "\"monthlyStipend\":5000.00,\"returnServiceYears\":2,\"isActive\":true,"
                + "\"criteria\":{\"minGwaPercentage\":85.0,\"allowFailingGrade\":false},"
                + "\"programIds\":[1,2],\"yearLevels\":[1,2],"
                + "\"requiredTags\":[{\"tagId\":1,\"mandatory\":false}],\"documentIds\":[1]}";
    }

    private boolean snapshotContains(long scholarshipId) throws Exception {
        MvcResult result = mvc.perform(get("/api/catalog")).andReturn();
        var root = objectMapper.readTree(result.getResponse().getContentAsString());
        for (var s : root.get("scholarships")) {
            if (s.get("scholarshipId").asLong() == scholarshipId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void adminCreateBumpsVersionAndAppearsInCatalog() throws Exception {
        String token = login("admin@iskolar.app", "password");
        int before = versionNow();

        MvcResult created = mvc.perform(post("/api/admin/scholarships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scholarshipJson("Sync Test Scholarship")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scholarshipId").isNumber())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("scholarshipId").asLong();
        assertThat(versionNow()).isEqualTo(before + 1);
        assertThat(snapshotContains(id)).isTrue();
    }

    @Test
    void adminUpdateChangesFieldsAndBumpsVersion() throws Exception {
        String token = login("admin@iskolar.app", "password");
        int before = versionNow();

        mvc.perform(put("/api/admin/scholarships/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scholarshipJson("DOST-SEI UPDATED")))
                .andExpect(status().isNoContent());

        assertThat(versionNow()).isEqualTo(before + 1);
        MvcResult result = mvc.perform(get("/api/catalog")).andReturn();
        var root = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean found = false;
        for (var s : root.get("scholarships")) {
            if (s.get("scholarshipId").asLong() == 1) {
                assertThat(s.get("title").asText()).isEqualTo("DOST-SEI UPDATED");
                assertThat(s.get("criteria").get("minGwaPercentage").asDouble()).isEqualTo(85.0);
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void adminDeleteRemovesScholarshipAndBumpsVersion() throws Exception {
        String token = login("admin@iskolar.app", "password");
        int before = versionNow();

        mvc.perform(delete("/api/admin/scholarships/3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(versionNow()).isEqualTo(before + 1);
        assertThat(snapshotContains(3)).isFalse();
    }

    @Test
    void studentTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@email.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        String token = login("student@email.com", "password123");

        mvc.perform(post("/api/admin/scholarships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scholarshipJson("Should Not Appear")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/admin/scholarships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scholarshipJson("Should Not Appear")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openDateAfterDeadlineIsBadRequest() throws Exception {
        String token = login("admin@iskolar.app", "password");
        String body = "{\"title\":\"Bad Dates\",\"providerName\":\"Test Provider\","
                + "\"providerType\":\"GOVERNMENT\",\"openDate\":\"2027-02-01\","
                + "\"deadlineDate\":\"2027-01-01\"}";

        mvc.perform(post("/api/admin/scholarships")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
