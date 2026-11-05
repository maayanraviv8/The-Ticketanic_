# Use Cases — Version 0

---

## Guest

### UC1 - Login

- **Actor:** Guest
- **Trigger:** Guest requests to login.
- **Parameters:** email, password.
- **Preconditions:** None.
- **Main Flow:**
  1. System: check if the credentials are valid with the Authentication Service.
  2. System: if the credentials are valid, generate a token and return it to the client.
  3. System: store the token in the database and link it to the member.
- **Alternatives:**
  - Invalid credentials: System notifies user that the credentials are invalid and asks for new credentials.
- **Postconditions:** Member status is logged in.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful login (positive scenario)                | 1. Member "user123" is registered with email "[user123@mail.com](mailto:user123@mail.com)" and password "Password1!".<br>2. The guest enters valid credentials: email "[user123@mail.com](mailto:user123@mail.com)" and password "Password1!". | 1. The system returns the member token.<br>2. Member "user123" status is logged in. |
| Login with incorrect password (negative scenario)   | 1. Member "user123" is registered with email "[user123@mail.com](mailto:user123@mail.com)" and password "Password1!".<br>2. The guest enters email "[user123@mail.com](mailto:user123@mail.com)" with an incorrect password.                   | 1. The system returns null.<br>2. Member "user123" status remains logged out.       |
| Login with a non-existent email (negative scenario) | 1. No member with email "[nobody@mail.com](mailto:nobody@mail.com)" exists in the system.<br>2. The guest enters email "[nobody@mail.com](mailto:nobody@mail.com)".                                                                            | 1. The system returns null.<br>2. No member with that email exists in the system.   |


---

### UC2 - Register

- **Actor:** Guest
- **Trigger:** Guest requests to register.
- **Parameters:** username, email, password.
- **Preconditions:** None.
- **Main Flow:**
  1. System: validates that the identifying details are unique.
  2. System: creates and stores a new member.
- **Postconditions:** Nem Member with that email exists in the system and Member status is logged out.
- **Expected Result:** Guest remains in guest mode until he logs in.

### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful register (positive scenario)          | 1. No member with email "[user123@mail.com](mailto:user123@mail.com)" exists in the system.<br>2. The guest registers with username "user123", email "[user123@mail.com](mailto:user123@mail.com)", password "Password1!".                      | 1. The system returns true.<br>2. A member with email "[user123@mail.com](mailto:user123@mail.com)" exists in the system.         |
| Register with existing email (negative scenario) | 1. Member "user123" is already registered with email "[user123@mail.com](mailto:user123@mail.com)".<br>2. The guest attempts to register again with the same email "[user123@mail.com](mailto:user123@mail.com)" but with a different username. | 1. The system returns false.<br>2. Only one member with email "[user123@mail.com](mailto:user123@mail.com)" exists in the system. |


### UC3 - Event Search

- **Actor:** User
- **Trigger:** User requests to search for events.
- **Parameters:** None.
- **Preconditions:** None.
- **Main Flow:**
  1. System: asks for production company, keywords, category, date range, location, price range.
  2. User: enters production company, keywords, category, date range, location, price range.
  3. System: search for events matching the criteria.
  4. System: display events matching the criteria.
- **Alternatives:**
  - No matching events: System notifies user that no active events match the criteria.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful search by criteria — keyword / category / date range / location / price range (positive scenario) | 1. Production Company "A" is created.<br>2. An event with matching attributes (artist name, event name, category, date range, location, price range) is created under company "A".<br>| 1. Each search returns the matching event.<br>2. The returned EventDTO fields match the searched criterion. |
| Search by criteria with no matching results — keyword / category / date range / location / price range (negative scenario) | 1. Production Company "A" is created.<br>2. An event exists under company "A".<br>3. searche using each criterion with a value that does not match the event. | 1. Each search returns an empty list.<br>2. No exception is thrown. |
| Successful search by production company (positive scenario) | 1. Production Company "A" is created with events under it.<br>2. Search for events filtering by production company "A". | 1. The system returns only events belonging to company "A".<br>2. The productionCompanyName field in the returned DTOs matches "A". |
| Search by production company with no matching events (negative scenario) | 1. Production Company "A" is created (no events).<br>2. Production Company "B" is created with events.<br>3. Search for events filtering by company "A". | 1. The system returns an empty list.<br>2. No exception is thrown. |


### UC4 - View Show Map

- **Actor:** User
- **Trigger:** User requests to view the show map.
- **Parameters:** Event ID.
- **Preconditions:** Show exists.
- **Main Flow:**
  1. System: display shows under the specific event.
  2. System: ask for the show ID.
  3. User: enters the show ID.
  4. System: display the show map.
- **Alternatives:**
  - No shows found: System notifies user that no shows are available for the event.
  - Invalid show ID: System notifies user that the show ID is invalid and asks for a valid show ID.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful show map display (positive scenario) | 1. User "user123" is created.<br>2. The user creates production company "A".<br>3. The user creates event "E" under company "A".<br>4. A show with a map is created under event "E".<br>5. The user searches for event "E" (per UC4).<br>6. The user selects the valid show. | 1. The system displays the show map.          |
| Invalid show number (negative scenario)         | Same as steps 1–5 above.<br>6. The user enters an invalid show number (e.g. a negative number or a non-numeric value).                                                                                                                                           | 1. An appropriate error message is displayed. |
| Non-existent show number (negative scenario)    | Same as steps 1–5 above.<br>6. The user enters a show number that does not exist under event "E".                                                                                                                                                                | 1. An appropriate error message is displayed. |


### UC5 - Reserve Ticket

- **Actor:** User (Guest or Member)
- **Trigger:** User requests to view the show map and start the reservation process.
- **Parameters:** showId, userId, raffleCode, zoneId, seatIds, standingQuantity.
- **Preconditions:**
  - The selected event is active and exists in the system.
  - System is initialized and connected to external services.
- **Main Flow:**
  1. System: checks if the event requires a Raffle winning code for access.
  2. System: (If required) asks for the Raffle winning code.
  3. User: enters the code.
  4. System: validates the code.
  5. System: checks the current load for the event to determine if a virtual queue is necessary.
  6. System: (If high load) places the user in a virtual queue and provides a position/estimated wait time update.
  7. System: grants the user access to the inventory once their turn arrives.
  8. System: displays the show map with real-time seat and area availability.
  9. User: selects specific seats or a quantity of tickets in a general area.
  10. System: validates that the selected tickets are available in the inventory.
  11. System: creates an "Active Order" for the user and locks the selected tickets for 10 minutes.
  12. User: (Optional) requests to remove tickets or change quantities within the current active order. (UC6)
  13. System: updates the order details and synchronizes the inventory in the domain layer.
  14. System: validates the order against the Purchase Policy and applies the relevant Discount Policy.
  15. System: displays the final order summary, price, and the remaining time until the reservation expires.
- **Alternatives:**
  - **Invalid Raffle code:** System notifies the user that the code is invalid or expired and denies access to the map.
  - **Order already exists:** System identifies that the user already has an active order for the event and prevents opening a second one.
  - **Tickets taken:** Another user reserved the selected seats before the lock was finalized; System notifies the user and requests a new selection.
  - **Policy violation:** System identifies a violation (e.g., exceeding maximum ticket limit); System alerts the user and blocks the reservation until corrected.
  - **Guest leaves the platform during reservation:** The active order holds the tickets for the time left; the Guest loses access to the order and cannot return to it even if they visit again.
  - **Member leaves the platform during reservation:** The active order remains linked to the Member's identity; the Member can return and continue the process if they log in again before the timer expires
- **Postconditions:**
  - An Active Order is created in the domain and linked to the user.
  - The selected tickets are locked in the inventory for the duration of the reservation.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful seat reservation — no raffl, no qeueu, logged in member (positive scenario)           | 1. Production Company "pc123" exists with founder "founder1".<br>2. Event "event123" (under "pc123") is active and does not require a raffle.<br>3. Show "show123" (under "event123") exists.<br>4. Ticket "ticket1" exists with showId="show123", zone="zone1", seat="seat1" and is available.<br>5. Member "user123" is logged in and requests for new order of ticket "ticket1".                                       | 1. new ActiveOrder is created and linked to member "user123".<br>2. Ticket "ticket1" is locked and no longer available.                                                                       |
