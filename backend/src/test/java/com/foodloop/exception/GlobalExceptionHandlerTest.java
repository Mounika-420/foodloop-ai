package com.foodloop.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.foodloop.dto.ApiError;
import com.foodloop.entity.enums.Unit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockHttpServletRequest req = new MockHttpServletRequest("PATCH", "/api/test");

    private static HttpMessageNotReadableException unreadable(Throwable cause) {
        return new HttpMessageNotReadableException("bad body", cause, mock(HttpInputMessage.class));
    }

    private static InvalidFormatException badValue(Object value, Class<?> type, Object... path) {
        InvalidFormatException e = InvalidFormatException.from(null, "bad", value, type);
        for (int i = path.length - 1; i >= 0; i--) {
            if (path[i] instanceof Integer idx) {
                e.prependPath(new Object(), idx);
            } else {
                e.prependPath(new Object(), (String) path[i]);
            }
        }
        return e;
    }

    private JsonNode body(ResponseEntity<ApiError> r) {
        assertEquals(400, r.getStatusCode().value());
        return mapper.valueToTree(r.getBody());
    }

    @Test
    void invalidEnumNamesTheFieldAndListsAllowedValuesWithoutEchoingTheInput() {
        JsonNode b = body(handler.handleUnreadable(unreadable(badValue("BOGUS", Unit.class, "status")), req));

        assertEquals("Invalid value for 'status'", b.path("message").asText());
        assertEquals("must be one of KG, LITRE, PORTIONS, PIECES", b.path("fieldErrors").path("status").asText());
        assertFalse(b.toString().contains("BOGUS"));
    }

    @Test
    void nestedFieldUsesADottedPath() {
        JsonNode b = body(handler.handleUnreadable(
                unreadable(badValue("abc", BigDecimal.class, "pickupLocation", "latitude")), req));

        assertEquals("has an invalid format", b.path("fieldErrors").path("pickupLocation.latitude").asText());
    }

    @Test
    void listIndexIsPartOfThePath() {
        JsonNode b = body(handler.handleUnreadable(unreadable(badValue("x", Unit.class, "items", 2, "unit")), req));

        assertTrue(b.path("fieldErrors").has("items[2].unit"));
    }

    @Test
    void bodyThatIsNotJsonStaysGeneric() {
        JsonNode b = body(handler.handleUnreadable(unreadable(new RuntimeException("Unexpected character")), req));

        assertEquals("Malformed or invalid request", b.path("message").asText());
        assertTrue(b.path("fieldErrors").isNull() || b.path("fieldErrors").isMissingNode());
    }

    @Test
    void invalidEnumInTheQueryStringListsAllowedValues() {
        JsonNode b = body(handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("BOGUS", Unit.class, "unit", null, null), req));

        assertEquals("Invalid value for 'unit'", b.path("message").asText());
        assertEquals("must be one of KG, LITRE, PORTIONS, PIECES", b.path("fieldErrors").path("unit").asText());
        assertFalse(b.toString().contains("BOGUS"));
    }

    @Test
    void nonEnumQueryValueSaysInvalidFormat() {
        JsonNode b = body(handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null), req));

        assertEquals("has an invalid format", b.path("fieldErrors").path("id").asText());
    }

    @Test
    void missingRequestParameterStaysGeneric() {
        JsonNode b = body(handler.handleMalformed(new MissingServletRequestParameterException("status", "String"), req));

        assertEquals("Malformed or invalid request", b.path("message").asText());
    }
}