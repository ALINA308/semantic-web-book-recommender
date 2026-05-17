package com.bookrec.model;

public class User {

    private String id;
    private String name;
    private String userLevel;
    private String prefersGenre;

    public User() {
    }

    public User(String id, String name, String userLevel, String prefersGenre) {
        this.id = id;
        this.name = name;
        this.userLevel = userLevel;
        this.prefersGenre = prefersGenre;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserLevel() {
        return userLevel;
    }

    public void setUserLevel(String userLevel) {
        this.userLevel = userLevel;
    }

    public String getPrefersGenre() {
        return prefersGenre;
    }

    public void setPrefersGenre(String prefersGenre) {
        this.prefersGenre = prefersGenre;
    }
}
