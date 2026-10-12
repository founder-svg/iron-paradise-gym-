const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { optionalAuth } = require('../middleware/auth');

// 1. Get all customers
router.get('/', optionalAuth, (req, res) => {
    db.all('SELECT * FROM customers ORDER BY name ASC', (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ customers: rows });
    });
});

// 2. Add or update customer
router.post('/', optionalAuth, (req, res) => {
    const { name, phone, email = '' } = req.body;

    if (!name || !phone) {
        return res.status(400).json({ error: 'Customer name and phone number are required' });
    }

    db.get('SELECT * FROM customers WHERE phone = ?', [phone.trim()], (err, existing) => {
        if (err) return res.status(500).json({ error: err.message });

        if (existing) {
            db.run('UPDATE customers SET name = ?, email = ? WHERE id = ?', [name.trim(), email.trim(), existing.id], function (err) {
                if (err) return res.status(500).json({ error: err.message });
                res.json({ message: 'Customer updated', customer: { ...existing, name: name.trim(), email: email.trim() } });
            });
        } else {
            const now = Date.now();
            db.run(`
                INSERT INTO customers (name, phone, email, totalPurchases, createdAt)
                VALUES (?, ?, ?, 0.0, ?)
            `, [name.trim(), phone.trim(), email.trim(), now], function (err) {
                if (err) return res.status(500).json({ error: err.message });
                res.status(201).json({
                    message: 'Customer added',
                    customer: { id: this.lastID, name: name.trim(), phone: phone.trim(), email: email.trim(), totalPurchases: 0.0, createdAt: now }
                });
            });
        }
    });
});

module.exports = router;
