package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.ProductDto;
import com.supplychain.controltower.entity.Category;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.repository.CategoryRepository;
import com.supplychain.controltower.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts(String query) {
        List<Product> products = (query != null && !query.isBlank()) ?
                productRepository.searchProducts(query) : productRepository.findAll();

        return products.stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToDto(product);
    }

    @Transactional
    public ProductDto createProduct(ProductDto dto) {
        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId())
                    .orElse(null);
        }

        Product product = Product.builder()
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

        return mapToDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductDto dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
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

        return mapToDto(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    private ProductDto mapToDto(Product p) {
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
                .build();
    }
}
