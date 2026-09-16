import java.util.regex.Pattern;

/**
 * Login class for PROG5121 POE — Part 1: Registration and Login feature.
 *
 * Handles user registration (username, password, South African cell phone
 * number) and login authentication, following the validation rules and
 * output messages specified in the assignment brief.
 *
 * South African cell number validation regex adapted from common
 * international-format phone validation patterns.
 * Reference: Regular-Expressions.info, "Validating Phone Numbers",
 * https://www.regular-expressions.info/
 */
public class Login {

    // Stored registration/login details
    private String username;
    private String password;
    private String cellPhoneNumber;
    private String firstName;
    private String lastName;

    // South African international format: +27 followed by 9 digits
    private static final Pattern SA_CELL_PATTERN = Pattern.compile("^\\+27\\d{9}$");

    public Login() {
    }

    public Login(String username, String password, String cellPhoneNumber,
                 String firstName, String lastName) {
        this.username = username;
        this.password = password;
        this.cellPhoneNumber = cellPhoneNumber;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // ---------- Validation methods ----------

    /**
     * Username must contain an underscore and be no more than 5 characters long.
     */
    public boolean checkUserName(String username) {
        if (username == null) {
            return false;
        }
        return username.contains("_") && username.length() <= 5;
    }

    /**
     * Password must be at least 8 characters, and contain a capital letter,
     * a number, and a special character.
     */
    public boolean checkPasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasCapital = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasCapital && hasDigit && hasSpecial;
    }

    /**
     * Cell phone number must contain the South African international
     * country code (+27) followed by the subscriber number.
     */
    public boolean checkCellPhoneNumber(String cellPhoneNumber) {
        if (cellPhoneNumber == null) {
            return false;
        }
        return SA_CELL_PATTERN.matcher(cellPhoneNumber).matches();
    }

    // ---------- Registration ----------

    /**
     * Registers a user, validating username, password, and cell phone
     * number, and returns the appropriate message.
     */
    public String registerUser(String username, String password, String cellPhoneNumber,
                                String firstName, String lastName) {

        if (!checkUserName(username)) {
            return "Username is not correctly formatted; please ensure that your "
                    + "username contains an underscore and is no more than five "
                    + "characters in length.";
        }

        if (!checkPasswordComplexity(password)) {
            return "Password is not correctly formatted; please ensure that the "
                    + "password contains at least eight characters, a capital letter, "
                    + "a number, and a special character.";
        }

        if (!checkCellPhoneNumber(cellPhoneNumber)) {
            return "Cell number is incorrectly formatted or does not contain an "
                    + "international code; please correct the number and try again.";
        }

        // All checks passed — store the registration details
        this.username = username;
        this.password = password;
        this.cellPhoneNumber = cellPhoneNumber;
        this.firstName = firstName;
        this.lastName = lastName;

        return "Username successfully captured.";
    }

    // ---------- Login ----------

    /**
     * Verifies that the entered username and password match the stored
     * registration details.
     */
    public boolean loginUser(String enteredUsername, String enteredPassword) {
        if (username == null || password == null) {
            return false;
        }
        return username.equals(enteredUsername) && password.equals(enteredPassword);
    }

    /**
     * Returns the appropriate message based on login success/failure.
     */
    public String returnLoginStatus(boolean loginSuccessful) {
        if (loginSuccessful) {
            return "Welcome " + firstName + ", " + lastName + " it is great to see you again.";
        }
        return "Username or password incorrect, please try again.";
    }

    // ---------- Getters (useful for testing/other classes) ----------

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
