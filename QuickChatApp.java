package za.co.quickchat;

import java.io.PrintStream;
import java.util.Scanner;

public class QuickChatApp {

    private static final int MENU_SEND = 1;
    private static final int MENU_RECENT = 2;
    private static final int MENU_QUIT = 3;
    private static final int LOGIN_ATTEMPT_CEILING = 3;

    private final Scanner keyboardFeed;
    private final PrintStream screenOut;
    private int quotaOfMessages;
    private int messagesEntered;

    public QuickChatApp(Scanner keyboardFeed, PrintStream screenOut) {
        this.keyboardFeed = keyboardFeed;
        this.screenOut = screenOut;
    }

    public static void main(String[] launchArgs) {
        new QuickChatApp(new Scanner(System.in), System.out).run();
    }

    public void run() {
        try {
            Login gatekeeper = new Login();
            enrolUser(gatekeeper);
            if (!authenticate(gatekeeper)) {
                screenOut.println("Too many failed login attempts. Exiting QuickChat.");
                return;
            }
            screenOut.println("Welcome to QuickChat.");
            menuLoop();
        } catch (IllegalStateException inputClosed) {
            screenOut.println();
            screenOut.println("Input closed. Exiting QuickChat.");
        }
    }

    // registration / login

    private void enrolUser(Login gatekeeper) {
        screenOut.println("=== Register ===");
        String firstName = askLine("First name: ");
        String lastName = askLine("Last name: ");

        String handle = askLine("Username (underscore, max 5 characters): ");
        while (!gatekeeper.checkUserName(handle)) {
            screenOut.println("Username is not correctly formatted, please ensure that your username "
                    + "contains an underscore and is no more than five characters in length.");
            handle = askLine("Username (underscore, max 5 characters): ");
        }

        String secret = askLine("Password (8+ chars, capital, number, special character): ");
        while (!gatekeeper.checkPasswordComplexity(secret)) {
            screenOut.println("Password is not correctly formatted, please ensure that the password "
                    + "contains at least eight characters, a capital letter, a number, and a special character.");
            secret = askLine("Password (8+ chars, capital, number, special character): ");
        }

        String cell = askLine("Cell number (e.g. +27831234567): ");
        while (!gatekeeper.checkCellPhoneNumber(cell)) {
            screenOut.println("Cell number incorrectly formatted or does not contain international code, "
                    + "please correct the number and try again.");
            cell = askLine("Cell number (e.g. +27831234567): ");
        }

        screenOut.println(gatekeeper.registerUser(handle, secret, cell, firstName, lastName));
    }

    private boolean authenticate(Login gatekeeper) {
        screenOut.println("=== Login ===");
        for (int attempt = 1; attempt <= LOGIN_ATTEMPT_CEILING; attempt++) {
            String handle = askLine("Username: ");
            String secret = askLine("Password: ");
            boolean attemptPassed = gatekeeper.loginUser(handle, secret);
            screenOut.println(gatekeeper.returnLoginStatus());
            if (attemptPassed) {
                return true;
            }
        }
        return false;
    }

    // menu

    private void menuLoop() {
        boolean keepRunning = true;
        while (keepRunning) {
            screenOut.println();
            screenOut.println("1) Send Messages");
            screenOut.println("2) Show recently sent messages");
            screenOut.println("3) Quit");
            int menuPick = askWholeNumber("Choose an option: ", 1, 3);
          
             switch (menuPick) {
                case MENU_SEND:
                    quotaOfMessages = askWholeNumber("How many messages do you wish to enter? ", 1, Integer.MAX_VALUE);
                 break;
                case MENU_RECENT:
                    screenOut.println("Coming Soon.");
                    break;
                case MENU_QUIT:
                    screenOut.println("Goodbye.");
                    keepRunning = false;
                    break;
                default:
                    break;
            }
        }
    }

    private void sendBatch() {
        if (messagesEntered >= quotaOfMessages) {
            screenOut.println("You have already entered all " + quotaOfMessages + " message(s).");
            return;
        }
        while (messagesEntered < quotaOfMessages) {
            screenOut.println();
            screenOut.println("--- Message " + (messagesEntered + 1) + " of " + quotaOfMessages + " ---");
            composeOneMessage();
            messagesEntered++;
        }
        screenOut.println();
        screenOut.println("Total number of messages sent: " + Message.returnTotalMessages());
    }

    private void composeOneMessage() {
        String recipient = askLine("Recipient cell number (with international code, e.g. +27718693002): ");
        while (!Message.isRecipientFormatted(recipient)) {
            screenOut.println(Message.MSG_CELL_BAD);
            recipient = askLine("Recipient cell number (with international code, e.g. +27718693002): ");
        }
        screenOut.println(Message.MSG_CELL_GOOD);

        String body = askLine("Enter your message (max 250 characters): ");
        while (body.isBlank() || !Message.fitsBodyLimit(body)) {
            if (body.isBlank()) {
                screenOut.println("A message cannot be empty.");
            } else {
                screenOut.println("Please enter a message of less than 250 characters.");
            }
            body = askLine("Enter your message (max 250 characters): ");
        }
        screenOut.println("Message sent");

        Message draftMessage = new Message(recipient, body);
        screenOut.println(draftMessage.announceMessageID());

        boolean settled = false;
        while (!settled) {
            screenOut.println("1) Send Message");
            screenOut.println("2) Disregard Message");
            screenOut.println("3) Store Message to send later");
            int dispatchPick = askWholeNumber("Choose an option: ", 1, 3);

            String dispatchOutcome = draftMessage.sentMessage(dispatchPick);
            screenOut.println(dispatchOutcome);

            if (dispatchPick == Message.CHOICE_SEND) {
                screenOut.println();
                screenOut.println(draftMessage.describeMessage());
                settled = true;
            } else if (dispatchPick == Message.CHOICE_DISREGARD) {
                String deletePress = askLine("");
                if (deletePress.trim().equals("0")) {
                    screenOut.println("Message deleted.");
                    settled = true;
                } else {
                    screenOut.println("Message not deleted.");
                }
            } else if (dispatchOutcome.equals(Message.MSG_STORED_OK)) {
                settled = true;
            }
        }
    }

    // ------------------------------------------------------------ input helpers

    private String askLine(String promptText) {
        screenOut.print(promptText);
        if (!keyboardFeed.hasNextLine()) {
            throw new IllegalStateException("Input closed");
        }
        return keyboardFeed.nextLine();
    }

    private int askWholeNumber(String promptText, int lowest, int highest) {
        while (true) {
            String typed = askLine(promptText).trim();
            try {
                int parsed = Integer.parseInt(typed);
                if (parsed >= lowest && parsed <= highest) {
                    return parsed;
                }
            } catch (NumberFormatException notANumber) {
                // fall through to the reminder below
            }
            if (highest == Integer.MAX_VALUE) {
                screenOut.println("Please enter a whole number of " + lowest + " or more.");
            } else {
                screenOut.println("Please enter a whole number from " + lowest + " to " + highest + ".");
            }
        }
    }
}
