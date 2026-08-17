package com.medos.util;

import com.medos.entity.Charge;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * Centralized money/decimal utilities for consistent financial calculations.
 * 
 * Uses MathContext.DECIMAL64 (16 digits precision) with HALF_UP rounding
 * as per Indian GAAP/RBI guidelines for financial calculations.
 * 
 * GST rates: 
 * - Pharmacy/Medicines: 5%
 * - Room/Service charges: 12% (configurable)
 * - Lab/Procedure: 18% (configurable)
 */
@Slf4j
public final class MoneyUtil {

    // Precision context for financial calculations (16 digits, HALF_UP)
    public static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    
    // Scale for monetary amounts (2 decimal places for INR)
    public static final int SCALE = 2;
    
    // Default currency
    public static final Currency DEFAULT_CURRENCY = Currency.getInstance(Locale.forLanguageTag("en-IN"));
    
    // Standard GST rates for healthcare
    public static final BigDecimal GST_RATE_PHARMACY = new BigDecimal("5.00");    // 5% for medicines
    public static final BigDecimal GST_RATE_ROOM = new BigDecimal("12.00");       // 12% for room/accommodation
    public static final BigDecimal GST_RATE_SERVICE = new BigDecimal("18.00");    // 18% for services/lab/procedures
    public static final BigDecimal GST_RATE_ZERO = BigDecimal.ZERO;               // 0% exempt

    private MoneyUtil() {
        // Utility class
    }

    /**
     * Normalize a monetary amount to standard scale with HALF_UP rounding.
     */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Add two monetary amounts with normalization.
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return normalize(normalize(a).add(normalize(b)));
    }

    /**
     * Subtract b from a with normalization.
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return normalize(normalize(a).subtract(normalize(b)));
    }

    /**
     * Multiply two monetary amounts with normalization.
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return normalize(normalize(a).multiply(normalize(b), MATH_CONTEXT));
    }

    /**
     * Divide a by b with normalization.
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return normalize(normalize(a).divide(normalize(b), MATH_CONTEXT));
    }

    /**
     * Calculate percentage of amount (e.g., GST calculation).
     * amount * percentage / 100
     */
    public static BigDecimal percentageOf(BigDecimal amount, BigDecimal percentage) {
        return normalize(multiply(normalize(amount), percentage).divide(new BigDecimal("100")));
    }

    /**
     * Calculate GST amount for a given amount and rate.
     * gstAmount = amount * gstRate / 100
     */
    public static BigDecimal calculateGst(BigDecimal amount, BigDecimal gstRate) {
        return percentageOf(amount, gstRate);
    }

    /**
     * Calculate total amount including GST.
     * total = amount + gstAmount
     */
    public static BigDecimal addGst(BigDecimal amount, BigDecimal gstRate) {
        return add(normalize(amount), calculateGst(amount, gstRate));
    }

    /**
     * Extract base amount from GST-inclusive total.
     * base = total / (1 + gstRate/100)
     */
    public static BigDecimal extractBaseFromGstInclusive(BigDecimal totalInclusive, BigDecimal gstRate) {
        BigDecimal divisor = BigDecimal.ONE.add(percentageOf(BigDecimal.ONE, gstRate));
        return normalize(divide(totalInclusive, divisor));
    }

    /**
     * Extract GST amount from GST-inclusive total.
     * gst = total - base
     */
    public static BigDecimal extractGstFromInclusive(BigDecimal totalInclusive, BigDecimal gstRate) {
        BigDecimal base = extractBaseFromGstInclusive(totalInclusive, gstRate);
        return subtract(totalInclusive, base);
    }

    /**
     * Round to nearest whole number (for quantities).
     */
    public static BigDecimal roundToWhole(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Compare two amounts for equality (considering scale).
     */
    public static boolean equals(BigDecimal a, BigDecimal b) {
        return normalize(a).compareTo(normalize(b)) == 0;
    }

    /**
     * Check if amount is positive.
     */
    public static boolean isPositive(BigDecimal amount) {
        return normalize(amount).compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if amount is zero or negative.
     */
    public static boolean isZeroOrNegative(BigDecimal amount) {
        return normalize(amount).compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Calculate line item totals for billing.
     * Returns array: [amount, gstAmount, totalAmount]
     */
    public static BigDecimal[] calculateLineItem(BigDecimal unitPrice, int quantity, BigDecimal gstRate) {
        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal amount = normalize(multiply(unitPrice, qty));
        BigDecimal gstAmount = calculateGst(amount, gstRate);
        BigDecimal totalAmount = add(amount, gstAmount);
        return new BigDecimal[]{amount, gstAmount, totalAmount};
    }

    /**
     * Calculate line item totals with discount.
     * Returns array: [amount, discountAmount, gstAmount, totalAmount]
     */
    public static BigDecimal[] calculateLineItemWithDiscount(BigDecimal unitPrice, int quantity, BigDecimal gstRate, BigDecimal discountPercent) {
        BigDecimal[] base = calculateLineItem(unitPrice, quantity, gstRate);
        BigDecimal amount = base[0];
        BigDecimal discountAmount = percentageOf(amount, discountPercent);
        BigDecimal amountAfterDiscount = subtract(amount, discountAmount);
        BigDecimal gstAmount = calculateGst(amountAfterDiscount, gstRate);
        BigDecimal totalAmount = add(amountAfterDiscount, gstAmount);
        return new BigDecimal[]{amount, discountAmount, gstAmount, totalAmount};
    }

    /**
     * Get standard GST rate for charge type.
     */
    public static BigDecimal getGstRateForChargeType(Charge.ChargeType chargeType) {
        return switch (chargeType) {
            case pharmacy -> GST_RATE_PHARMACY;
            case room -> GST_RATE_ROOM;
            case consultation, lab, procedure -> GST_RATE_SERVICE;
            case misc -> GST_RATE_SERVICE;
            default -> GST_RATE_SERVICE;
        };
    }

    /**
     * Validate that amount is not negative.
     */
    public static void validateNonNegative(BigDecimal amount, String fieldName) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
    }

    /**
     * Format amount as string with currency symbol.
     */
    public static String format(BigDecimal amount) {
        return format(amount, DEFAULT_CURRENCY);
    }

    /**
     * Format amount with currency.
     */
    public static String format(BigDecimal amount, Currency currency) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        String symbol = currency.getSymbol();
        return String.format("%s %,.2f", symbol, normalize(amount));
    }
}