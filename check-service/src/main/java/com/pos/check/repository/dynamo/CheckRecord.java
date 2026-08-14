package com.pos.check.repository.dynamo;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A whole check stored as one DynamoDB item.
 *
 * <p><b>Why one item rather than an item collection.</b> The obvious
 * single-table design gives the check a partition and stores META, ITEM#n and
 * PAYMENT#n as separate rows. That is right when the children are updated
 * independently at high frequency, or when the aggregate could exceed
 * DynamoDB's 400 KB item limit. Neither applies here: a restaurant check with
 * fifty lines and modifiers is roughly 25 KB, and the check is always read and
 * written as a whole - {@code recalculateTotals()} needs every item and payment
 * anyway.
 *
 * <p>Storing it as one item buys real properties. A read is a single
 * {@code GetItem} instead of a {@code Query} plus assembly. A write is a single
 * conditional {@code PutItem}, which is atomic by construction - no
 * {@code TransactWriteItems}, and no possibility of a half-written check where
 * the total no longer matches the lines. That is the exact class of bug that
 * matters when the document is someone's bill.
 *
 * <p>The cost: every write rewrites the whole item, so write capacity scales
 * with check size rather than with the size of the change. At a few kilobytes
 * that is one or two WCUs - cheaper than the multiple writes and the
 * transaction the split design would need.
 *
 * <p>Lombok generates most accessors. The key getters are written by hand
 * because the enhanced client reads its annotations from the getter method, and
 * Lombok cannot place them there.
 */
@Data
@NoArgsConstructor
@DynamoDbBean
public class CheckRecord {

    public static final String GSI_ORG_STATUS = "gsi1-org-status";
    public static final String GSI_ORG_OPENED = "gsi2-org-opened";

    private String pk;
    private Long id;
    private String checkNumber;
    private String status;

    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal tipAmount;

    private Integer guestCount;
    private String tableNumber;
    private Long employeeId;
    private Long terminalId;
    private Long organizationId;
    private Long profitCenterId;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String gsi1pk;
    private String gsi1sk;
    private String gsi2pk;
    private String gsi2sk;

    private Long version;

    private List<ItemRecord> items = new ArrayList<>();
    private List<PaymentRecord> payments = new ArrayList<>();

    /** {@code CHECK#<id>}. */
    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    /** {@code ORG#<organizationId>#STATUS#<status>} - drives the open-checks query. */
    @DynamoDbSecondaryPartitionKey(indexNames = GSI_ORG_STATUS)
    public String getGsi1pk() {
        return gsi1pk;
    }

    @DynamoDbSecondarySortKey(indexNames = GSI_ORG_STATUS)
    public String getGsi1sk() {
        return gsi1sk;
    }

    /** {@code ORG#<organizationId>} - all checks for a tenant, newest first. */
    @DynamoDbSecondaryPartitionKey(indexNames = GSI_ORG_OPENED)
    public String getGsi2pk() {
        return gsi2pk;
    }

    @DynamoDbSecondarySortKey(indexNames = GSI_ORG_OPENED)
    public String getGsi2sk() {
        return gsi2sk;
    }

    /**
     * Optimistic locking. The enhanced client adds a condition that the stored
     * version matches, and fails the write otherwise.
     *
     * <p>This is what makes concurrent edits safe. Two servers adding items to
     * the same table's check would otherwise last-write-wins, and one line
     * silently vanishes from the bill.
     */
    @DynamoDbVersionAttribute
    public Long getVersion() {
        return version;
    }

    @Data
    @NoArgsConstructor
    @DynamoDbBean
    public static class ItemRecord {
        private Long id;
        private Long menuItemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String specialInstructions;
        private String status;
        private Boolean voided;
        private String voidReason;
        private List<ModifierRecord> modifiers = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @DynamoDbBean
    public static class ModifierRecord {
        private Long id;
        private Long modifierId;
        private String modifierName;
        private BigDecimal priceAdjustment;
    }

    @Data
    @NoArgsConstructor
    @DynamoDbBean
    public static class PaymentRecord {
        private Long id;
        private Long tenderId;
        private String tenderName;
        private String paymentType;
        private BigDecimal amount;
        private BigDecimal tipAmount;
        private String referenceNumber;
        private String authorizationCode;
        private String status;
        private LocalDateTime processedAt;
        private Boolean voided;
        private String voidReason;
    }
}
