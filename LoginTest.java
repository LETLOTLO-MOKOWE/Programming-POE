import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Login class, using the test data specified in the
 * PROG5121POE Part 1 assignment brief.
 */
public class LoginTest {

    private Login login;

    @BeforeEach
    public void setUp() {
        login = new Login();
    }

    // ---------- checkUserName ----------

    @Test
    public void testUsernameCorrectlyFormatted() {
        // Test Data: "kyl_1"
        assertTrue(login.checkUserName("kyl_1"));
    }

    @Test
    public void testUsernameIncorrectlyFormatted() {
        // Test Data: "kyle!!!!!!"
        assertFalse(login.checkUserName("kyle!!!!!!"));
    }

    // ---------- checkPasswordComplexity ----------

    @Test
    public void testPasswordMeetsComplexity() {
        // Test Data: "Ch&&sec@ke99!"
        assertTrue(login.checkPasswordComplexity("Ch&&sec@ke99!"));
    }

    @Test
    public void testPasswordDoesNotMeetComplexity() {
        // Test Data: "password"
        assertFalse(login.checkPasswordComplexity("password"));
    }

    // ---------- checkCellPhoneNumber ----------

    @Test
    public void testCellPhoneCorrectlyFormatted() {
        // Test Data: +27838968976
        assertTrue(login.checkCellPhoneNumber("+27838968976"));
    }

    @Test
    public void testCellPhoneIncorrectlyFormatted() {
        // Test Data: 08966553
        assertFalse(login.checkCellPhoneNumber("08966553"));
    }

    // ---------- registerUser messages ----------

    @Test
    public void testRegisterUserUsernameFailureMessage() {
        String result = login.registerUser("kyle!!!!!!", "Ch&&sec@ke99!",
                "+27838968976", "Kyle", "Test");
        assertEquals("Username is not correctly formatted; please ensure that your "
                + "username contains an underscore and is no more than five "
                + "characters in length.", result);
    }

    @Test
    public void testRegisterUserPasswordFailureMessage() {
        String result = login.registerUser("kyl_1", "password",
                "+27838968976", "Kyle", "Test");
        assertEquals("Password is not correctly formatted; please ensure that the "
                + "password contains at least eight characters, a capital letter, "
                + "a number, and a special character.", result);
    }

    @Test
    public void testRegisterUserCellFailureMessage() {
        String result = login.registerUser("kyl_1", "Ch&&sec@ke99!",
                "08966553", "Kyle", "Test");
        assertEquals("Cell number is incorrectly formatted or does not contain an "
                + "international code; please correct the number and try again.", result);
    }

    @Test
    public void testRegisterUserSuccessMessage() {
        String result = login.registerUser("kyl_1", "Ch&&sec@ke99!",
                "+27838968976", "Kyle", "Test");
        assertEquals("Username successfully captured.", result);
    }

    // ---------- loginUser / returnLoginStatus ----------

    @Test
    public void testLoginSuccessful() {
        login.registerUser("kyl_1", "Ch&&sec@ke99!", "+27838968976", "Kyle", "Test");
        assertTrue(login.loginUser("kyl_1", "Ch&&sec@ke99!"));
    }

    @Test
    public void testLoginFailed() {
        login.registerUser("kyl_1", "Ch&&sec@ke99!", "+27838968976", "Kyle", "Test");
        assertFalse(login.loginUser("kyl_1", "WrongPassword1!"));
    }

    @Test
    public void testReturnLoginStatusSuccess() {
        login.registerUser("kyl_1", "Ch&&sec@ke99!", "+27838968976", "Kyle", "Test");
        boolean loggedIn = login.loginUser("kyl_1", "Ch&&sec@ke99!");
        assertEquals("Welcome Kyle, Test it is great to see you again.",
                login.returnLoginStatus(loggedIn));
    }

    @Test
    public void testReturnLoginStatusFailure() {
        login.registerUser("kyl_1", "Ch&&sec@ke99!", "+27838968976", "Kyle", "Test");
        boolean loggedIn = login.loginUser("kyl_1", "WrongPassword1!");
        assertEquals("Username or password incorrect, please try again.",
                login.returnLoginStatus(loggedIn));
    }
}
