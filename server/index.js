require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const { initDatabase, db } = require('./src/database');

const app = express();
const PORT = process.env.PORT || 5000;

// Enable CORS for Android APKs and Web clients
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Ensure upload directory exists
const uploadDir = process.env.UPLOAD_DIR || path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

// Serve uploaded supplement photos
app.use('/uploads', express.static(uploadDir));
// Serve public assets (Status dashboard)
app.use(express.static(path.join(__dirname, 'public')));

// Configure Multer for Photo Uploads
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, uploadDir);
    },
    filename: (req, file, cb) => {
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1e9);
        const ext = path.extname(file.originalname) || '.jpg';
        cb(null, 'product-' + uniqueSuffix + ext);
    }
});
const upload = multer({
    storage,
    limits: { fileSize: 10 * 1024 * 1024 } // 10MB max
});

// Photo Upload Endpoint
app.post('/api/upload', upload.single('photo'), (req, res) => {
    if (!req.file) {
        return res.status(400).json({ error: 'No image file uploaded' });
    }

    const host = req.get('host');
    const protocol = req.protocol;
    const fileUrl = `${protocol}://${host}/uploads/${req.file.filename}`;

    res.json({
        message: 'Photo uploaded successfully',
        filename: req.file.filename,
        url: fileUrl
    });
});

// API Routes
app.use('/api/auth', require('./src/routes/auth'));
app.use('/api/staff', require('./src/routes/staff'));
app.use('/api/products', require('./src/routes/products'));
app.use('/api/sales', require('./src/routes/sales'));
app.use('/api/customers', require('./src/routes/customers'));
app.use('/api/reports', require('./src/routes/reports'));
app.use('/api/sync', require('./src/routes/sync'));

// 24/7 Server Health & Live Status Endpoint
app.get('/health', (req, res) => {
    db.get('SELECT COUNT(*) as userCount FROM users', (err, userRow) => {
        db.get('SELECT COUNT(*) as productCount FROM products', (err, prodRow) => {
            db.get('SELECT COUNT(*) as salesCount FROM sales', (err, saleRow) => {
                res.json({
                    status: 'ONLINE',
                    service: 'Iron Paradise Gym Cloud Backend (24/7)',
                    version: '1.0.0',
                    timestamp: new Date().toISOString(),
                    uptimeSeconds: Math.floor(process.uptime()),
                    environment: process.env.NODE_ENV || 'production',
                    stats: {
                        registeredUsers: (userRow && userRow.userCount) || 0,
                        supplementProducts: (prodRow && prodRow.productCount) || 0,
                        completedSales: (saleRow && saleRow.salesCount) || 0
                    }
                });
            });
        });
    });
});

// Start SQLite Database and Express Server
initDatabase();

app.listen(PORT, '0.0.0.0', () => {
    console.log(`=======================================================`);
    console.log(`⚡ IRON PARADISE GYM CRM - 24/7 CLOUD BACKEND SERVER`);
    console.log(`📡 Listening on: http://0.0.0.0:${PORT}`);
    console.log(`🩺 Health check: http://localhost:${PORT}/health`);
    console.log(`🌐 Public status dashboard: http://localhost:${PORT}`);
    console.log(`=======================================================`);
});
