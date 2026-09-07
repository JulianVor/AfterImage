package de.afterimage.web.admin;

import de.afterimage.catalog.application.AdminTrailService;
import de.afterimage.catalog.domain.Story;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.domain.StoryBlockType;
import de.afterimage.catalog.domain.StoryType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Controller
@RequestMapping("/admin/trails")
@Validated
public class AdminTrailController {

    private final AdminTrailService trails;
    private final ObjectMapper objectMapper;

    public AdminTrailController(AdminTrailService trails, ObjectMapper objectMapper) {
        this.trails = trails;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    String index(Model model) {
        model.addAttribute("trails", trails.stories());
        return "admin/trails";
    }

    @PostMapping("/import")
    String importStory(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        if (file.isEmpty()) {
            redirect.addFlashAttribute("error", "Wähle eine exportierte Geschichte-JSON-Datei aus.");
            return "redirect:/admin/trails";
        }
        try {
            AdminTrailService.StoryExport data = objectMapper.readValue(
                    file.getInputStream(), AdminTrailService.StoryExport.class);
            Story imported = trails.importStory(data);
            redirect.addFlashAttribute("message", "Geschichte „" + imported.getTitle() + "“ importiert.");
            return "redirect:/admin/trails/" + imported.getId();
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/trails";
        } catch (Exception exception) {
            redirect.addFlashAttribute("error", "Die Datei ist keine gültige Geschichte-Export-Datei.");
            return "redirect:/admin/trails";
        }
    }

    @GetMapping("/{id}/export")
    ResponseEntity<byte[]> export(@PathVariable UUID id) {
        Story story = trails.story(id);
        AdminTrailService.StoryExport data = trails.export(id);
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + story.getSlug() + ".json\"")
                .body(json);
    }

    @PostMapping
    String create(@RequestParam @NotBlank String title,
                  @RequestParam(defaultValue = "PATH") StoryType storyType) {
        return "redirect:/admin/trails/" + trails.create(title, storyType).getId();
    }

    @GetMapping("/{id}")
    String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("trail", trails.story(id));
        model.addAttribute("entities", trails.entityChoices());
        model.addAttribute("visibilities", Visibility.values());
        model.addAttribute("storyTypes", StoryType.values());
        model.addAttribute("blockTypes", StoryBlockType.values());
        return "admin/trail-edit";
    }

    @PostMapping("/{id}")
    String update(@PathVariable UUID id, @RequestParam String title,
                  @RequestParam(required = false) String teaser,
                  @RequestParam(required = false) UUID mainEntityId,
                  @RequestParam(required = false) StoryType storyType,
                  @RequestParam Visibility visibility,
                  RedirectAttributes redirect) {
        trails.update(id, title, teaser, mainEntityId, storyType, visibility);
        redirect.addFlashAttribute("message", "Geschichte gespeichert.");
        return "redirect:/admin/trails/" + id;
    }

    @PostMapping("/{id}/steps")
    String addStep(@PathVariable UUID id,
                   @RequestParam(defaultValue = "ENTRY") StoryBlockType blockType,
                   @RequestParam(required = false) UUID entityId,
                   @RequestParam(required = false) String heading,
                   @RequestParam(required = false) String text,
                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                   @RequestParam(required = false) String cameraFocus,
                   @RequestParam(required = false) String visualizationHint,
                   @RequestParam(required = false) String piwigoAlbumPath,
                   @RequestParam(required = false) Integer photoLimit) {
        trails.addBlock(id, blockType, entityId, heading, text, eventDate, cameraFocus, visualizationHint,
                piwigoAlbumPath, photoLimit);
        return "redirect:/admin/trails/" + id;
    }

    @PostMapping("/{id}/steps/{stepId}")
    String updateStep(@PathVariable UUID id, @PathVariable UUID stepId,
                      @RequestParam(required = false) String text,
                      @RequestParam(defaultValue = "ENTRY") StoryBlockType blockType,
                      @RequestParam(required = false) UUID entityId,
                      @RequestParam(required = false) String heading,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
                      @RequestParam(required = false) String cameraFocus,
                      @RequestParam(required = false) String visualizationHint,
                      @RequestParam(required = false) String piwigoAlbumPath,
                      @RequestParam(required = false) Integer photoLimit,
                      RedirectAttributes redirect) {
        trails.updateBlock(id, stepId, blockType, entityId, heading, text, eventDate,
                cameraFocus, visualizationHint, piwigoAlbumPath, photoLimit);
        redirect.addFlashAttribute("message", "Baustein gespeichert.");
        return "redirect:/admin/trails/" + id;
    }

    @PostMapping("/{id}/steps/{stepId}/move")
    String moveStep(@PathVariable UUID id, @PathVariable UUID stepId,
                    @RequestParam String direction) {
        trails.moveStep(id, stepId, direction);
        return "redirect:/admin/trails/" + id;
    }

    @PostMapping("/{id}/steps/{stepId}/delete")
    String deleteStep(@PathVariable UUID id, @PathVariable UUID stepId) {
        trails.deleteStep(id, stepId);
        return "redirect:/admin/trails/" + id;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable UUID id) {
        trails.delete(id);
        return "redirect:/admin/trails";
    }
}
