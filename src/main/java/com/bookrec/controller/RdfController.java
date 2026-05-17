package com.bookrec.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/rdf")
public class RdfController {

    @GetMapping("/upload")
    public String showUploadPage() {
        return "rdf-upload";
    }

    @PostMapping("/upload")
    public String uploadRdf(@RequestParam("file") MultipartFile file, ModelMap model) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Please choose an RDF/XML file to upload.");
            return "rdf-upload";
        }

        List<TripleView> triples = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream()) {
            Model rdfModel = ModelFactory.createDefaultModel();
            rdfModel.read(inputStream, null);

            StmtIterator iterator = rdfModel.listStatements();
            while (iterator.hasNext()) {
                Statement statement = iterator.nextStatement();
                String subject = statement.getSubject().toString();
                String predicate = statement.getPredicate().toString();
                String object = statement.getObject().toString();
                triples.add(new TripleView(subject, predicate, object));
            }
        } catch (IOException ioException) {
            model.addAttribute("error", "Unable to read uploaded file: " + ioException.getMessage());
            return "rdf-upload";
        } catch (Exception exception) {
            model.addAttribute("error", "Uploaded file is not valid RDF/XML: " + exception.getMessage());
            return "rdf-upload";
        }

        model.addAttribute("fileName", file.getOriginalFilename());
        model.addAttribute("triples", triples);
        return "rdf-graph";
    }

    public static class TripleView {

        private final String subject;
        private final String predicate;
        private final String object;

        public TripleView(String subject, String predicate, String object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        public String getSubject() {
            return subject;
        }

        public String getPredicate() {
            return predicate;
        }

        public String getObject() {
            return object;
        }
    }
}
