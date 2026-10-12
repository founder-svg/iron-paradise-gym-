const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { db } = require('../database');
const { JWT_SECRET, authenticateToken } = require('../middleware/auth');

// 1. Sign Up (Email & Password)
router.post('/register', (req, res) => {
    const { email, password, name, role = 'STAFF', authProvider = 'EMAIL', phone = '' } = req.body;

    if (!email || !password || !name) {
        return res.status(400).json({ error: 'Name, email and password are required' });
    }

    const cleanEmail = email.trim().toLowerCase();
    const cleanRole = role.toUpperCase() === 'ADMIN' ? 'ADMIN' : 'STAFF';
    // Staff signups default to PENDING; Admin can be auto-approved or pending based on setup
    const initialStatus = cleanRole === 'ADMIN' ? 'APPROVED' : 'PENDING';

    db.get('SELECT * FROM users WHERE email = ?', [cleanEmail], (err, existing) => {
        if (err) return res.status(500).json({ error: err.message });
        if (existing) {
            return res.status(409).json({ error: 'An account with this email already exists' });
        }

        const hashedPassword = bcrypt.hashSync(password, 10);
        const now = Date.now();

        db.run(`
            INSERT INTO users (email, password, name, role, status, authProvider, phone, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `, [cleanEmail, hashedPassword, name.trim(), cleanRole, initialStatus, authProvider, phone, now], function (err) {
            if (err) return res.status(500).json({ error: err.message });

            const userId = this.lastID;
            const token = jwt.sign(
                { id: userId, email: cleanEmail, name: name.trim(), role: cleanRole, status: initialStatus },
                JWT_SECRET,
                { expiresIn: '30d' }
            );

            res.status(201).json({
                message: cleanRole === 'STAFF' ? 'Staff registered successfully. Awaiting admin approval.' : 'Admin account created successfully.',
                token,
                user: {
                    id: userId,
                    email: cleanEmail,
                    name: name.trim(),
                    role: cleanRole,
                    status: initialStatus
                }
            });
        });
    });
});

// 2. Sign In (Email & Password / PIN)
router.post('/login', (req, res) => {
    const { email, password } = req.body;

    if (!email || !password) {
        return res.status(400).json({ error: 'Email and password are required' });
    }

    const cleanEmail = email.trim().toLowerCase();

    db.get('SELECT * FROM users WHERE email = ?', [cleanEmail], (err, user) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!user) {
            return res.status(401).json({ error: 'Invalid email or password' });
        }

        const isMatch = bcrypt.compareSync(password, user.password) || password === user.password;
        if (!isMatch) {
            return res.status(401).json({ error: 'Invalid email or password' });
        }

        if (user.status === 'PENDING') {
            return res.status(403).json({
                error: 'Your account is pending admin approval. Please wait for the admin to approve your account.',
                status: 'PENDING',
                user: {
                    id: user.id,
                    email: user.email,
                    name: user.name,
                    role: user.role,
                    status: user.status
                }
            });
        }

        if (user.status === 'REJECTED') {
            return res.status(403).json({ error: 'Your account registration was rejected by Admin.', status: 'REJECTED' });
        }

        const token = jwt.sign(
            { id: user.id, email: user.email, name: user.name, role: user.role, status: user.status },
            JWT_SECRET,
            { expiresIn: '30d' }
        );

        res.json({
            message: 'Login successful',
            token,
            user: {
                id: user.id,
                email: user.email,
                name: user.name,
                role: user.role,
                status: user.status,
                phone: user.phone
            }
        });
    });
});

// 3. Google Sign-In / 1-Tap Account Creation
router.post('/google', (req, res) => {
    const { email, name, role = 'STAFF' } = req.body;

    if (!email || !name) {
        return res.status(400).json({ error: 'Google email and name are required' });
    }

    const cleanEmail = email.trim().toLowerCase();
    const cleanRole = role.toUpperCase() === 'ADMIN' ? 'ADMIN' : 'STAFF';

    db.get('SELECT * FROM users WHERE email = ?', [cleanEmail], (err, user) => {
        if (err) return res.status(500).json({ error: err.message });

        if (user) {
            if (user.status === 'PENDING') {
                return res.status(403).json({
                    error: 'Staff account awaiting admin approval',
                    status: 'PENDING',
                    user: { id: user.id, email: user.email, name: user.name, role: user.role, status: user.status }
                });
            }

            const token = jwt.sign(
                { id: user.id, email: user.email, name: user.name, role: user.role, status: user.status },
                JWT_SECRET,
                { expiresIn: '30d' }
            );

            return res.json({
                message: 'Google login successful',
                token,
                user: { id: user.id, email: user.email, name: user.name, role: user.role, status: user.status }
            });
        }

        // New user through Google
        const status = cleanRole === 'ADMIN' ? 'APPROVED' : 'PENDING';
        const dummyPass = bcrypt.hashSync('google-auth-' + Date.now(), 10);
        const now = Date.now();

        db.run(`
            INSERT INTO users (email, password, name, role, status, authProvider, createdAt)
            VALUES (?, ?, ?, ?, ?, 'GOOGLE', ?)
        `, [cleanEmail, dummyPass, name.trim(), cleanRole, status, now], function (err) {
            if (err) return res.status(500).json({ error: err.message });

            const newUserId = this.lastID;
            const token = jwt.sign(
                { id: newUserId, email: cleanEmail, name: name.trim(), role: cleanRole, status },
                JWT_SECRET,
                { expiresIn: '30d' }
            );

            res.status(201).json({
                message: status === 'PENDING' ? 'Google staff account created. Awaiting admin approval.' : 'Google admin account created.',
                token,
                user: { id: newUserId, email: cleanEmail, name: name.trim(), role: cleanRole, status }
            });
        });
    });
});

// 4. Get Current User Profile (Auto-Login Verification)
router.get('/me', authenticateToken, (req, res) => {
    db.get('SELECT id, email, name, role, status, phone, authProvider, createdAt FROM users WHERE id = ?', [req.user.id], (err, user) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!user) return res.status(404).json({ error: 'User not found' });
        res.json({ user });
    });
});

module.exports = router;
