package com.kdshop.address;

import com.kdshop.address.dto.AddressRequest;
import com.kdshop.address.entity.Address;
import com.kdshop.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<Address>> list() {
        return ApiResponse.ok(addressService.list());
    }

    @PostMapping
    public ApiResponse<Address> create(@Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(addressService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Address> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        addressService.update(id, request);
        return ApiResponse.ok(addressService.requireOwned(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        addressService.delete(id);
        return ApiResponse.ok(Map.of("removed", true));
    }

    @PutMapping("/{id}/default")
    public ApiResponse<Map<String, Boolean>> setDefault(@PathVariable Long id) {
        addressService.setDefault(id);
        return ApiResponse.ok(Map.of("default", true));
    }
}
