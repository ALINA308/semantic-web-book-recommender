package com.bookrec.service;

import com.bookrec.model.Book;
import com.bookrec.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RdfIngestService {

    private final RdfService rdfService;
    private final LlmService llmService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public RdfIngestService(RdfService rdfService, LlmService llmService, JdbcTemplate jdbc) {
        this.rdfService = rdfService;
        this.llmService = llmService;
        this.jdbc = jdbc;
    }

    public int ingestAll() {
        int count = 0;
        for (Book book : rdfService.getAllBooks()) {
            try {
                ingestBook(book);
                count++;
            } catch (JsonProcessingException ignored) {
                // Skip the book if its metadata can't be serialised; continue with the rest.
            }
        }
        for (User user : rdfService.getAllUsers()) {
            try {
                ingestUser(user);
                count++;
            } catch (JsonProcessingException ignored) {
                // Skip the user if its metadata can't be serialised; continue with the rest.
            }
        }
        return count;
    }

    public void ingestBook(Book book) throws JsonProcessingException {
        String content = buildBookContent(book);
        String metadataJson = mapper.writeValueAsString(Map.of(
                "author", nullSafe(book.getAuthor()),
                "genres", book.getGenres() == null ? List.of() : book.getGenres(),
                "readingLevel", nullSafe(book.getReadingLevel())
        ));
        upsert("book", book.getId(), book.getTitle(), content, metadataJson);
    }

    public void ingestUser(User user) throws JsonProcessingException {
        String content = buildUserContent(user);
        String metadataJson = mapper.writeValueAsString(Map.of(
                "userLevel", nullSafe(user.getUserLevel()),
                "prefersGenre", nullSafe(user.getPrefersGenre())
        ));
        upsert("user", user.getId(), user.getName(), content, metadataJson);
    }

    private void upsert(String sourceType, String sourceId, String title, String content, String metadataJson) {
        double[] embedding = llmService.getEmbedding(content);
        String sql = "INSERT INTO rag.documents (source_type, source_id, title, content, metadata, embedding) "
                + "VALUES (?, ?, ?, ?, ?::jsonb, " + VectorRetrievalService.toVectorLiteral(embedding) + "::vector) "
                + "ON CONFLICT (source_type, source_id) DO UPDATE SET "
                + "title = EXCLUDED.title, content = EXCLUDED.content, "
                + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";
        jdbc.update(sql, sourceType, sourceId, title, content, metadataJson);
    }

    private String buildBookContent(Book book) {
        String genres = book.getGenres() == null ? "" : String.join(", ", book.getGenres());
        return "Title: " + nullSafe(book.getTitle())
                + ". Author: " + nullSafe(book.getAuthor())
                + ". Genres: " + genres
                + ". Reading level: " + nullSafe(book.getReadingLevel()) + ".";
    }

    private String buildUserContent(User user) {
        return "User: " + nullSafe(user.getName())
                + ". Reading level: " + nullSafe(user.getUserLevel())
                + ". Prefers genre: " + nullSafe(user.getPrefersGenre()) + ".";
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
