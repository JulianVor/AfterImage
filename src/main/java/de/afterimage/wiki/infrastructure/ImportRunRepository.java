package de.afterimage.wiki.infrastructure;

import de.afterimage.wiki.domain.ImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {
    List<ImportRun> findTop20ByOrderByStartedAtDesc();
}

