package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.StoryStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoryStepRepository extends JpaRepository<StoryStep, UUID> {
}
