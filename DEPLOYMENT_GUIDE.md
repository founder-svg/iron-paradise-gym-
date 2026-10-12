# 🏋️ Iron Paradise Gym CRM — 24/7 Cloud Server & Multi-User Deployment Guide

This guide explains how the **Iron Paradise Gym CRM** cloud architecture works and provides step-by-step instructions for hosting the backend server **24/7 for free** on cloud providers or deploying it to a Cloud VPS.

---

## 🏛️ System Architecture

Based on your system design:

```
                  ┌────────────────────────────────────────────────────────┐
                  │              Android APK (5 - 7 Users)                 │
                  │  👑 Admin APK (Full control, Stock In, margins, approvals)
                  │  🏋️ Staff APK (Sold Out badge, MRP only, billing)      │
                  └───────────────────────────┬────────────────────────────┘
                                              │ REST API / JWT
                                              ▼
                  ┌────────────────────────────────────────────────────────┐
                  │             Secure Cloud Backend (Node.js)             │
                  │  • Authentication & Persistent Session Memory          │
                  │  • Role Enforcement & Staff Sign-Up Approvals          │
                  │  • Stock Deductions & Inventory Calculation            │
                  │  • Discount Controls & Profit Analytics                │
                  └───────────────┬────────────────────────┬───────────────┘
                                  │                        │
                                  ▼                        ▼
                  ┌────────────────────────┐      ┌────────────────────────┐
                  │   Database (SQLite/PG) │      │   File & Photo Storage │
                  │ Business & Client Data │      │ Supplement Photos/PDFs │
                  └────────────────────────┘      └────────────────────────┘
```

---

## 🚀 Option 1: 100% Free 24/7 Cloud Deployment (Render.com)

Render provides free hosting for web services with automatic HTTPS:

1. Create a free account at [Render.com](https://render.com).
2. Click **New +** → **Web Service**.
3. Connect your GitHub repository: `https://github.com/founder-svg/iron-paradise-gym-`.
4. Configure the settings:
   - **Name**: `iron-paradise-gym-api`
   - **Root Directory**: `server`
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `node index.js`
   - **Plan**: `Free`
5. Click **Create Web Service**.
6. Render will build and deploy your API. Once deployed, you will get a live URL such as:
   `https://iron-paradise-gym-api.onrender.com`
7. Test the health endpoint in your browser:
   `https://iron-paradise-gym-api.onrender.com/health`

---

## 🌐 Option 2: 1-Click Cloud VPS Deployment (Docker / Ubuntu / Debian)

If you have a Linux VPS (DigitalOcean, Hetzner, AWS Lightsail, Linode, Contabo):

### Using Docker Compose (Recommended)

1. SSH into your VPS:
   ```bash
   ssh root@your_vps_ip
   ```
2. Clone the repository:
   ```bash
   git clone https://github.com/founder-svg/iron-paradise-gym-.git
   cd iron-paradise-gym-/server
   ```
3. Run the container in the background:
   ```bash
   docker compose up -d
   ```
4. Your server will run **24/7** and automatically restart on server reboots.
5. Access your dashboard at `http://your_vps_ip:5000`.

### Using PM2 Process Manager (Alternative)

1. Install Node.js and PM2 on the VPS:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
   sudo apt-get install -y nodejs
   sudo npm install -g pm2
   ```
2. Navigate to the server folder and start:
   ```bash
   cd iron-paradise-gym-/server
   npm install
   pm2 start index.js --name "iron-paradise-crm"
   pm2 startup
   pm2 save
   ```

---

## 📱 How Android APKs Connect to the Cloud Server

1. The Android APKs communicate with the backend at:
   - Local testing: `http://10.0.2.2:5000` (Android Emulator) or `http://192.168.x.x:5000` (Local Wi-Fi)
   - Cloud Production: `https://your-deployed-domain.com`
2. All accounts, inventory counts, and staff approval status sync automatically.
3. Login details and tokens are stored in the device's persistent memory so users only log in once.

---

## 🔒 Security & Role Rules Enforced by the Cloud Server

- **Cost / Purchase Price**: Only returned if the JWT token belongs to an `ADMIN` user.
- **Stock In**: Only `ADMIN` can execute stock-in transactions.
- **Discounts**: The server automatically rejects any discount applied by a `STAFF` account.
- **Staff Approvals**: Staff members remain in `PENDING` status until an Admin calls `/api/staff/:id/status` to approve them.