| Successful seat reservation — valid raffle code, no queue, logged in member (positive scenario)  | 1. Production Company "pc123" exists with founder "founder1".<br>2. Event "event123" (under "pc123") requires a raffle with valid winning code "ABC123".<br>3. Show "show123" (under "event123") exists.<br>4. Ticket "ticket1" exists with showId="show123", zone="zone1", seat="seat1" and is available.<br>5. Member "user123" is logged in and requests for new order of ticket "ticket1" and provides code "ABC123". | 1. new ActiveOrder is created containing "ticket1" and linked to member "user123".<br>2. Ticket "ticket1" is locked and no longer available.                                                  |
| Successful standing area reservation - no raffle, no queue, logged in member (positive scenario) | 1-3 Same as in first case.<br>4. Tickets "ticket1" and "ticket2" exist with showId="show123", zone="zone1", no assigned seat (standing), and are available.<br>5. Member "user123" is logged in and requests for new order of 2 standing tickets.                                                                                                                                                                   | 1. new ActiveOrder is created containing "ticket1" and "ticket2" and linked to member "user123".<br>2. Both tickets are locked and no longer available.                                       |
| Invalid raffle code (negative scenario)                                                          | 1-4 as in second case.<br>5. Member "user123" is logged in and provides code "WRONG".                                                                                                                                                                                                                                                                                                                            | 1. System shows error message "Invalid Raffle Code"<br>2. No ActiveOrder is created and assign to member "user123".<br>3. Ticket "ticket1" remains available.                                    |
| Show not found (negative scenario)                                                               | 1. Member "user123" is logged in.<br>2. Member requests reservation for non-existent show "invalid_show".                                                                                                                                                                                                                                                                                                        | 1. System shows error message "Show not found"<br>2. No ActiveOrder is created and assign to member "user123".                                                                                |
| Seat unavailable — tickets taken (negative scenario)                                             | 1-4 as in first case.<br>5. Active Order 'A' is existing contains ticket "ticket1"<br>6. Member "user123" is logged in and requests "ticket1".                                                                                                                                                                                                                                                                      | 1. System shows error message "Seat unavailable"<br>2. No ActiveOrder is created and assign to member "user123".<br>3. Ticket "ticket1" remains locked.                                          |
| Active Order already exists to member (negative scenario)                                        | 1-4 as in first case.<br>5. Member "user123" already has an ActiveOrder "A" for event "event123".<br>6. Member attempts to create a second reservation.                                                                                                                                                                                                                                                             | 1. System shows error message "Order already exists"<br>2. No second ActiveOrder is created and assign to member "user123".<br>3. Active Order "A" remains unchanged, linked to member "user123". |
| Policy violation — exceeds ticket limit (negative scenario)                                      | 1. PC "pc123" exists.<br>2. Event "event123" (under "pc123") has a purchase policy limiting 4 tickets per member.<br>3. Show "show123" (under "event123") has 5 available tickets.<br>4. Member "user123" is logged in and attempts to reserve all 5.                                                                                                                                                                  | 1. System shows error message "Policy violation"<br>2. No ActiveOrder is created and assign to member "user123".<br>3. Tickets remain available.                                                 |


### UC6 - Edit Active Order (remove ticket)

- **Actor:** User
- **Trigger:** User requests to edit the active order.
- **Parameters:** Active Order ID, Ticket ID.
- **Preconditions:** Active Order exists.
- **Main Flow:**
  1. System: display the active order.
  2. User: selects the ticket to remove or to add.
  3. System: remove/add the ticket from the active order.
  4. System: update the inventory of the show.
  5. System: display a confirmation to the User.
- **Alternatives:**
  - ticket not found: System notifies the user that the ticket was not found in the active order.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful ticket removal (positive scenario) | 1. Member "user123" is logged in with an `ActiveOrder` containing tickets "ticket1" and "ticket2".<br>2. Member requests to remove "ticket1".           | 1. "ticket1" is removed from the `ActiveOrder` in `activeOrderRepo`.<br>2. "ticket1" is immediately released back to available stock in the `VenueMap`.<br>3. The `ActiveOrder` still contains "ticket2". |
| Ticket not in order (negative scenario)       | 1. Member "user123" is logged in with an `ActiveOrder` containing only "ticket2".<br>2. Member requests to remove "ticket99" which is not in the order. | 1. System throws an exception.<br>2. The `ActiveOrder` remains unchanged in `activeOrderRepo`.                                                                                                         |
| Active order not found (negative scenario)    | 1. Member "user123" is logged in but has no active order.<br>2. Member requests to remove "ticket1".                                                    | 1. System throws an exception.<br>2. No change is made to any repository.                                                                                                                              |


### UC7 - Checkout

- **Actor:** User
- **Trigger:** User requests to complete the reservation of a ticket.
- **Parameters:** Active Order ID.
- **Preconditions:** Active Order exists.
- **Main Flow:**
  1. System: ask for the Customer Details.
  2. User: enters the Customer Details.
  3. System: navigate to the Payment Service to process the payment.
  4. Payment Service: ask for the Payment Method.
  5. User: enters the Payment Method.
  6. Payment Service: process the payment.
  7. System: navigate to the Ticket Issuance Service to issue the tickets.
  8. Ticket Issuance Service: issue the tickets.
  9. System: create a new Order with the Active Order ID and the Customer Details.
  10. System: display a confirmation to the User.
- **Postconditions:** New Order and Customer Details are created in the system.
- **Expected Result:** User is notified that the order is completed and the payment is processed.
- **Alternatives:**
  - Payment failed: System notifies user that the payment failed and asks for a new Payment Method and display the active order again.
  - Tickets not issued: System notifies user that the tickets were not issued and Refund the payment.
  - Active Order expired: System notifies user that the active order expired and redirects them to the home page.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful checkout (positive scenario)  | 1. User "user123" is created.<br>2. The user creates production company "A".<br>3. The user creates event "E" under company "A" with available tickets.<br>4. A show is created under event "E".<br>5. The user reserves a ticket for the show (active order is created).<br>6. The user enters valid customer details and a valid payment method. | 1. The payment is processed successfully.<br>2. Tickets are issued.<br>3. A new order is created in the system.<br>4. The user receives a confirmation message.                                                |
| Payment failed (negative scenario)       | Same as steps 1–5 above.<br>6. The user enters invalid payment details.                                                                                                                                                                                                                                                                | 1. The system notifies the user that the payment failed.<br>2. The user is asked to enter a new payment method.<br>3. The active order is displayed again.                                                  |
| Tickets not issued (negative scenario)   | Same as steps 1–5 above.<br>6. The user enters valid payment details but the ticket issuance service fails.                                                                                                                                                                                                                            | 1. The system notifies the user that the tickets were not issued.<br>2. The payment is refunded.                                                                                                         |
| Active order expired (negative scenario) | Same as steps 1–5 above.<br>6. The active order expires before the user completes checkout.                                                                                                                                                                                                                                            | 1. The system notifies the user that the active order has expired.<br>2. The tickets under the expired order are released and become available to all users.<br>3. The user is redirected to the home page. |


## Member (includes Guest use cases)

### UC8 - Logout

- **Actor:** Member
- **Trigger:** User requests to log out
- **Parameters:** None
- **Preconditions:** User is logged in as an authenticated member-visitor
- **Main Flow:**
  1. System: if there is an active order — preserves the link between the active order and the member's identity.
  2. System: update member's status to logged out.
  3. System: transitions the user back to guest mode.
  4. System: displays the home screen in guest mode.
- **Alternatives:**
  - None — logout always succeeds.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Logout with an active order (positive)    | 1. Member "user123" is created.<br>2. user123 logs in.<br>3. Active order "A" created for event "event123" and show "show123" is created.<br>4. Active order "A" is linked to Member "user123".<br>5. Member "user123" performs Logout. | 1. Member "user123" status is logged out.<br>2. The active order "A" remains linked to Member "user123".<br>3. Home screen is displayed in guest mode.      |
| Logout without an active order (positive) | 1. Member "user123" is created.<br>2. user123 logs in.<br>3. No active order is linked to Member "user123".<br>4. Member "user123" performs Logout.                                                                                 | 1. Member "user123" status is logged out.<br>2. No active order is linked to Member "user123".<br>3. Home screen is displayed in guest mode without errors. |


### UC9 - View Order History

- **Actor:** Member
- **Trigger:** User requests to view personal purchase history
- **Parameters:** None
- **Preconditions:** User is logged in as a member
- **Main Flow:**
  1. System: retrieves all purchases made under the member's identity.
  2. System: displays the purchase details including past orders and ticket statuses for future events (even if the event was canceled or the production company was closed).
- **Alternatives:**
  - No purchases found: System displays an empty list.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| View populated order history (positive)              | 1. Member "user123" is registered and logs in.<br>2. ActiveOrder "activeOrder1" is created for event "event123" and show "show123".<br>3. Order "order1" is created from ActiveOrder "activeOrder1".<br>4. Order ID "order1" is assigned to Member "user123".<br>5. ActiveOrder "activeOrder2" is created for event "event123" and show "show123".<br>6. Order "order2" is created from ActiveOrder "activeOrder2".<br>7. Order ID "order2" is assigned to Member "user123".<br>8. Member "user123" requests order history.                                                                                                       | 1. System returns a list containing exactly 2 orders.<br>2. Both "order1" and "order2" appear in the list.                               |
| History unaffected by ticket price change (positive) | 1. Member "user123" is registered and logs in.<br>2. Ticket "ticket1" is created with price 100.0 for show "show123", zone "zone1", seat "seat1".<br>3. ActiveOrder "activeOrder1" is created and Ticket "ticket1" is added to it (subtotal = 100.0).<br>4. Order "order1" is created from ActiveOrder "activeOrder1" with finalPrice = 100.0.<br>5. Order ID "order1" is assigned to Member "user123".<br>6. Ticket "ticket1" price is changed to 200.0.<br>7. Member "user123" requests order history.                                                                                                                       | 1. System returns "order1" with finalPrice = 100.0.<br>2. The ticket price change does not affect the stored order.                      |
| History unaffected by show cancellation (positive)   | 1. Member "user123" is registered and logs in.<br>2. Show "show123" is created as active.<br>3. Ticket "ticket1" is created with price 100.0 for show "show123".<br>4. ActiveOrder "activeOrder1" is created and Ticket "ticket1" is added to it.<br>5. Order "order1" is created from ActiveOrder "activeOrder1".<br>6. Order ID "order1" is assigned to Member "user123".<br>7. Show "show123" status is changed to inactive.<br>8. Member "user123" requests order history.                                                                                                                                                    | 1. System returns "order1" with the original orderId and showId unchanged.<br>2. The show cancellation does not affect the stored order. |
| History unaffected by company closure (positive)     | 1. Member "user123" is registered and logs in.<br>2. Production Company "company123" is created with founder "user1".<br>3. Event "event123" is created under Company "company123".<br>4. Show "show123" is created under Event "event123".<br>5. Ticket "ticket1" is created with price 100.0 for Show "show123".<br>6. ActiveOrder "activeOrder1" is created and Ticket "ticket1" is added to it.<br>7. Order "order1" is created from ActiveOrder "activeOrder1".<br>8. Order ID "order1" is assigned to Member "user123".<br>9. Company "company123" status is changed to inactive.<br>10. Member "user123" requests order history. | 1. System returns "order1" with the original orderId and showId unchanged.<br>2. The company closure does not affect the stored order.   |
| View empty order history (positive)                  | 1. Member "user123" is registered and logs in.<br>2. No order IDs are assigned to Member "user123".<br>3. Member "user123" requests order history.                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 1. System returns an empty list.<br>2. A message is displayed indicating no purchases have been made yet.                                |


