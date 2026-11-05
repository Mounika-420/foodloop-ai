# UML and DFD Diagrams (Mermaid – renders on GitHub, VS Code Markdown Preview, mermaid.live)

## 1. Use-case diagram
```mermaid
flowchart LR
  A((Admin)) --- U1[Verify donors and NGOs]
  A --- U2[Manage users]
  A --- U3[Monitor donations]
  A --- U4[View analytics and reports]
  A --- U5[Handle complaints]
  A --- U6[View audit logs]
  D((Donor)) --- D1[Register and login]
  D --- D2[Create donation]
  D --- D3[View AI surplus prediction]
  D --- D4[View matched NGOs]
  D --- D5[Track and cancel donation]
  N((NGO)) --- N1[Post requirements]
  N --- N2[View recommended donations]
  N --- N3[Accept or reject donation]
  N --- N4[Schedule pickup]
  N --- N5[Confirm receipt]
  V((Volunteer)) --- V1[View and accept tasks]
  V --- V2[Update pickup status]
  V --- V3[Update delivery status]
  D2 -. include .-> M1[Run AI matching]
  M1 -. include .-> M2[Send notifications]
  N3 -. include .-> M2
```

## 2. Activity diagram – donation lifecycle
```mermaid
flowchart TD
  S([Start]) --> A[Donor submits donation form]
  A --> B{Valid and donor VERIFIED?}
  B -- No --> A1[Show validation error] --> A
  B -- Yes --> C[Save donation AVAILABLE]
  C --> D[Call AI: candidate NGOs ranked]
  D --> E{AI reachable?}
  E -- No --> E1[Rule-based fallback ranking, flag fallback]
  E -- Yes --> F[Store matches and prediction]
  E1 --> F
  F --> G[Status MATCHED, notify NGOs]
  G --> H{NGO accepts before expiry?}
  H -- No --> H1[EXPIRED or REJECTED_ALL] --> Z([End])
  H -- Yes --> I[ACCEPTED, others WITHDRAWN]
  I --> J[NGO schedules pickup]
  J --> K[Volunteer assigned and notified]
  K --> L[Volunteer picks up]
  L --> M[In transit]
  M --> N[Delivered]
  N --> O[NGO confirms receipt]
  O --> P[COMPLETED, analytics updated]
  P --> Z
```

## 3. Sequence diagram – create donation and match
```mermaid
sequenceDiagram
  actor Donor
  participant FE as Frontend
  participant API as Spring Boot
  participant DB as MySQL
  participant AI as Flask AI
  actor NGO
  Donor->>FE: Fill form + images
  FE->>API: POST /api/donations (Bearer JWT)
  API->>API: Validate, check role DONOR + VERIFIED
  API->>DB: INSERT donation (AVAILABLE) + images
  API->>AI: POST /api/v1/recommend/ngos (donation + candidate NGOs)
  AI-->>API: ranked NGOs, scores, breakdown
  API->>DB: INSERT donation_matches, predictions, status MATCHED
  API->>DB: INSERT notifications (+ email async)
  API-->>FE: 201 donation + matches
  NGO->>API: POST /api/ngo/matches/{id}/accept
  API->>DB: UPDATE donation (version check) ACCEPTED, others WITHDRAWN
  API-->>NGO: 200
  API->>DB: notify donor + create pickup_request
```

## 4. Sequence diagram – delivery
```mermaid
sequenceDiagram
  actor Vol as Volunteer
  actor NGO
  participant API as Spring Boot
  participant DB as MySQL
  API->>DB: assign volunteer (pickup ASSIGNED), notify volunteer
  Vol->>API: POST /api/volunteer/pickups/{id}/accept
  Vol->>API: PATCH /api/volunteer/deliveries/{id}/status PICKED_UP
  Vol->>API: PATCH ... IN_TRANSIT (lat,lng)
  Vol->>API: PATCH ... DELIVERED
  API->>DB: donation DELIVERED, notify NGO
  NGO->>API: POST /api/ngo/deliveries/{id}/confirm (quantity)
  API->>DB: delivery RECEIPT_CONFIRMED, donation COMPLETED, volunteer total_deliveries+1
  API->>DB: audit_logs + notifications
```

