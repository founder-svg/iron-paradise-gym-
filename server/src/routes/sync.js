const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { optionalAuth } = require('../middleware/auth');

// 1. Pull All Data for Device Sync (Bidirectional Sync across all 10 Admin & Staff devices)
router.get('/pull', optionalAuth, (req, res) => {
    const isAdmin = req.user && req.user.role === 'ADMIN';

    db.all('SELECT * FROM products ORDER BY name ASC', (err, products) => {
        if (err) return res.status(500).json({ error: err.message });

        db.all('SELECT * FROM customers ORDER BY name ASC', (err, customers) => {
            if (err) return res.status(500).json({ error: err.message });

            db.all('SELECT * FROM stock_transactions ORDER BY date DESC, id DESC LIMIT 1000', (err, transactions) => {
                if (err) return res.status(500).json({ error: err.message });

                db.all('SELECT * FROM sales ORDER BY date DESC, id DESC LIMIT 500', (err, sales) => {
                    if (err) return res.status(500).json({ error: err.message });

                    db.all('SELECT * FROM sale_items', (err, allSaleItems) => {
                        if (err) return res.status(500).json({ error: err.message });

                        const itemsBySale = {};
                        (allSaleItems || []).forEach(item => {
                            if (!itemsBySale[item.saleId]) {
                                itemsBySale[item.saleId] = [];
                            }
                            itemsBySale[item.saleId].push(item);
                        });

                        // Attach items to sales
                        const fullSales = sales.map(s => ({
                            ...s,
                            items: itemsBySale[s.id] || []
                        }));

                        // Mask sensitive financial fields if non-admin (Staff APK)
                        const safeProducts = products.map(p => {
                            if (!isAdmin) {
                                const { purchasePrice, ...rest } = p;
                                return { ...rest, purchasePrice: 0.0 };
                            }
                            return p;
                        });

                        const safeTransactions = transactions.map(t => {
                            if (!isAdmin && t.transactionType === 'STOCK_IN') {
                                return {
                                    ...t,
                                    unitPrice: 0.0,
                                    total: 0.0,
                                    notes: 'Stock In Record'
                                };
                            }
                            return t;
                        });

                        res.json({
                            syncTimestamp: Date.now(),
                            products: safeProducts,
                            customers,
                            transactions: safeTransactions,
                            sales: fullSales
                        });
                    });
                });
            });
        });
    });
});

