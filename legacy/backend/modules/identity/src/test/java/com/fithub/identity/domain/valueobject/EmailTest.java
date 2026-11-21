package com.fithub.identity.domain.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD: RFC 5322 email validation via Apache Commons
 */
class EmailTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "simple@example.com",
        "user.name@example.co.uk",
        "user+tag@example.org",
        "123@example.com"
    })
    void shouldAcceptValidEmails(String email) {
        Email validEmail = Email.createAndValidate(email);
        assertEquals(email, validEmail.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "@example.com",
        "user@",
        "user..name@example.com",
        "user name@example.com",
        "user@.com",
        "invalid"
    })
    void shouldRejectInvalidEmails(String email) {
        assertThrows(IllegalArgumentException.class,
            () -> Email.createAndValidate(email));
    }

    @Test
    void shouldBypassValidationForPersistedEmails() {
        Email email = Email.fromStorage("user@example.com");
        assertEquals("user@example.com", email.getValue());
    }

    @Test
    void shouldBeValueObject() {
        Email email1 = Email.createAndValidate("test@example.com");
        Email email2 = Email.createAndValidate("test@example.com");

        assertEquals(email1, email2);
    }
}
