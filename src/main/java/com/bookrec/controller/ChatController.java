package com.bookrec.controller;

import com.bookrec.model.Book;
import com.bookrec.model.RagDocument;
import com.bookrec.service.LlmService;
import com.bookrec.service.RdfIngestService;
import com.bookrec.service.RdfService;
import com.bookrec.service.VectorRetrievalService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final List<String> KNOWN_THEMES = List.of(
            "science fiction", "fantasy", "mystery", "murder", "adventure"
    );
    private static final String NO_MATCH_ANSWER = "I couldn't find a matching book in the library.";

    private final RdfIngestService ingestService;
    private final VectorRetrievalService retrievalService;
    private final LlmService llmService;
    private final RdfService rdfService;

    public ChatController(RdfIngestService ingestService,
                          VectorRetrievalService retrievalService,
                          LlmService llmService,
                          RdfService rdfService) {
        this.ingestService = ingestService;
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.rdfService = rdfService;
    }

    @GetMapping("/admin/ingest")
    @PostMapping("/admin/ingest")
    public ResponseEntity<Map<String, Object>> ingest() {
        int count = ingestService.ingestAll();
        return ResponseEntity.ok(Map.of("ingested", count));
    }

    @GetMapping("/starters")
    public ResponseEntity<List<String>> starters(@RequestParam(value = "context", required = false) String context) {
        if (context != null && context.startsWith("book:")) {
            String id = context.substring("book:".length());
            Book book = rdfService.getBookById(id);
            if (book != null) {
                return ResponseEntity.ok(List.of(
                        "Tell me about " + book.getTitle(),
                        "What other books are similar to " + book.getTitle() + "?",
                        "Who is the author of " + book.getTitle() + " and what else did they write?"
                ));
            }
        }
        return ResponseEntity.ok(List.of(
                "What is a book that I am most likely to enjoy from this list?",
                "Which books in the library are about adventure?",
                "Suggest a book for advanced readers?"
        ));
    }

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> message(@RequestBody Map<String, Object> body) {
        String message = (String) body.getOrDefault("message", "");
        List<Map<String, String>> history = extractHistory(body.get("history"));

        AuthorThemeQuery exact = parseAuthorThemeQuery(message);
        if (exact != null) {
            List<String> matches = matchBooks(exact);
            if (matches.isEmpty()) {
                return ResponseEntity.ok(Map.of("answer", NO_MATCH_ANSWER));
            }
            return ResponseEntity.ok(Map.of(
                    "answer", String.join(", ", matches),
                    "sources", matches
            ));
        }

        List<RagDocument> books = retrievalService.retrieveBooks(message, 4);
        List<RagDocument> users = retrievalService.getAllUsers();
        String answer = llmService.generateAnswer(message, books, users, history);

        List<Map<String, String>> sources = new ArrayList<>(books.size());
        for (RagDocument d : books) {
            sources.add(Map.of("id", d.sourceId(), "title", d.title()));
        }
        return ResponseEntity.ok(Map.of("answer", answer, "sources", sources));
    }

    private List<Map<String, String>> extractHistory(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, String>> out = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> turn
                    && turn.get("role") instanceof String role
                    && turn.get("content") instanceof String content) {
                out.add(Map.of("role", role, "content", content));
            }
        }
        return out;
    }

    private List<String> matchBooks(AuthorThemeQuery query) {
        String authorLower = query.author().toLowerCase(Locale.ROOT);
        String themeLower = query.theme().toLowerCase(Locale.ROOT);

        List<String> matches = new ArrayList<>();
        for (Book b : rdfService.getAllBooks()) {
            if (b.getAuthor() == null || b.getGenres() == null) continue;
            if (!b.getAuthor().toLowerCase(Locale.ROOT).contains(authorLower)) continue;
            for (String g : b.getGenres()) {
                if (g.toLowerCase(Locale.ROOT).contains(themeLower)) {
                    matches.add(b.getTitle());
                    break;
                }
            }
        }
        return matches;
    }

    private AuthorThemeQuery parseAuthorThemeQuery(String text) {
        if (!StringUtils.hasText(text)) return null;
        String lower = text.toLowerCase(Locale.ROOT);

        String author = extractAuthor(lower);
        if (author == null) return null;

        String theme = null;
        for (String candidate : KNOWN_THEMES) {
            if (lower.contains(candidate)) {
                theme = candidate;
                break;
            }
        }
        if (theme == null) return null;

        return new AuthorThemeQuery(author, theme);
    }

    private String extractAuthor(String lower) {
        int idx = lower.indexOf("author ");
        if (idx >= 0) {
            return extractWordAfter(lower, idx + "author ".length());
        }
        int by = lower.indexOf(" by ");
        if (by >= 0) {
            return extractWordAfter(lower, by + " by ".length());
        }
        return null;
    }

    private String extractWordAfter(String s, int pos) {
        if (pos >= s.length()) return null;
        String tail = s.substring(pos).trim();
        int end = tail.length();
        for (String stop : List.of(" and ", ",", "?", ".")) {
            int candidate = tail.indexOf(stop);
            if (candidate >= 0 && candidate < end) end = candidate;
        }
        return tail.substring(0, end).trim();
    }

    private record AuthorThemeQuery(String author, String theme) {
    }
}