## 5. Class diagram (core domain)
```mermaid
classDiagram
  class User{Long id; String email; String passwordHash; String fullName; Role role; UserStatus status}
  class DonorProfile{String organizationName; DonorType type; VerificationStatus status}
  class NgoProfile{String registrationNumber; BigDecimal dailyCapacityKg; VerificationStatus status}
  class VolunteerProfile{VehicleType vehicle; BigDecimal radiusKm; Availability availability}
  class Donation{String code; BigDecimal quantityKg; LocalDateTime expiresAt; DonationStatus status; int version}
  class DonationMatch{BigDecimal score; BigDecimal distanceKm; String breakdownJson; MatchStatus status}
  class PickupRequest{LocalDateTime scheduledAt; PickupStatus status}
  class Delivery{DeliveryStatus status; BigDecimal quantityReceivedKg}
  class Notification{NotificationType type; boolean read}
  class Prediction{PredictionType type; String modelName; boolean trainedOnSynthetic}
  User "1" -- "0..1" DonorProfile
  User "1" -- "0..1" NgoProfile
  User "1" -- "0..1" VolunteerProfile
  DonorProfile "1" -- "*" Donation
  Donation "1" -- "*" DonationMatch
  NgoProfile "1" -- "*" DonationMatch
  Donation "1" -- "0..1" PickupRequest
  VolunteerProfile "1" -- "*" PickupRequest
  PickupRequest "1" -- "0..1" Delivery
  User "1" -- "*" Notification
  Donation "1" -- "*" Prediction
```

## 6. DFD Level 0 (context)
```mermaid
flowchart LR
  Donor[Donor] -- donation details --> P((FoodLoop AI System))
  NGO[NGO] -- requirements, accept/reject, receipt --> P
  Vol[Volunteer] -- status updates --> P
  Admin[Admin] -- verification, management --> P
  P -- matches, notifications, tracking --> Donor
  P -- recommended donations, pickups --> NGO
  P -- tasks --> Vol
  P -- analytics, reports --> Admin
  P <-- geocoding / map tiles --> Map[Map API]
  P -- emails --> Mail[Email server]
```

## 7. DFD Level 1
```mermaid
flowchart TD
  U[Users] --> P1(1.0 Authentication and user management)
  P1 <--> D1[(D1 Users and profiles)]
  Donor --> P2(2.0 Donation management)
  P2 <--> D2[(D2 Donations and images)]
  P2 --> P3(3.0 AI prediction and matching)
  P3 <--> AI[Flask AI service]
  P3 --> D3[(D3 Matches and predictions)]
  P3 --> P4(4.0 Notification)
  P4 --> D4[(D4 Notifications)]
  NGO --> P5(5.0 Acceptance and pickup scheduling)
  P5 <--> D2
  P5 <--> D5[(D5 Pickups and deliveries)]
  Vol --> P6(6.0 Delivery tracking)
  P6 <--> D5
  P6 --> P4
  D2 --> P7(7.0 Analytics and reports)
  D5 --> P7
  D3 --> P7
  P7 --> Admin
  P1 --> D6[(D6 Audit logs)]
  P2 --> D6
  P5 --> D6
```

## 8. DFD Level 2 – process 3.0 AI prediction and matching
```mermaid
flowchart TD
  A[3.1 Receive new donation] --> B[3.2 Load verified NGOs in radius with capacity and open requirements]
  B --> C[3.3 Build request JSON]
  C --> D[3.4 Call Flask: surplus prediction]
  C --> E[3.5 Call Flask: NGO ranking]
  D --> F[3.6 Store prediction]
  E --> G[3.7 Store top-N matches with breakdown]
  G --> H[3.8 Set donation MATCHED and trigger notifications]
  E -. AI down or timeout .-> R[3.9 Rule-based fallback ranking]
  R --> G
```
