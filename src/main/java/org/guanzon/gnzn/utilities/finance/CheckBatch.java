package org.guanzon.gnzn.utilities.finance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One "H" header + N "D" (and nested C/T/I) records = one uploadable
 * CC file, per the source template:
 *
 *   File Extension : txt
 *   Service        : Corporate Check
 *   Product Type   : Self Service and Outsourced
 *   Layout Type    : Header, Details
 *
 *   HEADER FIELDS (row 7-10):
 *     Constant        : "H"
 *     Total Count     : number of DETAIL ("D") records in this file
 *     Total Amount    : sum of all DETAIL amounts in this file
 *     Account Number  : Enrolled Funding Account Number (<=16 chars)
 *
 * Note on the sheet: "Batch Upload should be either All Self service or
 * Outsourced only" - i.e. a single file may not mix self-service and
 * outsourced DETAIL records. This is enforced in
 * {@link com.rmjm.checkexport.io.PnbCcFileWriter#validate}.
 */
public final class CheckBatch {

    private final String accountNumber;
    private final List<CheckDetail> details;

    public CheckBatch(String accountNumber) {
        this.accountNumber = accountNumber;
        this.details = new ArrayList<CheckDetail>();
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public CheckBatch addDetail(CheckDetail detail) {
        details.add(detail);
        return this;
    }

    public List<CheckDetail> getDetails() {
        return Collections.unmodifiableList(details);
    }
}
