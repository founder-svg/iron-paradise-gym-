# IRON Paradise GYM CRM — Future Roadmap & Architecture Plan

## Phase 1 (Completed)
- [x] **Native Android CRM Application** (`IronParadiseGymCRM.apk`)
- [x] Full supplement inventory engine (atomic transaction ledger)
- [x] Real-time stock validation (prevents over-selling)
- [x] Multi-item sales & A4 PDF invoice generator with sharing/printing
- [x] Stock adjustment & sale reversal audit trails
- [x] Role-based PIN authentication (Admin & Staff)
- [x] Financial & profit analytics
- [x] Local offline-first Room database with backup/export

---

## Phase 2 & 3 Roadmap (Upcoming)

### 1. iOS Application Build
- **Target Platform**: Apple iOS (iPhone / iPad).
- **Options**:
  - Kotlin Multiplatform (KMP) sharing data logic with Compose Multiplatform / SwiftUI, or
  - Cross-platform Flutter / React Native port with the existing high-contrast dark gym aesthetic.
- **Shared Data Layer**: Transitioning from pure local SQLite to a synchronized backend (Supabase / PostgreSQL / Firebase) to allow both Android and iOS devices to share the same live inventory in real time.

---

### 2. Dedicated Gym Owner "Monitor-Only" Admin App
- **Target User**: Gym Owner (Remote Monitoring).
- **Core Purpose**: Real-time stock observation and business oversight without point-of-sale friction.
- **Key Capabilities**:
  - **Read-Only Stock Dashboard**: Monitor live product stock units, low-stock alerts, out-of-stock items.
  - **Stock-In Audit**: Review incoming shipments, batch numbers, and supplier purchases.
  - **Financial Metrics**: Live gross sales, inventory valuation, and profit margins.
  - **Restriction**: **No Stock-Out / Sale execution** from this app (strictly monitoring to prevent accidental inventory tampering from the owner's mobile device).
  - **Push Notifications**: Instant alerts when any supplement reaches low-stock or out-of-stock threshold.
