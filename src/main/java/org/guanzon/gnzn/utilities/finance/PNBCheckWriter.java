package org.guanzon.gnzn.utilities.finance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and writes a Corporate Check ("CC") upload file that follows the
 * PNB "CC File Layout - Standard CC Format" template supplied by the bank
 * (sheet: "PNB CC File Layout").
 *
 * <p>Fields are separated with a pipe ({@code |}), per spec. A text value
 * that itself contains a pipe/quote/line-break is wrapped in double quotes
 * with internal quotes doubled (RFC-4180 style, just with {@code |} as the
 * delimiter instead of {@code ,}). Change {@link #DELIMITER} (and, if the
 * quoting convention ever changes too, {@link #csvField(String)}) if that
 * ever needs to be revisited - the record/field structure below already
 * mirrors the sheet exactly.
 *
 * <p>Record types emitted, in order, per the sheet:
 * <pre>
 *   H  - one HEADER line for the whole file
 *   D  - one DETAIL line per check
 *   C  - one HEADER CWT line, only if that check has CWT
 *   T  - one or more CWT FIELDS lines, only if that check has CWT
 *   I  - zero or more VOUCHER (invoice) lines per check
 * </pre>
 */
public final class PNBCheckWriter {

    /** Column separator, per spec. */
    public static final String DELIMITER = "|";

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private static final String CRLF = "\r\n";

    /** Max field lengths straight from the "Field Length" column of the sheet. */
    private static final class Len {
        static final int ACCOUNT_NUMBER = 16;
        static final int TOTAL_COUNT = 4;
        static final int TOTAL_AMOUNT = 9;

        static final int PAYEE_NAME = 295;
        static final int AMOUNT = 9;
        static final int CLIENT_REFERENCE_NUMBER = 90;
        static final int PARTICULARS = 295;
        static final int REMARKS = 295;
        static final int PICKUP_BRANCH = 4;
        static final int DELIVERY_ADDRESS = 295;
        static final int AUTHORIZED_REPRESENTATIVE = 295;

        static final int CWT_PAYEE_ADDRESS = 100;
        static final int CWT_PAYEE_ZIP = 32;
        static final int CWT_PAYEE_TIN = 14;

        static final int ATC_CODE = 10;
        static final int MONTH_INCOME = 9;

        static final int INVOICE_NUMBER = 50;
        static final int INVOICE_DESCRIPTION = 295;
        static final int INVOICE_AMOUNT_FIELD = 9;
    }

    /**
     * Validates the whole batch against the sheet's mandatory /
     * conditional-mandatory rules and its field-length caps.
     * Throws IllegalArgumentException describing the first problem found.
     */
    public void validate(CheckBatch batch) {
        if (batch.getAccountNumber() == null || batch.getAccountNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Account Number is MANDATORY (Funding Account)");
        }
        requireMaxLength("Account Number", batch.getAccountNumber(), Len.ACCOUNT_NUMBER);

        if (batch.getDetails().isEmpty()) {
            throw new IllegalArgumentException("At least one DETAIL (check) record is required");
        }

        // "Batch Upload should be either All Self service or Outsourced only"
        boolean firstIsSelfService = batch.getDetails().get(0).isSelfService();
        for (CheckDetail d : batch.getDetails()) {
            if (d.isSelfService() != firstIsSelfService) {
                throw new IllegalArgumentException(
                        "Batch Upload should be either All Self Service or All Outsourced only "
                                + "- found a mix of both in this batch");
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CheckDetail d : batch.getDetails()) {
            validateDetail(d);
            total = total.add(d.getAmount());
        }
        requireMaxLength("Total Amount", formatAmount(total), Len.TOTAL_AMOUNT);
        requireMaxLength("Total Count", String.valueOf(batch.getDetails().size()), Len.TOTAL_COUNT);
    }

    private void validateDetail(CheckDetail d) {
        requireMaxLength("Payee Name", d.getPayeeName(), Len.PAYEE_NAME);
        requireMaxLength("Amount", formatAmount(d.getAmount()), Len.AMOUNT);
        requireMaxLength("Client Reference Number", d.getClientReferenceNumber(), Len.CLIENT_REFERENCE_NUMBER);
        requireMaxLength("Particulars", d.getParticulars(), Len.PARTICULARS);
        requireMaxLength("Remarks", d.getRemarks(), Len.REMARKS);

        if (!d.isSelfService()) {
            // Outsourced: Pick-up or Delivery? is MANDATORY.
            if (d.getDeliveryMode() == null) {
                throw new IllegalArgumentException(
                        "Pick-up or Delivery? is MANDATORY when Is Self Service? = Outsourced "
                                + "(payee: " + d.getPayeeName() + ")");
            }
            if (d.getDeliveryMode() == CheckDetail.DeliveryMode.PICK_UP) {
                if (d.getPickupBranch() == null || d.getPickupBranch().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Pick up Branch is MANDATORY when Outsourced and Pick up "
                                    + "(payee: " + d.getPayeeName() + ")");
                }
                requireMaxLength("Pick up Branch", d.getPickupBranch(), Len.PICKUP_BRANCH);
                if (d.getClaimant() == null) {
                    throw new IllegalArgumentException(
                            "Is Claimant Payee? is MANDATORY when Outsourced and Pick up "
                                    + "(payee: " + d.getPayeeName() + ")");
                }
                if (d.getClaimant() == CheckDetail.Claimant.AUTHORIZED_REPRESENTATIVE
                        && isBlank(d.getAuthorizedRepresentative())) {
                    throw new IllegalArgumentException(
                            "Authorized Representative is MANDATORY when the Claimant is not the Payee "
                                    + "(payee: " + d.getPayeeName() + ")");
                }
            } else { // DELIVERY
                if (isBlank(d.getDeliveryAddress())) {
                    throw new IllegalArgumentException(
                            "Delivery Address is MANDATORY when Outsourced and for Delivery "
                                    + "(payee: " + d.getPayeeName() + ")");
                }
                requireMaxLength("Delivery Address", d.getDeliveryAddress(), Len.DELIVERY_ADDRESS);
                if (isBlank(d.getAuthorizedRepresentative())) {
                    throw new IllegalArgumentException(
                            "Authorized Representative is MANDATORY when Outsourced and for Delivery "
                                    + "(payee: " + d.getPayeeName() + ")");
                }
            }
            requireMaxLength("Authorized Representative", d.getAuthorizedRepresentative(),
                    Len.AUTHORIZED_REPRESENTATIVE);
        }

        if (d.getCwtInfo() != null) {
            CwtInfo cwt = d.getCwtInfo();
            requireMaxLength("Payee Address (CWT)", cwt.getPayeeAddress(), Len.CWT_PAYEE_ADDRESS);
            requireMaxLength("Payee ZIP Code (CWT)", cwt.getPayeeZipCode(), Len.CWT_PAYEE_ZIP);
            requireMaxLength("Payee TIN (CWT)", cwt.getPayeeTin(), Len.CWT_PAYEE_TIN);
            if (cwt.getTaxPeriodFrom() == null || cwt.getTaxPeriodTo() == null) {
                throw new IllegalArgumentException(
                        "Tax Period From/To are MANDATORY when the check is with CWT "
                                + "(payee: " + d.getPayeeName() + ")");
            }
            if (cwt.getLines().isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one CWT FIELDS (Alphanumeric Tax Code) line is required "
                                + "when the check is with CWT (payee: " + d.getPayeeName() + ")");
            }
            for (CwtLine line : cwt.getLines()) {
                if (isBlank(line.getAlphanumericTaxCode())) {
                    throw new IllegalArgumentException(
                            "Alphanumeric Tax Code is MANDATORY on every CWT FIELDS line "
                                    + "(payee: " + d.getPayeeName() + ")");
                }
                requireMaxLength("Alphanumeric Tax Code", line.getAlphanumericTaxCode(), Len.ATC_CODE);
                requireMaxLength("First Month of Quarter Income",
                        formatNullableAmount(line.getFirstMonthIncome()), Len.MONTH_INCOME);
                requireMaxLength("Second Month of Quarter Income",
                        formatNullableAmount(line.getSecondMonthIncome()), Len.MONTH_INCOME);
                requireMaxLength("Third Month of Quarter Income",
                        formatNullableAmount(line.getThirdMonthIncome()), Len.MONTH_INCOME);
            }
        }

        for (Invoice inv : d.getInvoices()) {
            requireMaxLength("Invoice Number", inv.getInvoiceNumber(), Len.INVOICE_NUMBER);
            requireMaxLength("Invoice Description", inv.getDescription(), Len.INVOICE_DESCRIPTION);
            requireMaxLength("Invoice Amount", formatNullableAmount(inv.getAmount()), Len.INVOICE_AMOUNT_FIELD);
            requireMaxLength("Invoice WHT Amount", formatNullableAmount(inv.getWhtAmount()), Len.INVOICE_AMOUNT_FIELD);
            requireMaxLength("Invoice VAT Amount", formatNullableAmount(inv.getVatAmount()), Len.INVOICE_AMOUNT_FIELD);
            requireMaxLength("Invoice Net Amount", formatNullableAmount(inv.getNetAmount()), Len.INVOICE_AMOUNT_FIELD);
        }
    }

    /** Builds every line of the file, in the order PNB expects them, without writing anything. */
    public List<String> buildLines(CheckBatch batch) {
        validate(batch);

        List<String> lines = new ArrayList<String>();
        lines.add(buildHeaderLine(batch));
        for (CheckDetail d : batch.getDetails()) {
            lines.add(buildDetailLine(d));
            if (d.getCwtInfo() != null) {
                lines.add(buildCwtHeaderLine(d));
                for (CwtLine line : d.getCwtInfo().getLines()) {
                    lines.add(buildCwtFieldsLine(line));
                }
            }
            for (Invoice inv : d.getInvoices()) {
                lines.add(buildVoucherLine(inv));
            }
        }
        return lines;
    }

    /** Validates, builds, and writes the file to disk (UTF-8, CRLF line endings). */
    public void writeToFile(CheckBatch batch, File destination) throws IOException {
        List<String> lines = buildLines(batch);
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(destination.toPath()), StandardCharsets.UTF_8));
        try {
            for (String line : lines) {
                writer.write(line);
                writer.write(CRLF);
            }
        } finally {
            writer.close();
        }
    }

    // ---------------------------------------------------------------- H

    private String buildHeaderLine(CheckBatch batch) {
        BigDecimal total = BigDecimal.ZERO;
        for (CheckDetail d : batch.getDetails()) {
            total = total.add(d.getAmount());
        }
        List<String> fields = new ArrayList<String>();
        fields.add("H");
        fields.add(String.valueOf(batch.getDetails().size()));   // Total Count
        fields.add(formatAmount(total));                          // Total Amount
        fields.add(batch.getAccountNumber());                     // Account Number
        return join(fields);
    }

    // ---------------------------------------------------------------- D

    private String buildDetailLine(CheckDetail d) {
        List<String> fields = new ArrayList<String>();
        fields.add("D");
        fields.add(d.isSelfService() ? "1" : "0");
        fields.add(formatDate(d.getCheckDate()));
        fields.add(d.getPayeeName());
        fields.add(formatAmount(d.getAmount()));
        fields.add(d.isCrossed() ? "1" : "0");
        fields.add(nullToEmpty(d.getClientReferenceNumber()));
        fields.add(nullToEmpty(d.getParticulars()));
        fields.add(nullToEmpty(d.getRemarks()));

        if (d.isSelfService()) {
            // Outsourced-only fields: left blank for self-service checks.
            fields.add("");
            fields.add("");
            fields.add("");
            fields.add("");
            fields.add("");
        } else {
            fields.add(d.getDeliveryMode() == CheckDetail.DeliveryMode.PICK_UP ? "1" : "0");
            fields.add(d.getDeliveryMode() == CheckDetail.DeliveryMode.PICK_UP
                    ? nullToEmpty(d.getPickupBranch()) : "");
            fields.add(d.getDeliveryMode() == CheckDetail.DeliveryMode.DELIVERY
                    ? nullToEmpty(d.getDeliveryAddress()) : "");
            fields.add(d.getDeliveryMode() == CheckDetail.DeliveryMode.PICK_UP
                    ? (d.getClaimant() == CheckDetail.Claimant.PAYEE ? "1" : "0") : "");
            fields.add(nullToEmpty(d.getAuthorizedRepresentative()));
        }
        return join(fields);
    }

    // ---------------------------------------------------------------- C

    private String buildCwtHeaderLine(CheckDetail d) {
        CwtInfo cwt = d.getCwtInfo();
        List<String> fields = new ArrayList<String>();
        fields.add("C");
        fields.add(d.getPayeeName());
        fields.add(nullToEmpty(cwt.getPayeeAddress()));
        fields.add(nullToEmpty(cwt.getPayeeZipCode()));
        fields.add(nullToEmpty(cwt.getPayeeTin()));
        fields.add(formatDate(cwt.getTaxPeriodFrom()));
        fields.add(formatDate(cwt.getTaxPeriodTo()));
        return join(fields);
    }

    // ---------------------------------------------------------------- T

    private String buildCwtFieldsLine(CwtLine line) {
        List<String> fields = new ArrayList<String>();
        fields.add("T");
        fields.add(line.getAlphanumericTaxCode());
        fields.add(formatNullableAmount(line.getFirstMonthIncome()));
        fields.add(formatNullableAmount(line.getSecondMonthIncome()));
        fields.add(formatNullableAmount(line.getThirdMonthIncome()));
        return join(fields);
    }

    // ---------------------------------------------------------------- I

    private String buildVoucherLine(Invoice inv) {
        List<String> fields = new ArrayList<String>();
        fields.add("I");
        fields.add(nullToEmpty(inv.getInvoiceNumber()));
        fields.add(inv.getInvoiceDate() == null ? "" : formatDate(inv.getInvoiceDate()));
        fields.add(nullToEmpty(inv.getDescription()));
        fields.add(formatNullableAmount(inv.getAmount()));
        fields.add(formatNullableAmount(inv.getWhtAmount()));
        fields.add(formatNullableAmount(inv.getVatAmount()));
        fields.add(formatNullableAmount(inv.getNetAmount()));
        return join(fields);
    }

    // ------------------------------------------------------------ helpers

    private String join(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(DELIMITER);
            }
            sb.append(csvField(fields.get(i)));
        }
        return sb.toString();
    }

    /** Quotes a field RFC-4180 style if it contains the delimiter, a quote, or a line break. */
    private String csvField(String value) {
        String v = nullToEmpty(value);
        if (v.contains(DELIMITER) || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String formatDate(LocalDate date) {
        return date.format(DATE_FORMAT);
    }

    /** Amounts are rendered without forcing decimals/padding, matching the sheet's sample values
     *  (e.g. 9000, 3000, 200.5) rather than always "9000.00". */
    private String formatAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    /** CWT monthly income / invoice amounts: "blank if no amount (do not put zero)". */
    private String formatNullableAmount(BigDecimal amount) {
        return amount == null ? "" : formatAmount(amount);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void requireMaxLength(String fieldName, String value, int maxLength) {
        String v = nullToEmpty(value);
        if (v.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds max length of " + maxLength
                            + " (got " + v.length() + " chars: \"" + v + "\")");
        }
    }
}
