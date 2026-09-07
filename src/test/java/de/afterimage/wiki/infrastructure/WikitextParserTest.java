package de.afterimage.wiki.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WikitextParserTest {

    private final WikitextParser parser = new WikitextParser();

    @Test
    void extractsFactsAndNavigationWithoutTreatingQueriesOrLiteralExamplesAsFacts() {
        String source = """
                {{DISPLAYTITLE:''A Film''}}
                {{#set:
                  | Page type=Video
                  | Director=[[Julian Vornfeld|Julian]]
                  | Note={{Nested|a|b}}
                }}
                [[Featured band::Mute Tales]]
                [[Category:Music videos 2024]]
                A link to [[Zwickau#History|the city]] and https://example.org/trailer.
                {{#ask:[[Hidden fact::must-not-import]]|?Hidden fact}}
                <nowiki>[[Literal fact::must-not-import]]</nowiki>
                <!-- [[Comment fact::must-not-import]] -->
                """;

        var parsed = parser.parse(source);

        assertThat(parsed.displayTitle()).isEqualTo("A Film");
        assertThat(parsed.properties()).containsEntry("Page type", java.util.List.of("Video"));
        assertThat(parsed.properties()).containsEntry("Director", java.util.List.of("[[Julian Vornfeld|Julian]]"));
        assertThat(parsed.properties()).containsEntry("Note", java.util.List.of("{{Nested|a|b}}"));
        assertThat(parsed.properties()).containsEntry("Featured band", java.util.List.of("Mute Tales"));
        assertThat(parsed.properties()).doesNotContainKeys("Hidden fact", "Literal fact", "Comment fact");
        assertThat(parsed.categories()).containsExactly("Music videos 2024");
        assertThat(parsed.links()).contains("Zwickau");
        assertThat(parsed.externalUrls()).containsExactly("https://example.org/trailer");
    }

    @Test
    void canonicalizesLinkedSemanticValues() {
        assertThat(WikitextParser.canonicalValue(" [[Julian Vornfeld|Julian]] "))
                .isEqualTo("Julian Vornfeld");
    }

    @Test
    void extractsCleanEditorialIntroductionWithoutMetadataOrArticleSections() {
        String source = """
                {{DISPLAYTITLE:''Acid Head''}}
                {{#set:|Page type=Band|Genre=Modern Metal}}
                <translate>
                {| class="wikitable"
                |-
                ! Herkunft
                | Hamburg
                |}

                '''Acid Head''' ist eine 2015 gegründete Metalband aus [[Hamburg]].

                Die Band verbindet '''Modern Metal''' mit deutschsprachigen Texten.

                == Geschichte ==
                Dieser Abschnitt gehört nicht mehr in die redaktionelle Einleitung.
                </translate>
                [[Kategorie:Bands]]
                """;

        var editorial = parser.editorialText(source);

        assertThat(editorial.shortDescription())
                .isEqualTo("Acid Head ist eine 2015 gegründete Metalband aus Hamburg.");
        assertThat(editorial.description())
                .isEqualTo("Die Band verbindet Modern Metal mit deutschsprachigen Texten.");
        assertThat(editorial.description()).doesNotContain("Geschichte", "Herkunft", "Page type");
    }

    @Test
    void findsDisplayedImagesInDocumentOrderAndIgnoresCommentsQueriesAndLiteralExamples() {
        String source = """
                <!-- [[File:Commented.jpg|300px]] -->
                {{#ask:[[File:Query-result.jpg]]|format=gallery}}
                <nowiki>[[File:Example.jpg]]</nowiki>
                [[Datei:First photo.jpg|thumb|Portrait]]
                <gallery>
                File:Second_photo.png|Live
                File:First photo.jpg|Duplicate
                </gallery>
                """;

        assertThat(parser.imageTitles(source))
                .containsExactly("File:First photo.jpg", "File:Second photo.png");
    }

    @Test
    void extractsHeadedNarrativeSectionsAndPreservesTheirStructure() {
        String source = """
                {{#set:|Page type=Video}}
                == Handlung ==

                Das Musikvideo verbindet die Performance mit einer symbolischen Handlung.

                * eine Szene in einem hellen Raum
                * eine Szene in einem dunklen Raum

                === Die Darstellerin im roten Kleid ===

                Sie verkörpert die inneren '''„Dämonen“''' des Sängers.
                """;

        var sections = parser.contentSections(source);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("Handlung");
        assertThat(sections.get(0).level()).isEqualTo(2);
        assertThat(sections.get(0).body()).contains("* eine Szene in einem hellen Raum");
        assertThat(sections.get(1).heading()).isEqualTo("Die Darstellerin im roten Kleid");
        assertThat(sections.get(1).level()).isEqualTo(3);
        assertThat(sections.get(1).body()).contains("'''„Dämonen“'''");
    }
}
