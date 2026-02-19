package com.busticket.client;

import java.io.IOException;
import java.util.Scanner;

/**
 * ClientDemo - Demonstrates Bus Ticketer Client functionality
 *
 * Features:
 * 1. Check availability for passengers (stores route details for quick booking)
 * 2. Reserve tickets (uses stored route from availability check)
 */
public class ClientDemo {
    // Store last availability search to auto-fill reservation form
    private String lastJourneyDate;
    private int lastJourneyId;
    private String lastOrigin;
    private String lastDestination;
    private int lastPassengerCount;
    private BigDecimalWrapper lastFarePerPassenger;
    private BigDecimalWrapper lastTotalPrice;
    private java.util.List<String> lastAvailableSeats;

    public static void main(String[] args) {
        ClientDemo demo = new ClientDemo();
        demo.run();
    }

    private void run() {
        Scanner scanner = new Scanner(System.in);
        String serverUrl = getServerUrl(scanner);
        BusTicketerClient client = new BusTicketerClient(serverUrl);

        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    checkAvailability(client, scanner);
                    break;
                case "2":
                    reserveTicket(client, scanner);
                    break;
                case "3":
                    running = false;
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Invalid option. Please try again.\n");
            }
        }

        scanner.close();
    }

    /**
     * Get server URL from user or use default
     */
    private String getServerUrl(Scanner scanner) {
        System.out.print("Enter server URL (default: http://localhost:9091/bus-ticketer-service): ");
        String url = scanner.nextLine().trim();
        if (url.isEmpty()) {
            url = "http://localhost:9091/bus-ticketer-service";
        }
        System.out.println("Using server: " + url + "\n");
        return url;
    }

    /**
     * Print menu options
     */
    private void printMenu() {
        System.out.println("\n========== Bus Ticket Booking Menu ==========");
        System.out.println("1. Check Availability");
        System.out.println("2. Reserve Ticket");
        System.out.println("3. Exit");
        System.out.println("===========================================");
    }

    /**
     * API 1: Check Availability
     * Stores the search parameters for use in Reserve Ticket
     */
    private void checkAvailability(BusTicketerClient client, Scanner scanner) {
        System.out.println("\n--- Check Availability ---");

        // Clear ALL old availability and reservation data before checking new availability
        lastJourneyDate = null;
        lastJourneyId = 0;
        lastOrigin = null;
        lastDestination = null;
        lastPassengerCount = 0;
        lastTotalPrice = null;
        lastFarePerPassenger = null;
        lastAvailableSeats = null;

        // Get journey date with validation (only 3 days from today)
        String journeyDate = null;
        while (journeyDate == null) {
            String defaultDate = java.time.LocalDate.now().toString();
            System.out.print("Journey Date (YYYY-MM-DD, default: " + defaultDate + "): ");
            String userDate = scanner.nextLine().trim();

            if (userDate.isEmpty()) {
                journeyDate = defaultDate;
                break;
            }

            try {
                java.time.LocalDate selectedDate = java.time.LocalDate.parse(userDate);
                java.time.LocalDate today = java.time.LocalDate.now();

                // Check if date is in the past
                if (selectedDate.isBefore(today)) {
                    System.out.println("❌ Error: Cannot book for past dates. Please select today or a future date.");
                    continue;
                }

                // Check if date exceeds 3 days from today
                if (selectedDate.isAfter(today.plusDays(2))) {
                    System.out.println("❌ Error: Only 3 days from today can be booked. Available dates: " +
                            today + " to " + today.plusDays(2));
                    continue;
                }

                journeyDate = userDate;
            } catch (Exception e) {
                System.out.println("❌ Error: Invalid date format. Please use YYYY-MM-DD format.");
            }
        }

        System.out.print("Number of Passengers: ");
        int passengerCount = getIntInput(scanner);

        // Get origin with validation and re-prompting
        String origin = null;
        while (origin == null) {
            System.out.print("From Stop (origin - A/B/C/D): ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (!isValidStop(input)) {
                System.out.println("❌ Error: Invalid origin '" + input + "'. Must be one of: A, B, C, D");
                continue;
            }
            origin = input;
        }

        // Get destination with validation and re-prompting
        String destination = null;
        while (destination == null) {
            System.out.print("To Stop (destination - A/B/C/D): ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (!isValidStop(input)) {
                System.out.println("❌ Error: Invalid destination '" + input + "'. Must be one of: A, B, C, D");
                continue;
            }
            if (input.equals(origin)) {
                System.out.println("❌ Error: Origin and destination must be different");
                continue;
            }
            destination = input;
        }

        try {
            System.out.println("\nFetching availability for date: " + journeyDate);
            String response = client.checkAvailability(origin, destination, passengerCount, journeyDate);
            System.out.println("\n✅ Availability Response:");
            System.out.println(prettyPrintJson(response));

            // Store availability search parameters for quick booking
            this.lastJourneyDate = journeyDate;
            this.lastOrigin = origin;
            this.lastDestination = destination;
            this.lastPassengerCount = passengerCount;

            // Extract journey ID from response (will be set below if journeys are found)
            this.lastJourneyId = 0;

            // Extract fares from response for convenience
            try {
                String farePerPassengerStr = extractFarePerPassengerFromResponse(response);
                if (farePerPassengerStr != null) {
                    this.lastFarePerPassenger = new BigDecimalWrapper(farePerPassengerStr);
                }

                String totalFareStr = extractFareFromResponse(response);
                if (totalFareStr != null) {
                    this.lastTotalPrice = new BigDecimalWrapper(totalFareStr);
                }
            } catch (Exception e) {
                // If fare extraction fails, user will need to enter it manually
            }

            // Extract journey ID, available seats and soft lock expiration info
            try {
                int journeyId = extractJourneyId(response);
                if (journeyId > 0) {
                    this.lastJourneyId = journeyId;
                }

                this.lastAvailableSeats = extractAvailableSeats(response);

                if (lastAvailableSeats != null && !lastAvailableSeats.isEmpty()) {
                    System.out.println("\n🪑 Available Seats (" + lastAvailableSeats.size() + "): " + lastAvailableSeats);

                    // Display prices from availability response
                    if (lastFarePerPassenger != null) {
                        System.out.println("💰 Fare per Passenger: $" + lastFarePerPassenger.getFormattedPrice());
                    }
                    if (lastTotalPrice != null) {
                        System.out.println("💰 Total Price (" + lastPassengerCount + " passengers): $" + lastTotalPrice.getFormattedPrice());
                    }

                    // Seats will be auto-assigned during booking
                    System.out.println("💡 Seats will be automatically assigned during booking.");
                    System.out.println("\n💡 Tip: You can now use 'Reserve Ticket' to book for this route!");
                } else {
                    // No journeys available
                    System.out.println("\n❌ No journeys available for " + origin + "→" + destination + " with " + passengerCount + " passenger(s) on " + journeyDate);
                    System.out.println("💡 Tip: Try a different date or fewer passengers.");
                }
            } catch (Exception e) {
                // If seat extraction fails, continue with auto-assignment
                this.lastAvailableSeats = new java.util.ArrayList<>();
                System.out.println("\n💡 Tip: You can now use 'Reserve Ticket' to book for this route!");
            }
        } catch (IOException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    /**
     * API 2: Reserve Ticket
     * Uses stored availability data if available, prompts user to check availability first otherwise
     * Now supports multi-passenger reservations
     */
    private void reserveTicket(BusTicketerClient client, Scanner scanner) {
        // Check if user has checked availability first
        if (lastJourneyDate == null) {
            System.out.println("\n❌ Error: Please check availability first before making a reservation.");
            System.out.println("💡 Use option 1 (Check Availability) to select your route and then come back here.");
            return;
        }

        // Check if a valid journey was found (journey ID must be > 0)
        if (lastJourneyId <= 0) {
            System.out.println("\n❌ Error: No available journeys for this route.");
            System.out.println("💡 Your requested number of passengers may exceed available seats.");
            System.out.println("💡 Please check availability again with fewer passengers.");
            return;
        }

        // Use the total price from availability check
        String totalPrice = (lastTotalPrice != null) ? lastTotalPrice.getFormattedPrice() : "0.00";

        System.out.println("\n--- Reserve Ticket (" + lastPassengerCount + " Passenger(s)) ---");
        System.out.println("Route: " + lastOrigin + " → " + lastDestination + " | Date: " + lastJourneyDate + " | Journey ID: " + lastJourneyId);
        System.out.println("💰 Total Price: $" + totalPrice);
        System.out.println();

        // Collect lead passenger details only (for group booking)
        System.out.println("--- Lead Passenger Information ---");

        System.out.print("Lead pax name: ");
        String passengerName = scanner.nextLine().trim();

        // Get phone with validation and re-prompting
        String passengerPhone = null;
        while (passengerPhone == null) {
            System.out.print("Passenger Phone (10 digits): ");
            String input = scanner.nextLine().trim();
            if (!input.matches("\\d{10}")) {
                System.out.println("❌ Error: Invalid phone '" + input + "'. Must be exactly 10 digits");
                continue;
            }
            passengerPhone = input;
        }

        // Get email with validation and re-prompting
        String passengerEmail = null;
        while (passengerEmail == null) {
            System.out.print("Passenger Email: ");
            String input = scanner.nextLine().trim();
            if (!input.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                System.out.println("❌ Error: Invalid email '" + input + "'. Please enter a valid email address");
                continue;
            }
            passengerEmail = input;
        }

        // Build passenger entries - lead passenger with full info, rest with default values
        java.util.List<java.util.Map<String, String>> passengers = new java.util.ArrayList<>();

        // Add lead passenger with full info
        java.util.Map<String, String> leadPassenger = new java.util.HashMap<>();
        leadPassenger.put("name", passengerName);
        leadPassenger.put("phone", passengerPhone);
        leadPassenger.put("email", passengerEmail);
        passengers.add(leadPassenger);

        // Add remaining passengers with default values (same contact for group)
        for (int i = 1; i < lastPassengerCount; i++) {
            java.util.Map<String, String> passengerInfo = new java.util.HashMap<>();
            passengerInfo.put("name", "Passenger " + (i + 1));  // Default name
            passengerInfo.put("phone", passengerPhone);  // Same contact phone
            passengerInfo.put("email", passengerEmail);  // Same contact email
            passengers.add(passengerInfo);
        }

        // Get contact email (from lead passenger)
        String contactEmail = passengers.get(0).get("email");

        try {
            System.out.println("\nReserving ticket for date: " + lastJourneyDate);
            String response = client.reserveTicket(lastJourneyId, lastOrigin, lastDestination, passengers,
                    contactEmail, null, totalPrice);
            System.out.println("\n✅ Reservation Response:");
            System.out.println(prettyPrintJson(response));

            // Keep the stored data so user can make another reservation for another passenger
            System.out.println("\n💡 Tip: You can reserve another ticket for the same route!");
        } catch (IOException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    /**
     * Extract fare per passenger from availability response JSON
     */
    private String extractFarePerPassengerFromResponse(String json) {
        try {
            // Look for "fare_per_passenger": followed by a number
            int startIdx = json.indexOf("\"fare_per_passenger\":");
            if (startIdx == -1) {
                return null;
            }
            startIdx = json.indexOf(":", startIdx) + 1;

            // Skip whitespace and newlines
            while (startIdx < json.length() && (json.charAt(startIdx) == ' ' || json.charAt(startIdx) == '\n')) {
                startIdx++;
            }

            int endIdx = startIdx;
            // Read until we hit a comma, closing brace, or newline
            while (endIdx < json.length() && json.charAt(endIdx) != ',' && json.charAt(endIdx) != '}' && json.charAt(endIdx) != '\n') {
                endIdx++;
            }

            String fareStr = json.substring(startIdx, endIdx).trim();
            // Remove any trailing whitespace or special characters
            fareStr = fareStr.replaceAll("[^0-9.]", "");
            return fareStr.isEmpty() ? null : fareStr;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract fare from availability response JSON
     */
    private String extractFareFromResponse(String json) {
        try {
            // Look for "total_fare": followed by a number
            int startIdx = json.indexOf("\"total_fare\":");
            if (startIdx == -1) {
                return null;
            }
            startIdx = json.indexOf(":", startIdx) + 1;

            // Skip whitespace and newlines
            while (startIdx < json.length() && (json.charAt(startIdx) == ' ' || json.charAt(startIdx) == '\n')) {
                startIdx++;
            }

            int endIdx = startIdx;
            // Read until we hit a comma, closing brace, or newline
            while (endIdx < json.length() && json.charAt(endIdx) != ',' && json.charAt(endIdx) != '}' && json.charAt(endIdx) != '\n') {
                endIdx++;
            }

            String fareStr = json.substring(startIdx, endIdx).trim();
            // Remove any trailing whitespace or special characters
            fareStr = fareStr.replaceAll("[^0-9.]", "");
            return fareStr.isEmpty() ? null : fareStr;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract available seats list from availability response JSON
     */
    private java.util.List<String> extractAvailableSeats(String json) {
        java.util.List<String> seats = new java.util.ArrayList<>();
        try {
            // Look for "available_seats_list": [...]
            int startIdx = json.indexOf("\"available_seats_list\":");
            if (startIdx == -1) {
                return seats;
            }

            startIdx = json.indexOf("[", startIdx);
            int endIdx = json.indexOf("]", startIdx);
            if (startIdx == -1 || endIdx == -1) {
                return seats;
            }

            String seatsArray = json.substring(startIdx + 1, endIdx);
            // Extract seat numbers from quoted strings
            String[] parts = seatsArray.split(",");
            for (String part : parts) {
                String seat = part.trim().replaceAll("\"", "").trim();
                if (!seat.isEmpty()) {
                    seats.add(seat);
                }
            }
        } catch (Exception e) {
            // Return empty list if extraction fails
        }
        return seats;
    }

    /**
     * Extract journey ID from availability response JSON (first journey)
     */
    private int extractJourneyId(String json) {
        try {
            // Look for "journey_id": followed by a number
            int startIdx = json.indexOf("\"journey_id\":");
            if (startIdx == -1) {
                return 0;
            }
            startIdx = json.indexOf(":", startIdx) + 1;

            // Skip whitespace
            while (startIdx < json.length() && (json.charAt(startIdx) == ' ' || json.charAt(startIdx) == '\n')) {
                startIdx++;
            }

            int endIdx = startIdx;
            // Read until we hit a comma or closing brace
            while (endIdx < json.length() && json.charAt(endIdx) != ',' && json.charAt(endIdx) != '}') {
                endIdx++;
            }

            String journeyIdStr = json.substring(startIdx, endIdx).trim();
            return Integer.parseInt(journeyIdStr);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get integer input from scanner
     */
    private int getIntInput(Scanner scanner) {
        while (true) {
            try {
                String line = scanner.nextLine().trim();
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.print("Invalid input. Please enter a number: ");
            }
        }
    }

    /**
     * Get integer input with default value (if user presses Enter without input)
     */
    private int getIntInput(Scanner scanner, int defaultValue) {
        while (true) {
            try {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    return defaultValue;
                }
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.print("Invalid input. Please enter a number or press Enter for default: ");
            }
        }
    }

    /**
     * Validate if stop is valid (A, B, C, or D)
     */
    private boolean isValidStop(String stop) {
        return stop != null && (stop.equals("A") || stop.equals("B") || stop.equals("C") || stop.equals("D"));
    }

    /**
     * Format ISO 8601 timestamps to readable format (YYYY-MM-DD HH:MM:SS)
     * Converts: "2026-02-18T21:15:47.505987" to "2026-02-18 21:15:47"
     */
    private String formatTimestamps(String json) {
        // Match ISO 8601 timestamp pattern
        return json.replaceAll("(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2}:\\d{2})\\.\\d+", "$1 $2");
    }

    /**
     * Pretty print JSON response with readable timestamps
     */
    private String prettyPrintJson(String json) {
        // Format ISO 8601 timestamps to readable format (YYYY-MM-DD HH:MM:SS)
        json = formatTimestamps(json);

        // Simple formatting for JSON output
        return json
                .replace("{", "{\n  ")
                .replace(",", ",\n  ")
                .replace("}", "\n}");
    }

    /**
     * Simple wrapper class to handle fare (already calculated for the requested passenger count)
     */
    private static class BigDecimalWrapper {
        private String fareValue;

        BigDecimalWrapper(String fareValue) {
            this.fareValue = fareValue;
        }

        String getFormattedPrice() {
            try {
                double fare = Double.parseDouble(fareValue.trim());
                return String.format("%.2f", fare);
            } catch (NumberFormatException e) {
                return fareValue;
            }
        }
    }
}
