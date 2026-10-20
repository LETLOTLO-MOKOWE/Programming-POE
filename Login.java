package za.co.quickchat;

import java.util.regex.Pattern;

public class Login {

    private static final Pattern SA_CELL_SHAPE = Pattern.compile("^\\+27\\d{9}$");

    private String enrolledHandle;
    private String enrolledSecret;
    private String enrolledCell;
    private String givenName;
    private String familyName;
    private boolean enrolmentComplete;
    private boolean latestAttemptPassed;

    public boolean checkUserName(String candidateHandle) {
        return candidateHandle != null
                && candidateHandle.length() <= 5
                && candidateHandle.contains("_");
    }

    public boolean checkPasswordComplexity(String candidateSecret) {
        if (candidateSecret == null || candidateSecret.length() < 8) {
            return false;
        }
        boolean sawUpper = false;
        boolean sawDigit = false;
        boolean sawSymbol = false;
        for (char glyph : candidateSecret.toCharArray()) {
            if (Character.isUpperCase(glyph)) {
                sawUpper = true;
            } else if (Character.isDigit(glyph)) {
                sawDigit = true;
            } else if (!Character.isLetter(glyph)) {
                sawSymbol = true;
            }
        }
        return sawUpper && sawDigit && sawSymbol;
    }

    public boolean checkCellPhoneNumber(String candidateCell) {
        return candidateCell != null && SA_CELL_SHAPE.matcher(candidateCell).matches();
    }

    public String registerUser(String handle, String secret, String cell,
                               String firstName, String lastName) {
        StringBuilder outcome = new StringBuilder();
        boolean handleOk = checkUserName(handle);
        boolean secretOk = checkPasswordComplexity(secret);
        boolean cellOk = checkCellPhoneNumber(cell);

        outcome.append(handleOk
                ? "Username successfully captured."
                : "Username is not correctly formatted, please ensure that your username "
                + "contains an underscore and is no more than five characters in length.");
        outcome.append('\n');
        outcome.append(secretOk
                ? "Password successfully captured."
                : "Password is not correctly formatted, please ensure that the password "
                + "contains at least eight characters, a capital letter, a number, and a special character.");
        outcome.append('\n');
        outcome.append(cellOk
                ? "Cell number successfully added."
                : "Cell number incorrectly formatted or does not contain international code, "
                + "please correct the number and try again.");

        enrolmentComplete = handleOk && secretOk && cellOk;
        if (enrolmentComplete) {
            enrolledHandle = handle;
            enrolledSecret = secret;
            enrolledCell = cell;
            givenName = firstName;
            familyName = lastName;
        }
        return outcome.toString();
    }

    public boolean loginUser(String handle, String secret) {
        latestAttemptPassed = enrolmentComplete
                && enrolledHandle.equals(handle)
                && enrolledSecret.equals(secret);
        return latestAttemptPassed;
    }

    public String returnLoginStatus() {
        if (latestAttemptPassed) {
            return "Welcome " + givenName + ", " + familyName + " it is great to see you again.";
        }
        return "Username or password incorrect, please try again.";
    }

    public boolean isEnrolmentComplete() {
        return enrolmentComplete;
    }

    public String getEnrolledCell() {
        return enrolledCell;
    }
}
