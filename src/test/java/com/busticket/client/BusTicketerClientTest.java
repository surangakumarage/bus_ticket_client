package com.busticket.client;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for BusTicketerClient
 *
 * Tests the two core API methods:
 * 1. checkAvailability() - Check availability and price for x passengers
 * 2. reserveTicket() - Reserve a ticket
 *
 * Note: Integration tests require the server to be running on localhost:8080
 */
public class BusTicketerClientTest {

    private BusTicketerClient client;

    @Before
    public void setUp() {
        client = new BusTicketerClient("http://localhost:8080");
    }

    @Test
    public void testClientInitialization() {
        // Assert
        assertNotNull("Client should not be null", client);
        assertEquals("Server URL should be set", "http://localhost:8080", client.getServerUrl());
    }

    @Test
    public void testSetServerUrl() {
        // Arrange
        String newUrl = "http://192.168.1.1:9000";

        // Act
        client.setServerUrl(newUrl);

        // Assert
        assertEquals("Server URL should be updated", newUrl, client.getServerUrl());
    }

    @Test
    public void testSetServerUrlWithTrailingSlash() {
        // Arrange
        String urlWithSlash = "http://localhost:8080/";

        // Act
        client.setServerUrl(urlWithSlash);

        // Assert
        assertEquals("Trailing slash should be removed", "http://localhost:8080", client.getServerUrl());
    }

    /**
     * Integration tests - uncomment and run when server is active
     */

    /*
    @Test
    public void testCheckAvailability() throws IOException {
        // Act
        String response = client.checkAvailability(1, 2, "A", "D");

        // Assert
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain availability info",
                   response.contains("available_seats") || response.contains("total_price"));
    }

    @Test
    public void testReserveTicket() throws IOException {
        // Act
        String response = client.reserveTicket(1, "John Doe", "5551234567", "john@example.com",
                                               "A", "D", "1A", "150");

        // Assert
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain booking/reservation number",
                   response.contains("booking_number") || response.contains("reservation_number"));
    }

    @Test
    public void testCheckAvailabilityMultiplePassengers() throws IOException {
        // Act
        String response = client.checkAvailability(1, 5, "A", "D");

        // Assert
        assertNotNull("Response should not be null", response);
        // Response should calculate price for 5 passengers
        assertTrue("Response should contain pricing info",
                   response.contains("total_price") || response.contains("price"));
    }
    */
}
