package de.afterimage.web.admin;

import de.afterimage.catalog.application.ArchiveHealthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/health")
public class AdminHealthController {

    private final ArchiveHealthService health;

    public AdminHealthController(ArchiveHealthService health) {
        this.health = health;
    }

    @GetMapping
    String health(Model model) {
        model.addAttribute("report", health.report());
        return "admin/health";
    }
}
