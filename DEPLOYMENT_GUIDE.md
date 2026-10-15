# 🏋️ Iron Paradise Gym CRM — 24/7 Cloud Server & Multi-User Deployment Guide

This guide explains how the **Iron Paradise Gym CRM** cloud architecture works and provides step-by-step instructions for hosting the backend server **24/7 for free** on cloud providers or deploying it to a Cloud VPS.

---

## 🏛️ System Architecture

Based on your system design:

```
                  ┌────────────────────────────────────────────────────────┐
                  │          Multi-Device Access (Android & Apple iOS)     │
                  │  📱 Android APK: Admin & Staff Universal Build         │
                  │  🍎 Apple iOS / iPad: PWA (1-Click WhatsApp Install)   │
                  │  💻 Desktop Web: Chrome / Safari / Edge Dashboard      │
                  └───────────────────────────┬────────────────────────────┘
                                              │ REST API / JWT / PWA
                                              ▼
                  ┌────────────────────────────────────────────────────────┐
                  │             Secure Cloud Backend (Node.js)             │
                  │  • Authentication & Persistent Session Memory          │
                  │  • Unified Admin & Staff Permissions                   │
                  │  • Stock Management: Add, Edit, Delete, Stock In       │
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

## 🍎 Apple iOS Installation & WhatsApp 1-Click Sharing

Apple devices (iPhones & iPads) do not permit direct `.apk` installations from WhatsApp due to iOS sandbox security policies. To give iPhone users an immediate native app experience without paying $99/year for Apple Developer certs:

### 🌐 Instant GitHub Domain for WhatsApp Sharing:
Your repository's free, permanent GitHub domain is:
👉 **`https://founder-svg.github.io/iron-paradise-gym-/`**

#### How to Activate GitHub Pages (1-Time Setup in 10 Seconds):
1. Go to your repository on GitHub: [founder-svg/iron-paradise-gym-](https://github.com/founder-svg/iron-paradise-gym-).
2. Click **Settings** (top tab) → **Pages** (in the left sidebar).
3. Under **Branch**, select **`main`** branch and choose **`/docs`** folder.
4. Click **Save**.
5. GitHub will immediately publish your web portal & iOS app at:
   **`https://founder-svg.github.io/iron-paradise-gym-/`**

---

### 📲 How to Install on iPhone via WhatsApp:
1. **Share on WhatsApp**:
   - Send the link: `https://founder-svg.github.io/iron-paradise-gym-/`
   - Or tap the green **"📲 Share to WhatsApp"** button inside the portal.
2. **Install on iPhone in 5 Seconds**:
   - The user opens the link in **Safari**.
   - Tap the **Share** button (the square with an arrow pointing up ⬆️ at the bottom of the screen).
   - Scroll down and tap **"Add to Home Screen"**.
   - Tap **"Add"** in the top-right corner.
3. **Result**:
   - An **"Iron Paradise"** gold icon appears on their iPhone home screen.
   - Launches in full-screen standalone mode (no Safari address bar).
   - Complies with 100% of Apple's security checks and iOS sandboxing.
   - Offers real-time Stock & Inventory management (Add, Edit, Remove, Stock In), financial reports, and downloads for Android APKs.

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
