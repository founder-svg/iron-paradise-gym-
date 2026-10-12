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

// 3. Edit Customer (Admin Only)
router.put('/:id', optionalAuth, (req, res) => {
    if (req.user && req.user.role !== 'ADMIN') {
        return res.status(403).json({ error: 'Access Denied: Only Admin can edit registered customer records' });
    }
    const { id } = req.params;
    const { name, phone, email } = req.body;

    db.run(`
        UPDATE customers
        SET name = COALESCE(?, name),
            phone = COALESCE(?, phone),
            email = COALESCE(?, email)
        WHERE id = ?
    `, [name, phone, email, id], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Customer not found' });
        res.json({ message: 'Customer updated successfully by Admin', id });
    });
});

// 4. Delete Customer (Admin Only)
router.delete('/:id', optionalAuth, (req, res) => {
    if (req.user && req.user.role !== 'ADMIN') {
        return res.status(403).json({ error: 'Access Denied: Only Admin can delete customer records' });
    }
    const { id } = req.params;

    db.run('DELETE FROM customers WHERE id = ?', [id], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Customer not found' });
        res.json({ message: 'Customer deleted successfully by Admin', id });
    });
});

module.exports = router;
