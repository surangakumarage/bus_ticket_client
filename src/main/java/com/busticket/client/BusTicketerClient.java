package com.busticket.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * BusTicketerClient - Unified REST client for Bus Ticketer Service
 *f
 * Features:
 * 1. Core Booking APIs:
 *    - checkAvailability() - Check seat availability and price for x passengers
 *    - reserveTicket() - Reserve a ticket with passenger details
 *
 * 2. Generic HTTP Methods:
 *    - get() - Make GET requests with parameters
 *    - post() - Make POST requests with JSON body
 *    - handleResponse() - Process HTTP responses
 *
 * Uses raw HttpURLConnection without framework dependencies
 */
public class BusTicketerClient {
    private String serverUrl;
    private static final int TIMEOUT_MS = 10000;
    private static final String CONTENT_TYPE = "application/json";

    public BusTicketerClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    /**
     * API 1: Check Availability and Price for Passengers
     *
     * Request Parameters:
     * - origin: Origin stop (A, B, C, D)
     * - destination: Destination stop (A, B, C, D)
     * - passengerCount: Number of passengers
     * - journeyDate: Journey date (YYYY-MM-DD)
     *
     * Response contains:
     * - journeys: Available journeys with seat and price info
     * - passengerCount: Number of passengers
     * - availableCount: Number of available journeys
     */
    public String checkAvailability(String origin, String destination, int passengerCount, String journeyDate) throws IOException {
        String endpoint = "/api/v1/reservation/availability?" +
                "origin=" + urlEncode(origin) +
                "&destination=" + urlEncode(destination) +
                "&passenger_count=" + passengerCount +
                "&journey_date=" + urlEncode(journeyDate);
        return sendGet(endpoint);
    }

    /**
     * API 2: Reserve Ticket (Single Passenger) - Overloaded version
     *
     * Request Parameters:
     * - journeyId: Journey ID
     * - origin: Origin stop (A, B, C, D)
     * - destination: Destination stop (A, B, C, D)
     * - passengerName: Name of passenger
     * - passengerPhone: Phone number (10 digits)
     * - passengerEmail: Email address
     * - preferredSeatId: Preferred seat identifier (e.g., "1A"), can be null for auto-assignment
     * - totalPrice: Total price (from availability response)
     *
     * Response contains:
     * - status: SUCCESS or ERROR
     * - data: Contains reservation details including:
     *   - ticket_number: Booking confirmation ticket number
     *   - booking_number: Booking reference number
     *   - journey_info: Journey details
     *   - bookings: Array of booking details with seat assignments
     *   - total_price: Confirmed total price
     */
    public String reserveTicket(int journeyId, String origin, String destination,
                                String passengerName, String passengerPhone,
                                String passengerEmail, String preferredSeatId, String totalPrice) throws IOException {
        // Build passengers array with single passenger object
        StringBuilder passengersJson = new StringBuilder();
        passengersJson.append("[{");
        passengersJson.append("\"name\":\"").append(escapeJson(passengerName)).append("\",");
        passengersJson.append("\"phone\":\"").append(escapeJson(passengerPhone)).append("\",");
        passengersJson.append("\"email\":\"").append(escapeJson(passengerEmail)).append("\"");

        // Add preferred seat if provided
        if (preferredSeatId != null && !preferredSeatId.isEmpty()) {
            passengersJson.append(",\"preferred_seat\":\"").append(escapeJson(preferredSeatId)).append("\"");
        }

        passengersJson.append("}]");

        // Build complete JSON request body
        String jsonBody = "{" +
                "\"journey_id\":" + journeyId + "," +
                "\"origin\":\"" + escapeJson(origin) + "\"," +
                "\"destination\":\"" + escapeJson(destination) + "\"," +
                "\"passenger_count\":1," +
                "\"contact_email\":\"" + escapeJson(passengerEmail) + "\"," +
                "\"passengers\":" + passengersJson.toString() + "," +
                "\"payment\":{\"amount\":" + totalPrice + "}" +
                "}";
        return sendPost("/api/v1/reservation/book", jsonBody);
    }

