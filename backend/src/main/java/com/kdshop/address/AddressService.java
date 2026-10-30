package com.kdshop.address;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kdshop.address.dto.AddressRequest;
import com.kdshop.address.entity.Address;
import com.kdshop.address.mapper.AddressMapper;
import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressService {

    private final AddressMapper addressMapper;

    public AddressService(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public List<Address> list() {
        return addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, currentUserId())
                .isNull(Address::getDeletedAt)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId));
    }

    @Transactional
    public Address create(AddressRequest request) {
        Long userId = currentUserId();
        Address address = new Address();
        fill(address, request);
        address.setUserId(userId);
        if (Boolean.TRUE.equals(request.isDefault()) || countOf(userId) == 0) {
            address.setIsDefault(1);
            clearDefault(userId);
        } else {
            address.setIsDefault(0);
        }
        addressMapper.insert(address);
        return address;
    }

    @Transactional
    public void update(Long id, AddressRequest request) {
        Address address = requireOwned(id);
        fill(address, request);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(address.getUserId());
            address.setIsDefault(1);
        }
        addressMapper.updateById(address);
    }

    @Transactional
    public void delete(Long id) {
        Address address = requireOwned(id);
        address.setDeletedAt(LocalDateTime.now());
        addressMapper.updateById(address);
    }

    @Transactional
    public void setDefault(Long id) {
        Address address = requireOwned(id);
        clearDefault(address.getUserId());
        Address patch = new Address();
        patch.setId(id);
        patch.setIsDefault(1);
        addressMapper.updateById(patch);
    }

    public Address requireOwned(Long id) {
        Address address = addressMapper.selectById(id);
        if (address == null || address.getDeletedAt() != null || !address.getUserId().equals(currentUserId())) {
            throw new BusinessException(ErrorCode.ADDRESS_INVALID);
        }
        return address;
    }

    private void fill(Address address, AddressRequest request) {
        address.setReceiver(request.receiver().trim());
        address.setPhone(request.phone().trim());
        address.setProvince(request.province().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setDetail(request.detail().trim());
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .set(Address::getIsDefault, 0));
    }

    private long countOf(Long userId) {
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .isNull(Address::getDeletedAt));
        return count == null ? 0 : count;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
