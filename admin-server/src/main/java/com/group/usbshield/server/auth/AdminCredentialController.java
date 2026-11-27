package com.group.usbshield.server.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/account")
@RequiredArgsConstructor
public class AdminCredentialController {
    private static final int MAX_PAYLOAD_BYTES = 4_096;
    private final AdminCredentialService credentials;
    private final ObjectMapper json;

    private record Change(String currentPassword, String newUsername, String newPassword) {
        @Override public String toString() { return "Change[redacted]"; }
    }

    @PostMapping(path = "/credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal AdminIdentity identity,
                       @SessionAttribute(name = AdminSessionVersionFilter.SESSION_VERSION, required = false) Long version,
                       HttpServletRequest request) {
        Change body = readChange(request);
        try {
            credentials.change(identity, version, body.currentPassword(), body.newUsername(), body.newPassword());
        } catch (AdminCredentialService.Rejected rejected) {
            throw switch (rejected.reason()) {
                case STALE_SESSION -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again");
                case WRONG_PASSWORD -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Current password did not match");
                case INVALID_CHANGE -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credential change");
                case USERNAME_TAKEN -> new ResponseStatusException(HttpStatus.CONFLICT, "Username is unavailable");
            };
        }
        try {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
        } catch (IllegalStateException alreadyInvalidated) {
            // A concurrent logout or the session filter may complete this cleanup first.
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Change readChange(HttpServletRequest request) {
        // Read inside the handler so MVC never binds or traces a password-bearing argument.
        byte[] payload = null;
        try {
            if (request.getContentLengthLong() > MAX_PAYLOAD_BYTES) throw invalidFields();
            payload = request.getInputStream().readNBytes(MAX_PAYLOAD_BYTES + 1);
            if (payload.length > MAX_PAYLOAD_BYTES) throw invalidFields();
            JsonNode fields = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(payload);
            if (fields == null || !fields.isObject()) throw invalidFields();
            String current = text(fields, "currentPassword", 128);
            if (current == null || current.isBlank()) throw invalidFields();
            return new Change(current, text(fields, "newUsername", 100), text(fields, "newPassword", 128));
        } catch (IOException malformed) {
            throw invalidFields();
        } finally {
            if (payload != null) Arrays.fill(payload, (byte) 0);
        }
    }

    private static String text(JsonNode fields, String key, int maxLength) {
        JsonNode field = fields.get(key);
        if (field == null || field.isNull()) return null;
        if (!field.isTextual() || field.textValue().length() > maxLength) throw invalidFields();
        return field.textValue();
    }

    private static ResponseStatusException invalidFields() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request fields");
    }
}
