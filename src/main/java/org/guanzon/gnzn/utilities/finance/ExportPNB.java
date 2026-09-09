package org.guanzon.gnzn.utilities.finance;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

/**
 * Demonstrates {@link PnbCcFileWriter} with a Philippine-setting sample
 * data set: PSA/typical Filipino payee names, Metro Manila / Cebu
 * addresses, Philippine ZIP codes, a 12-digit TIN, a BIR Alphanumeric
 * Tax Code, and a PNB-style funding account number.
 *
 * Per the sheet's own note ("Batch Upload should be either All Self
 * service or Outsourced only"), this produces TWO separate output files:
 * one all-self-service batch and one all-outsourced batch, so both
 * conditional branches (pick-up vs delivery, with/without CWT, with/
 * without invoice) are shown without mixing the two product types in a
 * single file.
 *
 * Run with:
 *   javac -d out $(find src -name "*.java")
 *   java -cp out com.rmjm.checkexport.demo.Main
 */
public final class ExportPNB {

    private static final String FUNDING_ACCOUNT_NUMBER = "141234567890"; // sample PNB account

    public static void main(String[] args) throws IOException {
        PNBCheckWriter writer = new PNBCheckWriter();

        CheckBatch selfServiceBatch = buildSelfServiceBatch();
        File selfServiceFile = new File("d:/CC_SELFSERVICE_20260825.txt");
        writer.writeToFile(selfServiceBatch, selfServiceFile);
        System.out.println("Wrote " + selfServiceFile.getPath());
        for (String line : writer.buildLines(selfServiceBatch)) {
            System.out.println("  " + line);
        }

        CheckBatch outsourcedBatch = buildOutsourcedBatch();
        File outsourcedFile = new File("d:/CC_OUTSOURCED_20260825.txt");
        writer.writeToFile(outsourcedBatch, outsourcedFile);
        System.out.println("Wrote " + outsourcedFile.getPath());
        for (String line : writer.buildLines(outsourcedBatch)) {
            System.out.println("  " + line);
        }
    }

    private static CheckBatch buildSelfServiceBatch() {
        CheckBatch batch = new CheckBatch(FUNDING_ACCOUNT_NUMBER);

        // 1) Plain self-service check, no CWT, no invoice.
        batch.addDetail(CheckDetail.builder()
                .selfService(true)
                .checkDate(LocalDate.of(2026, 8, 26))
                .payeeName("Maria Clara Santos")
                .amount(new BigDecimal("15000"))
                .crossed(true)
                .clientReferenceNumber("PO-2026-00981")
                .particulars("Office supplies - August")
                .remarks("Replenishment check")
                .build());

        // 2) Self-service check settling a supplier invoice (no CWT).
        batch.addDetail(CheckDetail.builder()
                .selfService(true)
                .checkDate(LocalDate.of(2026, 8, 27))
                .payeeName("Robinsons Supermart Corp.")
                .amount(new BigDecimal("48250.75"))
                .crossed(true)
                .clientReferenceNumber("AP-2026-04471")
                .particulars("Pantry supplies - Head Office")
                .addInvoice(new Invoice(
                        "SI-88291",
                        LocalDate.of(2026, 8, 15),
                        "Pantry and janitorial supplies",
                        new BigDecimal("48250.75"),
                        BigDecimal.ZERO,
                        new BigDecimal("5169.73"),
                        new BigDecimal("48250.75")))
                .build());

        // 3) Self-service check to an individual professional, with CWT withheld.
        LocalDate profFeeDate = LocalDate.of(2026, 8, 28);
        batch.addDetail(CheckDetail.builder()
                .selfService(true)
                .checkDate(profFeeDate)
                .payeeName("Juan Dela Cruz")
                .amount(new BigDecimal("45000"))
                .crossed(true)
                .clientReferenceNumber("PF-2026-00213")
                .particulars("Professional fee - Structural consultancy, August 2026")
                .cwtInfo(new CwtInfo(
                        "Unit 12B, 123 Tondo, Manila",
                        "1012",
                        "123456789012",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 9, 30),
                        Collections.singletonList(new CwtLine(
                                "WC160",
                                null,           // July - no payment
                                null,           // August - no payment
                                new BigDecimal("4500"))))) // September ATC WC160, 10% of 45,000
                .addInvoice(new Invoice(
                        "OR-2026-0055",
                        profFeeDate,
                        "Structural consultancy services - August 2026",
                        new BigDecimal("45000"),
                        new BigDecimal("4500"),
                        BigDecimal.ZERO,
                        new BigDecimal("40500")))
                .build());

        return batch;
    }

    private static CheckBatch buildOutsourcedBatch() {
        CheckBatch batch = new CheckBatch(FUNDING_ACCOUNT_NUMBER);

        // 1) Outsourced, for pick-up, claimant is the payee.
        batch.addDetail(CheckDetail.builder()
                .outsourced()
                .checkDate(LocalDate.of(2026, 8, 26))
                .payeeName("Andres Bonifacio Trading")
                .amount(new BigDecimal("22750"))
                .crossed(true)
                .clientReferenceNumber("PO-2026-01123")
                .particulars("Construction materials - Site 3")
                .deliveryMode(CheckDetail.DeliveryMode.PICK_UP)
                .pickupBranch("1236") // sample PNB branch code, e.g. Ermita
                .claimant(CheckDetail.Claimant.PAYEE)
                .build());

        // 2) Outsourced, for delivery, claimed by an authorized representative.
        batch.addDetail(CheckDetail.builder()
                .outsourced()
                .checkDate(LocalDate.of(2026, 8, 27))
                .payeeName("Emilio Aguinaldo Realty Corp.")
                .amount(new BigDecimal("135000"))
                .crossed(true)
                .clientReferenceNumber("LEASE-2026-Q3")
                .particulars("Q3 2026 office lease - RMJM Manila")
                .remarks("Deliver to receiving office only")
                .deliveryMode(CheckDetail.DeliveryMode.DELIVERY)
                .deliveryAddress("8F Rufino Pacific Tower, 6784 Ayala Ave, Makati City, 1226 Metro Manila")
                .authorizedRepresentative("Ana Reyes-Torres (Authorized Liaison)")
                .build());

        return batch;
    }
}
