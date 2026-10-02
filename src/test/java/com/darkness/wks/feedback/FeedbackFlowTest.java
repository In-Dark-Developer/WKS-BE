package com.darkness.wks.feedback;

import com.darkness.wks.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackFlowTest extends PostgresIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void anonymousSubmissionIsCommittedToPostgres() throws Exception {
        String content = "다음 축제에도 만나요! " + UUID.randomUUID();
        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(post("/api/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  " + content + "  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        var rows = jdbc.query("SELECT id, content, created_at FROM feedback WHERE content = ?",
                (rs, row) -> {
                    assertThat(rs.getObject("id", UUID.class).version()).isEqualTo(4);
                    assertThat(rs.getString("content")).isEqualTo(content);
                    assertThat(rs.getObject("created_at", OffsetDateTime.class)).isNotNull();
                    return rs.getObject("id", UUID.class);
                }, content);
        assertThat(rows).hasSize(1);
    }
}
