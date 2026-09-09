package org.guanzon.gnzn.utilities.finance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HEADER CWT block ("C" record) from the CC File Layout, carried on a
 * CheckDetail whenever the check is subject to Creditable Withholding Tax.
 * Followed by one or more CwtLine ("T" record) entries.
 *
 * Spec reference (row 29-35):
 *   Constant          : "C"                          (Record Type)
 *   Payee Name        : should be identical to the DETAIL Payee Name
 *   Payee Address     : <=100 chars
 *   Payee ZIP Code    : <=32 chars
 *   Payee TIN         : <=14 chars
 *   Tax Period From   : MM/DD/YYYY, mandatory if with CWT
 *   Tax Period To     : MM/DD/YYYY, mandatory if with CWT
 */
public final class CwtInfo {

    private final String payeeAddress;
    private final String payeeZipCode;
    private final String payeeTin;
    private final LocalDate taxPeriodFrom;
    private final LocalDate taxPeriodTo;
    private final List<CwtLine> lines;

    public CwtInfo(String payeeAddress,
                    String payeeZipCode,
                    String payeeTin,
                    LocalDate taxPeriodFrom,
                    LocalDate taxPeriodTo,
                    List<CwtLine> lines) {
        this.payeeAddress = payeeAddress;
        this.payeeZipCode = payeeZipCode;
        this.payeeTin = payeeTin;
        this.taxPeriodFrom = taxPeriodFrom;
        this.taxPeriodTo = taxPeriodTo;
        this.lines = lines == null ? new ArrayList<CwtLine>() : new ArrayList<CwtLine>(lines);
    }

    public String getPayeeAddress() {
        return payeeAddress;
    }

    public String getPayeeZipCode() {
        return payeeZipCode;
    }

    public String getPayeeTin() {
        return payeeTin;
    }

    public LocalDate getTaxPeriodFrom() {
        return taxPeriodFrom;
    }

    public LocalDate getTaxPeriodTo() {
        return taxPeriodTo;
    }

    public List<CwtLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
