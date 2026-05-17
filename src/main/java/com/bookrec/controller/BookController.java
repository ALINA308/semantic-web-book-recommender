package com.bookrec.controller;

import com.bookrec.model.Book;
import com.bookrec.service.RdfService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping
public class BookController {

    private final RdfService rdfService;

    public BookController(RdfService rdfService) {
        this.rdfService = rdfService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/books";
    }

    @GetMapping("/books")
    public String getBooks(Model model) {
        model.addAttribute("books", rdfService.getAllBooks());
        return "books";
    }

    @GetMapping("/books/{id}")
    public String getBookDetail(@PathVariable String id, Model model) {
        Book book = rdfService.getBookById(id);
        if (book == null) {
            return "redirect:/books";
        }
        model.addAttribute("book", book);
        model.addAttribute("users", rdfService.getAllUsers());
        return "book-detail";
    }

    @GetMapping("/books/add")
    public String showAddForm(Model model) {
        Book emptyBook = new Book();
        emptyBook.setId("");
        model.addAttribute("book", emptyBook);
        model.addAttribute("genresInput", "");
        model.addAttribute("formAction", "/books/add");
        model.addAttribute("isEdit", false);
        return "book-form";
    }

    @PostMapping("/books/add")
    public String addBook(@ModelAttribute Book book, @RequestParam("genresInput") String genresInput) {
        book.setGenres(List.of(genresInput));
        rdfService.addBook(book);
        return "redirect:/books";
    }

    @GetMapping("/books/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model) {
        Book book = rdfService.getBookById(id);
        if (book == null) {
            return "redirect:/books";
        }

        model.addAttribute("book", book);
        model.addAttribute("genresInput", String.join(", ", book.getGenres()));
        model.addAttribute("formAction", "/books/edit/" + id);
        model.addAttribute("isEdit", true);
        return "book-form";
    }

    @PostMapping("/books/edit/{id}")
    public String updateBook(@PathVariable String id,
                             @ModelAttribute Book book,
                             @RequestParam("genresInput") String genresInput) {
        // Keep URL path id as source of truth for update target.
        book.setId(id);
        book.setGenres(List.of(genresInput));
        rdfService.updateBook(id, book);
        return "redirect:/books/" + id;
    }
}
