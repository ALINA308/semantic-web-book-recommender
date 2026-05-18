package com.bookrec.service;

import com.bookrec.model.RagDocument;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class VectorRetrievalService {

    private static final RowMapper<RagDocument> ROW_MAPPER = (rs, rowNum) -> new RagDocument(
            rs.getString("source_id"),
            rs.getString("title"),
            rs.getString("content")
    );

    private final JdbcTemplate jdbc;
    private final LlmService llmService;

    public VectorRetrievalService(JdbcTemplate jdbc, LlmService llmService) {
        this.jdbc = jdbc;
        this.llmService = llmService;
    }

    public List<RagDocument> retrieveBooks(String query, int k) {
        double[] embedding = llmService.getEmbedding(query);
        if (embedding.length == 0) {
            return List.of();
        }

        String sql = "SELECT source_id, title, content "
                + "FROM rag.documents "
                + "WHERE source_type = 'book' "
                + "ORDER BY embedding <=> " + toVectorLiteral(embedding) + "::vector "
                + "LIMIT ?";

        return jdbc.query(sql, ROW_MAPPER, k);
    }

    public List<RagDocument> getAllUsers() {
        String sql = "SELECT source_id, title, content "
                + "FROM rag.documents "
                + "WHERE source_type = 'user' "
                + "ORDER BY source_id";
        return jdbc.query(sql, ROW_MAPPER);
    }

    static String toVectorLiteral(double[] embedding) {
        StringBuilder sb = new StringBuilder("ARRAY[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.10f", embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
