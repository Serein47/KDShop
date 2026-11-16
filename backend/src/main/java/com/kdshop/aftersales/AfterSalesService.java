package com.kdshop.aftersales;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdshop.admin.AuditService;
import com.kdshop.aftersales.dto.AfterSaleView;
import com.kdshop.aftersales.dto.ApplyAfterSaleRequest;
import com.kdshop.aftersales.dto.ReviewAfterSaleRequest;
import com.kdshop.aftersales.entity.AfterSale;
import com.kdshop.aftersales.entity.AfterSaleLog;
import com.kdshop.aftersales.mapper.AfterSaleLogMapper;
import com.kdshop.aftersales.mapper.AfterSaleMapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.User;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.config.MetaProperties;
import com.kdshop.order.entity.MerchantOrder;
import com.kdshop.order.entity.Order;
import com.kdshop.order.mapper.MerchantOrderMapper;
import com.kdshop.order.mapper.OrderMapper;
import com.kdshop.refund.RefundService;
import com.kdshop.refund.entity.Refund;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后单：子单完成后 15 天内可申请，商家 48 小时内审核。
 * 仅退款通过后直接退款；退货退款通过后进入待寄回（质检在 ISSUE-38 接入）。
 */
@Service
public class AfterSalesService {

    private static final DateTimeFormatter AS_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Set<String> TYPES = Set.of("REFUND_ONLY", "RETURN_REFUND");
    private static final Set<String> REASONS = Set.of(
            "QUALITY", "NOT_AS_DESCRIBED", "DAMAGED", "WRONG_ITEM", "NO_LONGER_NEEDED", "OTHER");
    /** 定制商品只接受这两类原因（ADR-0008）。 */
    private static final Set<String> CUSTOM_REASONS = Set.of("QUALITY", "NOT_AS_DESCRIBED");
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "APPLIED", "APPROVED", "RETURNING", "RECEIVED", "INSPECTED", "ESCALATED");

    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private RoleMapper roleMapper;
    @Autowired(required = false)
    private UserRoleMapper userRoleMapper;

    private final AfterSaleMapper afterSaleMapper;
    private final AfterSaleLogMapper logMapper;
    private final MerchantOrderMapper merchantOrderMapper;
    private final OrderMapper orderMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;
    private final MetaProperties meta;

    public AfterSalesService(AfterSaleMapper afterSaleMapper, AfterSaleLogMapper logMapper,
                             MerchantOrderMapper merchantOrderMapper, OrderMapper orderMapper,
                             ShopMapper shopMapper, UserMapper userMapper, RefundService refundService,
                             ObjectMapper objectMapper, MetaProperties meta) {
        this.afterSaleMapper = afterSaleMapper;
        this.logMapper = logMapper;
        this.merchantOrderMapper = merchantOrderMapper;
        this.orderMapper = orderMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.refundService = refundService;
        this.objectMapper = objectMapper;
        this.meta = meta;
    }

    @Transactional
    public AfterSaleView apply(String orderNo, String subOrderNo, ApplyAfterSaleRequest request) {
        Long userId = currentUserId();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        MerchantOrder sub = merchantOrderMapper.selectOne(new LambdaQueryWrapper<MerchantOrder>()
                .eq(MerchantOrder::getSubOrderNo, subOrderNo)
                .eq(MerchantOrder::getOrderId, order.getId()));
        if (sub == null) {
            throw new BusinessException(ErrorCode.SUB_ORDER_STATE_INVALID, "子单不存在");
        }
        if (!"COMPLETED".equals(sub.getStatus())) {
            throw new BusinessException(ErrorCode.AFTER_SALES_NOT_ELIGIBLE, "只有已完成的子单可以申请售后");
        }
        if (sub.getCompletedAt() == null
                || sub.getCompletedAt().plusDays(meta.afterSalesWindowDays()).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AFTER_SALES_NOT_ELIGIBLE,
                    "已超过 " + meta.afterSalesWindowDays() + " 天售后申请期限");
        }

        String type = normalizeType(request.type());
        String reasonCode = normalizeReason(request.reasonCode());
        if ("CUSTOM".equals(order.getOrderType()) && !CUSTOM_REASONS.contains(reasonCode)) {
            throw new BusinessException(ErrorCode.AFTER_SALES_NOT_ELIGIBLE,
                    "定制商品仅支持「质量问题」与「与描述不符」两类原因");
        }
        checkNoOpenAfterSale(sub.getId());

        int amount = refundableAmount(sub);
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.AFTER_SALES_NOT_ELIGIBLE, "该子单没有可退金额");
        }

        AfterSale afterSale = new AfterSale();
        afterSale.setAsNo("AS" + LocalDateTime.now().format(AS_NO_TIME)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));
        afterSale.setMerchantOrderId(sub.getId());
        afterSale.setOrderId(order.getId());
        afterSale.setMerchantId(sub.getMerchantId());
        afterSale.setUserId(userId);
        afterSale.setType(type);
        afterSale.setReasonCode(reasonCode);
        afterSale.setDescription(request.description());
        afterSale.setEvidenceJson(writeJson(request.evidence()));
        afterSale.setStatus("APPLIED");
        afterSale.setRefundAmountCent(amount);
        afterSale.setReapplyCount(0);
        afterSaleMapper.insert(afterSale);

        writeLog(afterSale.getId(), null, "APPLIED", "USER", userId, "提交售后申请");
        audit("USER", userId, "AFTER_SALES_APPLY", afterSale.getAsNo(),
                Map.of("type", type, "reason", reasonCode));
        return view(afterSale, true);
    }

    public List<AfterSaleView> listMine() {
        Long userId = currentUserId();
        return afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getUserId, userId)
                        .orderByDesc(AfterSale::getId)
                        .last("LIMIT 100"))
                .stream().map(as -> view(as, false)).toList();
    }

    public List<AfterSaleView> listForMerchant(String status) {
        Shop shop = currentShop();
        LambdaQueryWrapper<AfterSale> query = new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getMerchantId, shop.getId())
                .orderByDesc(AfterSale::getId)
                .last("LIMIT 100");
        if (StringUtils.hasText(status)) {
            query.eq(AfterSale::getStatus, status.toUpperCase());
        }
        return afterSaleMapper.selectList(query).stream().map(as -> view(as, false)).toList();
    }

    public List<AfterSaleView> listAll(String status) {
        requireAdmin();
        LambdaQueryWrapper<AfterSale> query = new LambdaQueryWrapper<AfterSale>()
                .orderByDesc(AfterSale::getId)
                .last("LIMIT 100");
        if (StringUtils.hasText(status)) {
            query.eq(AfterSale::getStatus, status.toUpperCase());
        }
        return afterSaleMapper.selectList(query).stream().map(as -> view(as, false)).toList();
    }

    public AfterSaleView detail(String asNo) {
        AfterSale afterSale = require(asNo);
        requireAccess(afterSale);
        return view(afterSale, true);
    }

    @Transactional
    public AfterSaleView review(String asNo, ReviewAfterSaleRequest request) {
        Shop shop = currentShop();
        AfterSale afterSale = require(asNo);
        if (!afterSale.getMerchantId().equals(shop.getId())) {
            throw new BusinessException(ErrorCode.MERCHANT_SCOPE_FORBIDDEN);
        }
        if (!"APPLIED".equals(afterSale.getStatus())) {
            throw new BusinessException(ErrorCode.AFTER_SALES_STATE_INVALID, "该售后单已处理");
        }

        if (!request.approve()) {
            if (!StringUtils.hasText(request.reason())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
            }
            afterSale.setStatus("REJECTED");
            afterSale.setRejectReason(request.reason().trim());
            afterSale.setOperatorType("MERCHANT");
            afterSale.setOperatorId(shop.getId());
            afterSaleMapper.updateById(afterSale);
            writeLog(afterSale.getId(), "APPLIED", "REJECTED", "MERCHANT", shop.getId(),
                    "驳回：" + request.reason().trim());
            audit("MERCHANT", shop.getId(), "AFTER_SALES_REJECT", asNo,
                    Map.of("reason", request.reason().trim()));
            return view(afterSale, true);
        }

        afterSale.setStatus("APPROVED");
        afterSale.setOperatorType("MERCHANT");
        afterSale.setOperatorId(shop.getId());
        afterSaleMapper.updateById(afterSale);
        writeLog(afterSale.getId(), "APPLIED", "APPROVED", "MERCHANT", shop.getId(), "商家同意售后");

        if ("REFUND_ONLY".equals(afterSale.getType())) {
            MerchantOrder sub = merchantOrderMapper.selectById(afterSale.getMerchantOrderId());
            Refund refund = refundService.executeForAfterSales(sub, afterSale.getRefundAmountCent(),
                    "售后 " + asNo, "MERCHANT", shop.getId());
            afterSale.setStatus("REFUNDED");
            afterSale.setRefundNo(refund.getRefundNo());
            afterSale.setCompletedAt(LocalDateTime.now());
            afterSaleMapper.updateById(afterSale);
            writeLog(afterSale.getId(), "APPROVED", "REFUNDED", "SYSTEM", null,
                    "仅退款完成：" + refund.getRefundNo());
        } else {
            afterSale.setStatus("RETURNING");
            afterSaleMapper.updateById(afterSale);
            writeLog(afterSale.getId(), "APPROVED", "RETURNING", "SYSTEM", null, "等待顾客寄回");
        }

        audit("MERCHANT", shop.getId(), "AFTER_SALES_APPROVE", asNo,
                Map.of("type", afterSale.getType()));
        return view(afterSale, true);
    }

    /** 被驳回后补充证据重新申请，整张售后单只有一次机会。 */
    @Transactional
    public AfterSaleView reapply(String asNo, ApplyAfterSaleRequest request) {
        Long userId = currentUserId();
        AfterSale afterSale = require(asNo);
        if (!afterSale.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        if (!"REJECTED".equals(afterSale.getStatus())) {
            throw new BusinessException(ErrorCode.AFTER_SALES_STATE_INVALID, "只有被驳回的售后单可以重新申请");
        }
        if (afterSale.getReapplyCount() != null && afterSale.getReapplyCount() >= 1) {
            throw new BusinessException(ErrorCode.AFTER_SALES_STATE_INVALID, "补充申请机会已用完");
        }
        Order order = orderMapper.selectById(afterSale.getOrderId());
        String type = normalizeType(request.type());
        String reasonCode = normalizeReason(request.reasonCode());
        if (order != null && "CUSTOM".equals(order.getOrderType()) && !CUSTOM_REASONS.contains(reasonCode)) {
            throw new BusinessException(ErrorCode.AFTER_SALES_NOT_ELIGIBLE,
                    "定制商品仅支持「质量问题」与「与描述不符」两类原因");
        }

        afterSale.setType(type);
        afterSale.setReasonCode(reasonCode);
        afterSale.setDescription(request.description());
        afterSale.setEvidenceJson(writeJson(request.evidence()));
        afterSale.setStatus("APPLIED");
        afterSale.setRejectReason(null);
        afterSale.setReapplyCount((afterSale.getReapplyCount() == null ? 0 : afterSale.getReapplyCount()) + 1);
        afterSaleMapper.updateById(afterSale);
        writeLog(afterSale.getId(), "REJECTED", "APPLIED", "USER", userId, "补充证据重新申请");
        return view(afterSale, true);
    }

    // ---- 后续 Issue（#47 寄回/质检、#48 裁决、#49 结算冻结）在此扩展 ----

    private void checkNoOpenAfterSale(Long merchantOrderId) {
        List<AfterSale> existing = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getMerchantOrderId, merchantOrderId));
        for (AfterSale row : existing) {
            if (ACTIVE_STATUSES.contains(row.getStatus())) {
                throw new BusinessException(ErrorCode.AFTER_SALES_EXISTS, "该子单已有进行中的售后单");
            }
            if ("REJECTED".equals(row.getStatus())
                    && (row.getReapplyCount() == null || row.getReapplyCount() < 1)) {
                throw new BusinessException(ErrorCode.AFTER_SALES_EXISTS,
                        "售后被驳回，可在售后详情补充证据重新申请（限 1 次）");
            }
            throw new BusinessException(ErrorCode.AFTER_SALES_EXISTS,
                    "该子单的售后流程已结束，不能再次申请");
        }
    }

    private int refundableAmount(MerchantOrder sub) {
        int pay = sub.getPayAmountCent() == null ? 0 : sub.getPayAmountCent();
        int refunded = sub.getRefundedAmountCent() == null ? 0 : sub.getRefundedAmountCent();
        return Math.max(pay - refunded, 0);
    }

    private String normalizeType(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
        if (!TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "售后类型只能是仅退款或退货退款");
        }
        return value;
    }

    private String normalizeReason(String reasonCode) {
        String value = StringUtils.hasText(reasonCode) ? reasonCode.trim().toUpperCase() : "";
        if (!REASONS.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "售后原因不合法");
        }
        return value;
    }

    private AfterSale require(String asNo) {
        AfterSale afterSale = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getAsNo, asNo));
        if (afterSale == null) {
            throw new BusinessException(ErrorCode.AFTER_SALES_STATE_INVALID, "售后单不存在");
        }
        return afterSale;
    }

    private void requireAccess(AfterSale afterSale) {
        Long userId = currentUserId();
        if (afterSale.getUserId().equals(userId) || isAdmin(userId)) {
            return;
        }
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop != null && shop.getId().equals(afterSale.getMerchantId())) {
            return;
        }
        throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
    }

    private boolean isAdmin(Long userId) {
        if (roleMapper == null || userRoleMapper == null) {
            return false;
        }
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (role == null) {
            return false;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        return count != null && count > 0;
    }

    private void requireAdmin() {
        Long userId = currentUserId();
        if (!isAdmin(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private Shop currentShop() {
        Long userId = currentUserId();
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "没有开通店铺");
        }
        return shop;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private AfterSaleView view(AfterSale afterSale, boolean withLogs) {
        MerchantOrder sub = merchantOrderMapper.selectById(afterSale.getMerchantOrderId());
        Order order = orderMapper.selectById(afterSale.getOrderId());
        Shop shop = shopMapper.selectById(afterSale.getMerchantId());
        User user = userMapper.selectById(afterSale.getUserId());
        List<AfterSaleView.LogView> logs = withLogs
                ? logMapper.selectList(new LambdaQueryWrapper<AfterSaleLog>()
                        .eq(AfterSaleLog::getAfterSalesId, afterSale.getId())
                        .orderByAsc(AfterSaleLog::getId))
                .stream()
                .map(row -> new AfterSaleView.LogView(row.getFromStatus(), row.getToStatus(),
                        row.getOperatorType(), row.getNote(), row.getCreatedAt()))
                .toList()
                : null;
        return new AfterSaleView(
                afterSale.getAsNo(),
                order == null ? "" : order.getOrderNo(),
                sub == null ? "" : sub.getSubOrderNo(),
                afterSale.getMerchantId(),
                shop == null ? null : shop.getShopName(),
                user == null ? null : user.getNickname(),
                afterSale.getType(),
                afterSale.getReasonCode(),
                afterSale.getDescription(),
                readList(afterSale.getEvidenceJson()),
                afterSale.getStatus(),
                afterSale.getRefundAmountCent() == null ? 0 : afterSale.getRefundAmountCent(),
                afterSale.getRefundNo(),
                afterSale.getReturnCarrier(),
                afterSale.getReturnTrackingNo(),
                afterSale.getReturnShippedAt(),
                afterSale.getReceivedAt(),
                afterSale.getInspectResult(),
                afterSale.getInspectNote(),
                readList(afterSale.getInspectImagesJson()),
                afterSale.getResponsibility(),
                afterSale.getVerdictNote(),
                afterSale.getRejectReason(),
                afterSale.getReapplyCount() == null ? 0 : afterSale.getReapplyCount(),
                afterSale.getCreatedAt(),
                afterSale.getCompletedAt(),
                logs);
    }

    private void writeLog(Long afterSaleId, String from, String to, String operatorType,
                          Long operatorId, String note) {
        AfterSaleLog log = new AfterSaleLog();
        log.setAfterSalesId(afterSaleId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setNote(note);
        logMapper.insert(log);
    }

    private void audit(String operatorType, Long operatorId, String action, String target,
                       Map<String, Object> detail) {
        if (auditService != null) {
            auditService.record(operatorType, operatorId, action, "AFTER_SALES", target, null, detail);
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
