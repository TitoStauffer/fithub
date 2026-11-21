package com.fithub.identity.domain.aggregate;

import com.fithub.identity.domain.valueobject.Email;
import com.fithub.identity.domain.valueobject.UserRole;
import com.fithub.identity.domain.valueobject.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD: Testes de agregado User
 */
class UserTest {

    @Test
    void shouldRegisterNewPatientUser() {
        Email email = Email.createAndValidate("patient@test.com");
        User user = User.registerNew(email, "password123", UserRole.PATIENT, "Test Patient");

        assertNotNull(user.getId());
        assertEquals(email, user.getEmail());
        assertEquals("Test Patient", user.getName());
        assertEquals(UserRole.PATIENT, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.getPasswordHash().length() > 20);
    }

    @Test
    void shouldEncryptPasswordWithBCrypt() {
        Email email = Email.createAndValidate("user@test.com");
        User user = User.registerNew(email, "plainPassword", UserRole.PT, "PT");

        assertNotEquals("plainPassword", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
    }

    @Test
    void shouldAuthenticateWithCorrectPassword() {
        Email email = Email.createAndValidate("trainer@test.com");
        User user = User.registerNew(email, "correctPassword", UserRole.PT, "Trainer");

        assertTrue(user.authenticate("correctPassword"));
    }

    @Test
    void shouldRejectWrongPassword() {
        Email email = Email.createAndValidate("nutritionist@test.com");
        User user = User.registerNew(email, "correctPassword", UserRole.NUTRITIONIST, "Nutritionist");

        assertFalse(user.authenticate("wrongPassword"));
    }

    @Test
    void shouldDeactivateUser() {
        Email email = Email.createAndValidate("user@test.com");
        User user = User.registerNew(email, "password123", UserRole.PATIENT, "Patient");

        user.deactivate();
        assertEquals(UserStatus.INACTIVE, user.getStatus());
    }

    @Test
    void shouldRejectShortPassword() {
        Email email = Email.createAndValidate("user@test.com");

        assertThrows(IllegalArgumentException.class,
            () -> User.registerNew(email, "short", UserRole.PATIENT, "User"));
    }

    @Test
    void shouldRejectEmptyName() {
        Email email = Email.createAndValidate("user@test.com");

        assertThrows(IllegalArgumentException.class,
            () -> User.registerNew(email, "password123", UserRole.PATIENT, ""));
    }
}
