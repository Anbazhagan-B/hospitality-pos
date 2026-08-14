package com.pos.check.repository.dynamo;

import com.pos.check.entity.Check;
import com.pos.check.entity.CheckItem;
import com.pos.check.entity.CheckItemModifier;
import com.pos.check.entity.CheckPayment;
import com.pos.check.repository.CheckStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of {@link CheckStore}.
 *
 * <p>Access patterns and how each is served:
 *
 * <table>
 *   <tr><td>by id</td>             <td>GetItem on {@code CHECK#<id>}</td></tr>
 *   <tr><td>by check number</td>   <td>GetItem - the number encodes the id</td></tr>
 *   <tr><td>open checks for org</td><td>Query gsi1-org-status</td></tr>
 *   <tr><td>all checks for org</td> <td>Query gsi2-org-opened, newest first</td></tr>
 * </table>
 *
 * <p>Every one is a GetItem or a Query against a partition. There is no Scan
 * anywhere, which is the property that keeps cost and latency flat as the table
 * grows - a Scan reads every item in the table and is the usual reason a
 * DynamoDB bill or p99 goes wrong.
 *
 * <p>Note what is <em>not</em> here. The JPA repository exposed finders by
 * employee, by terminal, by status across all tenants and by arbitrary date
 * range, none of which {@code CheckService} calls. In Postgres an unused finder
 * costs nothing; in DynamoDB each one is an index with its own storage and
 * write cost, so they are omitted until something needs them.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pos.check.store", havingValue = "dynamodb")
public class DynamoDbCheckStore implements CheckStore {

    private static final String KEY_PREFIX = "CHECK#";
    private static final String CHECK_NUMBER_PREFIX = "CHK-";
    private static final String SEQUENCE_PK = "SEQ#CHECK";

    private final DynamoDbTable<CheckRecord> table;
    private final DynamoDbClient lowLevelClient;
    private final String tableName;

    /**
     * Client-side id generator for lines and payments nested inside a check.
     * They are only ever unique within their parent item, so a per-JVM counter
     * seeded from the current time is sufficient and avoids a round-trip per
     * line added.
     */
    private final AtomicLong childIdSequence = new AtomicLong(System.currentTimeMillis());

    public DynamoDbCheckStore(DynamoDbTable<CheckRecord> table,
                              DynamoDbClient lowLevelClient,
                              @Value("${pos.dynamodb.table-name:pos-checks}") String tableName) {
        this.table = table;
        this.lowLevelClient = lowLevelClient;
        this.tableName = tableName;
    }

    // -----------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------

    @Override
    public Check save(Check check) {
        if (check.getId() == null) {
            // One sequence read yields both the id and the check number, so the
            // two can never disagree. findByCheckNumber decodes the id back out
            // of the number, and that only works if they were allocated
            // together.
            long id = nextSequence();
            check.setId(id);
            check.setCheckNumber(CHECK_NUMBER_PREFIX + id);
        }
        LocalDateTime now = LocalDateTime.now();
        if (check.getCreatedAt() == null) {
            check.setCreatedAt(now);
        }
        check.setUpdatedAt(now);

        assignChildIds(check);

        // putItem with a @DynamoDbVersionAttribute present becomes a conditional
        // write: it succeeds only if the stored version still matches the one
        // that was read. A concurrent modification fails with
        // ConditionalCheckFailedException rather than silently overwriting.
        CheckRecord record = toRecord(check);
        table.putItem(record);

        // The extension incremented the version on the record; reflect it back
        // so the caller holds a saveable object rather than a stale one.
        check.setVersion(record.getVersion());
        return check;
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    @Override
    public Optional<Check> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        CheckRecord record = table.getItem(Key.builder().partitionValue(KEY_PREFIX + id).build());
        return Optional.ofNullable(record).map(this::toDomain);
    }

    /**
     * The check number encodes the id ({@code CHK-1042} is check 1042), so this
     * resolves to the same GetItem rather than needing a third global secondary
     * index. An index would add storage plus a write unit on every save, to
     * serve a lookup that is already answerable from the key.
     */
    @Override
    public Optional<Check> findByCheckNumber(String checkNumber) {
        if (checkNumber == null || !checkNumber.startsWith(CHECK_NUMBER_PREFIX)) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(checkNumber.substring(CHECK_NUMBER_PREFIX.length()));
            return findById(id);
        } catch (NumberFormatException ex) {
            // A check number created by the old timestamp scheme, or hand-typed.
            log.debug("Check number '{}' is not in the sequence format", checkNumber);
            return Optional.empty();
        }
    }

    @Override
    public List<Check> findByOrganizationAndStatus(Long organizationId, Check.CheckStatus status) {
        DynamoDbIndex<CheckRecord> index = table.index(CheckRecord.GSI_ORG_STATUS);

        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(orgStatusKey(organizationId, status.name())).build());

        return index.query(QueryEnhancedRequest.builder().queryConditional(condition).build())
                .stream()
                .flatMap(page -> page.items().stream())
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * DynamoDB paginates by cursor, not by offset - there is no "skip 200 items"
     * because that would mean reading and discarding them. Spring's
     * {@link Pageable} is offset-based, so this bridges the two by reading up to
     * the end of the requested page.
     *
     * <p>That is fine for the first few pages of an admin screen and wasteful
     * beyond that. A UI that pages deeply should switch to cursor pagination and
     * pass {@code lastEvaluatedKey} back, which is what DynamoDB actually wants.
     */
    @Override
    public Page<Check> findByOrganizationId(Long organizationId, Pageable pageable) {
        DynamoDbIndex<CheckRecord> index = table.index(CheckRecord.GSI_ORG_OPENED);

        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(orgKey(organizationId)).build());

        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        int needed = offset + limit;

        List<CheckRecord> collected = new ArrayList<>(needed);
        var pages = index.query(QueryEnhancedRequest.builder()
                .queryConditional(condition)
                // Newest first: the sort key is openedAt, descending.
                .scanIndexForward(false)
                .limit(needed)
                .build());

        for (var page : pages) {
            collected.addAll(page.items());
            if (collected.size() >= needed) {
                break;
            }
        }

        List<Check> content = collected.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDomain)
                .collect(Collectors.toList());

        // Total count is deliberately approximated as "what we have seen".
        // DynamoDB has no cheap COUNT(*) over a partition - getting a true total
        // means reading every matching item, which defeats the point.
        return new PageImpl<>(content, pageable, collected.size());
    }

    // -----------------------------------------------------------------------
    // Sequence
    // -----------------------------------------------------------------------

    /**
     * Atomic counter: a single item incremented with {@code ADD}, returning the
     * new value.
     *
     * <p>DynamoDB applies {@code ADD} server-side under a per-item lock, so
     * concurrent callers across every replica each receive a distinct,
     * monotonically increasing value. This is what replaces
     * {@code System.currentTimeMillis()}, which produced duplicate check numbers
     * whenever two pods opened a check in the same millisecond.
     *
     * <p>Trade-off: one item means one partition, capped around 1,000 writes per
     * second. Comfortably beyond restaurant volumes; a chain that outgrows it
     * would shard the counter across N items and pick one at random.
     */
    private long nextSequence() {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", AttributeValue.fromS(SEQUENCE_PK)))
                .updateExpression("ADD seq :one")
                .expressionAttributeValues(Map.of(":one", AttributeValue.fromN("1")))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        var response = lowLevelClient.updateItem(request);
        return Long.parseLong(response.attributes().get("seq").n());
    }

    private void assignChildIds(Check check) {
        for (CheckItem item : check.getItems()) {
            if (item.getId() == null) {
                item.setId(childIdSequence.incrementAndGet());
            }
            for (CheckItemModifier modifier : item.getModifiers()) {
                if (modifier.getId() == null) {
                    modifier.setId(childIdSequence.incrementAndGet());
                }
            }
        }
        for (CheckPayment payment : check.getPayments()) {
            if (payment.getId() == null) {
                payment.setId(childIdSequence.incrementAndGet());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Key construction
    //
    // Every key leads with the organisation. Tenant isolation is structural -
    // a query cannot cross tenants because the partition key does not permit
    // it - rather than a WHERE clause someone has to remember.
    // -----------------------------------------------------------------------

    private String orgKey(Long organizationId) {
        return "ORG#" + organizationId;
    }

    private String orgStatusKey(Long organizationId, String status) {
        return "ORG#" + organizationId + "#STATUS#" + status;
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private CheckRecord toRecord(Check check) {
        CheckRecord r = new CheckRecord();
        r.setPk(KEY_PREFIX + check.getId());
        r.setId(check.getId());
        r.setCheckNumber(check.getCheckNumber());
        r.setStatus(check.getStatus() == null ? null : check.getStatus().name());

        r.setSubtotal(check.getSubtotal());
        r.setTaxAmount(check.getTaxAmount());
        r.setDiscountAmount(check.getDiscountAmount());
        r.setTotalAmount(check.getTotalAmount());
        r.setPaidAmount(check.getPaidAmount());
        r.setTipAmount(check.getTipAmount());

        r.setGuestCount(check.getGuestCount());
        r.setTableNumber(check.getTableNumber());
        r.setEmployeeId(check.getEmployeeId());
        r.setTerminalId(check.getTerminalId());
        r.setOrganizationId(check.getOrganizationId());
        r.setProfitCenterId(check.getProfitCenterId());

        r.setOpenedAt(check.getOpenedAt());
        r.setClosedAt(check.getClosedAt());
        r.setCreatedAt(check.getCreatedAt());
        r.setUpdatedAt(check.getUpdatedAt());
        r.setVersion(check.getVersion());

        // Index attributes are denormalised onto the item. DynamoDB has no
        // computed columns, so a GSI can only project attributes that are
        // physically stored.
        r.setGsi1pk(orgStatusKey(check.getOrganizationId(),
                check.getStatus() == null ? "UNKNOWN" : check.getStatus().name()));
        r.setGsi1sk(String.valueOf(check.getOpenedAt()));
        r.setGsi2pk(orgKey(check.getOrganizationId()));
        r.setGsi2sk(String.valueOf(check.getOpenedAt()));

        r.setItems(check.getItems().stream().map(this::toItemRecord).collect(Collectors.toList()));
        r.setPayments(check.getPayments().stream().map(this::toPaymentRecord).collect(Collectors.toList()));
        return r;
    }

    private CheckRecord.ItemRecord toItemRecord(CheckItem item) {
        CheckRecord.ItemRecord r = new CheckRecord.ItemRecord();
        r.setId(item.getId());
        r.setMenuItemId(item.getMenuItemId());
        r.setItemName(item.getItemName());
        r.setQuantity(item.getQuantity());
        r.setUnitPrice(item.getUnitPrice());
        r.setTotalPrice(item.getTotalPrice());
        r.setSpecialInstructions(item.getSpecialInstructions());
        r.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        r.setVoided(item.isVoided());
        r.setVoidReason(item.getVoidReason());
        r.setModifiers(item.getModifiers().stream().map(m -> {
            CheckRecord.ModifierRecord mr = new CheckRecord.ModifierRecord();
            mr.setId(m.getId());
            mr.setModifierId(m.getModifierId());
            mr.setModifierName(m.getModifierName());
            mr.setPriceAdjustment(m.getPriceAdjustment());
            return mr;
        }).collect(Collectors.toList()));
        return r;
    }

    private CheckRecord.PaymentRecord toPaymentRecord(CheckPayment payment) {
        CheckRecord.PaymentRecord r = new CheckRecord.PaymentRecord();
        r.setId(payment.getId());
        r.setTenderId(payment.getTenderId());
        r.setTenderName(payment.getTenderName());
        r.setPaymentType(payment.getPaymentType() == null ? null : payment.getPaymentType().name());
        r.setAmount(payment.getAmount());
        r.setTipAmount(payment.getTipAmount());
        r.setReferenceNumber(payment.getReferenceNumber());
        r.setAuthorizationCode(payment.getAuthorizationCode());
        r.setStatus(payment.getStatus() == null ? null : payment.getStatus().name());
        r.setProcessedAt(payment.getProcessedAt());
        r.setVoided(payment.isVoided());
        r.setVoidReason(payment.getVoidReason());
        return r;
    }

    private Check toDomain(CheckRecord r) {
        Check check = new Check();
        check.setId(r.getId());
        check.setCheckNumber(r.getCheckNumber());
        check.setStatus(r.getStatus() == null ? null : Check.CheckStatus.valueOf(r.getStatus()));

        check.setSubtotal(r.getSubtotal());
        check.setTaxAmount(r.getTaxAmount());
        check.setDiscountAmount(r.getDiscountAmount());
        check.setTotalAmount(r.getTotalAmount());
        check.setPaidAmount(r.getPaidAmount());
        check.setTipAmount(r.getTipAmount());

        check.setGuestCount(r.getGuestCount());
        check.setTableNumber(r.getTableNumber());
        check.setEmployeeId(r.getEmployeeId());
        check.setTerminalId(r.getTerminalId());
        check.setOrganizationId(r.getOrganizationId());
        check.setProfitCenterId(r.getProfitCenterId());

        check.setOpenedAt(r.getOpenedAt());
        check.setClosedAt(r.getClosedAt());
        check.setCreatedAt(r.getCreatedAt());
        check.setUpdatedAt(r.getUpdatedAt());
        check.setVersion(r.getVersion());

        List<CheckItem> items = new ArrayList<>();
        for (CheckRecord.ItemRecord ir : nullSafe(r.getItems())) {
            CheckItem item = new CheckItem();
            item.setId(ir.getId());
            item.setMenuItemId(ir.getMenuItemId());
            item.setItemName(ir.getItemName());
            item.setQuantity(ir.getQuantity());
            item.setUnitPrice(ir.getUnitPrice());
            item.setTotalPrice(ir.getTotalPrice());
            item.setSpecialInstructions(ir.getSpecialInstructions());
            item.setStatus(ir.getStatus() == null ? null : CheckItem.ItemStatus.valueOf(ir.getStatus()));
            item.setVoided(Boolean.TRUE.equals(ir.getVoided()));
            item.setVoidReason(ir.getVoidReason());
            item.setCheck(check);

            List<CheckItemModifier> modifiers = new ArrayList<>();
            for (CheckRecord.ModifierRecord mr : nullSafe(ir.getModifiers())) {
                CheckItemModifier modifier = new CheckItemModifier();
                modifier.setId(mr.getId());
                modifier.setModifierId(mr.getModifierId());
                modifier.setModifierName(mr.getModifierName());
                modifier.setPriceAdjustment(mr.getPriceAdjustment());
                modifier.setCheckItem(item);
                modifiers.add(modifier);
            }
            item.setModifiers(modifiers);
            items.add(item);
        }
        check.setItems(items);

        List<CheckPayment> payments = new ArrayList<>();
        for (CheckRecord.PaymentRecord pr : nullSafe(r.getPayments())) {
            CheckPayment payment = new CheckPayment();
            payment.setId(pr.getId());
            payment.setTenderId(pr.getTenderId());
            payment.setTenderName(pr.getTenderName());
            payment.setPaymentType(pr.getPaymentType() == null
                    ? null : CheckPayment.PaymentType.valueOf(pr.getPaymentType()));
            payment.setAmount(pr.getAmount());
            payment.setTipAmount(pr.getTipAmount());
            payment.setReferenceNumber(pr.getReferenceNumber());
            payment.setAuthorizationCode(pr.getAuthorizationCode());
            payment.setStatus(pr.getStatus() == null
                    ? null : CheckPayment.PaymentStatus.valueOf(pr.getStatus()));
            payment.setProcessedAt(pr.getProcessedAt());
            payment.setVoided(Boolean.TRUE.equals(pr.getVoided()));
            payment.setVoidReason(pr.getVoidReason());
            payment.setCheck(check);
            payments.add(payment);
        }
        check.setPayments(payments);

        return check;
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
