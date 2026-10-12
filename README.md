# 🏋️ Iron Paradise Gym CRM & 24/7 Cloud Backend

Comprehensive Gym Management CRM system featuring dual dedicated Android APKs (**Admin** & **Staff**) and a secure, 24/7 cloud backend server with real-time multi-device database synchronization.

---

## 📱 Dual Android Applications

- **👑 Admin APK (`IronParadiseAdmin.apk`)**:
  - Full control over stock-in, cost/purchase prices, margins, and profit calculations.
  - Decide selling prices and apply discounts.
  - Approve, reject, or remove new staff sign-ups.
  - Physical inventory stock adjustments & loss tracking.
  - Full financial reports and profit analysis.
  
- **🏋️ Staff APK (`IronParadiseStaff.apk`)**:
  - Front-desk operations: view inventory levels and bold **"SOLD OUT"** status indicators.
  - View **original catalog selling prices only** (Cost/purchase prices are hidden).
  - Quick sales checkout (Arbitrary discounts and price changes are locked to prevent tampering).
  - Customer CRM registration and PDF invoice/receipt sharing.
  - Requires Admin approval before initial access is granted.

---

## ⚡ 24/7 Cloud Backend Server (`server/`)

A high-performance Node.js / Express backend with multi-device database synchronization:

- **JWT Authentication**: Persistent session caching in device memory for 1-tap auto-login.
- **Role & Security Guards**: Admin-only endpoints, automatic discount blocking for staff, and cost price masking.
- **Multi-Device Sync**: Synchronizes inventory, transactions, and approvals across 5–7 users in real-time.
- **Photo Uploads**: Multipart image storage for supplement jars and products.
- **Cloud Ready**: Runs 24/7 on free cloud services (Render, Railway, Fly.io) or any Linux Cloud VPS via Docker.

---

## 🚀 Quick Start: Running the Server

```bash
cd server
npm install
npm start
```

The server will be live at `http://localhost:5000` with an interactive web dashboard at `/` and health metrics at `/health`.

See [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) for 1-click free 24/7 cloud hosting instructions.

---

## 🛠️ Building the APKs

```bash
# Build Admin APK
./gradlew assembleAdminDebug

# Build Staff APK
./gradlew assembleStaffDebug
```
Output APKs:
- `IronParadiseAdmin.apk`
- `IronParadiseStaff.apk`