// 2. Push Local Data from Device to Cloud Database
router.post('/push', optionalAuth, (req, res) => {
    const { products = [], customers = [], transactions = [], sales = [] } = req.body;
    const callerName = req.user ? req.user.name : 'Device Sync';
    const isAdmin = req.user && req.user.role === 'ADMIN';

    db.serialize(() => {
        // 1. Process Customers
        if (Array.isArray(customers) && customers.length > 0) {
            const custStmt = db.prepare(`
                INSERT INTO customers (name, phone, email, totalPurchases, createdAt)
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM customers WHERE phone = ?)
            `);
            customers.forEach(c => {
                if (c.phone) {
                    custStmt.run(
                        c.name || 'Customer',
                        c.phone,
                        c.email || '',
                        c.totalPurchases || 0.0,
                        c.createdAt || Date.now(),
                        c.phone
                    );
                }
            });
            custStmt.finalize();
        }

        // 2. Process Products (Admin or catalog updates)
        if (Array.isArray(products) && products.length > 0) {
            const prodStmt = db.prepare(`
                INSERT INTO products (id, name, brand, category, variant, unit, purchasePrice, sellingPrice, minimumStock, imageUri, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    brand = excluded.brand,
                    category = excluded.category,
                    variant = excluded.variant,
                    unit = excluded.unit,
                    sellingPrice = excluded.sellingPrice,
                    minimumStock = excluded.minimumStock,
                    imageUri = CASE WHEN excluded.imageUri != '' THEN excluded.imageUri ELSE products.imageUri END,
                    purchasePrice = CASE WHEN ? = 1 THEN excluded.purchasePrice ELSE products.purchasePrice END
            `);
            products.forEach(p => {
                if (p.id && p.name) {
                    prodStmt.run(
                        p.id,
                        p.name,
                        p.brand || 'Iron Paradise',
                        p.category || 'Supplements',
                        p.variant || '',
                        p.unit || 'Unit',
                        p.purchasePrice || 0.0,
                        p.sellingPrice || 0.0,
                        p.minimumStock || 0,
                        p.imageUri || '',
                        p.createdAt || Date.now(),
                        isAdmin ? 1 : 0
                    );
                }
            });
            prodStmt.finalize();
        }

        // 3. Process Sales (and their Line Items)
        if (Array.isArray(sales) && sales.length > 0) {
            sales.forEach(s => {
                if (!s.invoiceNumber) return;

                db.get('SELECT id FROM sales WHERE invoiceNumber = ?', [s.invoiceNumber], (err, row) => {
                    if (err || row) return; // already exists

                    db.run(`
                        INSERT INTO sales (invoiceNumber, customerId, customerName, customerPhone, subtotal, discount, grandTotal, paymentMethod, user, date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    `, [
                        s.invoiceNumber,
                        s.customerId || null,
                        s.customerName || 'Walk-in Customer',
                        s.customerPhone || '-',
                        s.subtotal || 0.0,
                        s.discount || 0.0,
                        s.grandTotal || 0.0,
                        s.paymentMethod || 'CASH',
                        s.user || s.createdByUser || callerName,
                        s.date || Date.now()
                    ], function (insertErr) {
                        if (insertErr) return;
                        const newSaleId = this.lastID;

                        // Insert sale items
                        if (Array.isArray(s.items)) {
                            const itemStmt = db.prepare(`
                                INSERT INTO sale_items (saleId, productId, productName, quantity, unitPrice, total)
                                VALUES (?, ?, ?, ?, ?, ?)
                            `);
                            s.items.forEach(it => {
                                itemStmt.run(
                                    newSaleId,
                                    it.productId,
                                    it.productName || 'Product',
                                    it.quantity || 1,
                                    it.unitPrice || 0.0,
                                    it.total || ((it.quantity || 1) * (it.unitPrice || 0.0))
                                );
                            });
                            itemStmt.finalize();
                        }
                    });
                });
            });
        }

        // 4. Process Stock Transactions (Stock In, Stock Out / Adjustments, Sales)
        if (Array.isArray(transactions) && transactions.length > 0) {
            const txStmt = db.prepare(`
                INSERT INTO stock_transactions (productId, productName, transactionType, quantity, unitPrice, total, balanceAfter, referenceId, notes, date, user)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM stock_transactions
                    WHERE referenceId = ? AND referenceId != '' AND productId = ?
                )
            `);
            transactions.forEach(t => {
                if (t.productId && t.transactionType) {
                    const ref = t.referenceId || `TX-${Date.now()}`;
                    txStmt.run(
                        t.productId,
                        t.productName || 'Product',
                        t.transactionType,
                        t.quantity || 0,
                        t.unitPrice || 0.0,
                        t.total || 0.0,
                        t.balanceAfter || 0,
                        ref,
                        t.notes || '',
                        t.date || Date.now(),
                        t.user || t.createdBy || callerName,
                        ref,
                        t.productId
                    );
                }
            });
            txStmt.finalize();
        }

        res.json({
            success: true,
            message: 'Data pushed and synchronized successfully to cloud database',
            timestamp: Date.now()
        });
    });
});

// 3. Check User Status Sync (for real-time approval update on staff phones)
router.get('/status', (req, res) => {
    const email = req.query.email;
    if (!email) return res.status(400).json({ error: 'Email parameter required' });

    db.get('SELECT id, email, name, role, status FROM users WHERE email = ?', [email.trim().toLowerCase()], (err, user) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!user) return res.status(404).json({ error: 'User not found' });
        res.json({ user });
    });
});

// 4. Admin Update Staff Approval Status (Real-time Cloud Sync)
router.post('/staff-status', (req, res) => {
    const { email, status } = req.body;
    if (!email || !status) return res.status(400).json({ error: 'Email and status required' });

    db.run('UPDATE users SET status = ? WHERE email = ?', [status, email.trim().toLowerCase()], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ success: true, email, status, changes: this.changes });
    });
});

// 5. Admin Remove Staff Member (Real-time Cloud Sync)
router.post('/delete-staff', (req, res) => {
    const { email } = req.body;
    if (!email) return res.status(400).json({ error: 'Email required' });

    db.run("DELETE FROM users WHERE email = ? AND role = 'STAFF'", [email.trim().toLowerCase()], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ success: true, email, deleted: this.changes });
    });
});

module.exports = router;