    /**
     * API 2: Reserve Ticket (Multiple Passengers)
     *
     * Request Parameters:
     * - journeyId: Journey ID
     * - origin: Origin stop (A, B, C, D)
     * - destination: Destination stop (A, B, C, D)
     * - passengers: List of passenger maps with keys: name, phone, email
     * - contactEmail: Email for booking confirmation
     * - preferredSeatId: Preferred seat for first passenger (optional)
     * - totalPrice: Total price for all passengers
     *
     * Response contains:
     * - status: SUCCESS or ERROR
     * - data: Contains reservation details with bookings for all passengers
     */
    public String reserveTicket(int journeyId, String origin, String destination,
                                java.util.List<java.util.Map<String, String>> passengers,
                                String contactEmail, String preferredSeatId, String totalPrice) throws IOException {
        // Build passengers array with multiple passenger objects
        StringBuilder passengersJson = new StringBuilder();
        passengersJson.append("[");

        for (int i = 0; i < passengers.size(); i++) {
            if (i > 0) passengersJson.append(",");

            java.util.Map<String, String> pax = passengers.get(i);
            passengersJson.append("{");
            passengersJson.append("\"name\":\"").append(escapeJson(pax.get("name"))).append("\",");
            passengersJson.append("\"phone\":\"").append(escapeJson(pax.get("phone"))).append("\",");
            passengersJson.append("\"email\":\"").append(escapeJson(pax.get("email"))).append("\"");

            // Add preferred seat only for first passenger if provided
            if (i == 0 && preferredSeatId != null && !preferredSeatId.isEmpty()) {
                passengersJson.append(",\"preferred_seat\":\"").append(escapeJson(preferredSeatId)).append("\"");
            }

            passengersJson.append("}");
        }

        passengersJson.append("]");

        // Build complete JSON request body
        String jsonBody = "{" +
                "\"journey_id\":" + journeyId + "," +
                "\"origin\":\"" + escapeJson(origin) + "\"," +
                "\"destination\":\"" + escapeJson(destination) + "\"," +
                "\"passenger_count\":" + passengers.size() + "," +
                "\"contact_email\":\"" + escapeJson(contactEmail) + "\"," +
                "\"passengers\":" + passengersJson.toString() + "," +
                "\"payment\":{\"amount\":" + totalPrice + "}" +
                "}";
        return sendPost("/api/v1/reservation/book", jsonBody);
    }

    /**
     * Generic GET request - for extending to other endpoints
     *
     * @param endpoint API endpoint (e.g., "/api/v1/reservation/availability")
     * @param params URL parameters as key-value pairs
     * @return ApiResponse with status code and body
     */
    public ApiResponse get(String endpoint, java.util.Map<String, String> params) throws IOException {
        String url = serverUrl + endpoint;

        if (params != null && !params.isEmpty()) {
            url += "?" + buildQueryString(params);
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", CONTENT_TYPE);

        return handleResponse(conn);
    }

    /**
     * Generic POST request - for extending to other endpoints
     *
     * @param endpoint API endpoint
     * @param jsonBody JSON request body
     * @return ApiResponse with status code and body
     */
    public ApiResponse post(String endpoint, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(serverUrl + endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", CONTENT_TYPE);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        return handleResponse(conn);
    }

    /**
     * Handle HTTP response and create ApiResponse
     */
    private ApiResponse handleResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        String responseBody = readResponseBody(conn);
        return new ApiResponse(responseCode, responseBody);
    }

    /**
     * Build query string from parameters
     */
    private String buildQueryString(java.util.Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (java.util.Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return sb.toString();
    }

    /**
     * URL encode a string
     */
    private String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    /**
     * Send GET request (internal - for specialized APIs)
     */
    private String sendGet(String endpoint) throws IOException {
        URL url = new URL(serverUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", CONTENT_TYPE);

        return readResponseBody(conn);
    }

    /**
     * Send POST request (internal - for specialized APIs)
     */
    private String sendPost(String endpoint, String jsonBody) throws IOException {
        URL url = new URL(serverUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", CONTENT_TYPE);
        conn.setRequestProperty("Accept", CONTENT_TYPE);
        conn.setDoOutput(true);

        // Write request body
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        return readResponseBody(conn);
    }

    /**
     * Read response body from connection
     */
    private String readResponseBody(HttpURLConnection conn) throws IOException {
        int statusCode = conn.getResponseCode();

        InputStream is;
        if (statusCode >= 400) {
            is = conn.getErrorStream();
        } else {
            is = conn.getInputStream();
        }

        if (is == null) {
            return "{\"statusCode\":" + statusCode + "}";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Get the server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Set the server URL
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    /**
     * API Response wrapper - holds HTTP status code and response body
     */
    public static class ApiResponse {
        public final int statusCode;
        public final String body;

        public ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean isError() {
            return statusCode >= 400;
        }

        @Override
        public String toString() {
            return "ApiResponse{" +
                    "statusCode=" + statusCode +
                    ", body='" + body + '\'' +
                    '}';
        }
    }
}
