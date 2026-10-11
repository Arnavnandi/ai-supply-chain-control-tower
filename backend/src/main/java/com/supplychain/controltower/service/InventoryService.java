package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.InventoryDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<InventoryDto> getAllInventory() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return inventoryRepository.findByCompanyId(companyId).stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getLowStockInventory() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return inventoryRepository.findLowStockInventory(companyId).stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getOverstockedInventory() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return inventoryRepository.findOverstockedInventory(companyId).stream().map(this::mapToDto).toList();
    }

    @Transactional
    public InventoryDto adjustStock(Long productId, Long warehouseId, Integer adjustmentQty) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found: " + companyId));

        Inventory inv = inventoryRepository.findByCompanyIdAndProductIdAndWarehouseId(companyId, productId, warehouseId)
                .orElseGet(() -> {
                    Product product = productRepository.findByCompanyIdAndId(companyId, productId)
                            .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId + " for company " + companyId));
                    Warehouse warehouse = warehouseRepository.findByCompanyIdAndId(companyId, warehouseId)
                            .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + warehouseId + " for company " + companyId));
                    return Inventory.builder()
                            .company(company)
                            .product(product)
                            .warehouse(warehouse)
                            .quantityAvailable(0)
                            .reservedQuantity(0)
                            .reorderLevel(product.getReorderLevel())
                            .safetyStock(product.getSafetyStock())
                            .build();
                });

        int newQty = Math.max(0, inv.getQuantityAvailable() + adjustmentQty);
        inv.setQuantityAvailable(newQty);
        if (adjustmentQty > 0) {
            inv.setLastRestockedAt(LocalDateTime.now());
        }

        return mapToDto(inventoryRepository.save(inv));
    }

    private InventoryDto mapToDto(Inventory inv) {
        String status = "OPTIMAL";
        if (inv.getQuantityAvailable() <= inv.getSafetyStock()) {
            status = "CRITICAL";
        } else if (inv.getQuantityAvailable() <= inv.getReorderLevel()) {
            status = "LOW_STOCK";
        } else if (inv.getQuantityAvailable() > inv.getReorderLevel() * 3) {
            status = "OVERSTOCK";
        }

        boolean isDemo = (inv.getCompany() != null && "DEMO".equalsIgnoreCase(inv.getCompany().getCode())) ||
                (inv.getProduct() != null && inv.getProduct().getSku() != null &&
                (inv.getProduct().getSku().startsWith("SKU-ELEC-") || inv.getProduct().getSku().startsWith("SKU-IND-") || inv.getProduct().getSku().startsWith("SKU-PKG-") || inv.getProduct().getSku().startsWith("SKU-PROD-")));

        return InventoryDto.builder()
                .id(inv.getId())
                .productId(inv.getProduct().getId())
                .productSku(inv.getProduct().getSku())
                .productName(inv.getProduct().getName())
                .productPrice(inv.getProduct().getPrice())
                .warehouseId(inv.getWarehouse().getId())
                .warehouseCode(inv.getWarehouse().getCode())
                .warehouseName(inv.getWarehouse().getName())
                .quantityAvailable(inv.getQuantityAvailable())
                .reservedQuantity(inv.getReservedQuantity())
                .reorderLevel(inv.getReorderLevel())
                .safetyStock(inv.getSafetyStock())
                .lastRestockedAt(inv.getLastRestockedAt())
                .status(status)
                .isDemo(isDemo)
                .build();
    }
}

