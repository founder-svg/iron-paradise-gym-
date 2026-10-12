const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { authenticateToken, requireAdmin } = require('../middleware/auth');

// All endpoints in this route require Admin authentication
router.use(authenticateToken);
router.use(requireAdmin);

// 1. Get all pending staff members
router.get('/pending', (req, res) => {
    db.all(`
        SELECT id, email, name, role, status, authProvider, phone, createdAt
        FROM users
        WHERE role = 'STAFF' AND status = 'PENDING'
        ORDER BY createdAt DESC
    `, (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ pendingCount: rows.length, staff: rows });
    });
});

// 2. Get all staff members (Approved, Pending, Rejected)
router.get('/all', (req, res) => {
    db.all(`
        SELECT id, email, name, role, status, authProvider, phone, createdAt
        FROM users
        WHERE role = 'STAFF'
        ORDER BY createdAt DESC
    `, (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ staff: rows });
    });
});

// 3. Update staff approval status (APPROVED / REJECTED)
router.patch('/:id/status', (req, res) => {
    const { status } = req.body;
    const { id } = req.params;

    if (!['APPROVED', 'REJECTED', 'PENDING'].includes(status)) {
        return res.status(400).json({ error: 'Status must be APPROVED, REJECTED, or PENDING' });
    }

    db.run('UPDATE users SET status = ? WHERE id = ?', [status, id], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Staff member not found' });

        res.json({ message: `Staff status updated to ${status}`, id, status });
    });
});

// 4. Delete staff member
router.delete('/:id', (req, res) => {
    const { id } = req.params;

    db.run("DELETE FROM users WHERE id = ? AND role = 'STAFF'", [id], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Staff member not found or is not staff' });

        res.json({ message: 'Staff member removed successfully', id });
    });
});

module.exports = router;
