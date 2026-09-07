package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipOrigin;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.domain.Motif;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.MotifRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.text.Normalizer;
import java.util.Locale;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogService {

    private final ArchiveEntityRepository entities;
    private final RelationshipRepository relationships;
    private final SlugService slugService;
    private final MotifRepository motifs;

    public AdminCatalogService(ArchiveEntityRepository entities,
                               RelationshipRepository relationships,
                               SlugService slugService,
                               MotifRepository motifs) {
        this.entities = entities;
        this.relationships = relationships;
        this.slugService = slugService;
        this.motifs = motifs;
    }

    @Transactional(readOnly = true)
    public List<ArchiveEntity> entities() {
        return entities.findAllByOrderByTitleAsc();
    }

    @Transactional(readOnly = true)
    public ArchiveEntity entity(UUID id) {
        ArchiveEntity entity = entities.findById(id).orElseThrow();
        entity.getMotifs().size();
        entity.getTags().size();
        return entity;
    }

    @Transactional(readOnly = true)
    public List<Relationship> relations(UUID entityId) {
        return relationships.findAllForEntity(entityId);
    }

    @Transactional
    public ArchiveEntity create(String title, EntityType type) {
        long suffix = Math.abs((long) title.hashCode());
        ArchiveEntity entity = new ArchiveEntity(slugService.uniqueSlug(title, suffix), type, title.trim());
        entity.setSource(SourceKind.MANUAL);
        entity.setVisibility(Visibility.PUBLIC);
        return entities.save(entity);
    }

    @Transactional
    public void update(UUID id, String title, String subtitle, String shortDescription, String description,
                       Visibility visibility, boolean featured, int sortOrder, String wikiUrl) {
        ArchiveEntity entity = entity(id);
        entity.setTitle(title.trim());
        entity.setSubtitle(blankToNull(subtitle));
        entity.setShortDescription(blankToNull(shortDescription));
        entity.setDescription(blankToNull(description));
        entity.setVisibility(visibility);
        entity.setFeatured(featured && entity.getVisibility() == Visibility.PUBLIC && !entity.hasPrivateTitle());
        entity.setSortOrder(sortOrder);
        entity.setWikiUrl(blankToNull(wikiUrl));
    }

    @Transactional
    public void linkWikiPage(UUID id, String wikiTitle, String wikiUrl) {
        ArchiveEntity entity = entity(id);
        entity.setWikiTitle(wikiTitle);
        entity.setWikiUrl(wikiUrl);
    }

    @Transactional
    public void createRelationship(UUID sourceId, UUID targetId, RelationshipType type,
                                   String label, Visibility visibility, int strength) {
        ArchiveEntity source = entity(sourceId);
        ArchiveEntity target = entity(targetId);
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Eine Beziehung kann nicht auf sich selbst verweisen");
        }
        Relationship relationship = new Relationship(source, target, type, RelationshipOrigin.MANUAL);
        relationship.setLabel(blankToNull(label));
        relationship.setVisibility(visibility);
        relationship.setStrength(strength);
        relationships.save(relationship);
    }

    @Transactional
    public void deleteRelationship(UUID relationshipId) {
        relationships.deleteById(relationshipId);
    }

    @Transactional
    public void updateRelationship(UUID relationshipId, RelationshipType type, String label,
                                   Visibility visibility, int strength) {
        Relationship relationship = relationships.findById(relationshipId).orElseThrow();
        relationship.setType(type);
        relationship.setLabel(blankToNull(label));
        relationship.setVisibility(visibility);
        relationship.setStrength(strength);
    }

    @Transactional(readOnly = true)
    public List<Motif> motifs() {
        return motifs.findAllByOrderByTitleAsc();
    }

    @Transactional
    public Motif createMotif(String title, String description, Visibility visibility) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "motif";
        String slug = motifs.existsBySlug(base) ? base + "-" + Math.abs((long) title.hashCode()) : base;
        Motif motif = new Motif(slug, title.trim());
        motif.setDescription(blankToNull(description));
        motif.setVisibility(visibility);
        return motifs.save(motif);
    }

    @Transactional
    public void assignMotif(UUID entityId, UUID motifId) {
        entity(entityId).getMotifs().add(motifs.findById(motifId).orElseThrow());
    }

    @Transactional
    public void unassignMotif(UUID entityId, UUID motifId) {
        entity(entityId).getMotifs().removeIf(motif -> motif.getId().equals(motifId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
