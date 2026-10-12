const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { authenticateToken, optionalAuth } = require('../middleware/auth');

// 1. Record New Sale / Checkout
router.post('/', optionalAuth, (req, res) => {
    const {
        customerId = null,
        customerName = 'Walk-in Customer',
        customerPhone = '-',
        items = [],
        discount = 0.0,
        paymentMethod = 'CASH'
    } = req.body;

    if (!items || items.length === 0) {
        return res.status(400).json({ error: 'At least one item is required in the sale' });
    }

    const isStaff = !req.user || req.user.role === 'STAFF';
    // Rule: Staff cannot grant discounts!
    const effectiveDiscount = isStaff ? 0.0 : Math.max(0.0, parseFloat(discount) || 0.0);
    const saleUser = req.user ? req.user.name : 'Gym Front Desk';

    // Verify all products and check stock availability
    db.all('SELECT * FROM products', (err, allProducts) => {
        if (err) return res.status(500).json({ error: err.message });

        const prodMap = {};
        allProducts.forEach(p => { prodMap[p.id] = p; });

        db.all(`
            SELECT productId,
                   SUM(CASE
                       WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity
                       ELSE -quantity
                   END) as netStock
            FROM stock_transactions
            GROUP BY productId
        `, (err, stockRows) => {
            if (err) return res.status(500).json({ error: err.message });

            const stockMap = {};
            stockRows.forEach(r => { stockMap[r.productId] = r.netStock || 0; });

            // Validate inventory and calculate totals
            let subtotal = 0.0;
            const processedItems = [];

            for (const item of items) {
                const prod = prodMap[item.productId];
                if (!prod) {
                    return res.status(404).json({ error: `Product not found: ${item.productId}` });
                }

                const currentStock = stockMap[item.productId] || 0;
                if (currentStock <= 0) {
                    return res.status(400).json({ error: `Item ${prod.name} is SOLD OUT and cannot be purchased.` });
                }

                if (item.quantity > currentStock) {
                    return res.status(400).json({
                        error: `Insufficient stock for ${prod.name}. Available: ${currentStock}, Requested: ${item.quantity}`
                    });
                }

                // If staff, lock unit price to catalog selling price!
                const unitPrice = isStaff ? prod.sellingPrice : (item.unitPrice !== undefined ? item.unitPrice : prod.sellingPrice);
                const itemTotal = item.quantity * unitPrice;
                subtotal += itemTotal;

                processedItems.push({
                    productId: prod.id,
                    productName: prod.name,
                    quantity: item.quantity,
                    unitPrice,
                    total: itemTotal,
                    currentStock
                });
            }

            const grandTotal = Math.max(0.0, subtotal - effectiveDiscount);
            const now = Date.now();
            const invoiceNumber = `INV-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${Math.floor(1000 + Math.random() * 9000)}`;

            // Begin transaction to record sale, items, and stock deductions
            db.serialize(() => {
                db.run(`
                    INSERT INTO sales (invoiceNumber, customerId, customerName, customerPhone, subtotal, discount, grandTotal, paymentMethod, user, date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                `, [invoiceNumber, customerId, customerName, customerPhone, subtotal, effectiveDiscount, grandTotal, paymentMethod, saleUser, now], function (err) {
                    if (err) return res.status(500).json({ error: err.message });

                    const saleId = this.lastID;

                    // Insert line items and stock reduction transactions
                    processedItems.forEach(pi => {
                        db.run(`
                            INSERT INTO sale_items (saleId, productId, productName, quantity, unitPrice, total)
                            VALUES (?, ?, ?, ?, ?, ?)
                        `, [saleId, pi.productId, pi.productName, pi.quantity, pi.unitPrice, pi.total]);

                        const balanceAfter = pi.currentStock - pi.quantity;
                        db.run(`
                            INSERT INTO stock_transactions (productId, productName, transactionType, quantity, unitPrice, total, balanceAfter, referenceId, notes, date, user)
                            VALUES (?, ?, 'SALE', ?, ?, ?, ?, ?, 'POS Checkout', ?, ?)
                        `, [pi.productId, pi.productName, pi.quantity, pi.unitPrice, pi.total, balanceAfter, invoiceNumber, now, saleUser]);
                    });

                    // Update customer CRM stats if customer provided
                    if (customerId) {
                        db.run('UPDATE customers SET totalPurchases = totalPurchases + ? WHERE id = ?', [grandTotal, customerId]);
                    }

                    res.status(201).json({
                        message: 'Sale recorded successfully',
                        sale: {
                            id: saleId,
                            invoiceNumber,
                            customerName,
                            subtotal,
                            discount: effectiveDiscount,
                            grandTotal,
                            paymentMethod,
                            user: saleUser,
                            date: now,
                            items: processedItems
                        }
                    });
                });
            });
        });
    });
});

// 2. Get Sales History
router.get('/', optionalAuth, (req, res) => {
    db.all(`
        SELECT id, invoiceNumber, customerName, customerPhone, subtotal, discount, grandTotal, paymentMethod, user, date
        FROM sales
        ORDER BY date DESC
        LIMIT 100
    `, (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ sales: rows });
    });
});

// 3. Get Sale / Invoice Details
router.get('/:id', optionalAuth, (req, res) => {
    const { id } = req.params;

    db.get('SELECT * FROM sales WHERE id = ? OR invoiceNumber = ?', [id, id], (err, sale) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!sale) return res.status(404).json({ error: 'Invoice not found' });

        db.all('SELECT * FROM sale_items WHERE saleId = ?', [sale.id], (err, items) => {
            if (err) return res.status(500).json({ error: err.message });
            res.json({ sale, items });
        });
    });
});

module.exports = router;
