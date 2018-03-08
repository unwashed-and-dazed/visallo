package org.visallo.core.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SourceInfoSnippetSanitizerTest {

    @Test
    public void sanitizeIrregularSnippetEscaped() {
        assertEquals("test&lt;ing",
                SourceInfoSnippetSanitizer.sanitizeSnippet("test<ing"));
    }

    @Test
    public void sanitizeRegularSnippetEscaped() {
        assertEquals("testing &amp; <span class=\"selection\">a</span>more text &amp; bold",
                SourceInfoSnippetSanitizer.sanitizeSnippet("testing & <span class=\"selection\">a</span>more text & bold"));
    }

    @Test
    public void sanitizeXssSnippetEscaped() {
        assertEquals("&lt;script&gt;xss&lt;/script&gt;",
                SourceInfoSnippetSanitizer.sanitizeSnippet("<script>xss</script>"));
    }

    @Test
    public void sanitizeRegularXssSnippetEscaped() {
        assertEquals("&lt;script&gt;xss&lt;/script&gt;<span class=\"selection\">&lt;script&gt;xss&lt;/script&gt;</span>&lt;script&gt;xss&lt;/script&gt;",
                SourceInfoSnippetSanitizer.sanitizeSnippet("<script>xss</script><span class=\"selection\"><script>xss</script></span><script>xss</script>"));
    }

    @Test
    public void sanitizeSpanAttributes() {
        assertEquals("prefix&lt;span onClick=&quot;javascript:void&quot; class=&quot;selection&quot;&gt;normal&lt;/span&gt;post",
                SourceInfoSnippetSanitizer.sanitizeSnippet("prefix<span onClick=\"javascript:void\" class=\"selection\">normal</span>post"));
    }

    @Test
    public void sanitizeSelectionWithUnicode() {
        assertEquals("😎<span class=\"selection\">😎</span>😎",
                SourceInfoSnippetSanitizer.sanitizeSnippet("😎<span class=\"selection\">😎</span>😎"));
    }

    @Test
    public void sanitizeNoPrefix() {
        assertEquals("<span class=\"selection\">sel</span>post",
                SourceInfoSnippetSanitizer.sanitizeSnippet("<span class=\"selection\">sel</span>post"));
    }

    @Test
    public void sanitizeNoSuffix() {
        assertEquals("pre<span class=\"selection\">sel</span>",
                SourceInfoSnippetSanitizer.sanitizeSnippet("pre<span class=\"selection\">sel</span>"));
    }

    @Test
    public void sanitizeOnlySelection() {
        assertEquals("<span class=\"selection\">sel</span>",
                SourceInfoSnippetSanitizer.sanitizeSnippet("<span class=\"selection\">sel</span>"));
    }


    @Test
    public void sanitizeMultipleSelection() {
        assertEquals("<span class=\"selection\">sel</span>&lt;span class=&quot;selection&quot;&gt;sel&lt;/span&gt;",
                SourceInfoSnippetSanitizer.sanitizeSnippet("<span class=\"selection\">sel</span><span class=\"selection\">sel</span>"));
    }

    @Test
    public void sanitizeHandlesNull() {
        assertEquals(null,
                SourceInfoSnippetSanitizer.sanitizeSnippet(null));
    }
}
