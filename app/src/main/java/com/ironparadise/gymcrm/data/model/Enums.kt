package com.ironparadise.gymcrm.data.model

enum class TransactionType {
    STOCK_IN,
    SALE,
    SALE_RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT
}

enum class PaymentMethod {
    CASH,
    UPI,
    CARD,
    OTHER
}

enum class UserRole {
    ADMIN,
    STAFF
}

enum class ProductStatus {
    ACTIVE,
    INACTIVE
}

enum class AdjustmentReason {
    DAMAGED,
    EXPIRED,
    LOST,
    COUNTING_CORRECTION,
    RETURNED,
    OTHER
}
