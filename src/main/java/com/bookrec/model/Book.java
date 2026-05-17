package com.bookrec.model;

import java.util.ArrayList;
import java.util.List;

public class Book {

    private String id;
    private String title;
    private String author;
    private List<String> genres = new ArrayList<>();
    private String readingLevel;

    public Book() {
    }

    public Book(String id, String title, String author, List<String> genres, String readingLevel) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.genres = genres;
        this.readingLevel = readingLevel;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public String getReadingLevel() {
        return readingLevel;
    }

    public void setReadingLevel(String readingLevel) {
        this.readingLevel = readingLevel;
    }
}