### UC10 - Register Event Raffle

- **Actor:** Member.
- **Trigger:** User requests to register for a raffle for a high-demand event.
- **Parameters:** Event ID.
- **Preconditions:** The event has a raffle mechanism enabled. User is logged in as a member.
- **Main Flow:**
  1. System: verifies the user is a logged-in member.
  2. System: registers the member in the raffle candidates list for the specific event.
- **Alternatives:**
  - Guest attempts to register: System rejects the request and prompts the user to register/log in.
  - Member already registered for this raffle: System rejects as a duplicate.
  - Raffle is full: System rejects the request and displays a message indicating the raffle is full.
  - Raffle is not active: System rejects the request and displays a message indicating the raffle is not active.
  - Raffle registration period has ended: System rejects the request and displays a message indicating the raffle registration period has ended.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful raffle registration (positive) | 1. Member "user123" is created and logs in.<br>2. Event with an active raffle (maxParticipants=100) is stored.<br>3. user123 requests to register for the raffle. | 1. No exception is thrown.<br>2. user123's email appears in the raffle's participants list. |
| Guest attempts to register for raffle (negative) | 1. Event with an active raffle is stored.<br>2. An unauthenticated request (null token) is sent to register for the raffle. | 1. System throws UnauthorizedException.<br>2. The raffle participants list remains empty. |
| Duplicate raffle registration (negative) | 1. Member "user123" is created and logs in.<br>2. Event with an active raffle is stored.<br>3. user123's email is manually added to the raffle participants.<br>4. user123 attempts to register again. | 1. System throws an exception.<br>2. The raffle participants list still contains exactly 1 entry (user123). |
| Raffle is full (negative) | 1. Member "user123" is created and logs in.<br>2. Event with a raffle (maxParticipants=2) is stored.<br>3. Two other participants are added, filling the raffle.<br>4. user123 attempts to register. | 1. System throws an exception.<br>2. The raffle participants list remains at 2 (unchanged). |
| Raffle is not active (negative) | 1. Member "user123" is created and logs in.<br>2. Event with a raffle is stored.<br>3. The raffle is set to inactive.<br>4. user123 attempts to register. | 1. System throws an exception.<br>2. The raffle participants list remains empty. |
| Raffle registration period has ended (negative) | 1. Member "user123" is created and logs in.<br>2. Event with a raffle is stored.<br>3. The raffle's registration deadline is set to yesterday.<br>4. user123 attempts to register. | 1. System throws an exception.<br>2. The raffle participants list remains empty. |


### UC11 - Edit Personal Details

- **Actor:** Member.
- **Trigger:** User requests to update their personal identification details.
- **Parameters:** Updated details- username/ password.
- **Preconditions:** User is logged in as a member.
- **Main Flow:**
  1. System: retrieves and displays the member's current username and password.
  2. User: enters new details to update.
  3. System: validates that is a new username and unique / a new password.
  4. System: change the Member details and confirms success.
- **Alternatives:**
  - Username already taken: System rejects the update and notifies the user the username is taken.
  - Same details as before: System rejects the update and notifies the user that the details need to be different.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully view and update personal details (positive) | 1. Member "user123" is created.<br>2. Member "user123" logs in.<br>3. Member "user123" requests to update their details.<br>4. Member "user123" enters new valid details and submits. | 1. Member "user123" details are updated.<br>2. A success confirmation is displayed.                                                                                                                 |
| Attempt to update to an existing username (negative)     | 1. Member "User1" is created.<br>2. Member "user123" is created.<br>3. Member "user123" logs in.<br>4. Member "user123" attempts to change their username to "User1".                | 1. System rejects the update request because the username is already taken.<br>2. Member "user123" details remain unchanged in the domain.<br>3. An appropriate error message is displayed.            |
| Attempt to update with an invalid password (negative)    | 1. Member "user123" is created.<br>2. Member "user123" logs in.<br>3. Member "user123" attempts to update their password with the same password as registered.                    | 1. System rejects the update request due to invalid password.<br>2. Member "user123" details remain unchanged in the domain.<br>3. System notifies Member "user123" that the details were not updated. |


### UC12 - Accept/Decline Appointment for Production Company Manager

- **Actor:** Target member who received an appointment notification.
- **Trigger:** Following UC19, an owner of a production company appointed the member as a manager; the system delivered a pending appointment notification to the member, who now responds to it.
- **Parameters:** Company ID, user response (accept / decline).
- **Preconditions:** The member is logged in and has a pending manager-appointment notification for the given production company.
- **Main Flow:**
  1. System: presents the pending appointment notification to the target member.
  2. Target member: accepts/declines the appointment.
  3. System: If accepted - grants the target member the manager role in the production company (with the permissions specified by the appointer in UC19).
  4. System: removes the pending appointment notification.
- **Alternatives:**
  - Target member already has a role in the company: System rejects the request and notifies the member.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully accept manager appointment (positive)         | 1. Member "user1" opens production company "company123" (becomes Founder).<br>2. Member "user123" is registered.<br>3. user1 appoints user123 as a manager of "company123" — system creates a pending appointment notification for user123.<br>4. user123 logs in and accepts the appointment.                  | 1. System grants user123 the Manager role in "company123" with the permissions specified in the appointment.<br>2. user123's pending appointment notification is removed.<br>3. System confirms the role assignment. |
| Target member declines appointment (positive)              | 1. Member "user1" opens production company "company123" (becomes Founder).<br>2. Member "user123" is registered.<br>3. user1 appoints user123 as a manager of "company123" — system creates a pending appointment notification for user123.<br>4. user123 logs in and declines the appointment.                  | 1. No role is granted; user123 retains their regular member status with no new permissions.<br>2. user123's pending appointment notification is removed.<br>3. System confirms the declination.                       |
| Target member already has a role in the company (negative) | 1. Member "user1" opens production company "company123" (becomes Founder).<br>2. Member "user123" is registered.<br>3.user123 has an Owner role in "company123".<br>4. user1 appoints user123 as a manager of "company123" — system creates a pending appointment notification for user123.<br>5. user123 logs in and accepts the manager appointment. | 1. System throws an exception and rejects the acceptance.<br>2. user123's existing Owner role in "company123" is unchanged. |


### UC13 - Open Production Company

- **Actor:** Member.
- **Trigger:** Member open a new production company.
- **Parameters:** Company name, identification details.
- **Preconditions:** User is logged in as a member.
- **Main Flow:**
  1. System: verifies the company name is unique.
  2. System: registers the company and designates the requesting member as "Founder" and first Owner.
  3. System: sets the company status to Active.
- **Alternatives:**
  - Company name already exists: System rejects the request.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully open a production company (positive) | 1. Member "user123" is registered and logs in.<br>2. "user123" submits a request to open a new production company with a unique name "company123".                                                  | 1. System verifies the company name is not already in the database.<br>2. New company is created and saved.<br>3. New Role "Founder" is created and assigned to Member "user123" and PC "company123".<br>4. Company status is set to Active. |
| Open a company with an existing name (negative)   | 1. Production company "company123" is created with founder "user1".<br>2. Member "user2" is registered and logs in.<br>3. user2 attempts to open a new production company using the name "company123". | 1. System detects the name is not unique.<br>2. Request is rejected — no new company is created and no role is assigned.<br>3. An appropriate error is thrown.                                                                            |


## Manager

### UC14 - Manage Event (create, edit, remove) - including inventory management

- **Actor:** Manager (with Event Editing permission) / Owner
- **Trigger:** Manager requests to manage (add, edit, remove) an event or its ticket inventory.
- **Parameters:** Event details, dates/shows, inventory details (seating areas, rows/seats, standing area capacity).
- **Preconditions:** The Manager is logged in and has been granted the "Event Editing" permission for the active Production Company.
- **Main Flow:**
  1. System: presents the catalog of events and options to add, edit, or remove an event.
  2. Manager: selects an action (e.g., add a new event or edit an existing one) and enters the event details (type, dates/shows) and inventory details (seating areas, rows/seats, standing ticket quantities).
  3. System: validates the provided details.
  4. System: updates the event catalog and ticket inventory for the production company.
  5. System: notifies the Manager that the operation was successful.
- **Alternatives:**
  - Unauthorized access: Manager lacks the permission; System blocks access and notifies the Manager.
  - Invalid or missing parameters: Manager enters invalid quantities or incomplete details; System notifies the Manager of the specific error.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful creation / edit / removal of an event by a manager (positive scenario) | 1. User "user123" is logged in as a Manager in Production Company "company123" with "Event Editing" permissions.<br>2. Manager creates a new event with valid details (name, start date, end date, capacity), or edits an existing event's details, or removes an existing event with no shows. | 1. System saves / updates / deletes the event.<br>2. Repository reflects the change accurately.                              |
