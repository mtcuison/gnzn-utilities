package org.guanzon.gnzn.utilities.finance;

import java.math.BigDecimal;

/**
 * CWT FIELDS block ("T" record) from the CC File Layout.
 * One CWT header ("C" record) may be followed by one or more of these,
 * one per BIR Alphanumeric Tax Code (ATC) being withheld against the payee
 * for the quarter named in the parent CwtInfo's tax period.
 *
 * Spec reference (row 37-41):
 *   Constant                        : "T"                (Record Type)
 *   Alphanumeric Tax Code           : <=10 chars           e.g. "WC160"
 *   First Month of Quarter Income   : <=9 chars, blank if no amount (do not put zero)
 *   Second Month of Quarter Income  : <=9 chars, blank if no amount (do not put zero)
 *   Third Month of Quarter Income   : <=9 chars, blank if no amount (do not put zero)
 *
 * Only the month(s) in which an actual payment fell should carry an amount;
 * the rest are left null (rendered as an empty field, never "0").
 *
 * NOTE: The Alphanumeric Tax Code (ATC) must match a code that is valid
 * under current BIR regulations for the nature of income being paid
 * (e.g. professional fees, rentals, goods, services). This sample uses
 * WC160 purely because it is the example value in the source template;
 * verify the correct ATC for each transaction against BIR issuances /
 * your tax team before using this in production.
 */
public final class CwtLine {

    private final String alphanumericTaxCode;
    private final BigDecimal firstMonthIncome;
    private final BigDecimal secondMonthIncome;
    private final BigDecimal thirdMonthIncome;

    public CwtLine(String alphanumericTaxCode,
                   BigDecimal firstMonthIncome,
                   BigDecimal secondMonthIncome,
                   BigDecimal thirdMonthIncome) {
        this.alphanumericTaxCode = alphanumericTaxCode;
        this.firstMonthIncome = firstMonthIncome;
        this.secondMonthIncome = secondMonthIncome;
        this.thirdMonthIncome = thirdMonthIncome;
    }

    public String getAlphanumericTaxCode() {
        return alphanumericTaxCode;
    }

    public BigDecimal getFirstMonthIncome() {
        return firstMonthIncome;
    }

    public BigDecimal getSecondMonthIncome() {
        return secondMonthIncome;
    }

    public BigDecimal getThirdMonthIncome() {
        return thirdMonthIncome;
    }
}
