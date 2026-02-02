/*
 * Copyright (C) 2024 Example Company Ltd. All rights reserved.
 *
 * This document is the property of Example Company Ltd.
 * It is considered confidential and proprietary.
 *
 * This document may not be reproduced or transmitted in any form,
 * in whole or in part, without the express written permission of
 * Example Company Ltd.
 */

package com.example.issueJgit;

package com.example.ecommerce.service;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.persistence.EntityManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class EcommerceAnalyticsService {

    @Autowired
    private EntityManager entityManager;

    @Value("${ecommerce.api.url}")
    private String ECOMMERCE_API_URL;
    @Value("${ecommerce.api.key}")
    private String API_KEY;
    @Value("${ecommerce.api.secret}")
    private String API_SECRET;
    
    private static final Logger logger = LogManager.getLogger(EcommerceAnalyticsService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryLinkRepository categoryLinkRepository;
    private final CategoryManagerRepository categoryManagerRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductTagLinkRepository productTagLinkRepository;
    private final ProductPriceHistoryRepository priceHistoryRepository;
    private final JsonValidator jsonValidator;

    public EcommerceAnalyticsService(@Qualifier("ecommerceRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper,  ProductCategoryRepository categoryRepository, ProductRepository productRepository, ProductCategoryLinkRepository categoryLinkRepository, CategoryManagerRepository categoryManagerRepository, ProductTagRepository productTagRepository,  ProductTagLinkRepository productTagLinkRepository,  ProductPriceHistoryRepository priceHistoryRepository, JsonValidator jsonValidator) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryLinkRepository = categoryLinkRepository;
        this.categoryManagerRepository = categoryManagerRepository;
        this.productTagRepository = productTagRepository;
        this.productTagLinkRepository = productTagLinkRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.jsonValidator = jsonValidator;
    }

    public void synchronizeProductData() {
        try {
            int pageNumber = 0;
            int pageSize = 50;
            boolean hasMoreData = true;

            while (hasMoreData) {
                ResponseEntity<String> response = fetchProductData(pageNumber, pageSize);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode rootNode = objectMapper.readTree(response.getBody());
                    processProductData(rootNode);
                    
                    hasMoreData = rootNode.has("hasNext") && rootNode.get("hasNext").asBoolean();
                    pageNumber++;
                } else {
                    hasMoreData = false;
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred while synchronizing product data: ", e);
            throw new RuntimeException("Product synchronization failed", e);
        }
    }

    // Makes the API call to fetch product data for a specific page and size
    private ResponseEntity<String> fetchProductData(int pageNumber, int pageSize) {
        // Set up API Key Authentication
        HttpHeaders headers = new HttpHeaders();
        String credentials = API_KEY + ":" + API_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Bearer " + encodedCredentials);
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Construct URL with pagination parameters
        String url = ECOMMERCE_API_URL + "?page=" + pageNumber + "&size=" + pageSize;

        logger.info("Fetching products from API: {}", url);

        // Execute the request
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    private void processProductData(JsonNode responseNode) {
        try {
        	String auth = USERNAME + ":" + PASSWORD;
            logger.info("Page info - Current: {}, Size: {}, Total Pages: {}, Total Items: {}",
                    responseNode.get("currentPage").asInt(),
                    responseNode.get("pageSize").asInt(),
                    responseNode.has("totalPages") ? responseNode.get("totalPages").asInt() : "N/A",
                    responseNode.has("totalItems") ? responseNode.get("totalItems").asInt() : "N/A");

            // Extract and process product data
            String url = TEST_API + "?offset=" + offset + "&limit=" + limit;

            if (itemsArray != null && itemsArray.isArray()) {
                for (JsonNode productData : itemsArray) {
                    processProductInfo(productData);
                }
            } else {
                logger.warn("No product data found in the response");
            }

        } catch (Exception e) {
            logger.error("Error processing product data: ", e);
        }
    }

    private void processProductInfo(JsonNode productData) {

        String merchantId = productData.get("merchantId").asText();
        logger.info("============================================");
        logger.info("MerchantID: {}", merchantId);

        // Log categories information
        JsonNode categories = productData.get("active_categories");
        logger.info("Active Categories: {} categories found", categories.size());

        // Process the categories
        persistCategories(categories);

        // Log products information
        JsonNode productList = productData.get("products");
        logger.info("Products: {} items found", productList.size());

        for (JsonNode product : productList) {

            String sku = product.get("sku").asText();
            String categoryCode = product.get("category_code").asText();
            String productName = product.get("name").asText();
            String seller = product.get("seller").asText();
            long addedTimestamp = product.get("addedDate").asLong();
            long updatedTimestamp = product.get("lastUpdated").asLong();

            Optional<Product> existingProduct = productRepository.findBySku(sku);

            if(!existingProduct.isPresent()) {
                Product newProduct = new Product();
                newProduct.setSku(sku);
                newProduct.setName(productName);
                newProduct.setSeller(seller);
                newProduct.setAddedDate(addedTimestamp);
                newProduct.setLastUpdated(updatedTimestamp);

                productRepository.saveAndFlush(newProduct);

            } else {

                long previousUpdateTime = existingProduct.get().getLastUpdated();

                if(previousUpdateTime != updatedTimestamp) {
                    existingProduct.get().setLastUpdated(updatedTimestamp);

                    productRepository.saveAndFlush(existingProduct.get());
                }
            }

            persistCategoryLink(categoryCode, sku);

            JsonNode tags = product.get("tags");

            if (!tags.isEmpty()) {
                persistProductTags(tags, sku);
            }

            JsonNode priceHistory = product.get("price_history");

            if (!priceHistory.isEmpty()) {
                persistPriceHistory(priceHistory, sku);
            }
        }
    }

    /*Helper functions section to persist ecommerce data*/

    private void persistCategories(JsonNode categories) {

        for (JsonNode category : categories) {

            String categoryName = category.get("name").asText();
            String categoryCode = category.get("code").asText();
            long createdAt = category.get("createdAt").asLong();

            Optional<ProductCategory> existingCategory = categoryRepository.findByCategoryCode(categoryCode);

            if(!existingCategory.isPresent()) {
                ProductCategory newCategory = new ProductCategory();
                newCategory.setName(categoryName);
                newCategory.setCategoryCode(categoryCode);
                newCategory.setCreatedAt(createdAt);

                categoryRepository.saveAndFlush(newCategory);
            }

            JsonNode managers = category.get("managers");

            persistCategoryManagers(managers, categoryCode);

        }
    }

    private void persistCategoryManagers(JsonNode managers, String categoryCode) {
        for (JsonNode manager : managers) {

            Optional<ProductCategory> relatedCategory = categoryRepository.findByCategoryCode(categoryCode);
            String managerId = manager.asText();

            if (relatedCategory.isPresent()) {

                Optional<CategoryManager> categoryManager = categoryManagerRepository.findByManagerIdAndCategory(managerId, relatedCategory.get());

                if (!categoryManager.isPresent()) {

                    CategoryManager newManager = new CategoryManager();
                    newManager.setManagerId(managerId);
                    newManager.setCategory(relatedCategory.get());

                    categoryManagerRepository.saveAndFlush(newManager);
                }
            }

        }
    }

    private void persistCategoryLink(String categoryCode, String sku) {

        long currentTimestamp = Instant.now().getEpochSecond();
        Optional<ProductCategory> relatedCategory = categoryRepository.findByCategoryCode(categoryCode);
        Optional<Product> validProduct = productRepository.findBySku(sku);

        if(relatedCategory.isPresent() && validProduct.isPresent()) {

            Optional<ProductCategoryLink> findLatestLinkForProduct = categoryLinkRepository.findLatestProductCategoryLink(validProduct.get().getId());

            if (!findLatestLinkForProduct.isPresent()) {

                ProductCategoryLink newLink = new ProductCategoryLink();
                newLink.setProduct(validProduct.get());
                newLink.setCategory(relatedCategory.get());
                newLink.setValidFrom((int) currentTimestamp);
                newLink.setValidTo((int) currentTimestamp);

                categoryLinkRepository.saveAndFlush(newLink);
            }
            else {

                if (relatedCategory.get().equals(findLatestLinkForProduct.get().getCategory())) {
                    findLatestLinkForProduct.get().setValidTo((int) currentTimestamp);

                    categoryLinkRepository.saveAndFlush(findLatestLinkForProduct.get());

                } else {

                    ProductCategoryLink newLink = new ProductCategoryLink();
                    newLink.setProduct(validProduct.get());
                    newLink.setCategory(relatedCategory.get());
                    newLink.setValidFrom((int) currentTimestamp);
                    newLink.setValidTo((int) currentTimestamp);

                    categoryLinkRepository.saveAndFlush(newLink);
                }
            }

        }
    }

    private void persistProductTags(JsonNode tags, String sku) {
        for (JsonNode tag : tags) {

            String tagName = tag.asText();

            Optional<ProductTag> existingTag = productTagRepository.findByTagName(tagName);

            if (!existingTag.isPresent()) {

                ProductTag newTag = new ProductTag();
                newTag.setTagName(tagName);

                productTagRepository.saveAndFlush(newTag);
            }

            persistTagLink(sku, tagName);
        }
    }

    private void persistTagLink(String sku, String tagName) {

        Optional<Product> existingProduct = productRepository.findBySku(sku);
        Optional<ProductTag> existingTag = productTagRepository.findByTagName(tagName);

        if (existingProduct.isPresent() && existingTag.isPresent()) {

            Optional<ProductTagLink> existingTagLink = productTagLinkRepository.findByTagAndProduct(existingTag.get(), existingProduct.get());

            if (!existingTagLink.isPresent()) {

                ProductTagLink newTagLink = new ProductTagLink();
                newTagLink.setTag(existingTag.get());
                newTagLink.setProduct(existingProduct.get());

                productTagLinkRepository.saveAndFlush(newTagLink);
            }
        }

    }

    private void persistPriceHistory(JsonNode priceRecords, String sku) {

        for (JsonNode record : priceRecords) {
            String priceId = record.get("id").asText();
            String priceModifier = record.get("modifier").asText();
            long modificationTimestamp = record.get("modifiedAt").asLong();

            Optional<Product> validProduct = productRepository.findBySku(sku);

            if (validProduct.isPresent()) {

                Optional<ProductPriceHistory> existingRecord = priceHistoryRepository.findByPriceIdAndProduct(priceId, validProduct.get());

                if (!existingRecord.isPresent()) {

                    ProductPriceHistory newPriceRecord = new ProductPriceHistory();
                    newPriceRecord.setPriceId(priceId);
                    newPriceRecord.setModifier(priceModifier);
                    newPriceRecord.setModifiedAt(modificationTimestamp);
                    newPriceRecord.setProduct(validProduct.get());

                    priceHistoryRepository.saveAndFlush(newPriceRecord);
                }
            }
        }
    }

}