const express = require('express');
const router = express.Router();
const { db } = require('../database');
const { authenticateToken, requireAdmin } = require('../middleware/auth');

router.use(authenticateToken);
router.use(requireAdmin);

// 1. Profit, Sales & Financial Summary (Admin Only)
router.get('/summary', (req, res) => {
    db.get(`
        SELECT COUNT(*) as totalSalesCount,
               COALESCE(SUM(subtotal), 0) as totalGrossSales,
               COALESCE(SUM(discount), 0) as totalDiscountsGiven,
               COALESCE(SUM(grandTotal), 0) as totalRevenue
        FROM sales
    `, (err, salesStats) => {
        if (err) return res.status(500).json({ error: err.message });

        // Calculate Cost of Goods Sold from sale_items and product purchasePrice
        db.get(`
            SELECT COALESCE(SUM(si.quantity * p.purchasePrice), 0) as totalCogs
            FROM sale_items si
            JOIN products p ON si.productId = p.id
        `, (err, cogsStats) => {
            if (err) return res.status(500).json({ error: err.message });

            const revenue = salesStats.totalRevenue || 0.0;
            const cogs = cogsStats.totalCogs || 0.0;
            const grossProfit = revenue - cogs;
            const profitMargin = revenue > 0 ? ((grossProfit / revenue) * 100).toFixed(1) : '0.0';

            // Total Current Inventory Valuation
            db.all(`
                SELECT p.purchasePrice, p.sellingPrice,
                       SUM(CASE
                           WHEN st.transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN st.quantity
                           ELSE -st.quantity
                       END) as stock
                FROM products p
                LEFT JOIN stock_transactions st ON p.id = st.productId
                GROUP BY p.id
            `, (err, stockValRows) => {
                if (err) return res.status(500).json({ error: err.message });

                let inventoryCostValuation = 0.0;
                let inventoryRetailValuation = 0.0;

                (stockValRows || []).forEach(r => {
                    const st = r.stock || 0;
                    if (st > 0) {
                        inventoryCostValuation += st * (r.purchasePrice || 0);
                        inventoryRetailValuation += st * (r.sellingPrice || 0);
                    }
                });

                res.json({
                    sales: {
                        count: salesStats.totalSalesCount || 0,
                        revenue,
                        discounts: salesStats.totalDiscountsGiven || 0
                    },
                    profitability: {
                        costOfGoodsSold: cogs,
                        grossProfit,
                        marginPercentage: `${profitMargin}%`
                    },
                    inventory: {
                        costValuation: inventoryCostValuation,
                        retailValuation: inventoryRetailValuation
                    }
                });
            });
        });
    });
});

module.exports = router;
