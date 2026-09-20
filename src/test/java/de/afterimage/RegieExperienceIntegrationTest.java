package de.afterimage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegieExperienceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void againRendersInteractiveSwitcherWithIndependentLooks() throws Exception {
        mvc.perform(get("/regie/again"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("regie-program-stage")))
                .andExpect(content().string(containsString("regie-program-video")))
                .andExpect(content().string(containsString("data-camera=\"stellwerk\"")))
                .andExpect(content().string(containsString("data-look=\"effect\"")))
                .andExpect(content().string(not(containsString("Challenge"))))
                .andExpect(content().string(containsString("Meine Regie ansehen")));
    }

    @Test
    void lessonsUnlearnedRendersAllCameraGroups() throws Exception {
        mvc.perform(get("/regie/lessons-unlearned"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gesamtschnitt")))
                .andExpect(content().string(containsString("Einzelkameras")))
                .andExpect(content().string(containsString("data-camera=\"drums-back\"")))
                .andExpect(content().string(containsString("data-camera=\"hand-right\"")))
                .andExpect(content().string(containsString("data-camera=\"stage-left\"")))
                .andExpect(content().string(containsString("lessons-unlearned_effekt.mp4")));
    }
}
