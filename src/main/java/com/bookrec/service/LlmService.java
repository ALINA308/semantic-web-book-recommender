package com.bookrec.service;

import com.bookrec.model.RagDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class LlmService {

    private static final String FALLBACK_ANSWER = "I don't know - I couldn't find it in the library.";

    private static final String SYSTEM_PROMPT = """
            You are a helpful book recommendation assistant for a small library.
            Ground every factual claim (title, author, genre, reading level, user preferences)
            in the SOURCES provided with each user message; do not invent books, authors, or
            users that are not listed. Reasoning, comparison, and recommendations across the
            provided sources are encouraged.

            The SOURCES include both BOOKS (Title / Author / Genres / Reading level) and
            KNOWN USERS (name / reading level / preferred genre).

            If the user's question depends on who they are (for example "what would I enjoy",
            "is this book for me", "recommend me a book") and they have not already told you
            which library user they are earlier in this conversation, do not guess: ask them
            which of the known users they are. Once they identify themselves, use that user's
            preferences and reading level for the rest of the conversation.""";

    private final WebClient webClient;
    private final String apiKey;
    private final String embeddingModel;
    private final String chatModel;

    public LlmService(WebClient.Builder builder,
                      @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
                      @Value("${openai.api-key:}") String apiKey,
                      @Value("${openai.embedding-model:text-embedding-3-small}") String embeddingModel,
                      @Value("${openai.model:gpt-4o-mini}") String chatModel) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    public double[] getEmbedding(String text) {
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", text
        );

        Map<?, ?> response = webClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            return new double[0];
        }
        List<?> data = (List<?>) response.get("data");
        if (data == null || data.isEmpty()) {
            return new double[0];
        }
        List<?> embeddingList = (List<?>) ((Map<?, ?>) data.get(0)).get("embedding");
        double[] vec = new double[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            vec[i] = ((Number) embeddingList.get(i)).doubleValue();
        }
        return vec;
    }

    public String generateAnswer(String question,
                                 List<RagDocument> books,
                                 List<RagDocument> users,
                                 List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (history != null) {
            for (Map<String, String> turn : history) {
                String role = turn.get("role");
                String content = turn.get("content");
                if (role == null || content == null) continue;
                if (!role.equals("user") && !role.equals("assistant")) continue;
                messages.add(Map.of("role", role, "content", content));
            }
        }
        messages.add(Map.of("role", "user", "content", buildRagPrompt(question, books, users)));

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", messages,
                "max_tokens", 400
        );

        Map<?, ?> response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            return FALLBACK_ANSWER;
        }
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return FALLBACK_ANSWER;
        }
        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        Object content = message == null ? null : message.get("content");
        return content == null ? FALLBACK_ANSWER : content.toString().trim();
    }

    private String buildRagPrompt(String question, List<RagDocument> books, List<RagDocument> users) {
        StringBuilder sb = new StringBuilder("SOURCES:\n");
        sb.append("BOOKS:\n");
        if (books.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (int i = 0; i < books.size(); i++) {
                RagDocument d = books.get(i);
                sb.append('[').append(i + 1).append("] ").append(d.content()).append('\n');
            }
        }
        sb.append("KNOWN USERS:\n");
        if (users.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (RagDocument u : users) {
                sb.append("- ").append(u.content()).append('\n');
            }
        }
        sb.append("\nQuestion: ").append(question);
        return sb.toString();
    }
}
