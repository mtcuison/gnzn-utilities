package org.guanzon.gnzn.utilities.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DETAIL FIELDS block ("D" record) from the CC File Layout - one physical
 * check being issued.
 *
 * Spec reference (row 12-25):
 *   Constant                  : "D"                       (Record Type)
 *   Is Self Service?          : 0-Outsourced ; 1-Self Service
 *   Check Date                : MM/DD/YYYY
 *   Payee Name                : <=295 chars
 *   Amount                    : <=9 chars
 *   Is Payee Account Only?    : 0-Not Crossed ; 1-Crossed
 *   Client Reference Number   : <=90 chars, not required
 *   Particulars               : <=295 chars, not required
 *   Remarks                   : <=295 chars, not required
 *   Pick-up or Delivery?      : 0-Delivery ; 1-Pick up      (mandatory if Outsourced)
 *   Pick up Branch            : <=4 chars                   (mandatory if Outsourced AND Pick up)
 *   Delivery Address          : <=295 chars                 (mandatory if Outsourced AND Delivery)
 *   Is Claimant Payee?        : 0-Authorized Rep ; 1-Payee  (mandatory if Outsourced AND Pick up)
 *   Authorized Representative : <=295 chars                 (mandatory if Outsourced AND
 *                                                             (Delivery OR Claimant is Auth Rep))
 *
 * Optionally carries one HEADER CWT ("C"+"T"...) block and/or one or more
 * VOUCHER ("I") invoice lines, per the sheet's CWT FIELDS / VOUCHER FIELDS
 * sections, which only apply "if with CWT" / "if with Invoice".
 *
 * Built via {@link Builder} so that the outsourced pick-up/delivery
 * conditional-mandatory rules can be validated in one place
 * (see {@link com.rmjm.checkexport.io.PnbCcFileWriter#validate}).
 */
public final class CheckDetail {

    /** 0 - Delivery ; 1 - Pick up (only meaningful when outsourced). */
    public enum DeliveryMode { PICK_UP, DELIVERY }

    /** 0 - Authorized Rep ; 1 - Payee (only meaningful when outsourced + pick up). */
    public enum Claimant { PAYEE, AUTHORIZED_REPRESENTATIVE }

    private final boolean selfService;
    private final LocalDate checkDate;
    private final String payeeName;
    private final BigDecimal amount;
    private final boolean crossed;
    private final String clientReferenceNumber;
    private final String particulars;
    private final String remarks;
    private final DeliveryMode deliveryMode;
    private final String pickupBranch;
    private final String deliveryAddress;
    private final Claimant claimant;
    private final String authorizedRepresentative;
    private final CwtInfo cwtInfo;
    private final List<Invoice> invoices;

    private CheckDetail(Builder b) {
        this.selfService = b.selfService;
        this.checkDate = b.checkDate;
        this.payeeName = b.payeeName;
        this.amount = b.amount;
        this.crossed = b.crossed;
        this.clientReferenceNumber = b.clientReferenceNumber;
        this.particulars = b.particulars;
        this.remarks = b.remarks;
        this.deliveryMode = b.deliveryMode;
        this.pickupBranch = b.pickupBranch;
        this.deliveryAddress = b.deliveryAddress;
        this.claimant = b.claimant;
        this.authorizedRepresentative = b.authorizedRepresentative;
        this.cwtInfo = b.cwtInfo;
        this.invoices = Collections.unmodifiableList(new ArrayList<Invoice>(b.invoices));
    }

    public boolean isSelfService() {
        return selfService;
    }

    public LocalDate getCheckDate() {
        return checkDate;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isCrossed() {
        return crossed;
    }

    public String getClientReferenceNumber() {
        return clientReferenceNumber;
    }

    public String getParticulars() {
        return particulars;
    }

    public String getRemarks() {
        return remarks;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public String getPickupBranch() {
        return pickupBranch;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public Claimant getClaimant() {
        return claimant;
    }

    public String getAuthorizedRepresentative() {
        return authorizedRepresentative;
    }

    public CwtInfo getCwtInfo() {
        return cwtInfo;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean selfService = true;
        private LocalDate checkDate;
        private String payeeName;
        private BigDecimal amount;
        private boolean crossed = true;
        private String clientReferenceNumber = "";
        private String particulars = "";
        private String remarks = "";
        private DeliveryMode deliveryMode;
        private String pickupBranch;
        private String deliveryAddress;
        private Claimant claimant;
        private String authorizedRepresentative;
        private CwtInfo cwtInfo;
        private List<Invoice> invoices = new ArrayList<Invoice>();

        private Builder() {
        }

        public Builder selfService(boolean selfService) {
            this.selfService = selfService;
            return this;
        }

        public Builder outsourced() {
            this.selfService = false;
            return this;
        }

        public Builder checkDate(LocalDate checkDate) {
            this.checkDate = checkDate;
            return this;
        }

        public Builder payeeName(String payeeName) {
            this.payeeName = payeeName;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder amount(String amount) {
            this.amount = new BigDecimal(amount);
            return this;
        }

        public Builder crossed(boolean crossed) {
            this.crossed = crossed;
            return this;
        }

        public Builder clientReferenceNumber(String clientReferenceNumber) {
            this.clientReferenceNumber = clientReferenceNumber;
            return this;
        }

        public Builder particulars(String particulars) {
            this.particulars = particulars;
            return this;
        }

        public Builder remarks(String remarks) {
            this.remarks = remarks;
            return this;
        }

        public Builder deliveryMode(DeliveryMode deliveryMode) {
            this.deliveryMode = deliveryMode;
            return this;
        }

        public Builder pickupBranch(String pickupBranch) {
            this.pickupBranch = pickupBranch;
            return this;
        }

        public Builder deliveryAddress(String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        public Builder claimant(Claimant claimant) {
            this.claimant = claimant;
            return this;
        }

        public Builder authorizedRepresentative(String authorizedRepresentative) {
            this.authorizedRepresentative = authorizedRepresentative;
            return this;
        }

        public Builder cwtInfo(CwtInfo cwtInfo) {
            this.cwtInfo = cwtInfo;
            return this;
        }

        public Builder addInvoice(Invoice invoice) {
            this.invoices.add(invoice);
            return this;
        }

        public Builder invoices(List<Invoice> invoices) {
            this.invoices = new ArrayList<Invoice>(invoices);
            return this;
        }

        public CheckDetail build() {
            if (payeeName == null || payeeName.trim().isEmpty()) {
                throw new IllegalStateException("Payee Name is MANDATORY");
            }
            if (checkDate == null) {
                throw new IllegalStateException("Check Date is MANDATORY");
            }
            if (amount == null) {
                throw new IllegalStateException("Amount is MANDATORY");
            }
            return new CheckDetail(this);
        }
    }
}