| Successful creation / edit / removal of an event by a founder (positive scenario) | 1. User "user123" is logged in and is the Founder of Production Company "company123".<br>2. Founder creates a new event with valid details, or edits an existing event's details, or removes an existing event with no shows.                                                                   | 1. System saves / updates / deletes the event.<br>2. Repository reflects the change accurately.                              |
| Attempt to manage event without proper permissions (negative scenario)            | 1. User "user123" is logged in as a Manager in "company123" with default permissions only (no "Event Editing").<br>2. Manager attempts to create, edit, or remove an event.                                                                                                                     | 1. System throws an exception and denies the request.<br>2. No changes are made to the repository.                           |
| Invalid event details on creation or edit (negative scenario)                     | 1. User "user123" is logged in as a Manager with correct permissions.<br>2. Manager submits an EventDTO with one of the following invalid inputs: null or blank name, null start date, null end date, end date before start date, or negative total capacity.                                   | 1. System throws an exception for each invalid input.<br>2. No event is created or modified in the repository.               |
| Removal of an event that has associated shows (positive scenario)                 | 1. User "user123" is logged in as a Manager with "Manage Event Inventory "Event Editing" permissions.<br>2. Event "event123" exists with at least one associated show.                                                                                                                          | 1. System removes the event from the event repository.<br>2. All associated shows are also removed from the show repository. |


### UC15 - Define Hall Layout in Event Map

- **Actor:** Manager (with Define Hall Layout permission) / Owner
- **Trigger:** Manager requests to define the seating configuration and visual layout map for an existing show.
- **Parameters:** Visual map representation, logical zones mapping, seating configurations (rows, seats, blocks, or standing capacities) per zone.
- **Preconditions:** The Manager is logged in and has the "Define Hall Layout in Event Map" permission for the company.
- **Main Flow:**
  1. System: presents the interface for configuring a graphical hall layout and defining logical seating regions.
  2. Manager: defines the visual map and maps the visual regions to logical zones (defining rows and seats for seated areas, and max capacities for standing areas).
  3. System: validates that the map properly connects visual zones to the defined inventory capacity and seating setup, reflecting live status (available/taken).
  4. System: stores the finalized layout for the specific event.
  5. System: verifies the map's capacity accurately mirrors the total allowed inventory and displays success.
- **Alternatives:**
  - Detail mismatch: Manager configures 200 seats in the layout, but the inventory only has 150 available. System alerts the discrepancy and prevents saving.
  - Unauthorized access: Manager lacks the permission; System blocks access.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully define a valid map (positive scenario) | 1. Member "user123" is logged in and assigned as a Manager with EVENT_EDITING permission in company "company123".<br>2. An event is created under "company123".<br>3. A show is created for that event with totalCapacity=100.<br>4. Manager submits a VenueMapDTO with 1 zone of 10 rows × 10 seats (totalCapacity=100). | 1. No exception is thrown.<br>2. The venue map is persisted in the repository with totalCapacity=100 and linked to the show. |
| Configuration vs Inventory mismatch (negative scenario) | 1. Member "user123" is logged in and assigned as a Manager with EVENT_EDITING permission in company "company123".<br>2. A show is created with totalCapacity=50.<br>3. Manager submits a VenueMapDTO with 1 zone of 6 rows × 10 seats (totalCapacity=60). | 1. System throws InvalidShowException.<br>2. No venue map is saved for the show. |
| Non-existent show (negative scenario) | 1. Member "user123" is logged in and assigned as a Manager with EVENT_EDITING permission in company "company123".<br>2. Manager attempts to define a hall layout for show id "non-existent-show". | 1. System throws ShowNotFoundException.<br>2. No show is found in the repository.<br>3. No venue map is saved. |
| Venue map already exists (negative scenario) | 1. Member "user123" is logged in and assigned as a Manager with EVENT_EDITING permission in company "company123".<br>2. A show is created with totalCapacity=100 and a venue map is already saved for it.<br>3. Manager attempts to define a new hall layout for the same show. | 1. System throws VenueMapAlreadyExistsException.<br>2. The original venue map is not overwritten (totalCapacity remains 0). |
| Manager without permission is denied (negative scenario) | 1. Member "user123" is logged in and assigned as a Manager in company "company123" with no EVENT_EDITING permission.<br>2. A show is created with totalCapacity=100.<br>3. Manager attempts to define a hall layout for the show. | 1. System throws Exception.<br>2. No venue map is saved. |



### UC16 - Change Purchase/Discount Policy for Production Company

- **Actor:** Manager (with Policy Management permissions) / Owner
- **Trigger:** Manager requests to update how tickets are sold (e.g., regular vs lottery) or change the purchase restrictions and discount structures.
- **Parameters:** Policy types (Early bird, limit rules), discount values, active time ranges.
- **Preconditions:** Manager is logged in with "Change Purchase/Discount Policy" permissions for the associated company.
- **Main Flow:**
  1. System: displays the active policies (sales methods, discount definitions) for the company and its events.
  2. Manager: selects to Add/Edit a policy and inputs new rules (e.g., maximum 4 tickets per buyer, or applying a 10% discount).
  3. System: validates the policy parameters (e.g., ensuring no negative discounts, valid date ranges).
  4. System: persists the new policies.
  5. System: notifies the Manager of the successful update.
- **Alternatives:**
  - Conflicting policies: Manager creates a policy contradicting a systemic rule. System alerts the Manager and blocks the action.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully impose a new ticket cap (positive scenario) | 1. Manager with permission logs in.<br>2. Manager updates the Purchase Policy for "Concert X" to maximum 4 tickets per user. | 1. Policy is successfully saved.<br>2. Any buyer attempting to checkout >4 tickets for "Concert X" is blocked by the new rule. |
| Set invalid date for discount (negative scenario)        | 1. Manager attempts to add an Early Bird discount ending *after* the event happens.                                       | 1. System validates date logic.<br>2. System rejects the discount rule and shows a date discrepancy error.                     |


### UC17 - View Event Purchase History

- **Actor:** Manager (with View Purchase History permissions) / Owner
- **Trigger:** Manager requests a historical view of all purchased tickets and transactions for the company's events.
- **Parameters:** Event id, date ranges.
- **Preconditions:** Manager is logged in with "View Purchase History" permissions.
- **Main Flow:**
  1. System: queries the database for all purchased tickets linked to the specific event.
  2. System: retrieves the immutable purchase records (which remain unaffected by later platform changes such as event cancellation, location change, or subsequent price modifications).
  3. System: visualizes the raw purchase data to the Manager including original purchase prices and ticket counts.
- **Alternatives:**
  - Unauthorized access: Manager lacks the permission; System blocks access.
  - Non-existent event: System throws Exception.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful view by manager with permission (positive scenario) | 1. Member "manager1" is created and assigned as a Manager in Production Company "company123" with VIEW_PURCHASE_HISTORY permission.<br>2. An order exists for an event under "company123".<br>3. manager1 requests the purchase history for that event. | 1. No exception is thrown.<br>2. The returned list contains the order with correct fields (price, purchaseTime, customerDetails). |
| Successful view by owner (positive scenario) | 1. Member "owner1" is created and is an Owner of Production Company "company123".<br>2. An order exists for an event under "company123".<br>3. owner1 requests the purchase history for that event. | 1. No exception is thrown.<br>2. The returned list contains the order with correct fields. |
| Manager without permission is denied (negative scenario) | 1. Member "manager1" is created and assigned as a Manager in Production Company "company123" with default permissions only (no VIEW_PURCHASE_HISTORY).<br>2. manager1 requests the purchase history for an event under "company123". | 1. System throws an exception.<br>2. No purchase data is returned. |
| Non-existent event (negative scenario) | 1. Member "manager1" is created with VIEW_PURCHASE_HISTORY permission in "company123".<br>2. manager1 requests the purchase history for a non-existent event ID. | 1. System throws Exception.<br>2. No data is returned. |
| Purchase history is unaffected by post-purchase event changes (positive scenario) | 1. An order is created with finalPrice=100 for event "E" (location="Tel Aviv", name="Concert", event active).<br>2. After purchase: ticket price is updated, event location is changed, event name is changed, show is set inactive, production company is closed.<br>3. Manager requests purchase history for event "E". | 1. The returned order still shows finalPrice=100.<br>2. All original order fields (price, purchaseTime, customerDetails) are unchanged and unaffected by any post-purchase modification. |


### UC18 - Generate Sales Report for Events Under Manager

- **Actor:** Manager (with Generate Sales Report permissions) / Owner / Founder
- **Trigger:** Manager requests a consolidated sales report detailing total revenues and tickets sold.
- **Parameters:** Report filters (e.g., specific timeframes or events).
- **Preconditions:** Manager is logged in with "Generate Sales Report" permissions.
- **Main Flow:**
  1. System: identifies all events directly managed by the user, PLUS all events managed by the user's "appointment subtree" (anyone they appointed as a manager/owner, and the managers appointed under them recursively).
  2. System: computes exact sales revenues and total tickets exclusively for those identified events.
  3. System: dynamically generates the report and presents it to the Manager.
- **Alternatives:**
  - Subtree calculates empty: User has no managed events, nor do their subordinates. System computes an empty report showing 0 items.
  - Actor not having permission: The system notifies the Actor that he does not have permission to generate a sales report.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Sub-tree hierarchy accurately represented (positive scenario) | 1. User "A" appointed User "B".<br>2. User "A" requests their sales report.<br>3. User "B" has "Event B1" which sold 50 tickets. User "A" has "Event A1" which sold 20 tickets. | 1. User "A"'s sales report totals 70 tickets.<br>2. It correctly comprises "Event A1" (direct) and "Event B1" (subtree).             |
