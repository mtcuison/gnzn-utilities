package org.guanzon.gnzn.utilities.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * VOUCHER FIELDS block ("I" record) from the CC File Layout.
 * One check (DETAIL record) may carry zero or more invoices attached to it
 * (e.g. when the check is settling one or several supplier invoices).
 *
 * Spec reference (row 43-50 of "PNB CC File Layout" sheet):
 *   Constant                : "I"                      (Record Type)
 *   Repeating Field 1       : Invoice Number            (<=50 chars)
 *   Repeating Field 2       : Invoice Date               (MM/DD/YYYY)
 *   Repeating Field 3       : Invoice Description        (<=295 chars)
 *   Repeating Field 4       : Invoice Amount             (<=9 chars)
 *   Repeating Field 5       : Invoice WHT Amount         (<=9 chars)
 *   Repeating Field 6       : Invoice VAT Amount         (<=9 chars)
 *   Repeating Field 7       : Invoice Net Amount         (<=9 chars)
 *
 * All fields in this block are documented as "not required" individually,
 * but if you attach an Invoice at all, invoiceNumber/date/amount are the
 * practical minimum for the record to mean anything.
 */
public final class Invoice {

    private final String invoiceNumber;
    private final LocalDate invoiceDate;
    private final String description;
    private final BigDecimal amount;
    private final BigDecimal whtAmount;
    private final BigDecimal vatAmount;
    private final BigDecimal netAmount;

    public Invoice(String invoiceNumber,
                    LocalDate invoiceDate,
                    String description,
                    BigDecimal amount,
                    BigDecimal whtAmount,
                    BigDecimal vatAmount,
                    BigDecimal netAmount) {
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.description = description;
        this.amount = amount;
        this.whtAmount = whtAmount;
        this.vatAmount = vatAmount;
        this.netAmount = netAmount;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getWhtAmount() {
        return whtAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }
}
