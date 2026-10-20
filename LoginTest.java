package za.co.quickchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginTest {

    @Test
    void usernameNeedsUnderscoreAndMaxFiveCharacters() {
        Login probe = new Login();
        assertTrue(probe.checkUserName("kg_01"));
        assertFalse(probe.checkUserName("kg01"));
        assertFalse(probe.checkUserName("kyle!!!"));
        assertFalse(probe.checkUserName("kyle_smith"));
    }

    @Test
    void passwordNeedsLengthCapitalNumberAndSpecialCharacter() {
        Login probe = new Login();
        assertTrue(probe.checkPasswordComplexity("Ch&&sec@ke99!"));
        assertFalse(probe.checkPasswordComplexity("password"));
        assertFalse(probe.checkPasswordComplexity("Pass1!"));
        assertFalse(probe.checkPasswordComplexity("Password!"));
        assertFalse(probe.checkPasswordComplexity("Password1"));
    }

    @Test
    void cellNumberNeedsSouthAfricanInternationalCode() {
        Login probe = new Login();
        assertTrue(probe.checkCellPhoneNumber("+27838968976"));
        assertFalse(probe.checkCellPhoneNumber("08966553"));
        assertFalse(probe.checkCellPhoneNumber("0838968976"));
    }

    @Test
    void loginSucceedsOnlyWithTheRegisteredDetails() {
        Login probe = new Login();
        probe.registerUser("kg_01", "Passw0rd!", "+27831234567", "Sam", "Dube");
        assertTrue(probe.loginUser("kg_01", "Passw0rd!"));
        assertEquals("Welcome Sam, Dube it is great to see you again.", probe.returnLoginStatus());
        assertFalse(probe.loginUser("kg_01", "wrong"));
        assertEquals("Username or password incorrect, please try again.", probe.returnLoginStatus());
    }

    @Test
    void loginFailsWhenRegistrationWasRejected() {
        Login probe = new Login();
        probe.registerUser("toolong_name", "weak", "123", "Sam", "Dube");
        assertFalse(probe.isEnrolmentComplete());
        assertFalse(probe.loginUser("toolong_name", "weak"));
    }
}