| Prevent viewing sibling nodes in tree (positive scenario)     | 1. Users "B1" and "B2" are both appointed by "A".<br>2. User "B1" runs their sales report.<br>3. "B2" has events that successfully sold tickets.                                | 1. "B1"'s generated report exclusively shows their own strictly subordinate sales.<br>2. "B2"'s sales are entirely hidden from "B1". |
| Actor not having permission (negative scenario) | 1. User "A" is not appointed by "B".<br>2. User "A" runs their sales report. | 1. System notifies the Actor that he does not have permission to generate a sales report. |
| No sales (positive scenario) | 1. User "A" has no managed event with successfully sold tickets. | 1. System generates an empty sales report. |

## Owner (includes Manager use cases)

### UC19 - Appoint Owner/Manager

- **Actor:** Production company Owner/Manager
- **Trigger:** Owner/Manager requests to appoint a member to a specific role within the production company.
- **Parameters:** Member ID to appoint, Role type to assign (Owner/Manager), Production Company ID.
- **Preconditions:** The Member is a registered Member.
- **Main Flow:** 
  1. (If assigning a Manager) Actor: Selects the specific permissions.
  2. System: Sends an appointment proposal to the Member.
  3. Target Member: Approves the appointment proposal (UC12)
  4. System: Creates a new role for the member and production company.
  5. System: saves the permissions for the member in the production company.
- **Alternatives:**
  - user to appoint not a member: The system notifies the Actor that the user to appoint was not a member.
  - Member already has role in this production company: The system notifies the Actor that the Member already has the role in this production company.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful appointment of a Manager (positive scenario)                  | 1. Owner "A" is a member of Production Company "B".<br>2. Owner "A" requests to appoint Member "C" as a Manager in Production Company "B".<br>3. Member "C" is a registered member.                               | 1. System creates notification with appointment proposal and a link to the Notification to Member "C".                                                                            |
| Failed appointment due to non-member target (negative scenario)          | 1. Owner "A" is a member of Production Company "B".<br>2. Owner "A" requests to appoint User "C" as a Manager in Production Company "B".<br>3. User "C" is *not* a registered member.                             | 1. System notifies the Owner that User "C" is not a member.<br>2. Notification is not sent to Member "C".<br>3. Production Company "B" stays unchanged.                                 |
| Failed appointment due to member already having role (negative scenario) | 1. Owner "A" is a member of Production Company "B".<br>2. User "C" is a registered member and Owner of Production Company "B".<br>3. Owner "A" requests to appoint User "C" as a Owner in Production Company "B". | 1. System notifies the Owner that User "C" already has the role in this production company.<br>2. Notification is not sent to Member "C".<br>3. Production Company "B" stays unchanged. |


### UC20 - Remove Owner/Manager Appointment

- **Actor:** Production company Owner/Manager
- **Trigger:** User requests to remove a role from a member they previously appointed.
- **Parameters:** Member ID to remove, Production Company ID.
- **Preconditions:** The Member is a registered Member and has a role in the production company.
- **Main Flow:**
  1. System: Removes the Member's role and associated permissions from the production company.
  2. System: Notifies the Member of the removal.
- **Alternatives:**
  - Member not having role: The system notifies the Actor that the Member does not have a role in the production company.
  - The Actor wants to remove someone that he did not appoint: The system notifies the Actor that he cannot remove someone that he did not appoint.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful removal of a Manager (positive scenario)              | 1. Owner "A" is a member of Production Company "B".<br>2. Owner "A" appointed User "C" as a Manager in Production Company "B".<br>3. Owner "A" requests to remove User "C" as a Manager in Production Company "B".     | 1. System removes the Manager role from User "C" in Production Company "B".<br>2. System removes the permissions for User "C" in Production Company "B".        |
| Failed removal due to non-appointed target (negative scenario)   | 1. Owner "A","D" are members of Production Company "B".<br>2. Owner "A" appointed User "C" as a Manager in Production Company "B".<br>3. Owner "D" requests to remove User "C" as a Manager in Production Company "B". | 1. System notifies the Owner that User "D" cannot remove someone that he did not appoint.<br>2. Production Company "B" still has the Manager role for User "C". |
| Failed removal due to member not having role (negative scenario) | 1. Owner "A" is a member of Production Company "B".<br>2. User "C" is a registered member.<br>3. Owner "A" requests to remove User "C" as a Manager in Production Company "B".                                         | 1. System notifies the Owner that User "C" does not have a Manager role in this production company.<br>2. Production Company "B" stays unchanged.               |


### UC21 - Relinquish Ownership of Production Company

- **Actor:** Owner
- **Trigger:** Owner requests to relinquish their ownership of a production company.
- **Parameters:** Production Company ID.
- **Preconditions:**
  1. The user is logged in to the system.
  2. The user is recognized as an owner and not as a founder of the company.
- **Main Flow:**
  1. System: asks for the Production Company ID from which the user wishes to relinquish ownership.
  2. User: enters the Production Company ID.
  3. System: validates that the ID exists and that the user is currently an Owner of this company.
  4. System: removes the user from the owners list of the company.
- **Alternatives:**
  - Invalid ID / Not an owner: the system displays an error: "Invalid Company ID or insufficient permissions."

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful Relinquish (positive scenario) | 1. Member "founder" is created and opens company "company123" (becomes Founder).<br>2. Member "owner1" is created and added as an Owner of "company123".<br>3. "owner1" logs in and calls relinquishOwnership for "company123". | 1. No exception is thrown.<br>2. "owner1" no longer has a role in "company123".<br>3. The Founder role of "founder" is unchanged. |
| Attempt by Founder (negative scenario) | 1. Member "founder" is created and opens company "company123" (becomes Founder).<br>2. "founder" logs in and calls relinquishOwnership for "company123". | 1. System throws an exception.<br>2. The Founder role of "founder" in "company123" remains unchanged. |
| Attempt by non-owner member (negative scenario) | 1. Member "founder" is created and opens company "company123".<br>2. Member "user123" is created (has no role in "company123").<br>3. "user123" logs in and calls relinquishOwnership for "company123". | 1. System throws an exception.<br>2. The roles in "company123" remain unchanged. |
| Attempt by owner of a different company (negative scenario) | 1. Member "founder" is created and opens company "company123".<br>2. Member "founder2" is created and opens company "companyA".<br>3. Member "owner1" is created and added as an Owner in "companyA" (has no role in "company123").<br>4. "owner1" logs in and calls relinquishOwnership for "company123". | 1. System throws an exception.<br>2. The roles in "company123" remain unchanged.<br>3. The Owner role of "owner1" in "companyA" remains unchanged. |


### UC22 - Add/Remove Manager Permissions

- **Actor:** Owner
- **Trigger:** Owner requests to add/remove the permissions assigned to an existing manager.
- **Parameters:** Company ID, manager ID.
- **Preconditions:** The user is an owner of the company and the target user is already an assigned Manager in the company.
- **Main Flow:**
  1. System: asks for the company ID and the manager ID.
  2. User: enters the company ID and the Manager ID.
  3. System: validates that the user is an owner of the company and that the Manager ID exists and is linked to a manager in this company.
  4. System: displays the current permissions of the selected manager.
  5. System: asks for the updated set of permissions.
  6. User: selects the new permissions for the manager.
  7. System: updates the manager's permissions in the system.
- **Alternatives:**
  - Target is not a Manager: If the ID belongs to another Owner or a Guest, the system notifies: "Action denied: Permissions can only be modified for users with a Manager role."
  - The Actor is not the oner that appointed the manager: The system notifies the Actor that he is not the Owner that appointed the manager.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful Permission Addition (positive scenario)        | 1. "Founder" opens "Comp_1" and appoints "Owner_A" as owner.<br>2. "Owner_A" appointed "Manager_B" as a manager in "Comp_1" with `VIEW_MANAGEMENT_HIERARCHY` permission.<br>3. "Owner_A" adds `EVENT_EDITING` to "Manager_B"'s permissions.                              | 1. "Manager_B"'s permissions now include both `VIEW_MANAGEMENT_HIERARCHY` and `EVENT_EDITING`.<br>2. System confirms the update.            |
| Successful Permission Removal (positive scenario)         | 1. "Founder" opens "Comp_1" and appoints "Owner_A" as owner.<br>2. "Owner_A" appointed "Manager_B" as a manager in "Comp_1" with `VIEW_MANAGEMENT_HIERARCHY` and `EVENT_EDITING` permissions.<br>3. "Owner_A" removes `EVENT_EDITING` from "Manager_B"'s permissions. | 1. "Manager_B"'s permissions retain only `VIEW_MANAGEMENT_HIERARCHY`; `EVENT_EDITING` is no longer present.<br>2. System confirms the update. |
| Target is not a Manager (negative scenario)               | 1. "Founder" opens "Comp_1" and appoints "Owner_A" as owner.<br>2. "Owner_A" appoints "Owner_B" as another owner.<br>3. "Owner_A" attempts to add `EVENT_EDITING` to "Owner_B"'s permissions / remove `EVENT_EDITING` from "Owner_B"'s permissions.                                                     | 1. System throws an exception.<br>2. "Owner_B" still holds the Owner role; no permissions are changed.                       |
| Actor is not the appointer of the manager (negative scenario) | 1. "Founder" opens "Comp_1" and appoints "Owner_A" and "Owner_C" as owners.<br>2. "Owner_A" appointed "Manager_B" as a manager with `VIEW_MANAGEMENT_HIERARCHY` permission.<br>3. "Owner_C" (who did not appoint "Manager_B") attempts to add `EVENT_EDITING` to "Manager_B"'s permissions / remove `VIEW_MANAGEMENT_HIERARCHY` from "Manager_B"'s permissions. | 1. System throws an exception.<br>2. "Manager_B"'s permissions remain unchanged (only `VIEW_MANAGEMENT_HIERARCHY`).            |


