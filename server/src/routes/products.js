const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { authenticateToken, requireAdmin, optionalAuth } = require('../middleware/auth');

// Helper to get net stock for products
function getProductsWithStock(callback) {
    db.all('SELECT * FROM products ORDER BY name ASC', (err, products) => {
        if (err) return callback(err);

        db.all(`
            SELECT productId,
                   SUM(CASE
                       WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity
                       ELSE -quantity
                   END) as netStock
            FROM stock_transactions
            GROUP BY productId
        `, (err, stockRows) => {
            if (err) return callback(err);

            const stockMap = {};
            stockRows.forEach(r => {
                stockMap[r.productId] = r.netStock || 0;
            });

            const result = products.map(p => {
                const stock = stockMap[p.id] || 0;
                return {
                    ...p,
                    currentStock: stock,
                    isOutOfStock: stock <= 0,
                    isSoldOut: stock <= 0,
                    isLowStock: stock > 0 && stock <= p.minimumStock
                };
            });

            callback(null, result);
        });
    });
}

// 1. Get all products (Role-sensitive: staff cannot see purchasePrice!)
router.get('/', optionalAuth, (req, res) => {
    const isAdmin = req.user && req.user.role === 'ADMIN';

    getProductsWithStock((err, products) => {
        if (err) return res.status(500).json({ error: err.message });

        // If not admin, mask/hide purchasePrice
        const responseProducts = products.map(p => {
            if (!isAdmin) {
                const { purchasePrice, ...staffSafeProduct } = p;
                return staffSafeProduct;
            }
            return p;
        });

        res.json({ products: responseProducts });
    });
});

// 2. Get single product detail
router.get('/:id', optionalAuth, (req, res) => {
    const isAdmin = req.user && req.user.role === 'ADMIN';
    const { id } = req.params;

    db.get('SELECT * FROM products WHERE id = ?', [id], (err, product) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!product) return res.status(404).json({ error: 'Product not found' });

        db.get(`
            SELECT SUM(CASE
                WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity
                ELSE -quantity
            END) as netStock
            FROM stock_transactions
            WHERE productId = ?
        `, [id], (err, row) => {
            const currentStock = (row && row.netStock) ? row.netStock : 0;
            const fullProduct = {
                ...product,
                currentStock,
                isOutOfStock: currentStock <= 0,
                isSoldOut: currentStock <= 0,
                isLowStock: currentStock > 0 && currentStock <= product.minimumStock
            };

            if (!isAdmin) {
                delete fullProduct.purchasePrice;
            }

            res.json({ product: fullProduct });
        });
    });
});

