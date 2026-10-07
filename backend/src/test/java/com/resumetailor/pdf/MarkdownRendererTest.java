package com.resumetailor.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rendersHeadingsCorrectly() {
        String md = "# Heading 1\n## Heading 2\n### Heading 3";
        String html = renderer.renderToHtml(md);

        assertThat(html)
                .contains("<h1>Heading 1</h1>")
                .contains("<h2>Heading 2</h2>")
                .contains("<h3>Heading 3</h3>");
    }

    @Test
    void rendersParagraphsCorrectly() {
        String md = "This is a simple paragraph about software development.";
        String html = renderer.renderToHtml(md);

        assertThat(html).contains("<p>This is a simple paragraph about software development.</p>");
    }

    @Test
    void rendersListsCorrectly() {
        String md = """
                - Item 1
                - Item 2
                
                1. First
                2. Second
                """;
        String html = renderer.renderToHtml(md);

        assertThat(html)
                .contains("<ul>")
                .contains("<li>Item 1</li>")
                .contains("<li>Item 2</li>")
                .contains("</ul>")
                .contains("<ol>")
                .contains("<li>First</li>")
                .contains("<li>Second</li>")
                .contains("</ol>");
    }

    @Test
    void rendersLinksCorrectly() {
        String md = "Visit [Google](https://google.com) or https://github.com directly.";
        String html = renderer.renderToHtml(md);

        assertThat(html)
                .contains("<a href=\"https://google.com\">Google</a>")
                .contains("<a href=\"https://github.com\">https://github.com</a>");
    }

    @Test
    void rendersCodeBlocksCorrectly() {
        String md = """
                Here is inline `java` and a block:
                
                ```java
                public class Hello {}
                ```
                """;
        String html = renderer.renderToHtml(md);

        assertThat(html)
                .contains("<code>java</code>")
                .contains("<pre><code class=\"language-java\">public class Hello {}")
                .contains("</code></pre>");
    }

    @Test
    void rendersTablesCorrectly() {
        String md = """
                | Skill | Experience |
                | :--- | :--- |
                | Java | 5 years |
                | Spring Boot | 4 years |
                """;
        String html = renderer.renderToHtml(md);

        assertThat(html)
                .contains("<table>")
                .contains("<thead>")
                .contains(">Skill</th>")
                .contains(">Experience</th>")
                .contains("<tbody>")
                .contains(">Java</td>")
                .contains(">5 years</td>")
                .contains("</table>");
    }

    @Test
    void handlesNullOrEmptyInputGracefully() {
        assertThat(renderer.renderToHtml(null)).isEmpty();
        assertThat(renderer.renderToHtml("")).isEmpty();
        assertThat(renderer.renderToHtml("   ")).isEmpty();
    }
}
