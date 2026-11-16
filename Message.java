package za.co.quickchat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One QuickChat message plus the session-wide bookkeeping the brief asks for:
 * the sent / disregarded / stored arrays, the hash and ID arrays, JSON storage,
 * searching, deleting and reporting (Parts 2 and 3).
 */
public final class Message {

    public static final int BODY_CHAR_CEILING = 250;
    public static final int TRACKING_CODE_DIGITS = 10;

    public static final int CHOICE_SEND = 1;
    public static final int CHOICE_DISREGARD = 2;
    public static final int CHOICE_STORE = 3;

    public static final String MSG_BODY_READY = "Message ready to send.";
    public static final String MSG_CELL_GOOD = "Cell phone number successfully captured.";
    public static final String MSG_CELL_BAD = "Cell phone number is incorrectly formatted or does not "
            + "contain an international code. Please correct the number and try again.";
    public static final String MSG_SENT_OK = "Message successfully sent.";
    public static final String MSG_DISREGARD_PROMPT = "Press 0 to delete the message.";
    public static final String MSG_STORED_OK = "Message successfully stored.";

    private static final String DEFAULT_SENDER = "Unknown sender";

    // International code (1-3 digits, no leading zero) followed by at most ten digits.
    private static final Pattern RECIPIENT_SHAPE = Pattern.compile("^\\+[1-9]\\d{0,2}\\d{1,10}$");
    private static final Pattern WORD_EDGE_JUNK =
            Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final SecureRandom TRACKING_DICE = new SecureRandom();

    // The five arrays required by Part 3 (all filled while the program runs, nothing hard-coded).
    private static final List<Message> DISPATCH_LEDGER = new ArrayList<>();   // Sent Messages
    private static final List<Message> DISREGARD_LEDGER = new ArrayList<>();  // Disregarded Messages
    private static final List<Message> STORED_LEDGER = new ArrayList<>();     // Stored Messages (read from JSON)
    private static final List<String> HASH_REGISTER = new ArrayList<>();      // Message Hash
    private static final List<String> ID_REGISTER = new ArrayList<>();        // Message ID

    private static Path vaultPath = Paths.get("storedMessages.json");
    private static String activeSender = DEFAULT_SENDER;

    private final String senderHandle;
    private final String trackingCode;
    private final int sequenceSlot;
    private final String recipientCell;
    private final String bodyText;
    private final String hashDigest;

    /** Normal constructor: random ten-digit ID, sequence = messages already sent. */
    public Message(String recipientCell, String bodyText) {
        this(activeSender, generateTrackingCode(), DISPATCH_LEDGER.size(), recipientCell, bodyText);
    }

    /** Explicit constructor so unit tests can supply a fixed ID and sequence number. */
    public Message(String trackingCode, int sequenceSlot, String recipientCell, String bodyText) {
        this(activeSender, trackingCode, sequenceSlot, recipientCell, bodyText);
    }

    /** Full constructor (also used when messages are read back from the JSON file). */
    public Message(String senderHandle, String trackingCode, int sequenceSlot,
                   String recipientCell, String bodyText) {
        this.senderHandle = senderHandle;
        this.trackingCode = trackingCode;
        this.sequenceSlot = sequenceSlot;
        this.recipientCell = recipientCell;
        this.bodyText = bodyText;
        this.hashDigest = createMessageHash();
    }

    // ---------------------------------------------------------------- validation

    /** Message ID must be present and no more than ten characters. */
    public boolean checkMessageID() {
        return trackingCode != null
                && !trackingCode.isEmpty()
                && trackingCode.length() <= TRACKING_CODE_DIGITS;
    }

    public static boolean isRecipientFormatted(String candidateCell) {
        return candidateCell != null && RECIPIENT_SHAPE.matcher(candidateCell).matches();
    }

    /** Success or failure text for this message's recipient number. */
    public String checkRecipientCell() {
        return isRecipientFormatted(recipientCell) ? MSG_CELL_GOOD : MSG_CELL_BAD;
    }

