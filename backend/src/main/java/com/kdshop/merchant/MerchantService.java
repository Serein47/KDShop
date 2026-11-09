package com.kdshop.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kdshop.auth.entity.Role;
import com.kdshop.auth.entity.UserRole;
import com.kdshop.auth.mapper.RoleMapper;
import com.kdshop.auth.mapper.UserRoleMapper;
import com.kdshop.catalog.entity.Shop;
import com.kdshop.catalog.mapper.ShopMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import com.kdshop.merchant.dto.ApplyRequest;
import com.kdshop.merchant.dto.MerchantView;
import com.kdshop.merchant.dto.ShopProfileRequest;
import com.kdshop.merchant.dto.ShopProfileView;
import com.kdshop.admin.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 商家入驻：申请、查看状态、平台审核。 */
@Service
public class MerchantService {

    @Autowired(required = false)
    private AuditService auditService;

    private final ShopMapper shopMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public MerchantService(ShopMapper shopMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.shopMapper = shopMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Transactional
    public MerchantView apply(ApplyRequest request) {
        Long userId = currentUserId();
        Shop existing = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userId));
        if (existing != null && !"REJECTED".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_APPLY_DUPLICATE,
                    "ACTIVE".equals(existing.getStatus()) ? "你已经有店铺了" : "申请正在审核中");
        }
        if (shopMapper.selectCount(new LambdaQueryWrapper<Shop>().eq(Shop::getShopName, request.shopName())) > 0) {
            throw new BusinessException(ErrorCode.SHOP_NAME_TAKEN);
        }

        Shop shop = existing == null ? new Shop() : existing;
        shop.setUserId(userId);
        shop.setShopName(request.shopName().trim());
        shop.setContactName(request.contactName().trim());
        shop.setContactPhone(request.contactPhone().trim());
        shop.setDescription(request.description());
        shop.setLicenseImage(request.licenseImage());
        shop.setStatus("PENDING");
        shop.setRejectReason(null);
        shop.setCommissionRate(500);
        if (shop.getId() == null) {
            shopMapper.insert(shop);
        } else {
            shopMapper.updateById(shop);
        }
        return toView(shop);
    }

    public MerchantView myShop() {
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, currentUserId()));
        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND, "还没有提交入驻申请");
        }
        return toView(shop);
    }

    public List<MerchantView> list(String status) {
        requireRole("ADMIN");
        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<Shop>()
                .orderByAsc(Shop::getStatus)
                .orderByDesc(Shop::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(Shop::getStatus, status.toUpperCase());
        }
        return shopMapper.selectList(query).stream().map(this::toView).toList();
    }

    @Transactional
    public MerchantView approve(Long shopId) {
        requireRole("ADMIN");
        Shop shop = requireShop(shopId);
        if (!"PENDING".equals(shop.getStatus()) && !"REJECTED".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.SHOP_STATE_INVALID, "该店铺当前状态不可审核");
        }
        shop.setStatus("ACTIVE");
        shop.setRejectReason(null);
        shop.setApprovedAt(LocalDateTime.now());
        shop.setApprovedBy(currentUserId());
        shopMapper.updateById(shop);
        grantMerchantRole(shop.getUserId());
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "MERCHANT_APPROVE", "MERCHANT",
                    String.valueOf(shopId), java.util.Map.of("status", "PENDING"), java.util.Map.of("status", "ACTIVE"));
        }
        return toView(shop);
    }

    @Transactional
    public MerchantView reject(Long shopId, String reason) {
        requireRole("ADMIN");
        Shop shop = requireShop(shopId);
        if (!"PENDING".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.SHOP_STATE_INVALID, "只有待审核的店铺可以驳回");
        }
        shop.setStatus("REJECTED");
        shop.setRejectReason(reason);
        shopMapper.updateById(shop);
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "MERCHANT_REJECT", "MERCHANT",
                    String.valueOf(shopId), java.util.Map.of("status", "PENDING"), java.util.Map.of("status", "REJECTED"));
        }
        return toView(shop);
    }

    public ShopProfileView myProfile() {
        Shop shop = requireMyShop();
        return toProfileView(shop);
    }

    @Transactional
    public ShopProfileView updateProfile(ShopProfileRequest request) {
        Shop shop = requireMyShop();
        Long taken = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getShopName, request.shopName().trim())
                .ne(Shop::getId, shop.getId()));
        if (taken != null && taken > 0) {
            throw new BusinessException(ErrorCode.SHOP_NAME_TAKEN);
        }
        shop.setShopName(request.shopName().trim());
        shop.setDescription(request.description());
        shop.setLogo(request.logo());
        shop.setContactName(request.contactName());
        shop.setContactPhone(request.contactPhone());
        shopMapper.updateById(shop);
        return toProfileView(shop);
    }

    private Shop requireMyShop() {
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, currentUserId()));
        if (shop == null || !"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_APPROVED, "店铺未开通");
        }
        return shop;
    }

    private ShopProfileView toProfileView(Shop shop) {
        return new ShopProfileView(shop.getId(), shop.getShopName(), shop.getDescription(), shop.getLogo(),
                shop.getContactName(), shop.getContactPhone(), shop.getStatus(), shop.getCommissionRate(),
                shop.getCreatedAt());
    }

    @Transactional
    public MerchantView suspend(Long shopId, String reason) {
        requireRole("ADMIN");
        Shop shop = requireShop(shopId);
        if (!"ACTIVE".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.SHOP_STATE_INVALID, "只有营业中的店铺可以封禁");
        }
        shop.setStatus("SUSPENDED");
        shop.setSuspendReason(reason);
        shopMapper.updateById(shop);
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "MERCHANT_SUSPEND", "MERCHANT",
                    String.valueOf(shopId), java.util.Map.of("status", "ACTIVE"), java.util.Map.of("status", "SUSPENDED", "reason", reason));
        }
        return toView(shop);
    }

    @Transactional
    public MerchantView resume(Long shopId) {
        requireRole("ADMIN");
        Shop shop = requireShop(shopId);
        if (!"SUSPENDED".equals(shop.getStatus())) {
            throw new BusinessException(ErrorCode.SHOP_STATE_INVALID, "只有已封禁的店铺可以解封");
        }
        shop.setStatus("ACTIVE");
        shop.setSuspendReason(null);
        shopMapper.updateById(shop);
        if (auditService != null) {
            auditService.record("ADMIN", currentUserId(), "MERCHANT_RESUME", "MERCHANT",
                    String.valueOf(shopId), java.util.Map.of("status", "SUSPENDED"), java.util.Map.of("status", "ACTIVE"));
        }
        return toView(shop);
    }

    private void grantMerchantRole(Long userId) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "MERCHANT"));
        if (role == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count == null || count == 0) {
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(role.getId());
            userRoleMapper.insert(link);
        }
    }

    private void requireRole(String code) {
        Long userId = currentUserId();
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, code));
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private Shop requireShop(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        }
        return shop;
    }

    private MerchantView toView(Shop shop) {
        return new MerchantView(shop.getId(), shop.getUserId(), shop.getShopName(), shop.getDescription(),
                shop.getContactName(), shop.getContactPhone(), shop.getStatus(), shop.getRejectReason(),
                shop.getSuspendReason(), shop.getCommissionRate(), shop.getCreatedAt(), shop.getApprovedAt());
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