### UC23 - View Role and Permission Information

 - **Actor:** Founder / Owner / Manager   
 - **Trigger:** Founder / Owner / Manager requests to view the list of managers/owners and their assigned permissions in the company.  
 - **Parameters:** Production Company ID.  
 - **Preconditions:** The user has a role in the company and permission to view the management hierarchy.

**Main Flow:**

1. **System:** asks for the Production Company ID.
2. **User:** enters the Production Company ID.
3. **System:** validates that the user has a role in the company and permission to view the management hierarchy.
4. **System:** retrieves the list of all users holding roles (Owner/Manager) in the company and their permissions.
5. **System:** displays the list of users, their roles, and their permissions.

**Alternatives:**

- **Invalid ID / Not an owner:** The system displays an error: "Unauthorized: You must be an owner of this company to view this information."
- **Manager with no permissions:** The system displays an error: "Unauthorized: You do not have permission to view the management hierarchy."

---

### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful View Role and Permission Info - Manager / Owner / Founder (positive scenario)            | 1. Member "founder" is created and opens company "company123" (becomes Founder).<br>2. Member "owner1" is created and added as an Owner of "company123".<br>3. Member "manager1" is created and added as a Manager of "company123" with `VIEW_MANAGEMENT_HIERARCHY` permission.<br>4. "founder" logs in and calls getAllRoles for "company123" / "owner1" logs in and calls getAllRoles for "company123" / "manager1" logs in and calls getAllRoles for "company123". | 1. System displays all users with roles in "company123".<br>2. Their specific permissions are clearly listed.                              |
| Manager with no view-hierarchy permission (negative scenario) | 1. Member "founder" opens company "company123" (becomes Founder).<br>2. Member "manager1" is added as a Manager of "company123" with a permission set that does NOT include `VIEW_MANAGEMENT_HIERARCHY`.<br>3. "manager1" logs in and calls getAllRoles for "company123".                                                                              | 1. System throws an exception.<br>2. No personnel or permission information is disclosed. |
| Member with no role in the company (negative scenario)        | 1. Member "founder" opens company "company123" (becomes Founder).<br>2. Member "owner1" is registered but has NO role in "company123".<br>3. "owner1" logs in and calls getAllRoles for "company123".                                                                                                                                                | 1. System throws an exception.<br>2. No personnel or permission information is disclosed. |
| Owner of a different company (negative scenario)              | 1. Member "founder" opens company "company123" (becomes Founder).<br>2. Member "founder2" opens company "companyA".<br>3. Member "owner1" is added as an Owner of "companyA" only (no role in "company123").<br>4. "owner1" logs in and calls getAllRoles for "company123".                                                                          | 1. System throws an exception.<br>2. No personnel or permission information is disclosed. |


### UC24 - Suspend Production Company

- **Actor:** Founder
- **Trigger:** The Founder requests to suspend the production company
- **Parameters:** Production Company ID.
- **Preconditions:** The requesting user is the Founder of the specified production company. The production company is currently active in the system.
- **Main Flow:**
  1. System: verifies that the requesting user is the Founder of the specified production company.
  2. System: verifies that the production company's current status is active.
  3. System: changes the production company's status to "Inactive".
  4. System: changes all the shows in the production company to "Inactive".
  5. System: sends notifications to all Owners and Managers regarding the suspension/closure.

- **Alternatives:**
  - The Actor is not the Founder: the system throws an exception, the system does not allow him to suspend/close the production company. 
  - Production company not active: the system throws an exception.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successful suspension/closure of a production company (positive scenario) | 1. Member "founder" opens production company "company123" (becomes Founder).<br>2. Member "owner1" is added as an Owner of "company123".<br>3. Member "manager1" is added as a Manager of "company123".<br>4. An active event is stored under "company123".<br>5. "founder" logs in and calls suspendProductionCompany for "company123". | 1. System sets the production company's status to Inactive.<br>2. System sets the company's events to Inactive.<br>3. System sends a "Company Suspended" notification to each Owner and Manager (one queued in the notification repository per member). |
| Failed suspension/closure due to non-founder (negative scenario)           | 1. Member "founder" opens production company "company123" (becomes Founder).<br>2. Member "owner1" is added as an Owner of "company123" (non-founder).<br>3. "owner1" logs in and calls suspendProductionCompany for "company123".                          | 1. System throws an exception.<br>2. "company123" remains Active.                            |
| Failed suspension/closure due to production company not active (negative) | 1. Member "founder" opens production company "company123" (becomes Founder).<br>2. "company123" is already Inactive. <br>3. "founder" logs in and calls suspendProductionCompany for "company123".         | 1. System throws an exception.<br>2. "company123" remains Inactive (no further state change). |


### UC25 - Reopen Closed Production Company

- **Actor:** Founder
- **Trigger:** The Founder of a production company requests to reopen it.
- **Parameters:** Company name.
- **Preconditions:** The requesting user is the Founder of that production company. The production company is currently suspended.
- **Main Flow:**
  1. System: verifies that the requesting user is the Founder of the specified production company.
  2. System: verifies that the production company's current status is suspended.
  3. System: sets the production company status back to Active.
  4. System: confirms the reopening to the Founder.
  5. System: sends a notification to all owners and managers of the production company informing them that it has been reopened.
- **Alternatives:**
  - Production company is already active: System rejects the request and notifies the Founder.
  - Requesting user is not the Founder: System rejects the request.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully reopen a suspended production company (positive) | 1. Member "founder" opens production company "company123" (becomes Founder).<br>2. Member "owner1" is added as an Owner of "company123".<br>3. Member "manager1" is added as a Manager of "company123".<br>4. "company123" is set to Inactive.<br>5. "founder" logs in and calls reopenProductionCompany for "company123". | 1. System sets the production company's status back to Active.<br>2. System sends a "Company Reopened" notification to each Owner and Manager. |
| Non-founder attempts to reopen a production company (negative) | 1. Member "founder" opens production company "company123" (becomes Founder).<br>2. Member "owner1" is added as an Owner of "company123" (non-founder).<br>3. "company123" is set to Inactive.<br>4. "owner1" logs in and calls reopenProductionCompany for "company123".                                                                                | 1. System throws an exception.<br>2. "company123" remains Inactive. |
| Attempt to reopen an already active production company (negative) | 1. Member "founder" opens production company "company123" (becomes Founder; company is Active by default).<br>2. "founder" calls reopenProductionCompany for "company123".                                                                                                                                                                                                            | 1. System throws an exception.<br>2. "company123" remains Active (no state change). |


## System Admin

### UC26 - Close Production Company

- **Actor:** System Admin
- **Trigger:** System Admin requests to close a production company due to violation of terms, fraud, or mass cancellations.
- **Parameters:** Production company ID.
- **Preconditions:** The production company exists and is active in the system.
- **Main Flow:**
  1. System: changes the company's status to "Closed".
  2. System: automatically revokes all appointments and permissions of the company's Owners and Managers within that specific company.
  3. System: sends notifications to the affected Owners and Managers about the closure of the company.
- **Postconditions:** The company's status in the system is "Closed" and Notifications have been sent to the affected Owners and Managers.
- **Expected Result:** The company is no longer visible to users.
- **Alternatives:**
  - Unauthorized access: A user without System Admin privileges attempts to close a company. The system blocks the action and displays an error message about insufficient permissions.
  - Company already closed: A System Admin attempts to close a company that is already in "Closed" status. The system prevents redundant actions and may display a message indicating the company is already closed.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully close a production company by a platform admin (positive scenario)       | 1. User "123" is created and designated as a System Admin.<br>2. User "234" is created.<br>3. User "234" logs in and creates production company "A".<br>4. User "123" logs in and accesses the system admin panel.<br>7. admin executes the close company function on company "A" and confirms the action.                                                                                                          | 1. The system changes company "A"'s status to "Closed" in the database.<br>2. The system automatically revokes all appointments and permissions of the company's Owners and Managers within company "A".<br>3. The system sends notifications to all affected role holders about the company closure and end of their positions.<br>4. Company "A" and its events no longer appear in search results for platform visitors. |
