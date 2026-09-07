package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.Motif;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MotifRepository extends JpaRepository<Motif, UUID> {
    List<Motif> findAllByOrderByTitleAsc();
    boolean existsBySlug(String slug);
}
