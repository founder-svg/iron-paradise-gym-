const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'iron_paradise_gym_secret_key_2026';

function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (!token) {
        return res.status(401).json({ error: 'Authentication required. Missing token.' });
    }

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (err) {
            return res.status(403).json({ error: 'Invalid or expired token.' });
        }

        if (user.status !== 'APPROVED') {
            return res.status(403).json({ error: 'Account pending admin approval or suspended.', status: user.status });
        }

        req.user = user;
        next();
    });
}

function requireAdmin(req, res, next) {
    if (!req.user || !['ADMIN', 'STAFF'].includes(req.user.role)) {
        return res.status(403).json({ error: 'Access denied. Valid account required.' });
    }
    next();
}

function optionalAuth(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (!token) {
        req.user = null;
        return next();
    }

    jwt.verify(token, JWT_SECRET, (err, user) => {
        if (!err && user.status === 'APPROVED') {
            req.user = user;
        } else {
            req.user = null;
        }
        next();
    });
}

module.exports = {
    JWT_SECRET,
    authenticateToken,
    requireAdmin,
    optionalAuth
};