// 3. Add new product (Admin Only)
router.post('/', authenticateToken, requireAdmin, (req, res) => {
    const {
        id,
        name,
        brand,
        category,
        variant = '',
        unit = 'Unit',
        purchasePrice = 0.0,
        sellingPrice = 0.0,
        minimumStock = 0,
        imageUri = '',
        initialStock = 0
    } = req.body;

    if (!name || !brand || !category) {
        return res.status(400).json({ error: 'Name, brand and category are required' });
    }

    const productId = id && id.trim().length > 0
        ? id.trim().toUpperCase()
        : `IP-${category.substring(0, 3).toUpperCase()}-${Date.now().toString().slice(-4)}`;

    const now = Date.now();

    db.run(`
        INSERT INTO products (id, name, brand, category, variant, unit, purchasePrice, sellingPrice, minimumStock, imageUri, createdAt)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [productId, name, brand, category, variant, unit, purchasePrice, sellingPrice, minimumStock, imageUri, now], function (err) {
        if (err) return res.status(500).json({ error: err.message });

        // If initial stock provided, create Stock In transaction
        if (initialStock > 0) {
            db.run(`
                INSERT INTO stock_transactions (productId, productName, transactionType, quantity, unitPrice, total, balanceAfter, referenceId, notes, date, user)
                VALUES (?, ?, 'STOCK_IN', ?, ?, ?, ?, 'INITIAL-STOCK', 'Opening inventory', ?, ?)
            `, [productId, name, initialStock, purchasePrice, initialStock * purchasePrice, initialStock, now, req.user.name]);
        }

        res.status(201).json({
            message: 'Product created successfully',
            productId,
            name
        });
    });
});

// 4. Update Product (Admin Only)
router.put('/:id', authenticateToken, requireAdmin, (req, res) => {
    const { id } = req.params;
    const { name, brand, category, variant, unit, purchasePrice, sellingPrice, minimumStock, imageUri } = req.body;

    db.run(`
        UPDATE products
        SET name = COALESCE(?, name),
            brand = COALESCE(?, brand),
            category = COALESCE(?, category),
            variant = COALESCE(?, variant),
            unit = COALESCE(?, unit),
            purchasePrice = COALESCE(?, purchasePrice),
            sellingPrice = COALESCE(?, sellingPrice),
            minimumStock = COALESCE(?, minimumStock),
            imageUri = COALESCE(?, imageUri)
        WHERE id = ?
    `, [name, brand, category, variant, unit, purchasePrice, sellingPrice, minimumStock, imageUri, id], function (err) {
        if (err) return res.status(500).json({ error: err.message });
        if (this.changes === 0) return res.status(404).json({ error: 'Product not found' });

        res.json({ message: 'Product updated successfully', id });
    });
});

// 5. Stock In Operation (Admin Only)
router.post('/:id/stock-in', authenticateToken, requireAdmin, (req, res) => {
    const { id } = req.params;
    const { quantity, unitCost, supplier = 'Generic Supplier', notes = '' } = req.body;

    if (!quantity || quantity <= 0) {
        return res.status(400).json({ error: 'Quantity must be greater than 0' });
    }

    db.get('SELECT * FROM products WHERE id = ?', [id], (err, product) => {
        if (err) return res.status(500).json({ error: err.message });
        if (!product) return res.status(404).json({ error: 'Product not found' });

        const cost = unitCost !== undefined ? unitCost : product.purchasePrice;
        const total = quantity * cost;
        const now = Date.now();

        // Calculate current stock to get balance after
        db.get(`
            SELECT SUM(CASE
                WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity
                ELSE -quantity
            END) as netStock
            FROM stock_transactions
            WHERE productId = ?
        `, [id], (err, row) => {
            const currentStock = (row && row.netStock) ? row.netStock : 0;
            const balanceAfter = currentStock + quantity;
            const refId = `IN-${Date.now().toString().slice(-6)}`;

            db.run(`
                INSERT INTO stock_transactions (productId, productName, transactionType, quantity, unitPrice, total, balanceAfter, referenceId, notes, date, user)
                VALUES (?, ?, 'STOCK_IN', ?, ?, ?, ?, ?, ?, ?, ?)
            `, [id, product.name, quantity, cost, total, balanceAfter, refId, notes || `Supplier: ${supplier}`, now, req.user.name], function (err) {
                if (err) return res.status(500).json({ error: err.message });

                // Also update product purchase price if changed
                if (unitCost !== undefined && unitCost > 0) {
                    db.run('UPDATE products SET purchasePrice = ? WHERE id = ?', [unitCost, id]);
                }

                res.status(201).json({
                    message: `Successfully stocked in ${quantity} units of ${product.name}`,
                    balanceAfter,
                    referenceId: refId
                });
            });
        });
    });
});

// 6. Delete Product Completely (Admin Only)
router.delete('/:id', authenticateToken, requireAdmin, (req, res) => {
    const { id } = req.params;

    db.run('DELETE FROM stock_transactions WHERE productId = ?', [id], (err) => {
        if (err) return res.status(500).json({ error: err.message });

        db.run('DELETE FROM products WHERE id = ?', [id], function (err) {
            if (err) return res.status(500).json({ error: err.message });
            if (this.changes === 0) return res.status(404).json({ error: 'Product not found' });

            res.json({ message: 'Product and stock history deleted completely', id });
        });
    });
});

module.exports = router;
