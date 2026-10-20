package za.co.quickchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageTest {

    private static final String FIXED_ID = "0012345678";

    // Test data for message 1 (Send) and message 2 (Disregard)
    private static final String FIRST_RECIPIENT = "+27718693002";
    private static final String FIRST_BODY = "Hi Mike, can you join us for dinner tonight?";
    private static final String SECOND_RECIPIENT = "08575975889";
    private static final String SECOND_BODY = "Hi Keegan, did you receive the payment?";

    @TempDir
    Path scratchFolder;

    private Path vaultFile;

    @BeforeEach
    void freshSession() {
        Message.resetSession();
        vaultFile = scratchFolder.resolve("storedMessages.json");
        Message.relocateVault(vaultFile);
    }

    // ---------------------------------------------------------- message length

    @Test
    void messageUnder250CharactersIsReadyToSend() {
        assertEquals("Message ready to send.", Message.checkMessageLength(FIRST_BODY));
    }

    @Test
    void messageOfExactly250CharactersIsStillAccepted() {
        assertEquals("Message ready to send.", Message.checkMessageLength("a".repeat(250)));
    }

    @Test
    void messageOver250CharactersReportsTheOvershoot() {
        assertEquals("Message exceeds 250 characters by 10; please reduce the size.",
                Message.checkMessageLength("a".repeat(260)));
    }

    // ---------------------------------------------------------- recipient number

    @Test
    void recipientWithInternationalCodeIsCaptured() {
        Message firstMessage = new Message(FIXED_ID, 0, FIRST_RECIPIENT, FIRST_BODY);
        assertEquals("Cell phone number successfully captured.", firstMessage.checkRecipientCell());
    }

    @Test
    void recipientWithoutInternationalCodeIsRejected() {
        Message secondMessage = new Message(FIXED_ID, 1, SECOND_RECIPIENT, SECOND_BODY);
        assertEquals("Cell phone number is incorrectly formatted or does not contain an "
                + "international code. Please correct the number and try again.",
                secondMessage.checkRecipientCell());
    }

    // ---------------------------------------------------------- message hash

    @Test
    void hashMatchesTestCaseOne() {
        Message firstMessage = new Message(FIXED_ID, 0, FIRST_RECIPIENT, FIRST_BODY);
        assertEquals("00:0:HITONIGHT", firstMessage.createMessageHash());
    }

    @Test
    void remainingHashesAreCheckedInALoop() {
        String[][] hashCases = {
            {FIXED_ID, "0", FIRST_BODY, "00:0:HITONIGHT"},
            {FIXED_ID, "1", SECOND_BODY, "00:1:HIPAYMENT"},
            {"1234567890", "2", "Okay!", "12:2:OKAYOKAY"},
            {"9876543210", "3", "  Let's meet at noon...  ", "98:3:LET'SNOON"},
            {"5555555555", "4", "Thanks", "55:4:THANKSTHANKS"}
        };
        for (String[] hashCase : hashCases) {
            Message probe = new Message(hashCase[0], Integer.parseInt(hashCase[1]), FIRST_RECIPIENT, hashCase[2]);
            assertEquals(hashCase[3], probe.createMessageHash());
            assertEquals(hashCase[3], probe.getHashDigest());
        }
    }

    // ---------------------------------------------------------- message ID

    @Test
    void messageIdIsGeneratedAsTenDigits() {
        Message freshMessage = new Message(FIRST_RECIPIENT, FIRST_BODY);
        String generatedId = freshMessage.getTrackingCode();
        assertEquals(10, generatedId.length());
        assertTrue(generatedId.matches("\\d{10}"));
        assertTrue(freshMessage.checkMessageID());
        assertEquals("Message ID generated: " + generatedId, freshMessage.announceMessageID());
    }

    @Test
    void messageIdLongerThanTenCharactersFailsTheCheck() {
        Message oversizedIdMessage = new Message("12345678901", 0, FIRST_RECIPIENT, FIRST_BODY);
        assertFalse(oversizedIdMessage.checkMessageID());
    }

    // ---------------------------------------------------------- send / disregard / store

    @Test
    void sendChoiceReturnsSuccessText() {
        Message sendCandidate = new Message(FIXED_ID, 0, FIRST_RECIPIENT, FIRST_BODY);
        assertEquals("Message successfully sent.", sendCandidate.sentMessage(1));
    }

    @Test
    void disregardChoiceAsksForZeroToDelete() {
        Message disregardCandidate = new Message(FIXED_ID, 1, SECOND_RECIPIENT, SECOND_BODY);
        assertEquals("Press 0 to delete the message.", disregardCandidate.sentMessage(2));
    }

    @Test
    void storeChoiceReturnsSuccessTextAndWritesJson() throws IOException {
        Message storeCandidate = new Message(FIXED_ID, 0, FIRST_RECIPIENT, FIRST_BODY);
        assertEquals("Message successfully stored.", storeCandidate.sentMessage(3));
        assertTrue(Files.exists(vaultFile));
    }

    // ---------------------------------------------------------- totals

    @Test
    void totalCountsOnlyTheMessageThatWasSent() {
        Message firstMessage = new Message(FIRST_RECIPIENT, FIRST_BODY);
        assertEquals(0, firstMessage.getSequenceSlot());
        firstMessage.sentMessage(1);

        Message secondMessage = new Message(SECOND_RECIPIENT, SECOND_BODY);
        assertEquals(1, secondMessage.getSequenceSlot());
        secondMessage.sentMessage(2);

        assertEquals(1, Message.returnTotalMessages());
        String listing = Message.printMessages();
        assertTrue(listing.contains(FIRST_BODY));
        assertFalse(listing.contains(SECOND_BODY));
    }

    @Test
    void sendingTheSameMessageTwiceIsCountedOnce() {
        Message repeatedMessage = new Message(FIRST_RECIPIENT, FIRST_BODY);
        repeatedMessage.sentMessage(1);
        repeatedMessage.sentMessage(1);
        assertEquals(1, Message.returnTotalMessages());
    }

    @Test
    void printMessagesListsDetailsInTheRequiredOrder() {
        Message firstMessage = new Message(FIXED_ID, 0, FIRST_RECIPIENT, FIRST_BODY);
        firstMessage.sentMessage(1);
        assertEquals("Message ID: 0012345678\n"
                + "Message Hash: 00:0:HITONIGHT\n"
                + "Recipient: +27718693002\n"
                + "Message: Hi Mike, can you join us for dinner tonight?",
                Message.printMessages());
    }

    // ---------------------------------------------------------- JSON storage

    @Test
    void storedMessagesAccumulateInOneJsonArray() throws IOException {
        new Message(FIXED_ID, 0, FIRST_RECIPIENT, FIRST_BODY).storeMessage();
        new Message("1122334455", 1, FIRST_RECIPIENT, "Say \"hello\"\nto Sam").storeMessage();

        String fileText = Files.readString(vaultFile, StandardCharsets.UTF_8);
        assertTrue(fileText.startsWith("["));
        assertTrue(fileText.endsWith("]"));
        assertTrue(fileText.contains("\"messageHash\": \"00:0:HITONIGHT\""));
        assertTrue(fileText.contains("\"message\": \"Say \\\"hello\\\"\\nto Sam\""));
        assertEquals(2, fileText.split("\"messageId\"", -1).length - 1);
    }
}
