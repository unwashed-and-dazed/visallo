package org.visallo.phoneNumber;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorker;
import org.visallo.core.ingest.graphProperty.TermMentionGraphPropertyWorkerTestBase;

import java.util.Arrays;

import static org.visallo.phoneNumber.PhoneNumberGraphPropertyWorker.PHONE_NUMBER_CONCEPT_INTENT;

@RunWith(MockitoJUnitRunner.class)
public class PhoneNumberGraphPropertyWorkerTest extends TermMentionGraphPropertyWorkerTestBase {
    private static final String PHONE_TEXT = "This terrorist's phone number is 410-678-2230, and his best buddy's phone number is +44 (0)207 437 0478";
    private static final String PHONE_NEW_LINES = "This terrorist's phone\n number is 410-678-2230, and his best buddy's phone number\n is +44 (0)207 437 0478";
    private static final String PHONE_MISSING = "This is a sentence without any phone numbers in it.";

    @Before
    public void setup() {
        addConceptWithIntent(CONCEPT_IRI, PHONE_NUMBER_CONCEPT_INTENT);
    }

    @Override
    public GraphPropertyWorker getGpw() throws Exception {
        return new PhoneNumberGraphPropertyWorker();
    }

    @Test
    public void testPhoneNumberExtraction() throws Exception {
        doExtractionTest(PHONE_TEXT, Arrays.asList(
                new ExpectedTermMention("+14106782230", 33L, 45L),
                new ExpectedTermMention("+442074370478", 84L, 103L)
        ));
    }

    @Test
    public void testPhoneNumberExtractionWithNewlines() throws Exception {
        doExtractionTest(PHONE_NEW_LINES, Arrays.asList(
                new ExpectedTermMention("+14106782230", 34L, 46L),
                new ExpectedTermMention("+442074370478", 86L, 105L)
        ));
    }

    @Test
    public void testNegativePhoneNumberExtraction() throws Exception {
        doExtractionTest(PHONE_MISSING, null);
    }
}
