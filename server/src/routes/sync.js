const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { optionalAuth } = require('../middleware/auth');

// 1. Pull All Data for Device Sync
router.get('/pull', optionalAuth, (req, res) => {
    const isAdmin = req.user && req.user.role === 'ADMIN';

    db.all('SELECT * FROM products ORDER BY name ASC', (err, products) => {
        if (err) return res.status(500).json({ error: err.message });

        db.all('SELECT * FROM customers ORDER BY name ASC', (err, customers) => {
            if (err) return res.status(500).json({ error: err.message });

            db.all('SELECT * FROM stock_transactions ORDER BY date DESC LIMIT 500', (err, transactions) => {
                if (err) return res.status(500).json({ error: err.message });

                db.all('SELECT * FROM sales ORDER BY date DESC LIMIT 200', (err, sales) => {
                    if (err) return res.status(500).json({ error: err.message });

                    // Mask purchase price if not admin
                    const safeProducts = products.map(p => {
                        if (!isAdmin) {
                            const { purchasePrice, ...rest } = p;
                            return rest;
                        }
                        return p;
                    });

                    res.json({
                        syncTimestamp: Date.now(),
                        products: safeProducts,
                        customers,
                        transactions,
                        sales
                    });
                });
            });
        });
    });
});

// 2. Check User Status Sync (for real-time approval update on staff phones)
router.get('/status', (req, res) => {
    const email = req.query.email;
    if (!email) return res.status(400).json({ error: 'Email parameter required' });

    db.get('SELECT id, email, name, role, status FROM users WHERE email = ?', [email.trim().toLowerCase()], (err, user) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!user) return res.status(404).json({ error: 'User not found' });
        res.json({ user });
    });
});

module.exports = router;
