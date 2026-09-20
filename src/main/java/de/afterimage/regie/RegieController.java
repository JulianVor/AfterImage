package de.afterimage.regie;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RegieController {

    private final RegieCatalog catalog;

    public RegieController(RegieCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/regie")
    String index(Model model) {
        model.addAttribute("experiences", catalog.all());
        return "public/regie-index";
    }

    @GetMapping("/regie/{slug}")
    String show(@PathVariable String slug, Model model) {
        RegieExperience experience = catalog.find(slug);
        if (experience == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("experience", experience);
        return "public/regie";
    }
}