    public static boolean fitsBodyLimit(String candidateBody) {
        return candidateBody != null && candidateBody.length() <= BODY_CHAR_CEILING;
    }

    /** Success or failure text for a message body (used by the unit tests). */
    public static String checkMessageLength(String candidateBody) {
        int overshoot = candidateBody.length() - BODY_CHAR_CEILING;
        if (overshoot > 0) {
            return "Message exceeds " + BODY_CHAR_CEILING + " characters by " + overshoot
                    + "; please reduce the size.";
        }
        return MSG_BODY_READY;
    }

    // ---------------------------------------------------------------- generation

    /** Random ten-digit number held as text (a leading zero is allowed). */
    public static String generateTrackingCode() {
        StringBuilder digitsBuilder = new StringBuilder(TRACKING_CODE_DIGITS);
        for (int position = 0; position < TRACKING_CODE_DIGITS; position++) {
            digitsBuilder.append(TRACKING_DICE.nextInt(10));
        }
        return digitsBuilder.toString();
    }

    public String announceMessageID() {
        return "Message ID generated: " + trackingCode;
    }

    /**
     * First two digits of the ID, a colon, the message number, a colon, then the
     * first and last words of the message, all in capitals. e.g. 00:0:HITONIGHT
     */
    public String createMessageHash() {
        String idPrefix = trackingCode.length() >= 2 ? trackingCode.substring(0, 2) : trackingCode;

        String firstWord = "";
        String lastWord = "";
        String[] rawTokens = bodyText.trim().split("\\s+");
        for (String rawToken : rawTokens) {
            String cleanedToken = WORD_EDGE_JUNK.matcher(rawToken).replaceAll("");
            if (cleanedToken.isEmpty()) {
                continue;
            }
            if (firstWord.isEmpty()) {
                firstWord = cleanedToken;
            }
            lastWord = cleanedToken;
        }
        return (idPrefix + ":" + sequenceSlot + ":" + firstWord + lastWord).toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- send / store

    /** Applies the user's choice (1 send, 2 disregard, 3 store) and returns the status text. */
    public String sentMessage(int userChoice) {
        switch (userChoice) {
            case CHOICE_SEND:
                if (!DISPATCH_LEDGER.contains(this)) {
                    DISPATCH_LEDGER.add(this);
                }
                registerIdentity();
                return MSG_SENT_OK;
            case CHOICE_DISREGARD:
                if (!DISREGARD_LEDGER.contains(this)) {
                    DISREGARD_LEDGER.add(this);
                }
                registerIdentity();
                return MSG_DISREGARD_PROMPT;
            case CHOICE_STORE:
                try {
                    if (!isIdInStoredArray(trackingCode)) {
                        storeMessage();
                    }
                    loadStoredMessages();
                    registerIdentity();
                    return MSG_STORED_OK;
                } catch (IOException problem) {
                    return "Message could not be stored: " + problem.getMessage();
                }
            default:
                return "Invalid option. Please choose 1, 2 or 3.";
        }
    }

    /** Appends this message to the JSON array file (the file is created if missing). */
    public void storeMessage() throws IOException {
        String newEntry = toJsonObject();
        String priorContent = Files.exists(vaultPath)
                ? Files.readString(vaultPath, StandardCharsets.UTF_8).trim()
                : "";

        String mergedContent;
        if (priorContent.isEmpty() || priorContent.equals("[]")) {
            mergedContent = "[\n" + newEntry + "\n]";
        } else if (priorContent.startsWith("[") && priorContent.endsWith("]")) {
            String openPart = priorContent.substring(0, priorContent.length() - 1).stripTrailing();
            mergedContent = openPart + ",\n" + newEntry + "\n]";
        } else {
            throw new IOException("Existing file is not a JSON array: " + vaultPath);
        }
        writeVaultText(mergedContent);
    }

    private static void writeVaultText(String fullText) throws IOException {
        Path parentFolder = vaultPath.toAbsolutePath().getParent();
        if (parentFolder != null) {
            Files.createDirectories(parentFolder);
        }
        Files.writeString(vaultPath, fullText, StandardCharsets.UTF_8);
    }

    private static void rewriteVault(List<Message> survivors) throws IOException {
        if (survivors.isEmpty()) {
            writeVaultText("[]");
            return;
        }
        StringBuilder rebuilt = new StringBuilder("[\n");
        for (int index = 0; index < survivors.size(); index++) {
            if (index > 0) {
                rebuilt.append(",\n");
            }
            rebuilt.append(survivors.get(index).toJsonObject());
        }
        rebuilt.append("\n]");
        writeVaultText(rebuilt.toString());
    }

    private String toJsonObject() {
        return "  {\n"
                + "    \"messageId\": \"" + escapeJson(trackingCode) + "\",\n"
                + "    \"messageHash\": \"" + escapeJson(hashDigest) + "\",\n"
                + "    \"sender\": \"" + escapeJson(senderHandle) + "\",\n"
                + "    \"recipient\": \"" + escapeJson(recipientCell) + "\",\n"
                + "    \"message\": \"" + escapeJson(bodyText) + "\",\n"
                + "    \"messageNumber\": " + sequenceSlot + "\n"
                + "  }";
    }

    private static String escapeJson(String rawText) {
        StringBuilder escaped = new StringBuilder(rawText.length() + 8);
        for (char glyph : rawText.toCharArray()) {
            switch (glyph) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (glyph < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) glyph));
                    } else {
                        escaped.append(glyph);
                    }
            }
        }
        return escaped.toString();
    }

    // ---------------------------------------------------------------- arrays (Part 3)

    /** Notes this message's ID and hash in the Message ID / Message Hash arrays (once each). */
    private void registerIdentity() {
        if (!ID_REGISTER.contains(trackingCode)) {
            ID_REGISTER.add(trackingCode);
            HASH_REGISTER.add(hashDigest);
        }
    }

    private static void forgetIdentity(String idToForget) {
        int slot = ID_REGISTER.indexOf(idToForget);
        if (slot >= 0) {
            ID_REGISTER.remove(slot);
            HASH_REGISTER.remove(slot);
        }
    }

    private static boolean isIdInStoredArray(String wantedId) {
        for (Message candidate : STORED_LEDGER) {
            if (candidate.trackingCode.equals(wantedId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills the Stored Messages array by reading the JSON file. A missing file
     * simply means nothing has been stored yet.
     */
    public static void loadStoredMessages() throws IOException {
        List<Message> fromFile = readVault();
        STORED_LEDGER.clear();
        STORED_LEDGER.addAll(fromFile);
        for (Message restored : fromFile) {
            restored.registerIdentity();
        }
    }

    private static List<Message> readVault() throws IOException {
        List<Message> restoredMessages = new ArrayList<>();
        if (!Files.exists(vaultPath)) {
            return restoredMessages;
        }
        String rawJson = Files.readString(vaultPath, StandardCharsets.UTF_8);
        if (rawJson.isBlank()) {
            return restoredMessages;
        }
        for (Map<String, String> record : VaultJsonReader.parseArray(rawJson)) {
            String restoredId = requiredField(record, "messageId");
            String restoredRecipient = requiredField(record, "recipient");
            String restoredBody = requiredField(record, "message");
            String restoredSender = record.getOrDefault("sender", DEFAULT_SENDER);
            int restoredNumber;
            try {
                restoredNumber = Integer.parseInt(record.getOrDefault("messageNumber", "0"));
            } catch (NumberFormatException badNumber) {
                throw new IOException("Stored message has an invalid messageNumber", badNumber);
            }
            restoredMessages.add(new Message(restoredSender, restoredId, restoredNumber,
                    restoredRecipient, restoredBody));
        }
        return restoredMessages;
    }

    private static String requiredField(Map<String, String> record, String fieldName) throws IOException {
        String found = record.get(fieldName);
        if (found == null) {
            throw new IOException("Stored message is missing the field '" + fieldName + "'");
        }
        return found;
    }

    public static List<Message> sentMessages() {
        return List.copyOf(DISPATCH_LEDGER);
    }

    public static List<Message> disregardedMessages() {
        return List.copyOf(DISREGARD_LEDGER);
    }

    public static List<Message> storedMessages() {
        return List.copyOf(STORED_LEDGER);
    }

    public static List<String> messageHashes() {
        return List.copyOf(HASH_REGISTER);
    }

    public static List<String> messageIds() {
        return List.copyOf(ID_REGISTER);
    }

    public static List<String> sentMessageTexts() {
        return bodiesOf(DISPATCH_LEDGER);
    }

    public static List<String> disregardedMessageTexts() {
        return bodiesOf(DISREGARD_LEDGER);
    }

    public static List<String> storedMessageTexts() {
        return bodiesOf(STORED_LEDGER);
    }

    private static List<String> bodiesOf(List<Message> source) {
        List<String> bodies = new ArrayList<>();
        for (Message entry : source) {
            bodies.add(entry.bodyText);
        }
        return bodies;
    }

    // ---------------------------------------------------------------- stored-message features (Part 3)

    /** 2a - sender and recipient of every stored message. */
    public static List<String> storedSenderAndRecipientLines() {
        List<String> lines = new ArrayList<>();
        for (Message entry : STORED_LEDGER) {
            lines.add("Sender: " + entry.senderHandle + " | Recipient: " + entry.recipientCell);
        }
        return lines;
    }

    /** 2b - the longest stored message (the first one if two are equally long). */
    public static String longestStoredMessage() {
        if (STORED_LEDGER.isEmpty()) {
            return "No stored messages.";
        }
        Message longest = STORED_LEDGER.get(0);
        for (Message entry : STORED_LEDGER) {
            if (entry.bodyText.length() > longest.bodyText.length()) {
                longest = entry;
            }
        }
        return longest.bodyText;
    }

    /** Looks for a message ID in the sent, disregarded and stored arrays. */
    public static Optional<Message> findMessageById(String wantedId) {
        String cleanedId = wantedId == null ? "" : wantedId.trim();
        for (List<Message> source : List.of(DISPATCH_LEDGER, DISREGARD_LEDGER, STORED_LEDGER)) {
            for (Message entry : source) {
                if (entry.trackingCode.equals(cleanedId)) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /** 2c - recipient and message for a message ID. */
    public static String searchByMessageId(String wantedId) {
        Optional<Message> hit = findMessageById(wantedId);
        if (hit.isEmpty()) {
            return "No message found with that message ID.";
        }
        return "Recipient: " + hit.get().recipientCell + "\n" + "Message: " + hit.get().bodyText;
    }

    /** 2d - texts of every stored message for one recipient. */
    public static List<String> storedMessagesForRecipient(String wantedCell) {
        return bodiesForRecipient(wantedCell, List.of(STORED_LEDGER));
    }

    /** Texts of every sent or stored message for one recipient (sent first, then stored). */
    public static List<String> messagesForRecipient(String wantedCell) {
        return bodiesForRecipient(wantedCell, List.of(DISPATCH_LEDGER, STORED_LEDGER));
    }

    private static List<String> bodiesForRecipient(String wantedCell, List<List<Message>> sources) {
        String cleanedCell = wantedCell == null ? "" : wantedCell.trim();
        List<String> matches = new ArrayList<>();
        for (List<Message> source : sources) {
            for (Message entry : source) {
                if (entry.recipientCell.equals(cleanedCell)) {
                    matches.add(entry.bodyText);
                }
            }
        }
        return matches;
    }

    /** 2e - removes a stored message (array and JSON file) using its message hash. */
    public static String deleteStoredMessageByHash(String wantedHash) {
        String cleanedHash = wantedHash == null ? "" : wantedHash.trim();
        for (int slot = 0; slot < STORED_LEDGER.size(); slot++) {
            Message candidate = STORED_LEDGER.get(slot);
            if (!candidate.hashDigest.equalsIgnoreCase(cleanedHash)) {
                continue;
            }
            List<Message> survivors = new ArrayList<>(STORED_LEDGER);
            survivors.remove(slot);
            try {
                rewriteVault(survivors);
            } catch (IOException problem) {
                return "Message could not be deleted: " + problem.getMessage();
            }
            STORED_LEDGER.remove(slot);
            forgetIdentity(candidate.trackingCode);
            String shownBody = candidate.bodyText.endsWith(".")
                    ? candidate.bodyText.substring(0, candidate.bodyText.length() - 1)
                    : candidate.bodyText;
            return "Message: \"" + shownBody + "\" successfully deleted.";
        }
        return "No stored message found with hash " + cleanedHash + ".";
    }

    /** Report listing every message sent while the program has been running. */
    public static String displaySentReport() {
        return buildReport("Sent Messages Report", DISPATCH_LEDGER, "There are no sent messages to report.");
    }

    /** 2f - report listing the full details of every stored message. */
    public static String displayStoredReport() {
        return buildReport("Stored Messages Report", STORED_LEDGER, "There are no stored messages to report.");
    }

    private static String buildReport(String reportTitle, List<Message> source, String emptyNote) {
        if (source.isEmpty()) {
            return emptyNote;
        }
        StringBuilder report = new StringBuilder(reportTitle);
        for (Message entry : source) {
            report.append("\n\n").append(entry.describeFullDetails());
        }
        return report.toString();
    }

    // ---------------------------------------------------------------- reporting

    /** Message ID, Message Hash, Recipient, Message - in the order the brief requires. */
    public String describeMessage() {
        return "Message ID: " + trackingCode + "\n"
                + "Message Hash: " + hashDigest + "\n"
                + "Recipient: " + recipientCell + "\n"
                + "Message: " + bodyText;
    }

    /** Message ID, Message Hash, Sender, Recipient, Message (used by the reports). */
    public String describeFullDetails() {
        return "Message ID: " + trackingCode + "\n"
                + "Message Hash: " + hashDigest + "\n"
                + "Sender: " + senderHandle + "\n"
                + "Recipient: " + recipientCell + "\n"
                + "Message: " + bodyText;
    }

    /** Every message sent while the program has been running. */
    public static String printMessages() {
        if (DISPATCH_LEDGER.isEmpty()) {
            return "No messages have been sent yet.";
        }
        StringBuilder listing = new StringBuilder();
        for (int index = 0; index < DISPATCH_LEDGER.size(); index++) {
            if (index > 0) {
                listing.append("\n\n");
            }
            listing.append(DISPATCH_LEDGER.get(index).describeMessage());
        }
        return listing.toString();
    }

    public static int returnTotalMessages() {
        return DISPATCH_LEDGER.size();
    }

    // ---------------------------------------------------------------- session hooks

    /** Records who is logged in, so new messages carry that sender. */
    public static void setActiveSender(String signedInHandle) {
        activeSender = (signedInHandle == null || signedInHandle.isBlank()) ? DEFAULT_SENDER : signedInHandle;
    }

    /** Clears every array and the sender (used between unit tests). */
    public static void resetSession() {
        DISPATCH_LEDGER.clear();
        DISREGARD_LEDGER.clear();
        STORED_LEDGER.clear();
        HASH_REGISTER.clear();
        ID_REGISTER.clear();
        activeSender = DEFAULT_SENDER;
    }

    /** Points JSON storage at another file (used by unit tests). */
    public static void relocateVault(Path newVaultPath) {
        vaultPath = newVaultPath;
    }

    public String getSenderHandle() {
        return senderHandle;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public int getSequenceSlot() {
        return sequenceSlot;
    }

    public String getRecipientCell() {
        return recipientCell;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getHashDigest() {
        return hashDigest;
    }
}
