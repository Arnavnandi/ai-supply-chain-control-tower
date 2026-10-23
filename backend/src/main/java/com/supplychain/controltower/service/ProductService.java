package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.ProductDto;
import com.supplychain.controltower.entity.Category;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.CategoryRepository;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts(String query) {
        Long companyId = TenantContext.getRequiredCompanyId();
        String companyCode = resolveCompanyCode(companyId);
        List<Product> products = (query != null && !query.isBlank()) ?
                productRepository.searchProducts(companyId, query) : productRepository.findByCompanyId(companyId);

        return products.stream().map(p -> mapToDto(p, companyCode)).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Product product = productRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToDto(product, resolveCompanyCode(companyId));
    }

    @Transactional
    public ProductDto createProduct(ProductDto dto) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findByCompanyIdAndId(companyId, dto.getCategoryId())
                    .orElse(null);
        }

        Product product = Product.builder()
                .company(company)
                .sku(dto.getSku())
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .category(category)
                .reorderLevel(dto.getReorderLevel())
                .safetyStock(dto.getSafetyStock())
                .leadTimeDays(dto.getLeadTimeDays())
                .unitOfMeasure(dto.getUnitOfMeasure() != null ? dto.getUnitOfMeasure() : "Units")
                .build();

        return mapToDto(productRepository.save(product), company.getCode());
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductDto dto) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Product product = productRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findByCompanyIdAndId(companyId, dto.getCategoryId()).orElse(null);
            product.setCategory(category);
        }

        product.setSku(dto.getSku());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setReorderLevel(dto.getReorderLevel());
        product.setSafetyStock(dto.getSafetyStock());
        product.setLeadTimeDays(dto.getLeadTimeDays());
        if (dto.getUnitOfMeasure() != null) {
            product.setUnitOfMeasure(dto.getUnitOfMeasure());
        }

        return mapToDto(productRepository.save(product), resolveCompanyCode(companyId));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Product product = productRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
    }

    private String resolveCompanyCode(Long companyId) {
        String code = TenantContext.getCompanyCode();
        if (code != null && !code.isBlank()) {
            return code;
        }
        return companyRepository.findById(companyId)
                .map(Company::getCode)
                .orElse(null);
    }

    private ProductDto mapToDto(Product p) {
        Long companyId = TenantContext.getCompanyId();
        String companyCode = (companyId != null) ? resolveCompanyCode(companyId) : null;
        if (companyCode == null && p.getCompany() != null) {
            companyCode = p.getCompany().getCode();
        }
        return mapToDto(p, companyCode);
    }

    private ProductDto mapToDto(Product p, String companyCode) {
        boolean isDemo = (companyCode != null && "DEMO".equalsIgnoreCase(companyCode)) ||
                (p.getSku() != null && (p.getSku().startsWith("SKU-ELEC-") || p.getSku().startsWith("SKU-IND-") || p.getSku().startsWith("SKU-PKG-") || p.getSku().startsWith("SKU-PROD-")));
        return ProductDto.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : "Uncategorized")
                .reorderLevel(p.getReorderLevel())
                .safetyStock(p.getSafetyStock())
                .leadTimeDays(p.getLeadTimeDays())
                .unitOfMeasure(p.getUnitOfMeasure())
                .isDemo(isDemo)
                .build();
    }
}