| Attempt to close a company by a non-admin user (negative scenario)                    | 1. User "123" is created.<br>2. User "234" is created.<br>3. User "234" logs in and creates production company "A".<br>4. User "123" logs in (but is not designated as a System Admin).<br>7. user "123" attempts to access the system admin panel and execute a close command on company "A".                                                                                                                      | 1. The system blocks the action and identifies that user "123" lacks System Admin permissions.<br>2. Company "A"'s status remains "Active", appointments are preserved, and no notifications are sent.<br>3. An error message is displayed to user "123" about insufficient system authority.                                                                                                                            |
| Attempt to close a company that is already closed (negative scenario)                 | 1. User "123" is created and designated as a System Admin.<br>2. User "234" is created.<br>3. User "234" logs in and creates production company "A".<br>4. User "123" logs in and successfully closes company "A" (status set to "Closed").<br>7. User "234" attempts to execute the close command on company "A" a second time.                                                                                    | 1. The system rejects the action and prevents redundant processing.<br>2. No additional changes are made, and no duplicate closure notifications are sent to users.<br>3. Company "A" remains in "Closed" status.                                                                                                                                                                                                        |
| Founder role and notification are preserved after closure (positive scenario)         | 1. User "admin" is created and designated as a System Admin.<br>2. User "founder" is created and logs in and creates production company "A".<br>3. User "owner" is created and "founder" appoints "owner" as an Owner in company "A".<br>4. User "manager" is created and "founder" appoints "manager" as a Manager in company "A".<br>5. admin executes the close company function on company "A".                 | 1. Company "A"'s status is set to "Closed".<br>2. The Founder role of "founder" in company "A" is preserved and not revoked.<br>3. The Owner role of "owner" and the Manager role of "manager" in company "A" are revoked.<br>4. "founder" receives a closure notification despite retaining the Founder role.<br>5. Both "owner" and "manager" receive closure notifications.                                                 |
| Closure of one company does not affect other companies (positive scenario)            | 1. User "admin" is created and designated as a System Admin.<br>2. User "founderA" is created and creates production company "A".<br>3. User "founderB" is created and creates production company "B".<br>4. "founderA" appoints user "ownerA" as an Owner in company "A".<br>5. "founderB" appoints user "managerB" as a Manager in company "B".<br>6. admin executes the close company function on company "A" only. | 1. Company "A"'s status is set to "Closed" and the Owner role of "ownerA" is revoked.<br>2. Company "B" remains active with its Founder and Manager roles fully intact.<br>3. Closure notifications are sent only to "founderA" and "ownerA".<br>4. "founderB" and "managerB" do not receive any notifications.                                                                                                             |
| Closure does not cancel the platform membership of affected users (positive scenario) | 1. User "admin" is created and designated as a System Admin.<br>2. User "founder" creates production company "A".<br>3. User "founder" appoints user "owner" as an Owner and user "manager" as a Manager in company "A".<br>4. admin executes the close company function on company "A".                                                                                                                         | 1. Company "A" is closed and the roles of "owner" and "manager" are revoked.<br>2. The platform membership status of "founder", "owner", and "manager" remains active.<br>3. None of the affected users are removed from the platform.<br>4. The role removal in company "A" does not change the users' general membership status on the platform.                                                                          |
| Concurrent close attempts on the same company by two admins (positive scenario)       | 1. Users "admin1" and "admin2" are both designated as System Admins.<br>2. User "founder" creates production company "A" with one Owner "owner".<br>3. "admin1" and "admin2" simultaneously execute the close company function on company "A".                                                                                                                                                                | 1. Exactly one of the close attempts succeeds.<br>2. The other attempt is rejected without producing any side effects.<br>3. Company "A" is closed exactly once.<br>4. Closure notifications for "founder" and "owner" are sent exactly once each, with no duplicates.                                                                                                                                                      |


---

### UC27 - Cancel Platform membership

- **Actor:** System Admin
- **Trigger:** System Admin initiates membership cancellation due to suspicion of fraud, bot operations, or serious violation of terms.
- **Parameters:** member ID.
- **Preconditions:** An active member account exists for the user in the system.
- **Main Flow:**
  1. System: Deactivates the user's membership account.
  2. System: Automatically revokes all roles and permissions held by the user across all production companies.
- **Postconditions:** The membership account is terminated; all role assignments and access rights are removed from the system.
- **Expected Result:** The user can no longer log in to the system, and their name is removed from the appointment trees of all companies.
- **Alternatives:**
  - Unauthorized access: A user without System Admin privileges attempts to cancel a subscription. The system blocks the action and displays an error message about insufficient permissions.
  - User does not exist: A System Admin attempts to cancel a subscription for a user that does not exist in the system. The system rejects the action and notifies the admin.
  - Member is founder of an active production company: The action is illegal. The system rejects the request and the member's subscription remains active.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully cancel subscription and remove all roles (positive scenario)              | 1. User "admin" is created and designated as a System Admin.<br>2. User "subscriber1" is created.<br>3. subscriber1 logs in and creates production company "A".<br>4. admin logs in and accesses the system admin panel.<br>6. admin executes the cancel subscription function for subscriber1 and confirms the action.      | 1. The system changes subscriber1's account status to "Canceled".<br>2. The system automatically revokes all roles and permissions subscriber1 had in company "A"..<br>3. subscriber1's name is removed from the appointment tree of company "A".                                              |
| Canceled subscriber login attempt (positive scenario)                                  | 1. User "admin" is created and designated as a System Admin.<br>2. User "subscriber1" is created with an active subscription.<br>3. admin logs in and successfully cancels subscriber1's subscription.<br>4. subscriber1 navigates to the login screen and attempts to log in with their credentials.                     | 1. The system rejects the login request.<br>2. subscriber1 is kept in guest mode.<br>3. An error message is displayed indicating the account is canceled or no longer exists.                                                                                                                  |
| Attempt to cancel subscription by non-admin user (negative scenario)                   | 1. User "subscriber1" is created with an active subscription.<br>2. User "subscriber2" is created with an active subscription.<br>3. subscriber2 logs in (but is not designated as a System Admin).<br>4. subscriber2 attempts to access the system admin panel and execute a cancel subscription command on subscriber1. | 1. The system blocks the action and identifies that subscriber2 lacks System Admin permissions.<br>2. subscriber1's subscription remains active and unchanged.<br>3. No roles or permissions are revoked.<br>4. An error message is displayed to subscriber2 about insufficient system authority. |
| Attempt to cancel non-existent subscription (negative scenario)                        | 1. User "admin" is created and designated as a System Admin.<br>2. admin logs in and accesses the system admin panel.<br>3. admin attempts to cancel a subscription using an invalid or non-existent user ID.                                                                                                          | 1. The system rejects the action and prevents processing.<br>2. No changes are made to any account.<br>3. An error message is displayed to admin indicating the specified user was not found in the system.                                                                                    |
| Attempt to cancel a member who is the founder of an active company (negative scenario) | 1. User "admin" is created and designated as a System Admin.<br>2. User "founder1" is created and logs in.<br>3. founder1 creates production company "A".<br>4. admin logs in and attempts to cancel founder1's subscription.                                                                                             | 1. The system identifies that founder1 is the founder of an active company.<br>2. The action is rejected as illegal — no cancellation occurs.<br>3. founder1's subscription remains active.<br>4. Company "A" remains active and unchanged.<br>5. An error message is displayed to admin.            |


---

### UC28 - View Global Purchase History (by buyers or production companies and events)

- **Actor:** System Admin
- **Trigger:** System Admin requests to view global purchase history.
- **Parameters:** Slice type (by buyers or by production companies and events), date range (optional).
- **Preconditions:** The user is authenticated in the system as a System Admin.
- **Main Flow:**
  1. System: displays the available slicing options to the admin.
  2. Admin: selects the desired slice and filtering parameters.
  3. System: displays the data according to the selected slice:
    - By buyers slice: displays a consolidated list of purchases for each buyer individually.
    - By companies and events slice: displays consolidated sales for each production company or specific event.
- **Postconditions:** Admin receives a detailed view of global purchase history according to the selected slice.
- **Alternatives:**
  - Unauthorized access: Non-admin user attempts to access global purchase history. System blocks access.
  - No purchases found: System displays an empty view with a clear message.

#### Acceptance Tests


| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| View purchase history by buyers (positive scenario)                                | 1. User "admin" is created and designated as a System Admin.<br>2. User "buyer1", "buyer2", "buyer3" are created.<br>3. buyer1 logs in and purchases tickets for multiple events.<br>4. buyer2 logs in and purchases tickets for different events.<br>5. admin logs in and accesses the system admin panel.<br>6. admin navigates to global purchase history and selects the "by buyers" slice.                                                                    | 1. The system displays a consolidated and detailed list of purchases for each buyer separately.<br>2. buyer1's purchase history is displayed distinctly.<br>3. buyer2's purchase history is displayed distinctly.<br>4. All transaction details are accurate and complete.                                                       |
| View purchase history by companies and events with date filter (positive scenario) | 1. User "admin" is created and designated as a System Admin.<br>2. User "founder" is created.<br>3. founder logs in and creates production company "A".<br>4. founder creates event "E1" and event "E2" under company "A".<br>5. Purchases are made for E1 and E2 across different dates.<br>6. admin logs in and accesses the global purchase history screen.<br>7. admin selects the "by companies and events" slice.<br>8. admin enters a specific date range filter. | 1. The system filters global data and ignores purchases outside the requested date range.<br>2. The view organizes and displays sales consolidated for each production company separately.<br>3. Under each company, detailed breakdown by specific event is shown.<br>4. Only transactions within the date range are displayed. |
| Attempt to view global history by non-admin user (negative scenario)               | 1. User "admin" is created and designated as a System Admin.<br>2. User "owner" is created and logs in.<br>3. owner creates production company "A".<br>4. owner attempts to access the global purchase history interface.                                                                                                                                                                                                                                    | 1. The system identifies that owner is not a System Admin.<br>2. Access to global purchase data is completely blocked.<br>3. An error message is displayed about insufficient permissions.<br>4. The user is not granted access to any global data.                                                                              |
| View empty global purchase history (positive scenario)                             | 1. User "admin" is created and designated as a System Admin.<br>2. A new empty system is set up with no transactions.<br>3. admin logs in and accesses the global purchase history screen.<br>4. admin requests a report in any available slice.                                                                                                                                                                                                             | 1. The system returns an empty view.<br>2. A clear message is displayed such as "No purchases exist in the system".<br>3. The interface remains accessible but shows no data entries.                                                                                                                                         |
| History unaffected by canceled member (positive scenario)                          | 1. User "admin" is created and designated as a System Admin.<br>2. User "buyer1" is created and logs in.<br>3. buyer1 purchases tickets for an event under production company "A".<br>4. admin cancels buyer1's membership.<br>5. admin requests global purchase history in the "by buyers" slice.                                                                                                                                                              | 1. buyer1's original purchase history is still displayed in the result.<br>2. The buyer1 entry appears under their original email despite the canceled membership status.<br>3. All transaction details remain unchanged.                                                                                                     |
| History unaffected by closed company (positive scenario)                           | 1. User "admin" is created and designated as a System Admin.<br>2. User "founder" is created and creates production company "A".<br>3. founder creates event "E" under company "A".<br>4. A buyer purchases tickets for event "E".<br>5. admin closes production company "A".<br>6. admin requests global purchase history in the "by companies and events" slice.                                                                                                 | 1. Company "A"'s original purchase history is still displayed in the result.<br>2. The closed status of company "A" does not remove its events or orders from the history view.<br>3. All transaction details remain unchanged.                                                                                               |
| Date filter includes orders on exact boundary dates (positive scenario)            | 1. User "admin" is created and designated as a System Admin.<br>2. A buyer makes two purchases: one with purchase date equal exactly to the filter's start date, and another with purchase date equal exactly to the filter's end date.<br>3. admin requests global purchase history with the corresponding start and end date filter.                                                                                                                    | 1. Both transactions appear in the result.<br>2. Orders dated exactly on the start date are included.<br>3. Orders dated exactly on the end date are included.                                                                                                                                                                |
| Same data set is consistent across both slicing methods (positive scenario)        | 1. User "admin" is created and designated as a System Admin.<br>2. Multiple buyers make purchases across different events under different production companies.<br>3. admin requests global purchase history first in the "by buyers" slice, then in the "by companies and events" slice.                                                                                                                                                                 | 1. The total number of orders presented in the "by buyers" view equals the total number presented in the "by companies and events" view.<br>2. The same set of order identifiers appears in both views.<br>3. No order appears in only one of the two slicing views.                                                          |
| Orphan order references are skipped without crashing (positive scenario)           | 1. User "admin" is created and designated as a System Admin.<br>2. The system contains an order whose reference points to a missing active-order record, or an active-order whose reference points to a missing event.<br>3. admin requests global purchase history.                                                                                                                                                                                      | 1. The system does not crash and returns a result without raising an exception.<br>2. The orphan order is silently omitted from the result.<br>3. Other valid orders in the system continue to appear normally in the result.                                                                                                 |


---

### UC29 - Suspend Member

- **Actor:** System Admin
- **Trigger:** System Admin requests to suspend a member's activity on the platform.
- **Parameters:** member email.
- **Preconditions:** An active, non-suspended member account with the given email exists in the system.
- **Main Flow:**
  1. System: verifies the requesting user is a System Admin.
  2. System: verifies that the target email belongs to a Member (not a System Admin).
  3. System: verifies the member is not already suspended.
  4. System: marks the member as suspended and records the suspension date.
- **Postconditions:** The member's `isSuspended` flag is `true`. The member can only perform view operations.
- **Alternatives:**
  - Unauthorized access: requesting user is not a System Admin — system throws an exception.
  - Target is a System Admin: system throws an exception.
  - Member not found: system throws an exception.
  - Member is already suspended: system throws an exception.

#### Acceptance Tests

| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully suspend a member (positive scenario) | 1. User "admin" is designated as a System Admin.<br>2. User "user123" is a registered, active, non-suspended member.<br>3. admin requests to suspend "user123@mail.com". | 1. No exception is thrown.<br>2. "user123" is marked as suspended.<br>3. The suspension record contains a non-null `suspensionDate`. |
| Suspended member cannot perform write actions (positive scenario) | 1. User "admin" is a System Admin.<br>2. User "user123" is a registered member.<br>3. admin suspends "user123".<br>4. "user123" attempts to reserve a ticket. | 1. The reservation attempt throws an exception.<br>2. "user123" remains suspended. |
| Non-admin attempts to suspend a member (negative scenario) | 1. User "user123" is a registered member.<br>2. User "user1" is also a registered member (not a System Admin).<br>3. "user1" attempts to suspend "user123". | 1. System throws an exception.<br>2. "user123" remains unsuspended. |
| Attempt to suspend a System Admin (negative scenario) | 1. User "admin1" is a System Admin.<br>2. User "admin2" is also a System Admin.<br>3. "admin1" attempts to suspend "admin2@mail.com". | 1. System throws an exception.<br>2. "admin2" is unaffected. |
| Attempt to suspend a non-existent member (negative scenario) | 1. User "admin" is a System Admin.<br>2. No member with email "nobody@mail.com" exists. | 1. System throws an exception. |
| Attempt to suspend an already-suspended member (negative scenario) | 1. User "admin" is a System Admin.<br>2. User "user123" is already suspended.<br>3. admin attempts to suspend "user123" again. | 1. System throws an exception.<br>2. The existing suspension record is unchanged. |


---

### UC30 - Unsuspend Member

- **Actor:** System Admin
- **Trigger:** System Admin requests to lift a suspension from a member.
- **Parameters:** member email.
- **Preconditions:** A suspended member account with the given email exists in the system.
- **Main Flow:**
  1. System: verifies the requesting user is a System Admin.
  2. System: verifies the target email belongs to a Member (not a System Admin).
  3. System: verifies the member is currently suspended.
  4. System: clears the member's suspension and records the end date on the suspension record.
- **Postconditions:** The member's `isSuspended` flag is `false`. The suspension record has a non-null `endDate`. The member can perform all operations again.
- **Alternatives:**
  - Unauthorized access: requesting user is not a System Admin — system throws an exception.
  - Member not found: system throws an exception.
  - Member is not currently suspended: system throws an exception.

#### Acceptance Tests

| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| Successfully unsuspend a member (positive scenario) | 1. User "admin" is a System Admin.<br>2. User "user123" is a suspended member.<br>3. admin requests to unsuspend "user123@mail.com". | 1. No exception is thrown.<br>2. "user123" is no longer suspended.<br>3. The suspension record has a non-null `endDate`. |
| Unsuspended member can perform write actions again (positive scenario) | 1. User "admin" is a System Admin.<br>2. User "user123" is a suspended member.<br>3. admin unsuspends "user123".<br>4. "user123" attempts to reserve a ticket. | 1. The reservation attempt succeeds (no suspension-related exception).<br>2. "user123" is no longer suspended. |
| Non-admin attempts to unsuspend a member (negative scenario) | 1. User "user123" is a suspended member.<br>2. User "user1" is a registered member (not a System Admin).<br>3. "user1" attempts to unsuspend "user123". | 1. System throws an exception.<br>2. "user123" remains suspended. |
| Attempt to unsuspend a non-suspended member (negative scenario) | 1. User "admin" is a System Admin.<br>2. User "user123" is active and not suspended.<br>3. admin attempts to unsuspend "user123". | 1. System throws an exception.<br>2. "user123" is unaffected. |
| Attempt to unsuspend a non-existent member (negative scenario) | 1. User "admin" is a System Admin.<br>2. No member with email "nobody@mail.com" exists. | 1. System throws an exception. |


---

### UC31 - View Member Suspensions

- **Actor:** System Admin
- **Trigger:** System Admin requests to view suspension records in the system.
- **Parameters:** optional filter: `activeOnly` (boolean).
- **Preconditions:** None.
- **Main Flow:**
  1. System: verifies the requesting user is a System Admin.
  2. System: retrieves all suspension records across all members.
  3. System: (if filter applied) filters by active suspensions only.
  4. System: returns a list of `SuspensionDTO` records, each containing: `memberEmail`, `suspensionDate`, `suspensionEndDate` (null if still active), and `duration`.
- **Alternatives:**
  - Unauthorized access: requesting user is not a System Admin — system throws an exception.
  - No records found: system returns an empty list (no exception).

#### Acceptance Tests

| Test Name | Setup & Parameters | Expected Results |
| --------- | ------------------ | ---------------- |
| View all suspensions — includes active and ended (positive scenario) | 1. User "admin" is a System Admin.<br>2. User "user1" was suspended and then unsuspended.<br>3. User "user2" is currently suspended.<br>4. admin requests all suspension records (no filter). | 1. No exception is thrown.<br>2. The returned list contains two records — one for "user1" (with non-null `endDate`) and one for "user2" (with null `endDate`). |
| Filter by active suspensions only (positive scenario) | 1. User "admin" is a System Admin.<br>2. User "user1" was suspended and then unsuspended.<br>3. User "user2" is currently suspended.<br>4. admin requests suspensions with `activeOnly=true`. | 1. No exception is thrown.<br>2. The returned list contains only the record for "user2".<br>3. The record for "user1" is excluded. |
| View suspensions when no suspensions exist (positive scenario) | 1. User "admin" is a System Admin.<br>2. No member has ever been suspended. | 1. No exception is thrown.<br>2. The returned list is empty. |
| Correct DTO fields are returned (positive scenario) | 1. User "admin" is a System Admin.<br>2. User "user123" is suspended at time T1.<br>3. admin requests all suspension records. | 1. The returned DTO for "user123" contains: `memberEmail="user123@mail.com"`, non-null `suspensionDate` equal to T1, null `suspensionEndDate`, non-null `duration`. |
| Non-admin attempts to view suspensions (negative scenario) | 1. User "user1" is a registered member (not a System Admin).<br>2. "user1" attempts to view suspension records. | 1. System throws an exception.<br>2. No data is returned. |
