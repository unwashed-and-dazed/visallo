package org.visallo.core;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.vertexium.*;
import org.vertexium.inmemory.InMemoryAuthorizations;
import org.vertexium.inmemory.InMemoryGraph;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.termMention.TermMentionBuilder;
import org.visallo.core.model.termMention.TermMentionRepository;
import org.visallo.core.model.textHighlighting.OffsetItem;
import org.visallo.core.model.textHighlighting.VertexOffsetItem;
import org.visallo.core.security.DirectVisibilityTranslator;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EntityHighlighterTest {
    private static final String PROPERTY_KEY = "";
    private static final String PERSON_IRI = "http://visallo.org/test/person";
    private static final String LOCATION_IRI = "http://visallo.org/test/location";

    InMemoryGraph graph;

    @Mock
    private User user;
    private Authorizations authorizations;

    private Visibility visibility;

    private VisibilityTranslator visibilityTranslator = new DirectVisibilityTranslator();

    @Before
    public void setUp() {
        visibility = new Visibility("");
        graph = InMemoryGraph.create();
        authorizations = new InMemoryAuthorizations(TermMentionRepository.VISIBILITY_STRING);

        when(user.getUserId()).thenReturn("USER123");
    }

    @Test
    public void testReplaceNonBreakingSpaces() throws Exception {
        EnumSet<EntityHighlighter.Options> none = EnumSet.noneOf(EntityHighlighter.Options.class);
        assertEquals(" ", EntityHighlighter.getHighlightedText("&nbsp;", Arrays.asList(), none));
        assertEquals("     ", EntityHighlighter.getHighlightedText("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;", Arrays.asList(), none));
        assertEquals("       ", EntityHighlighter.getHighlightedText(" &nbsp; &nbsp;&nbsp;&nbsp;&nbsp;", Arrays.asList(), none));
    }


    @Test
    public void testWithUTF8() throws Exception {
        String actual = EntityHighlighter.getHighlightedText("&nbsp;😎💃🏿", Arrays.asList(), EnumSet.noneOf(EntityHighlighter.Options.class));
        assertEquals(" 😎💃🏿", actual);
    }

    @Test
    public void testReplaceNonBreakingSpacesAcrossBuffer() throws Exception {
        String space = "&nbsp;";
        int spaceStart = Math.max(0, EntityHighlighter.BUFFER_SIZE - 2);
        int spaceEnd = spaceStart + space.length();

        StringBuilder longText = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < spaceEnd; i++) {
            if (i < spaceStart) {
                longText.append("_");
                expected.append("_");
            } else {
                longText.append(space);
                expected.append(" ");
                break;
            }
        }

        assertEquals(expected.toString(), EntityHighlighter.getHighlightedText(longText.toString(), Arrays.asList(), EnumSet.noneOf(EntityHighlighter.Options.class)));
    }

    @Test
    public void testSpanAcrossBuffer() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        String before = "joe";
        String after = " ferner";
        String sign = before + after;

        int start = Math.max(0, EntityHighlighter.BUFFER_SIZE - before.length());
        int end = start + sign.length();

        terms.add(createTermMention(outVertex, sign, PERSON_IRI, start, end));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);

        StringBuilder expected = new StringBuilder();
        StringBuilder str = new StringBuilder();
        boolean doneWithExpected = false;
        for (int i = 0; i < end; i++) {
            if (i < start) {
                expected.append("_");
                str.append("_");
            } else {
                str.append(sign.substring(i - start, (i - start) + 1));
                if (!doneWithExpected) {
                    expected.append("<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                            "title=\"joe ferner\" " +
                            "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:" + start + ",&quot;end&quot;:" + end + ",&quot;id&quot;:&quot;TM_" + start + "-" + end + "-5e180bf84df5539ac1e6762812dcd29e2b5b4374&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;joe ferner&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                            "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                            "joe ferner" +
                            "</span>" +
                            "<style>.text .res{border-image-outset: 0 0 0px 0;border-bottom: 1px solid black;border-image-source: linear-gradient(to right, black, black);border-image-slice: 0 0 1 0;border-image-width: 0 0 1px 0;border-image-repeat: repeat;}.text .res.resolvable{border-image-source: repeating-linear-gradient(to right, transparent, transparent 1px, rgb(0,0,0) 1px, rgb(0,0,0) 3px);}</style>");
                    doneWithExpected = true;
                }
            }
        }

        String highlightedText = EntityHighlighter.getHighlightedText(str.toString(), termAndTermMetadata);
        assertEquals(expected.toString(), highlightedText);
    }

    @Test
    public void testGetHighlightedText() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "joe ferner", PERSON_IRI, 18, 28));
        terms.add(createTermMention(outVertex, "jeff kunkle", PERSON_IRI, 33, 44, "uniq1"));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);
        String highlightedText = EntityHighlighter.getHighlightedText("Test highlight of Joe Ferner and Jeff Kunkle.", termAndTermMetadata);
        String joeDataInfo = "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:18,&quot;end&quot;:28,&quot;id&quot;:&quot;TM_18-28-5e180bf84df5539ac1e6762812dcd29e2b5b4374&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;joe ferner&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\"";
        String jeffDataInfo = "data-info=\"{&quot;process&quot;:&quot;uniq1&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:33,&quot;end&quot;:44,&quot;id&quot;:&quot;TM_33-44-ed6f44225a01778932642e3c8a1bc65ef5967432&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;jeff kunkle&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                "data-ref-id=\"" + termAndTermMetadata.get(1).getClassIdentifier() + "\"";
        String expectedText = "Test highlight of " +
                "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" title=\"joe ferner\" " + joeDataInfo + ">Joe Ferner</span>" +
                " and <span class=\"resolvable res " + termAndTermMetadata.get(1).getClassIdentifier() + "\" title=\"jeff kunkle\" " + jeffDataInfo + ">Jeff Kunkle</span>.";

        assertMatchStyleAndMeta(expectedText, highlightedText, 1);
    }

    @Test
    public void testLineBreaksOffsetsCorrect() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "joe", PERSON_IRI, 23, 29));

        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);
        /*
        Test highlight of: Joe
        Ferner
            and Jeff Kunkle.
         */
        String highlightedText = EntityHighlighter.getHighlightedText("Test highlight of: Joe\nFerner\n\tand Jeff Kunkle.", termAndTermMetadata);
        String joe = "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                        "title=\"joe\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:23,&quot;end&quot;:29,&quot;id&quot;:&quot;TM_23-29-f804acaaa829703b2de338ef485d68577e2bade6&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;joe&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">";

        String expectedText = "Test highlight of: Joe\n<br>" +
                joe + "Ferner</span>\n<br>" +
                "\tand Jeff Kunkle.";

        assertMatchStyleAndMeta(expectedText, highlightedText, 1);
    }

    @Test
    public void testLineBreaksSplitSpansOnlyWhenNotEmpty() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "joe", PERSON_IRI, 19, 30));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);
        String highlightedText = EntityHighlighter.getHighlightedText("Test highlight of: first\n\nlast", termAndTermMetadata);
        String expectedText =
                "Test highlight of: " +
                    "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                        "title=\"joe\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:19,&quot;end&quot;:30,&quot;id&quot;:&quot;TM_19-30-f804acaaa829703b2de338ef485d68577e2bade6&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;joe&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                            "first\n<br>\n<br>last" +
                    "</span>";

        assertMatchStyleAndMeta(expectedText, highlightedText, 1);
    }

    @Test
    public void testGetOverlappingHighlightedText() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "first", PERSON_IRI, 0, 5));
        terms.add(createTermMention(outVertex, "t se", PERSON_IRI, 4, 8));
        terms.add(createTermMention(outVertex, "third", PERSON_IRI, 13, 18));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);
        String highlightedText = EntityHighlighter.getHighlightedText("first second third", termAndTermMetadata);
        String expectedText =
                "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                    "title=\"first\" " +
                    "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:0,&quot;end&quot;:5,&quot;id&quot;:&quot;TM_0-5-e2cf06e792f9edf5f29a3737044ce1934bf0c08e&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;first&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                    "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                    "firs" +
                    "<span class=\"resolvable res " + termAndTermMetadata.get(1).getClassIdentifier() + "\" " +
                        "title=\"t se\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:4,&quot;end&quot;:8,&quot;id&quot;:&quot;TM_4-8-61ade973cf8381c3e77b307be4c7314703a03b27&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;t se&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(1).getClassIdentifier() + "\">" +
                        "t" +
                    "</span>" +
                "</span>" +
                "<span class=\"resolvable res " + termAndTermMetadata.get(1).getClassIdentifier() + "\" " +
                        "title=\"t se\" " +
                        "data-ref=\"" + termAndTermMetadata.get(1).getClassIdentifier() + "\"> " +
                        "se" +
                "</span>" +
                "cond " +
                "<span class=\"resolvable res " + termAndTermMetadata.get(2).getClassIdentifier() + "\" " +
                    "title=\"third\" " +
                    "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:13,&quot;end&quot;:18,&quot;id&quot;:&quot;TM_13-18-864b9b57177a5dd86de4dc29e8925cf0836c03c7&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;third&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                    "data-ref-id=\"" + termAndTermMetadata.get(2).getClassIdentifier() + "\">" +
                        "third" +
                "</span>";
        assertMatchStyleAndMeta(expectedText, highlightedText, 2);
    }

    @Test
    public void testCrazyOverlappingHighlightedText() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "first", PERSON_IRI, 0, 5));
        terms.add(createTermMention(outVertex, "firs", PERSON_IRI, 0, 4));
        terms.add(createTermMention(outVertex, "nd third", PERSON_IRI, 10, 18));
        terms.add(createTermMention(outVertex, "t se", PERSON_IRI, 4, 8));
        terms.add(createTermMention(outVertex, "irst sec", PERSON_IRI, 1, 9));
        terms.add(createTermMention(outVertex, "nd ", PERSON_IRI, 10, 13));
        terms.add(createTermMention(outVertex, "first second third", PERSON_IRI, 0, 18));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);
        String highlightedText = EntityHighlighter.getHighlightedText("first second third", termAndTermMetadata);
        String expectedText =
                "<span class=\"resolvable res " + termAndTermMetadata.get(6).getClassIdentifier() + "\" " +
                    "title=\"first second third\" " +
                    "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:0,&quot;end&quot;:18,&quot;id&quot;:&quot;TM_0-18-797c665056c681010dad6fb6a7f09f49a71605b2&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;first second third&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                    "data-ref-id=\"" + termAndTermMetadata.get(6).getClassIdentifier() + "\">" +
                    "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                        "title=\"first\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:0,&quot;end&quot;:5,&quot;id&quot;:&quot;TM_0-5-e2cf06e792f9edf5f29a3737044ce1934bf0c08e&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;first&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                        "<span class=\"resolvable res " + termAndTermMetadata.get(1).getClassIdentifier() + "\" " +
                            "title=\"firs\" " +
                            "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:0,&quot;end&quot;:4,&quot;id&quot;:&quot;TM_0-4-c4781cfb3f0ea21db9d28c44486e65439eedeb07&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;firs&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                            "data-ref-id=\"" + termAndTermMetadata.get(1).getClassIdentifier() + "\">" +
                            "f" +
                            "<span class=\"resolvable res " + termAndTermMetadata.get(4).getClassIdentifier() + "\" " +
                                "title=\"irst sec\" " +
                                "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:1,&quot;end&quot;:9,&quot;id&quot;:&quot;TM_1-9-e9c080fef2bcd729b0634c9d9e5bd562b3b1c1a4&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;irst sec&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                                "data-ref-id=\"" + termAndTermMetadata.get(4).getClassIdentifier() + "\">" +
                                "irs" +
                                "<span class=\"resolvable res " + termAndTermMetadata.get(3).getClassIdentifier() + "\" " +
                                    "title=\"t se\" " +
                                    "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:4,&quot;end&quot;:8,&quot;id&quot;:&quot;TM_4-8-61ade973cf8381c3e77b307be4c7314703a03b27&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;t se&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                                    "data-ref-id=\"" + termAndTermMetadata.get(3).getClassIdentifier() + "\">" +
                                "</span>" +
                            "</span>" +
                        "</span>" +
                        "<span class=\"resolvable res " + termAndTermMetadata.get(3).getClassIdentifier() + "\" " +
                            "title=\"t se\" " +
                            "data-ref=\"" + termAndTermMetadata.get(3).getClassIdentifier() + "\">" +
                            "<span class=\"resolvable res " + termAndTermMetadata.get(4).getClassIdentifier() + "\" " +
                                "title=\"irst sec\" " +
                                "data-ref=\"" + termAndTermMetadata.get(4).getClassIdentifier() + "\">t</span>" +
                            "</span>" +
                        "</span>" +
                    "<span class=\"resolvable res " + termAndTermMetadata.get(3).getClassIdentifier() + "\" " +
                        "title=\"t se\" " +
                        "data-ref=\"" + termAndTermMetadata.get(3).getClassIdentifier() + "\">" +
                        "<span class=\"resolvable res " + termAndTermMetadata.get(4).getClassIdentifier() + "\" " +
                            "title=\"irst sec\" " +
                            "data-ref=\"" + termAndTermMetadata.get(4).getClassIdentifier() + "\"> se</span>" +
                            "c" +
                        "</span>" +
                        "o" +
                        "<span class=\"resolvable res " + termAndTermMetadata.get(2).getClassIdentifier() + "\" " +
                            "title=\"nd third\" " +
                            "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:10,&quot;end&quot;:18,&quot;id&quot;:&quot;TM_10-18-24f7791aa39784662e0f22bd5b99c96aacba08bc&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;nd third&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                            "data-ref-id=\"" + termAndTermMetadata.get(2).getClassIdentifier() + "\">" +
                        "<span class=\"resolvable res " + termAndTermMetadata.get(5).getClassIdentifier() + "\" " +
                            "title=\"nd \" " +
                            "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:10,&quot;end&quot;:13,&quot;id&quot;:&quot;TM_10-13-7e00646a2ce55eec5a5c69725980b4cdf8678558&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;nd &quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                            "data-ref-id=\"" + termAndTermMetadata.get(5).getClassIdentifier() + "\">" +
                            "nd " +
                        "</span>" +
                        "third" +
                    "</span>" +
                "</span>";
        assertMatchStyleAndMeta(expectedText, highlightedText, 5);
    }

    private void assertMatchStyleAndMeta(String expected, String actual, int expectedDepth) throws Exception {
        Pattern pattern = Pattern.compile("^(.*)<style>(.*)</style>$", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(actual);
        if (matcher.matches() && matcher.groupCount() == 2) {
            assertEquals(expected, matcher.group(1));
            String style = matcher.group(2);
            StringBuilder selector = new StringBuilder(".text");
            for (int i = 0; i < expectedDepth; i++) {
                selector.append(" .res");
                String depthSelector = selector.toString() + "{";
                assertTrue("Style contains: " + depthSelector, style.contains(depthSelector));
            }
        } else {
            throw new Exception("Actual didn't match regex");
        }
    }

    private Vertex createTermMention(Vertex outVertex, String sign, String conceptIri, int start, int end) {
        return createTermMention(outVertex, sign, conceptIri, start, end, null, null);
    }

    private Vertex createTermMention(Vertex outVertex, String sign, String conceptIri, int start, int end, Vertex resolvedToVertex, Edge resolvedEdge) {
        TermMentionBuilder tmb = new TermMentionBuilder()
                .outVertex(outVertex)
                .propertyKey(PROPERTY_KEY)
                .propertyName(VisalloProperties.TEXT.getPropertyName())
                .conceptIri(conceptIri)
                .start(start)
                .end(end)
                .title(sign)
                .visibilityJson("")
                .process(getClass().getSimpleName());
        if (resolvedToVertex != null || resolvedEdge != null) {
            tmb.resolvedTo(resolvedToVertex, resolvedEdge);
        }
        return tmb.save(graph, visibilityTranslator, user, authorizations);
    }

    private Vertex createTermMention(Vertex outVertex, String sign, String conceptIri, int start, int end, String process) {
        return new TermMentionBuilder()
                .outVertex(outVertex)
                .propertyKey(PROPERTY_KEY)
                .propertyName(VisalloProperties.TEXT.getPropertyName())
                .conceptIri(conceptIri)
                .start(start)
                .end(end)
                .title(sign)
                .visibilityJson("")
                .process(process)
                .save(graph, visibilityTranslator, user, authorizations);
    }

    @Test
    public void testGetHighlightedTextOverlaps() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "joe ferner", PERSON_IRI, 18, 28));
        terms.add(createTermMention(outVertex, "jeff kunkle", PERSON_IRI, 18, 21));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);
        String highlightedText = EntityHighlighter.getHighlightedText("Test highlight of Joe Ferner.", termAndTermMetadata);
        String expectedText =
            "Test highlight of " +
                "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                    "title=\"joe ferner\" " +
                    "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:18,&quot;end&quot;:28,&quot;id&quot;:&quot;TM_18-28-5e180bf84df5539ac1e6762812dcd29e2b5b4374&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;joe ferner&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                    "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                    "<span class=\"resolvable res " + termAndTermMetadata.get(1).getClassIdentifier() + "\" " +
                        "title=\"jeff kunkle\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:18,&quot;end&quot;:21,&quot;id&quot;:&quot;TM_18-21-573648828a7888c138cbc1d580be8d09a1f2e782&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;jeff kunkle&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(1).getClassIdentifier() + "\">" +
                        "Joe" +
                    "</span> " +
                    "Ferner" +
                "</span>.";

        assertMatchStyleAndMeta(expectedText, highlightedText, 2);
    }

    @Test
    public void testGetHighlightedTextExactOverlapsTwoDocuments() throws Exception{
        Authorizations tmAuths = graph.createAuthorizations(TermMentionRepository.VISIBILITY_STRING);

        Vertex v1 = graph.addVertex("v1", visibility, authorizations);
        Vertex v2 = graph.addVertex("v2", visibility, authorizations);
        Vertex vjf = graph.addVertex("jf", visibility, authorizations);
        Edge e1 = graph.addEdge("e1", v1, vjf, "has", visibility, authorizations);
        Edge e2 = graph.addEdge("e2", v2, vjf, "has", visibility, authorizations);

        createTermMention(v1, "joe ferner", PERSON_IRI, 18, 28, vjf, e1);
        createTermMention(v2, "joe ferner", PERSON_IRI, 18, 28, vjf, e2);
        graph.flush();

        ArrayList<Vertex> terms = Lists.newArrayList(graph.getVertex("v1", tmAuths).getVertices(Direction.BOTH, VisalloProperties.TERM_MENTION_LABEL_HAS_TERM_MENTION, tmAuths));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", this.authorizations);
        String highlightedText = EntityHighlighter.getHighlightedText("Test highlight of Joe Ferner.", termAndTermMetadata);
        String expectedText =
                "Test highlight of <span class=\"resolved res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                        "title=\"joe ferner\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;resolvedToVertexId&quot;:&quot;jf&quot;,&quot;resolvedToEdgeId&quot;:&quot;e1&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:18,&quot;termMentionFor&quot;:&quot;VERTEX&quot;,&quot;termMentionForElementId&quot;:&quot;jf&quot;,&quot;end&quot;:28,&quot;id&quot;:&quot;TM_18-28-47b5d3c6dc289fae21f303034649da038a7db76d&quot;,&quot;outVertexId&quot;:&quot;v1&quot;,&quot;title&quot;:&quot;joe ferner&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                        "Joe Ferner</span>.";
        assertMatchStyleAndMeta(expectedText, highlightedText, 1);

        terms = Lists.newArrayList(graph.getVertex("v2", tmAuths).getVertices(Direction.BOTH, VisalloProperties.TERM_MENTION_LABEL_HAS_TERM_MENTION, tmAuths));
        termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", this.authorizations);
        highlightedText = EntityHighlighter.getHighlightedText("Test highlight of Joe Ferner.", termAndTermMetadata);
        expectedText =
                "Test highlight of <span class=\"resolved res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                        "title=\"joe ferner\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;resolvedToVertexId&quot;:&quot;jf&quot;,&quot;resolvedToEdgeId&quot;:&quot;e2&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:18,&quot;termMentionFor&quot;:&quot;VERTEX&quot;,&quot;termMentionForElementId&quot;:&quot;jf&quot;,&quot;end&quot;:28,&quot;id&quot;:&quot;TM_18-28-eb6560a01113a23abf584241655b8a4c5af1bce0&quot;,&quot;outVertexId&quot;:&quot;v2&quot;,&quot;title&quot;:&quot;joe ferner&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                        "Joe Ferner</span>.";
        assertMatchStyleAndMeta(expectedText, highlightedText, 1);
    }

    @Test
    public void testFilterResolvedTermMentions() throws Exception {
        String str = "This is a test sentence";
        List<OffsetItem> offsetItems = new ArrayList<>();

        Vertex text = graph.addVertex("1", visibility, authorizations);

        Vertex v1 = graph.addVertex("v1", visibility, authorizations);
        Edge e1 = graph.addEdge("e1", text, v1, "has", visibility, authorizations);


        List<Vertex> terms = new ArrayList<>();
        Vertex resolvable = createTermMention(text, "Wrong", PERSON_IRI, 0, 4);
        Vertex resolved = createTermMention(text, "This", PERSON_IRI, 0, 4, v1, e1);
        graph.addEdge("e2", resolved, resolvable, VisalloProperties.TERM_MENTION_RESOLVED_FROM, visibility, authorizations);
        terms.add(resolvable);
        terms.add(resolved);

        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", this.authorizations);

        String highlightedText = EntityHighlighter.getHighlightedText(str, termAndTermMetadata);
        String expectedText =
                "<span " +
                    "class=\"resolved res " + termAndTermMetadata.get(1).getClassIdentifier() + "\" " +
                    "title=\"This\" " +
                    "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;resolvedToEdgeId&quot;:&quot;e1&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/person&quot;,&quot;start&quot;:0,&quot;title&quot;:&quot;This&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;,&quot;resolvedToVertexId&quot;:&quot;v1&quot;,&quot;termMentionFor&quot;:&quot;VERTEX&quot;,&quot;termMentionForElementId&quot;:&quot;v1&quot;,&quot;end&quot;:4,&quot;id&quot;:&quot;TM_0-4-cc377e2478ec54c8e486a1a814b45e80b2d5db42&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;resolvedFromTermMentionId&quot;:&quot;TM_0-4-6e1990686375878517ed309bdc271b758164f939&quot;}\" " +
                    "data-ref-id=\"" + termAndTermMetadata.get(1).getClassIdentifier() + "\"" +
                ">This</span> is a test sentence";

        assertMatchStyleAndMeta(expectedText, highlightedText, 1);
    }

    @Test
    public void testGetHighlightedTextNestedEntity() throws Exception {
        String text = "This is a test sentence";
        List<OffsetItem> offsetItems = new ArrayList<>();

        OffsetItem mockEntity1 = mock(VertexOffsetItem.class);
        when(mockEntity1.getStart()).thenReturn(0l);
        when(mockEntity1.getEnd()).thenReturn(4l);
        when(mockEntity1.getResolvedToVertexId()).thenReturn("0");
        when(mockEntity1.getCssClasses()).thenReturn(asList(new String[]{"This"}));
        when(mockEntity1.shouldHighlight()).thenReturn(true);
        when(mockEntity1.getInfoJson()).thenReturn(new JSONObject("{\"data\":\"attribute\"}"));
        offsetItems.add(mockEntity1);

        OffsetItem mockEntity2 = mock(VertexOffsetItem.class);
        when(mockEntity2.getStart()).thenReturn(0l);
        when(mockEntity2.getEnd()).thenReturn(4l);
        when(mockEntity2.getResolvedToVertexId()).thenReturn("1");
        when(mockEntity2.getCssClasses()).thenReturn(asList(new String[]{"This"}));
        when(mockEntity2.shouldHighlight()).thenReturn(true);
        when(mockEntity2.getInfoJson()).thenReturn(new JSONObject("{\"data\":\"attribute\"}"));
        offsetItems.add(mockEntity2);

        OffsetItem mockEntity2x = mock(VertexOffsetItem.class);
        when(mockEntity2.getStart()).thenReturn(0l);
        when(mockEntity2.getEnd()).thenReturn(4l);
        when(mockEntity2.getResolvedToVertexId()).thenReturn("1");
        when(mockEntity2.getCssClasses()).thenReturn(asList(new String[]{"This"}));
        when(mockEntity2.shouldHighlight()).thenReturn(false);
        when(mockEntity2.getInfoJson()).thenReturn(new JSONObject("{\"data\":\"attribute\"}"));
        offsetItems.add(mockEntity2x);

        OffsetItem mockEntity3 = mock(VertexOffsetItem.class);
        when(mockEntity3.getStart()).thenReturn(0l);
        when(mockEntity3.getEnd()).thenReturn(7l);
        when(mockEntity3.getCssClasses()).thenReturn(asList(new String[]{"This is"}));
        when(mockEntity3.shouldHighlight()).thenReturn(true);
        when(mockEntity3.getInfoJson()).thenReturn(new JSONObject("{\"data\":\"attribute\"}"));
        offsetItems.add(mockEntity3);

        OffsetItem mockEntity4 = mock(VertexOffsetItem.class);
        when(mockEntity4.getStart()).thenReturn(5l);
        when(mockEntity4.getEnd()).thenReturn(9l);
        when(mockEntity4.getCssClasses()).thenReturn(asList(new String[]{"is a"}));
        when(mockEntity4.shouldHighlight()).thenReturn(true);
        when(mockEntity4.getClassIdentifier()).thenReturn("id");
        when(mockEntity4.getInfoJson()).thenReturn(new JSONObject("{\"data\":\"attribute\"}"));
        offsetItems.add(mockEntity4);

        OffsetItem mockEntity5 = mock(VertexOffsetItem.class);
        when(mockEntity5.getStart()).thenReturn(15l);
        when(mockEntity5.getEnd()).thenReturn(23l);
        when(mockEntity5.getCssClasses()).thenReturn(asList(new String[]{"sentence"}));
        when(mockEntity5.shouldHighlight()).thenReturn(true);
        when(mockEntity5.getInfoJson()).thenReturn(new JSONObject("{\"data\":\"attribute\"}"));
        offsetItems.add(mockEntity5);

        String highlightedText = EntityHighlighter.getHighlightedText(text, offsetItems);
        String expectedText =
                "<span class=\"This is\" data-info=\"{&quot;data&quot;:&quot;attribute&quot;}\">" +
                    "<span class=\"This\" data-info=\"{&quot;data&quot;:&quot;attribute&quot;}\">This</span> " +
                    "<span class=\"is a\" data-info=\"{&quot;data&quot;:&quot;attribute&quot;}\" data-ref-id=\"id\">is</span>" +
                "</span>" +
                "<span class=\"is a\" data-ref=\"id\"> a</span>" +
                " test " +
                "<span class=\"sentence\" data-info=\"{&quot;data&quot;:&quot;attribute&quot;}\">sentence</span>";

        assertMatchStyleAndMeta(expectedText, highlightedText, 2);
    }

    @Test
    public void testGetHighlightedTextWithAccentedCharacters() throws Exception {
        Vertex outVertex = graph.addVertex("1", visibility, authorizations);

        ArrayList<Vertex> terms = new ArrayList<>();
        terms.add(createTermMention(outVertex, "US", LOCATION_IRI, 48, 50));
        List<OffsetItem> termAndTermMetadata = new EntityHighlighter().convertTermMentionsToOffsetItems(terms, "", authorizations);

        String highlightedText = EntityHighlighter.getHighlightedText("Ejército de Liberación Nacional® partnered with US on peace treaty", termAndTermMetadata);
        String expectedText =
                "Ejército de Liberación Nacional® partnered with " +
                    "<span class=\"resolvable res " + termAndTermMetadata.get(0).getClassIdentifier() + "\" " +
                        "title=\"US\" " +
                        "data-info=\"{&quot;process&quot;:&quot;EntityHighlighterTest&quot;,&quot;conceptType&quot;:&quot;http://visallo.org/test/location&quot;,&quot;start&quot;:48,&quot;end&quot;:50,&quot;id&quot;:&quot;TM_48-50-3921918b09b047c621b891f4f6ed60abb5d7f9e4&quot;,&quot;outVertexId&quot;:&quot;1&quot;,&quot;title&quot;:&quot;US&quot;,&quot;sandboxStatus&quot;:&quot;PRIVATE&quot;}\" " +
                        "data-ref-id=\"" + termAndTermMetadata.get(0).getClassIdentifier() + "\">" +
                        "US" +
                    "</span> on peace treaty";
        assertMatchStyleAndMeta(expectedText, highlightedText, 1);
    }

    private List<String> asList(String[] strings) {
        List<String> results = new ArrayList<>();
        Collections.addAll(results, strings);
        return results;
    }
}
