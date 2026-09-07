package de.afterimage.wiki.infrastructure;

import de.afterimage.config.AfterimageProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlWikiSourceTest {

    private final XmlWikiSource source = new XmlWikiSource(new WikitextParser(),
            new AfterimageProperties(null, null, null, null, null, new AfterimageProperties.Import(20)));

    @Test
    void importsOnlyTheNewestRevisionOfEachPage() throws Exception {
        String xml = """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/">
                  <siteinfo><sitename>Archive</sitename><base>https://wiki.example/wiki/Main_Page</base>
                    <generator>MediaWiki 1.43</generator></siteinfo>
                  <page><title>Film</title><ns>0</ns><id>7</id>
                    <revision><id>10</id><timestamp>2024-01-01T00:00:00Z</timestamp>
                      <model>wikitext</model><format>text/x-wiki</format><text>{{#set:|Page type=Band}}</text></revision>
                    <revision><id>11</id><timestamp>2025-01-01T00:00:00Z</timestamp>
                      <contributor><username>Editor</username><id>99</id></contributor>
                      <model>wikitext</model><format>text/x-wiki</format><text>{{#set:|Page type=Video|Release year=2025}}</text></revision>
                  </page>
                </mediawiki>
                """;

        var export = source.read(stream(xml));

        assertThat(export.siteName()).isEqualTo("Archive");
        assertThat(export.pages()).hasSize(1);
        assertThat(export.pages().getFirst().revision().id()).isEqualTo(11);
        assertThat(export.pages().getFirst().firstValue("Page type")).isEqualTo("Video");
    }

    @Test
    void rejectsDoctypeBasedPayloads() {
        String xml = """
                <!DOCTYPE mediawiki [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/">
                  <siteinfo><sitename>&xxe;</sitename></siteinfo>
                </mediawiki>
                """;

        assertThatThrownBy(() -> source.read(stream(xml)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid MediaWiki XML export");
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
