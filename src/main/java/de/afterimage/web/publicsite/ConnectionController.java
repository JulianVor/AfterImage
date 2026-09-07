package de.afterimage.web.publicsite;

import de.afterimage.catalog.application.ConnectionPathService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ConnectionController {

    private final ConnectionPathService paths;

    public ConnectionController(ConnectionPathService paths) {
        this.paths = paths;
    }

    @GetMapping("/connect")
    String connect(@RequestParam(required = false) String from,
                   @RequestParam(required = false) String to,
                   Model model) {
        model.addAttribute("entries", paths.choices());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("path", paths.connect(from, to));
        return "public/connect";
    }
}
