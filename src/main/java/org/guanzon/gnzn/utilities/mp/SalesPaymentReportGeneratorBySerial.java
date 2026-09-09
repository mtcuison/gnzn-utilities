package org.guanzon.gnzn.utilities.mp;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.guanzon.appdriver.base.GRider;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;

/**
 * Duplicate of SalesPaymentReportGenerator that adds a Serial ID column right after Model.
 * Serial ID is normally unique per physical unit sold, so it is part of the grouping key here --
 * each output row is one Date + Store + Brand + Model + Serial ID combination, i.e. effectively
 * one row per unit sold, with Cash/Credit Card as 1/0 rather than an aggregate count.
 */
public class SalesPaymentReportGeneratorBySerial {
    private static String getSQ_Sales(String dateFrom, String dateThru) {
        String lsSQL = "SELECT" +
                            " a.sTransNox `Transaction Number`," +
                            " h.sAreaDesc `Area`," +
                            " c.sBranchNm `Branch`," +
                            " a.dTransact `Date`," +
                            " d.nSelPrice `Unit SRP`," +
                            " (((b.nUnitPrce) * ((100 - (b.nDiscRate)) / 100) * (b.nQuantity)) - (b.nDiscAmtx)) `Unit Cost`," +
                            " e.sBrandNme `Brand`," +
                            " f.sModelNme `Model Name`," +
                            " i.sCategrNm `Category 1`," +
                            " j.sCategrNm `Category 2`," +
                            " c.sBranchCd `Branch Code`," +
                            " b.sSerialID `Serial ID`" +
                        " FROM CP_SO_Master a" +
                            " LEFT JOIN CP_SO_Detail b" +
                                " ON a.sTransNox = b.sTransNox" +
                            " LEFT JOIN CP_Inventory d" +
                                " ON b.sStockIDx = d.sStockIDx" +
                            " LEFT JOIN CP_Brand e" +
                                " ON d.sBrandIDx = e.sBrandIDx" +
                            " LEFT JOIN CP_Model f" +
                                " ON d.sModelIDx = f.sModelIDx" +
                            " LEFT JOIN Branch c" +
                                " ON LEFT(a.sTransNox, 4) = c.sBranchCd" +
                            " LEFT JOIN Branch_Others g" +
                                " ON c.sBranchCd = g.sBranchCd" +
                            " LEFT JOIN Branch_Area h" +
                                " ON g.sAreaCode = h.sAreaCode" +
                            " LEFT JOIN Category i" +
                                " ON d.sCategID1 = i.sCategrID" +
                            " LEFT JOIN Category j" +
                                " ON d.sCategID2 = j.sCategrID" +
                        " WHERE a.cTranStat <> '3'" +
                            " AND d.sCategID1 = 'C001001'" +
                            " AND d.sCategID2 = 'C001019'";

        return MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + SQLUtil.toSQL(dateFrom) + " AND " + SQLUtil.toSQL(dateThru));
    }

    public static String getSQ_Payment(String transNo){
        String lsSQL = "SELECT" +
                            " sTransNox `Transaction Number`," +
                            " nTranTotl `Transaction Total`," +
                            " nCashAmtx `Cash Amount`," +
                            " 0 AS `Epayment`," +
                            " 0 AS `Other Finance`," +
                            " 0 AS `Northpoint`," +
                            " 0 AS `Credit Card Amount`," +
                            " 'Cash' AS `Payment Type`," +
                            " 'N/A' AS `Downpayment`," +
                            " '0' AS `Term`" +
                        " FROM CP_SO_Master" +
                        " WHERE sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND cTranStat <> 3" +
                            " AND (nCashAmtx = nTranTotl OR nCashAmtx > nTranTotl)" +
                        " UNION" +
                        " SELECT" +
                            " a.sTransNox," +
                            " a.nTranTotl," +
                            " a.nCashAmtx," +
                            " SUM(b.nAmtPaidx)," +
                            " 0," +
                            " 0," +
                            " 0," +
                            " 'EPayment'," +
                            " 'N/A'," +
                            " CASE" +
                                " WHEN c.sTermIDxx IS NULL" +
                                " THEN '0'" +
                                " WHEN b.sTermCode IS NOT NULL" +
                                " THEN c.sTermName" +
                            " END" +
                        " FROM CP_SO_Master a" +
                            " LEFT JOIN Other_Payment_Received b" +
                                " ON a.sTransNox = b.sSourceNo AND b.sSourceCd = 'CPSl'" +
                            " LEFT JOIN Term c" +
                                " ON b.sTermCode = c.sTermIDxx" +
                        " WHERE a.sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND a.cTranStat <> 3" +
                            " AND b.sTransNox IS NOT NULL" +
                        " GROUP BY b.sSourceNo" +
                        " UNION" +
                        " SELECT" +
                            " a.sTransNox," +
                            " a.nTranTotl," +
                            " a.nCashAmtx," +
                            " 0," +
                            " 0," +
                            " 0," +
                            " 0," +
                            " 'Charge Invoice'," +
                            " 'N/A'," +
                            " CASE" +
                                " WHEN d.nAcctTerm IS NULL" +
                                " THEN '0'" +
                                " WHEN d.nAcctTerm IS NOT NULL" +
                                " THEN d.nAcctTerm" +
                            " END" +
                        " FROM CP_SO_Master a" +
                            " LEFT JOIN CP_CO_Master b" +
                                " ON a.sTransNox = b.sTransNox" +
                            " LEFT JOIN CP_SO_Detail c" +
                                " ON a.sTransNox = c.sTransNox" +
                                    " AND c.nEntryNox = '1'" +
                            " LEFT JOIN MC_AR_Master d" +
                                " ON c.sSerialID = d.sSerialID" +
                        " WHERE a.sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND a.cTranStat <> 3" +
                            " AND b.sTransNox IS NOT NULL" +
                        " UNION" +
                        " SELECT" +
                            " a.sTransNox," +
                            " a.nTranTotl," +
                            " a.nCashAmtx," +
                            " 0," +
                            " b.nFinAmtxx," +
                            " 0," +
                            " 0," +
                            " 'Other Finance'," +
                            " b.nAmtPaidx," +
                            " 'N/A'" +
                        " FROM CP_SO_Master a" +
                            " LEFT JOIN CP_SO_Finance b" +
                            " ON a.sTransNox = b.sTransNox" +
                        " WHERE a.sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND a.cTranStat <> 3" +
                            " AND b.sTransNox IS NOT NULL" +
                        " UNION" +
                        " SELECT" +
                            " a.sTransNox," +
                            " a.nTranTotl," +
                            " a.nCashAmtx," +
                            " 0," +
                            " 0," +
                            " b.nFinAmtxx," +
                            " 0," +
                            " 'Northpoint'," +
                            " b.nAmtPaidx," +
                            " d.nAcctTerm" +
                        " FROM CP_SO_Master a" +
                            " LEFT JOIN CP_SO_Finance b" +
                                " ON a.sTransNox = b.sTransNox" +
                            " LEFT JOIN CP_SO_Detail c" +
                                " ON a.sTransNox = c.sTransNox" +
                                " AND c.nEntryNox = '1'" +
                            " LEFT JOIN MC_AR_Master d" +
                                " ON c.sSerialID = d.sSerialID" +
                                " AND a.dTransact = d.dPurchase" +
                        " WHERE a.sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND a.cTranStat <> 3" +
                            " AND b.sTransNox IS NOT NULL" +
                        " UNION" +
                        " SELECT" +
                            " a.sTransNox," +
                            " a.nTranTotl," +
                            " a.nCashAmtx," +
                            " 0," +
                            " 0," +
                            " 0," +
                            " c.nTranTotl," +
                            " 'Credit Card'," +
                            " 'N/A'," +
                            " d.sTermName" +
                        " FROM CP_SO_Master a" +
                            " LEFT JOIN MP_SO_Credit_Card b" +
                                " ON a.sTransNox = b.sTransNox" +
                            " LEFT JOIN MP_Credit_Card_Transaction c" +
                                " ON b.sReferNox = c.sTransNox" +
                            " LEFT JOIN Term d" +
                                " ON c.sTermIDxx = d.sTermIDxx" +
                        " WHERE a.sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND a.cTranStat <> 3" +
                            " AND b.sTransNox IS NOT NULL" +
                        " GROUP BY b.sTransNox" +
                        " UNION" +
                        " SELECT" +
                            " sTransNox," +
                            " nTranTotl," +
                            " nCashAmtx," +
                            " 0," +
                            " 0," +
                            " 0," +
                            " 0," +
                            " 'Replacement'," +
                            " 'N/A'," +
                            " '0'" +
                        " FROM CP_SO_Master" +
                        " WHERE sTransNox = " + SQLUtil.toSQL(transNo) +
                            " AND cTranStat <> 3" +
                            " AND nReplAmtx <> 0";

        return lsSQL;
    }

    // ------------------------------------------------------------------
    // CSV generation
    // ------------------------------------------------------------------

    /** One row from the sales query (one sale-detail line). */
    private static class SalesLine {
        String transactionNumber;
        String branch;
        String branchCode;
        LocalDate date;
        String brand;
        String model;
        String serialId;
    }

    /** Payment totals for one transaction (already resolved -- no double counting). */
    private static class PaymentTotals {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal creditCard = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO; // Epayment + Other Finance + Northpoint
        boolean cashSet = false;
    }

    /** Which of the two report buckets a transaction's payment falls into. */
    private enum PaymentCategory { CASH, CREDIT_CARD }

    /** Grouping key: Date + Branch + Brand + Model + Serial ID. Branch Code rides along with
     *  Branch since they're a 1:1 pair. Serial ID IS part of the grouping key -- it's normally
     *  unique per physical unit sold, so each row here is effectively one unit. */
    private static class GroupKey implements Comparable<GroupKey> {
        final LocalDate date;
        final String branch;
        final String branchCode;
        final String brand;
        final String model;
        final String serialId;

        GroupKey(LocalDate date, String branch, String branchCode, String brand, String model, String serialId) {
            this.date = date;
            this.branch = branch == null ? "" : branch;
            this.branchCode = branchCode == null ? "" : branchCode;
            this.brand = brand == null ? "" : brand;
            this.model = model == null ? "" : model;
            this.serialId = serialId == null ? "" : serialId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GroupKey)) return false;
            GroupKey k = (GroupKey) o;
            return date.equals(k.date) && branch.equals(k.branch) && branchCode.equals(k.branchCode)
                && brand.equals(k.brand) && model.equals(k.model) && serialId.equals(k.serialId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(date, branch, branchCode, brand, model, serialId);
        }

        @Override
        public int compareTo(GroupKey o) {
            int c = date.compareTo(o.date);
            if (c != 0) return c;
            c = branch.compareTo(o.branch);
            if (c != 0) return c;
            c = brand.compareTo(o.brand);
            if (c != 0) return c;
            c = model.compareTo(o.model);
            if (c != 0) return c;
            return serialId.compareTo(o.serialId);
        }
    }

    /** Number of DISTINCT transactions in this group falling into each payment bucket. */
    private static class GroupTotals {
        int cashCount = 0;
        int creditCardCount = 0;
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static List<SalesLine> loadSalesLines(ResultSet rs) throws SQLException {
        List<SalesLine> lines = new ArrayList<>();
        while (rs.next()) {
            SalesLine line = new SalesLine();
            line.transactionNumber = rs.getString("Transaction Number");
            line.branch = rs.getString("Branch");
            line.branchCode = rs.getString("Branch Code");
            line.date = rs.getDate("Date").toLocalDate();
            line.brand = rs.getString("Brand");
            line.model = rs.getString("Model Name");
            line.serialId = rs.getString("Serial ID");
            lines.add(line);
        }
        return lines;
    }

    /**
     * Reads every row returned for ONE transaction's payment query into a single PaymentTotals.
     * A transaction can legitimately produce more than one row here (e.g. part cash + part credit
     * card matches two UNION branches). Cash Amount is re-selected from the master row on every
     * branch, so it's the SAME value on every row for this transaction -- take it once, do not
     * sum. Credit Card Amount / Epayment / Other Finance / Northpoint are each 0 on every branch
     * except the one that applies, so summing them is safe.
     */
    private static PaymentTotals loadPaymentTotalsForTransaction(ResultSet rs) throws SQLException {
        PaymentTotals totals = new PaymentTotals();
        while (rs.next()) {
            if (!totals.cashSet) {
                BigDecimal cash = rs.getBigDecimal("Cash Amount");
                totals.cash = cash == null ? BigDecimal.ZERO : cash;
                totals.cashSet = true;
            }

            BigDecimal creditCard = rs.getBigDecimal("Credit Card Amount");
            if (creditCard != null) {
                totals.creditCard = totals.creditCard.add(creditCard);
            }

            BigDecimal epayment = rs.getBigDecimal("Epayment");
            BigDecimal otherFinance = rs.getBigDecimal("Other Finance");
            BigDecimal northpoint = rs.getBigDecimal("Northpoint");
            if (epayment != null) totals.other = totals.other.add(epayment);
            if (otherFinance != null) totals.other = totals.other.add(otherFinance);
            if (northpoint != null) totals.other = totals.other.add(northpoint);
        }
        return totals;
    }

    /**
     * Classifies a transaction's payment into exactly one bucket, or disregards it entirely:
     *   - Credit Card: any credit card amount at all -- pure credit card, or credit card combined
     *     with cash and/or other payment methods. Credit card always wins.
     *   - Cash: the payment was made ENTIRELY in cash -- no credit card and no "other" amount
     *     (Epayment/Other Finance/Northpoint) at all.
     *   - Disregarded (returns null): everything else -- e.g. cash combined with an "other"
     *     method, or "other" methods alone with no cash or credit card. These are simply not
     *     counted in either column.
     */
    private static PaymentCategory classify(PaymentTotals totals) {
        if (totals.creditCard.compareTo(BigDecimal.ZERO) > 0) {
            return PaymentCategory.CREDIT_CARD;
        }
        if (totals.cash.compareTo(BigDecimal.ZERO) > 0 && totals.other.compareTo(BigDecimal.ZERO) == 0) {
            return PaymentCategory.CASH;
        }
        return null;
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void writeCsv(TreeMap<GroupKey, GroupTotals> groups, String outputCsvPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputCsvPath))) {
            writer.write("Date,Store Code,Store,Brand,Model,Serial ID,Cash,Credit Card");
            writer.newLine();

            for (Map.Entry<GroupKey, GroupTotals> entry : groups.entrySet()) {
                GroupKey key = entry.getKey();
                GroupTotals totals = entry.getValue();

                if (totals.cashCount == 0 && totals.creditCardCount == 0) {
                    continue; // nothing classified as Cash or Credit Card for this group -- skip the line
                }

                writer.write(String.join(",",
                    key.date.format(DATE_FORMAT),
                    csvEscape(key.branchCode),
                    csvEscape(key.branch),
                    csvEscape(key.brand),
                    csvEscape(key.model),
                    csvEscape(key.serialId),
                    String.valueOf(totals.cashCount),
                    String.valueOf(totals.creditCardCount)
                ));
                writer.newLine();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String path;
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            path = "D:/GGC_Maven_Systems";
        }
        else{
            path = "/srv/GGC_Maven_Systems";
        }
        System.setProperty("sys.default.path.config", path);

        GRider instance = null;

        try {
            Properties props = new Properties();
            props.load(new FileInputStream(path + "/config/cas.properties"));

            if (props.getProperty("developer.mode").equals("1")){
                instance = new GRider("gRider");

                if (!instance.logUser("gRider", "M001000001")){
                    System.err.println(instance.getErrMsg());
                    System.exit(1);
                }
            } else {
                System.err.println("Unable to log user.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.exit(1);
        }

        String startDate = args.length > 0 ? args[0] : "2026-09-01"; // yyyy-MM-dd
        String endDate = args.length > 1 ? args[1] : "2026-09-07";   // yyyy-MM-dd
        String outputCsvPath = args.length > 2 ? args[2] : "d:/sales_payment_report_20260908.csv";

        // 1) Pull every qualifying sale-detail line for the period.
        ResultSet salesRs = instance.executeQuery(getSQ_Sales(startDate, endDate));
        List<SalesLine> salesLines = loadSalesLines(salesRs);
        salesRs.close();

        // 2) Distinct transaction numbers seen in the sales results (sTransNox is still the
        //    basis for the payment lookup, once per transaction, not once per sale line).
        Set<String> transactionNumbers = new LinkedHashSet<>();
        for (SalesLine line : salesLines) {
            transactionNumbers.add(line.transactionNumber);
        }

        Map<String, PaymentTotals> paymentByTransaction = new HashMap<>();
        for (String transactionNumber : transactionNumbers) {
            System.out.println("Generating payment for " + transactionNumber);
            ResultSet paymentRs = instance.executeQuery(getSQ_Payment(transactionNumber));
            try {
                paymentByTransaction.put(transactionNumber, loadPaymentTotalsForTransaction(paymentRs));
            } finally {
                paymentRs.close();
            }
        }

        // 3) Classify each transaction into exactly one bucket up front.
        Map<String, PaymentCategory> categoryByTransaction = new HashMap<>();
        for (Map.Entry<String, PaymentTotals> entry : paymentByTransaction.entrySet()) {
            PaymentCategory category = classify(entry.getValue());
            if (category != null) {
                categoryByTransaction.put(entry.getKey(), category);
            }
        }

        // 4) Collect the DISTINCT transaction numbers that fall under each Date + Branch + Brand +
        //    Model + Serial ID group (Serial ID is normally unique per unit, so this is effectively
        //    per-unit; it still dedupes a transaction that somehow has two lines for the same
        //    serial, and still handles a transaction spanning multiple different serials/models by
        //    counting it once under each).
        TreeMap<GroupKey, Set<String>> transactionsByGroup = new TreeMap<>();
        for (SalesLine line : salesLines) {
            System.out.println("Generating totals for " + line.transactionNumber);
            GroupKey key = new GroupKey(line.date, line.branch, line.branchCode, line.brand, line.model, line.serialId);
            transactionsByGroup.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(line.transactionNumber);
        }

        // 5) Tally how many distinct transactions in each group fall into each bucket.
        TreeMap<GroupKey, GroupTotals> groups = new TreeMap<>();
        for (Map.Entry<GroupKey, Set<String>> entry : transactionsByGroup.entrySet()) {
            GroupTotals totals = new GroupTotals();
            for (String transactionNumber : entry.getValue()) {
                PaymentCategory category = categoryByTransaction.get(transactionNumber);
                if (category == null) {
                    continue; // no payment record / nothing to classify -- not counted
                }
                switch (category) {
                    case CASH: totals.cashCount++; break;
                    case CREDIT_CARD: totals.creditCardCount++; break;
                }
            }
            groups.put(entry.getKey(), totals);
        }

        // 6) Write the CSV.
        writeCsv(groups, outputCsvPath);
        System.out.println("Report written to " + outputCsvPath);
    }
}