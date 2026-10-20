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
import java.util.regex.Pattern;

/**
 * One QuickChat message plus the session-wide bookkeeping the brief asks for
 * (messages sent while the program runs, the running total, JSON storage).
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

    // International code (1-3 digits, no leading zero) followed by at most ten digits.
    private static final Pattern RECIPIENT_SHAPE = Pattern.compile("^\\+[1-9]\\d{0,2}\\d{1,10}$");
    private static final Pattern WORD_EDGE_JUNK =
            Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final SecureRandom TRACKING_DICE = new SecureRandom();

    private static final List<Message> DISPATCH_LEDGER = new ArrayList<>();
    private static Path vaultPath = Paths.get("storedMessages.json");

    private final String trackingCode;
    private final int sequenceSlot;
    private final String recipientCell;
    private final String bodyText;
    private final String hashDigest;

    /** Normal constructor: random ten-digit ID, sequence = messages already sent. */
    public Message(String recipientCell, String bodyText) {
        this(generateTrackingCode(), DISPATCH_LEDGER.size(), recipientCell, bodyText);
    }

    /** Explicit constructor so unit tests can supply a fixed ID and sequence number. */
    public Message(String trackingCode, int sequenceSlot, String recipientCell, String bodyText) {
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
                return MSG_SENT_OK;
            case CHOICE_DISREGARD:
                return MSG_DISREGARD_PROMPT;
            case CHOICE_STORE:
                try {
                    storeMessage();
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

        Path parentFolder = vaultPath.toAbsolutePath().getParent();
        if (parentFolder != null) {
            Files.createDirectories(parentFolder);
        }
        Files.writeString(vaultPath, mergedContent, StandardCharsets.UTF_8);
    }

    private String toJsonObject() {
        return "  {\n"
                + "    \"messageId\": \"" + escapeJson(trackingCode) + "\",\n"
                + "    \"messageHash\": \"" + escapeJson(hashDigest) + "\",\n"
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

    // ---------------------------------------------------------------- reporting

    /** Message ID, Message Hash, Recipient, Message - in the order the brief requires. */
    public String describeMessage() {
        return "Message ID: " + trackingCode + "\n"
                + "Message Hash: " + hashDigest + "\n"
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

    // ---------------------------------------------------------------- test hooks

    /** Clears the sent-message ledger (used between unit tests). */
    public static void resetSession() {
        DISPATCH_LEDGER.clear();
    }

    /** Points JSON storage at another file (used by unit tests). */
    public static void relocateVault(Path newVaultPath) {
        vaultPath = newVaultPath;
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
