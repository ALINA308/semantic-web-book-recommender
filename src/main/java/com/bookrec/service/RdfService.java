package com.bookrec.service;

import com.bookrec.model.Book;
import com.bookrec.model.User;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RdfService {

    private static final String BASE_URI = "http://www.semanticweb.org/bookrec#";

    @Value("${rdf.file.path:src/main/resources/rdf/books.rdf}")
    private String rdfFilePath;

    public List<Book> getAllBooks() {
        Model model = loadModel();
        String sparql = """
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX bookrec: <http://www.semanticweb.org/bookrec#>
                SELECT ?book ?title ?author ?readingLevel
                WHERE {
                  ?book rdf:type bookrec:Book .
                  OPTIONAL { ?book bookrec:title ?title . }
                  OPTIONAL { ?book bookrec:author ?author . }
                  OPTIONAL { ?book bookrec:hasReadingLevel ?readingLevel . }
                }
                ORDER BY ?title
                """;

        List<Book> books = new ArrayList<>();
        Query query = QueryFactory.create(sparql);
        try (QueryExecution execution = QueryExecutionFactory.create(query, model)) {
            ResultSet resultSet = execution.execSelect();
            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();
                String bookUri = solution.getResource("book").getURI();
                String bookId = localName(bookUri);

                Book book = new Book();
                book.setId(bookId);
                book.setTitle(literalValue(solution, "title"));
                book.setAuthor(literalValue(solution, "author"));
                book.setReadingLevel(literalValue(solution, "readingLevel"));
                book.setGenres(getGenresByBookId(model, bookId));
                books.add(book);
            }
        }
        return books;
    }

    public Book getBookById(String id) {
        Model model = loadModel();
        String sparql = """
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX bookrec: <http://www.semanticweb.org/bookrec#>
                SELECT ?title ?author ?readingLevel ?genre
                WHERE {
                  BIND(bookrec:%s AS ?book)
                  ?book rdf:type bookrec:Book .
                  OPTIONAL { ?book bookrec:title ?title . }
                  OPTIONAL { ?book bookrec:author ?author . }
                  OPTIONAL { ?book bookrec:hasReadingLevel ?readingLevel . }
                  OPTIONAL { ?book bookrec:hasGenre ?genre . }
                }
                """.formatted(id);

        Book book = null;
        Query query = QueryFactory.create(sparql);
        try (QueryExecution execution = QueryExecutionFactory.create(query, model)) {
            ResultSet resultSet = execution.execSelect();
            Set<String> genres = new LinkedHashSet<>();
            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();
                if (book == null) {
                    book = new Book();
                    book.setId(id);
                    book.setTitle(literalValue(solution, "title"));
                    book.setAuthor(literalValue(solution, "author"));
                    book.setReadingLevel(literalValue(solution, "readingLevel"));
                }
                String genre = literalValue(solution, "genre");
                if (genre != null && !genre.isBlank()) {
                    genres.add(genre);
                }
            }
            if (book != null) {
                book.setGenres(new ArrayList<>(genres));
            }
        }
        return book;
    }

    public void addBook(Book book) {
        Model model = loadModel();
        String id = normalizeOrGenerateId(book.getId(), book.getTitle());
        Resource bookResource = model.createResource(BASE_URI + id);

        Property rdfType = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Property titleProperty = model.createProperty(BASE_URI + "title");
        Property authorProperty = model.createProperty(BASE_URI + "author");
        Property readingLevelProperty = model.createProperty(BASE_URI + "hasReadingLevel");
        Property genreProperty = model.createProperty(BASE_URI + "hasGenre");

        bookResource.addProperty(rdfType, model.createResource(BASE_URI + "Book"));
        addLiteralIfPresent(bookResource, titleProperty, book.getTitle());
        addLiteralIfPresent(bookResource, authorProperty, book.getAuthor());
        addLiteralIfPresent(bookResource, readingLevelProperty, book.getReadingLevel());

        for (String genre : normalizeGenres(book.getGenres())) {
            bookResource.addProperty(genreProperty, genre);
        }

        saveModel(model);
    }

    public void updateBook(String id, Book book) {
        Model model = loadModel();
        Resource bookResource = model.getResource(BASE_URI + id);

        if (!model.containsResource(bookResource)) {
            return;
        }

        removeProperty(bookResource, model.createProperty(BASE_URI + "title"));
        removeProperty(bookResource, model.createProperty(BASE_URI + "author"));
        removeProperty(bookResource, model.createProperty(BASE_URI + "hasReadingLevel"));
        removeProperty(bookResource, model.createProperty(BASE_URI + "hasGenre"));

        addLiteralIfPresent(bookResource, model.createProperty(BASE_URI + "title"), book.getTitle());
        addLiteralIfPresent(bookResource, model.createProperty(BASE_URI + "author"), book.getAuthor());
        addLiteralIfPresent(bookResource, model.createProperty(BASE_URI + "hasReadingLevel"), book.getReadingLevel());

        for (String genre : normalizeGenres(book.getGenres())) {
            bookResource.addProperty(model.createProperty(BASE_URI + "hasGenre"), genre);
        }

        saveModel(model);
    }

    public List<User> getAllUsers() {
        Model model = loadModel();
        String sparql = """
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX bookrec: <http://www.semanticweb.org/bookrec#>
                SELECT ?user ?userLevel ?prefersGenre
                WHERE {
                  ?user rdf:type bookrec:User .
                  OPTIONAL { ?user bookrec:hasUserLevel ?userLevel . }
                  OPTIONAL { ?user bookrec:prefersGenre ?prefersGenre . }
                }
                ORDER BY ?user
                """;

        List<User> users = new ArrayList<>();
        Query query = QueryFactory.create(sparql);
        try (QueryExecution execution = QueryExecutionFactory.create(query, model)) {
            ResultSet resultSet = execution.execSelect();
            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();
                String userUri = solution.getResource("user").getURI();
                String userId = localName(userUri);

                User user = new User();
                user.setId(userId);
                user.setName(userId);
                user.setUserLevel(literalValue(solution, "userLevel"));
                user.setPrefersGenre(literalValue(solution, "prefersGenre"));
                users.add(user);
            }
        }
        return users;
    }

    private Model loadModel() {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream inputStream = new FileInputStream(rdfFilePath)) {
            model.read(inputStream, BASE_URI);
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to load RDF model from " + rdfFilePath, ioException);
        }
        return model;
    }

    private void saveModel(Model model) {
        try (FileOutputStream outputStream = new FileOutputStream(rdfFilePath)) {
            model.write(outputStream, "RDF/XML");
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to save RDF model to " + rdfFilePath, ioException);
        }
    }

    private List<String> getGenresByBookId(Model model, String id) {
        Resource resource = model.getResource(BASE_URI + id);
        Property genreProperty = model.createProperty(BASE_URI + "hasGenre");

        List<String> genres = new ArrayList<>();
        StmtIterator iterator = resource.listProperties(genreProperty);
        while (iterator.hasNext()) {
            Statement statement = iterator.nextStatement();
            if (statement.getObject().isLiteral()) {
                genres.add(statement.getString());
            }
        }
        return genres;
    }

    private List<String> normalizeGenres(List<String> rawGenres) {
        if (rawGenres == null || rawGenres.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String value : rawGenres) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String[] splitValues = value.split(",");
            for (String part : splitValues) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    normalized.add(trimmed);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeOrGenerateId(String id, String title) {
        if (id != null && !id.isBlank()) {
            return id.replaceAll("[^a-zA-Z0-9]", "");
        }
        if (title == null || title.isBlank()) {
            return "Book" + System.currentTimeMillis();
        }
        String normalized = title.replaceAll("[^a-zA-Z0-9]", "");
        return normalized.isBlank() ? "Book" + System.currentTimeMillis() : normalized;
    }

    private String localName(String uri) {
        int idx = uri.lastIndexOf('#');
        if (idx >= 0 && idx < uri.length() - 1) {
            return uri.substring(idx + 1);
        }
        return uri;
    }

    private String literalValue(QuerySolution solution, String varName) {
        if (solution.contains(varName) && solution.get(varName).isLiteral()) {
            return solution.getLiteral(varName).getString();
        }
        return null;
    }

    private void addLiteralIfPresent(Resource resource, Property property, String value) {
        if (value != null && !value.isBlank()) {
            resource.addLiteral(property, value.trim());
        }
    }

    private void removeProperty(Resource resource, Property property) {
        resource.removeAll(property);
    }
}
