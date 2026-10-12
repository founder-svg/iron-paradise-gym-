const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');

const dataDir = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = path.join(dataDir, 'iron_paradise.db');
const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('❌ Failed to connect to SQLite database:', err.message);
    } else {
        console.log('✅ Connected to Iron Paradise SQLite Cloud Database at:', dbPath);
    }
});

function initDatabase() {
    db.serialize(() => {
        // 1. Users Table
        db.run(`
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                name TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'STAFF',
                status TEXT NOT NULL DEFAULT 'PENDING',
                authProvider TEXT NOT NULL DEFAULT 'EMAIL',
                phone TEXT DEFAULT '',
                createdAt INTEGER NOT NULL
            )
        `);

        // 2. Products Table
        db.run(`
            CREATE TABLE IF NOT EXISTS products (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                brand TEXT NOT NULL,
                category TEXT NOT NULL,
                variant TEXT DEFAULT '',
                unit TEXT DEFAULT '',
                purchasePrice REAL NOT NULL DEFAULT 0.0,
                sellingPrice REAL NOT NULL DEFAULT 0.0,
                minimumStock INTEGER NOT NULL DEFAULT 0,
                imageUri TEXT DEFAULT '',
                createdAt INTEGER NOT NULL
            )
        `);

        // 3. Stock Transactions Table
        db.run(`
            CREATE TABLE IF NOT EXISTS stock_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                productId TEXT NOT NULL,
                productName TEXT NOT NULL,
                transactionType TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                unitPrice REAL NOT NULL,
                total REAL NOT NULL,
                balanceAfter INTEGER NOT NULL,
                referenceId TEXT DEFAULT '',
                notes TEXT DEFAULT '',
                date INTEGER NOT NULL,
                user TEXT DEFAULT '',
                FOREIGN KEY (productId) REFERENCES products (id)
            )
        `);

        // 4. Customers Table
        db.run(`
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT DEFAULT '',
                totalPurchases REAL NOT NULL DEFAULT 0.0,
                createdAt INTEGER NOT NULL
            )
        `);

        // 5. Sales Table
        db.run(`
            CREATE TABLE IF NOT EXISTS sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                invoiceNumber TEXT UNIQUE NOT NULL,
                customerId INTEGER,
                customerName TEXT NOT NULL,
                customerPhone TEXT NOT NULL,
                subtotal REAL NOT NULL,
                discount REAL NOT NULL DEFAULT 0.0,
                grandTotal REAL NOT NULL,
                paymentMethod TEXT NOT NULL,
                user TEXT NOT NULL,
                date INTEGER NOT NULL
            )
        `);

        // 6. Sale Items Table
        db.run(`
            CREATE TABLE IF NOT EXISTS sale_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                saleId INTEGER NOT NULL,
                productId TEXT NOT NULL,
                productName TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                unitPrice REAL NOT NULL,
                total REAL NOT NULL,
                FOREIGN KEY (saleId) REFERENCES sales (id)
            )
        `);

        seedInitialData();
    });
}

function seedInitialData() {
    // Seed default admin and staff if not present
    db.get('SELECT COUNT(*) as count FROM users', (err, row) => {
        if (err) return;
        if (row.count === 0) {
            const adminPass = bcrypt.hashSync('admin123', 10);
            const staffPass = bcrypt.hashSync('staff123', 10);
            const now = Date.now();

            db.run(`
                INSERT INTO users (email, password, name, role, status, authProvider, phone, createdAt)
                VALUES ('admin@ironparadise.com', ?, 'Gym Owner (Admin)', 'ADMIN', 'APPROVED', 'EMAIL', '9876543210', ?)
            `, [adminPass, now]);

            db.run(`
                INSERT INTO users (email, password, name, role, status, authProvider, phone, createdAt)
                VALUES ('staff@ironparadise.com', ?, 'Gym Front Desk (Staff)', 'STAFF', 'APPROVED', 'EMAIL', '9876543211', ?)
            `, [staffPass, now]);

            console.log('🌱 Seeded default master admin account');
        }
    });
}

module.exports = {
    db,
    initDatabase
};
