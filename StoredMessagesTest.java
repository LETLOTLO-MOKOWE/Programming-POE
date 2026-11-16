package za.co.quickchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoredMessagesTest {

    private static final String BODY_ONE = "Did you get the cake?";
    private static final String BODY_TWO = "Where are you? You are late! I have asked you to be on time.";
    private static final String BODY_THREE = "Yohoooo, I am at your gate.";
    private static final String BODY_FOUR = "It is dinner time!";
    private static final String BODY_FIVE = "Ok, I am leaving without you.";

        private static final String DEVELOPER_ENTRY = "0838884567";

    @TempDir
    Path scratchFolder;

    private Path vaultFile;
    private Message msgOne;
    private Message msgTwo;
    private Message msgThree;
    private Message msgFour;
    private Message msgFive;

    @BeforeEach
    void freshSession() {
        Message.resetSession();
        vaultFile = scratchFolder.resolve("storedMessages.json");
        Message.relocateVault(vaultFile);
    }

        private void loadTestMessages(boolean includeMessageFive) {
        msgOne = new Message("1000000001", 0, "+27834557896", BODY_ONE);
        msgOne.sentMessage(Message.CHOICE_SEND);

        msgTwo = new Message("2200000002", 1, "+27838884567", BODY_TWO);
        msgTwo.sentMessage(Message.CHOICE_STORE);

        msgThree = new Message("3300000003", 2, "+27834484567", BODY_THREE);
        msgThree.sentMessage(Message.CHOICE_DISREGARD);

        msgFour = new Message(DEVELOPER_ENTRY, 3, DEVELOPER_ENTRY, BODY_FOUR);
        msgFour.sentMessage(Message.CHOICE_SEND);

        if (includeMessageFive) {
            msgFive = new Message("5500000005", 4, "+27838884567", BODY_FIVE);
            msgFive.sentMessage(Message.CHOICE_STORE);
        }
    }

    // arrays populated

    @Test
    void sentMessagesArrayHoldsMessagesOneAndFour() {
        loadTestMessages(false);
        assertEquals(List.of(BODY_ONE, BODY_FOUR), Message.sentMessageTexts());
    }

    @Test
    void disregardedMessagesArrayHoldsMessageThree() {
        loadTestMessages(true);
        assertEquals(List.of(BODY_THREE), Message.disregardedMessageTexts());
    }

    @Test
    void storedMessagesArrayIsReadBackFromTheJsonFile() throws IOException {
        loadTestMessages(true);
        assertTrue(Files.exists(vaultFile));
        assertEquals(List.of(BODY_TWO, BODY_FIVE), Message.storedMessageTexts());
    }

    @Test
    void hashAndIdArraysHoldEveryMessage() {
        loadTestMessages(true);
        assertEquals(List.of("1000000001", "2200000002", "3300000003", DEVELOPER_ENTRY, "5500000005"),
                Message.messageIds());
        assertEquals(List.of(msgOne.createMessageHash(), msgTwo.createMessageHash(),
                msgThree.createMessageHash(), msgFour.createMessageHash(), msgFive.createMessageHash()),
                Message.messageHashes());
        assertEquals("10:0:DIDCAKE", Message.messageHashes().get(0));
    }

    @Test
    void storingTheSameMessageTwiceKeepsOneCopy() {
        loadTestMessages(false);
        msgTwo.sentMessage(Message.CHOICE_STORE);
        assertEquals(List.of(BODY_TWO), Message.storedMessageTexts());
    }

    // required unit tests

    @Test
    void displayTheLongestMessage() {
        loadTestMessages(false);
        assertEquals(BODY_TWO, Message.longestStoredMessage());
    }

    @Test
    void longestStoredMessageStaysTheSameWhenMessageFiveIsAdded() {
        loadTestMessages(true);
        assertEquals(BODY_TWO, Message.longestStoredMessage());
    }

    @Test
    void searchForMessageIdReturnsMessageFour() {
        loadTestMessages(false);
        assertEquals(BODY_FOUR, Message.findMessageById(DEVELOPER_ENTRY).orElseThrow().getBodyText());
        assertEquals("Recipient: 0838884567\nMessage: It is dinner time!",
                Message.searchByMessageId(DEVELOPER_ENTRY));
    }

    @Test
    void searchForUnknownMessageIdSaysSo() {
        loadTestMessages(false);
        assertEquals("No message found with that message ID.", Message.searchByMessageId("9999999999"));
    }

    @Test
    void searchForRecipientReturnsMessagesTwoAndFive() {
        loadTestMessages(true);
        assertEquals(List.of(BODY_TWO, BODY_FIVE), Message.messagesForRecipient("+27838884567"));
        assertEquals(List.of(BODY_TWO, BODY_FIVE), Message.storedMessagesForRecipient("+27838884567"));
        assertTrue(Message.messagesForRecipient("+27000000000").isEmpty());
    }

    @Test
    void deleteUsingTheMessageHashOfTestMessageTwo() throws IOException {
        loadTestMessages(true);
        String outcome = Message.deleteStoredMessageByHash(msgTwo.createMessageHash());

        assertEquals("Message: \"Where are you? You are late! I have asked you to be on time\""
                + " successfully deleted.", outcome);
        assertEquals(List.of(BODY_FIVE), Message.storedMessageTexts());

        String fileText = Files.readString(vaultFile, StandardCharsets.UTF_8);
        assertFalse(fileText.contains("late!"));
        assertTrue(fileText.contains(BODY_FIVE));
        assertFalse(Message.messageIds().contains("2200000002"));
    }

    @Test
    void deleteWithAnUnknownHashChangesNothing() {
        loadTestMessages(true);
        assertEquals("No stored message found with hash NOPE.", Message.deleteStoredMessageByHash("NOPE"));
        assertEquals(2, Message.storedMessages().size());
    }

    @Test
    void sentMessagesReportShowsHashRecipientAndMessage() {
        loadTestMessages(true);
        String report = Message.displaySentReport();

        assertTrue(report.startsWith("Sent Messages Report"));
        assertTrue(report.contains("Message Hash: " + msgOne.createMessageHash()));
        assertTrue(report.contains("Recipient: +27834557896"));
        assertTrue(report.contains("Message: " + BODY_ONE));
        assertTrue(report.contains("Message Hash: " + msgFour.createMessageHash()));
        assertTrue(report.contains("Message: " + BODY_FOUR));
        assertFalse(report.contains(BODY_TWO));
    }

    @Test
    void storedMessagesReportListsFullDetailsOfStoredMessagesOnly() {
        Message.setActiveSender("kg_01");
        loadTestMessages(true);
        String report = Message.displayStoredReport();

        assertTrue(report.startsWith("Stored Messages Report"));
        assertTrue(report.contains("Message ID: 2200000002"));
        assertTrue(report.contains("Message Hash: " + msgTwo.createMessageHash()));
        assertTrue(report.contains("Sender: kg_01"));
        assertTrue(report.contains("Recipient: +27838884567"));
        assertTrue(report.contains("Message: " + BODY_FIVE));
        assertFalse(report.contains(BODY_ONE));
    }

    @Test
    void senderAndRecipientAreListedForEveryStoredMessage() {
        Message.setActiveSender("kg_01");
        loadTestMessages(true);
        assertEquals(List.of("Sender: kg_01 | Recipient: +27838884567",
                "Sender: kg_01 | Recipient: +27838884567"), Message.storedSenderAndRecipientLines());
    }

    //  JSON round trip

    @Test
    void jsonRoundTripKeepsAwkwardCharacters() throws IOException {
        String awkwardBody = "Tab\there, \"quotes\", back\\slash, caf\u00e9, control" + (char) 1;
        new Message("kg_01", "1234567890", 7, "+27831234567", awkwardBody).storeMessage();

        Message.loadStoredMessages();

        Message restored = Message.storedMessages().get(0);
        assertEquals(awkwardBody, restored.getBodyText());
        assertEquals("kg_01", restored.getSenderHandle());
        assertEquals(7, restored.getSequenceSlot());
        assertEquals("12:7:TABCONTROL", restored.getHashDigest().replace("\u0001", ""));
    }

    @Test
    void missingJsonFileMeansNoStoredMessages() throws IOException {
        Message.loadStoredMessages();
        assertTrue(Message.storedMessages().isEmpty());
        assertEquals("There are no stored messages to report.", Message.displayStoredReport());
        assertEquals("No stored messages.", Message.longestStoredMessage());
    }

    @Test
    void malformedJsonFileIsReportedNotIgnored() throws IOException {
        Files.writeString(vaultFile, "this is not json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, Message::loadStoredMessages);
    }

    @Test
    void deletingTheLastStoredMessageLeavesAnEmptyJsonArray() throws IOException {
        Message onlyMessage = new Message("4400000004", 0, "+27831234567", "Just one");
        onlyMessage.sentMessage(Message.CHOICE_STORE);
        Message.deleteStoredMessageByHash(onlyMessage.createMessageHash());

        assertEquals("[]", Files.readString(vaultFile, StandardCharsets.UTF_8).trim());
        assertTrue(Message.storedMessages().isEmpty());
    }
}
