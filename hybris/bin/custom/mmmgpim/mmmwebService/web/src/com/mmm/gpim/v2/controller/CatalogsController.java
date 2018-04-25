/*
 * [y] hybris Platform
 *
 * Copyright (c) 2017 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.mmm.gpim.v2.controller;

import de.hybris.platform.commercefacades.catalog.CatalogFacade;
import de.hybris.platform.commercefacades.catalog.CatalogOption;
import de.hybris.platform.commercefacades.catalog.PageOption;
import de.hybris.platform.commercefacades.catalog.data.CatalogData;
import de.hybris.platform.commercefacades.catalog.data.CatalogVersionData;
import de.hybris.platform.commercefacades.catalog.data.CatalogsData;
import de.hybris.platform.commercefacades.catalog.data.CategoryHierarchyData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.commercewebservicescommons.dto.catalog.CatalogListWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.catalog.CatalogVersionWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.catalog.CatalogWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.catalog.CategoryHierarchyWsDTO;
import de.hybris.platform.commercewebservicescommons.errors.exceptions.RequestParameterException;
import de.hybris.platform.webservicescommons.errors.exceptions.WebserviceValidationException;
import de.hybris.platform.webservicescommons.mapping.DataMapper;
import de.hybris.platform.webservicescommons.mapping.FieldSetBuilder;
import de.hybris.platform.webservicescommons.mapping.impl.FieldSetBuilderContext;
import org.springframework.security.access.annotation.Secured;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.mmm.gpim.facades.classification.MMMClassificationMoveFacade;
import com.mmm.gpim.pojo.Data;
import com.mmm.gpim.pojo.Entries;
import com.mmm.gpim.pojo.Entry;
import com.mmm.gpim.pojo.MMMResult;
import com.mmm.gpim.pojo.Value;


/**
 * @pathparam catalogId Catalog identifier
 * @pathparam catalogVersionId Catalog version identifier
 * @pathparam categoryId Category identifier
 */
@Controller
@RequestMapping(value = "/{baseSiteId}/catalogs")
public class CatalogsController extends BaseController {
    private static final Set<CatalogOption> OPTIONS;

    static {
        OPTIONS = getOptions();
    }

    @Resource(name = "cwsCatalogFacade")
    private CatalogFacade catalogFacade;
    @Resource(name = "fieldSetBuilder")
    private FieldSetBuilder fieldSetBuilder;
    @Resource(name = "mmmClassificationMoveFacade")
    private MMMClassificationMoveFacade mmmClassificationMoveFacade;


    /**
     * Returns all catalogs with versions defined for the base store.
     *
     * @return All catalogs defined for the base store.
     * @queryparam fields Response configuration (list of fields, which should be returned in response)
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public CatalogListWsDTO getCatalogs(@RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields) {
        final List<CatalogData> catalogDataList = catalogFacade.getAllProductCatalogsForCurrentSite(OPTIONS);
        final CatalogsData catalogsData = new CatalogsData();
        catalogsData.setCatalogs(catalogDataList);

        final FieldSetBuilderContext context = new FieldSetBuilderContext();
        context.setRecurrencyLevel(countRecurrecyLevel(catalogDataList));
        final Set<String> fieldSet = fieldSetBuilder.createFieldSet(CatalogListWsDTO.class, DataMapper.FIELD_PREFIX, fields,
                context);

        return getDataMapper().map(catalogsData, CatalogListWsDTO.class, fieldSet);
    }


    /**
     * Returns a information about a catalog based on its ID, along with versions defined for the current base store.
     *
     * @return Catalog structure
     * @queryparam fields Response configuration (list of fields, which should be returned in response)
     */
    @RequestMapping(value = "/{catalogId}", method = RequestMethod.GET)
    @ResponseBody
    public CatalogWsDTO getCatalog(@PathVariable final String catalogId,
                                   @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields) {
        final CatalogData catalogData = catalogFacade.getProductCatalogForCurrentSite(catalogId, OPTIONS);

        final FieldSetBuilderContext context = new FieldSetBuilderContext();
        context.setRecurrencyLevel(countRecurrencyForCatalogData(catalogData));
        final Set<String> fieldSet = fieldSetBuilder.createFieldSet(CatalogWsDTO.class, DataMapper.FIELD_PREFIX, fields, context);

        return getDataMapper().map(catalogData, CatalogWsDTO.class, fieldSet);
    }

    /**
     * Returns information about catalog version that exists for the current base store.
     *
     * @return Information about catalog version
     * @queryparam fields Response configuration (list of fields, which should be returned in response)
     */
    @RequestMapping(value = "/{catalogId}/{catalogVersionId}", method = RequestMethod.GET)
    @ResponseBody
    public CatalogVersionWsDTO getCatalogVersion(@PathVariable final String catalogId,
                                                 @PathVariable final String catalogVersionId, @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields) {
        final CatalogVersionData catalogVersionData = catalogFacade.getProductCatalogVersionForTheCurrentSite(catalogId,
                catalogVersionId, OPTIONS);

        final FieldSetBuilderContext context = new FieldSetBuilderContext();
        context.setRecurrencyLevel(countRecurrencyForCatalogVersionData(catalogVersionData));
        final Set<String> fieldSet = fieldSetBuilder.createFieldSet(CatalogVersionWsDTO.class, DataMapper.FIELD_PREFIX, fields,
                context);

        return getDataMapper().map(catalogVersionData, CatalogVersionWsDTO.class, fieldSet);
    }

    /**
     * Returns information about category that exists in a catalog version available for the current base store.
     *
     * @return Information about category
     * @queryparam currentPage The current result page requested.
     * @queryparam pageSize The number of results returned per page.
     * @queryparam fields Response configuration (list of fields, which should be returned in response)
     */
    @RequestMapping(value = "/{catalogId}/{catalogVersionId}/categories/{categoryId}", method = RequestMethod.GET)
    @ResponseBody
    public CategoryHierarchyWsDTO getCategories(@PathVariable final String catalogId, @PathVariable final String catalogVersionId,
                                                @PathVariable final String categoryId, @RequestParam(defaultValue = "DEFAULT") final String fields) {
        final PageOption page = PageOption.createForPageNumberAndPageSize(0, 10);
        final CategoryHierarchyData categoryHierarchyData = catalogFacade.getCategoryById(catalogId, catalogVersionId, categoryId,
                page, OPTIONS);

        final FieldSetBuilderContext context = new FieldSetBuilderContext();
        context.setRecurrencyLevel(countRecurrencyForCategoryHierarchyData(1, categoryHierarchyData));
        final Set<String> fieldSet = fieldSetBuilder.createFieldSet(CategoryHierarchyWsDTO.class, DataMapper.FIELD_PREFIX, fields,
                context);

        return getDataMapper().map(categoryHierarchyData, CategoryHierarchyWsDTO.class, fieldSet);
    }


	/*
     * @RequestMapping(value = "/classAttribute", method = RequestMethod.POST)
	 *
	 * @ResponseStatus(value = HttpStatus.CREATED) public void registerUser(@RequestParam(required = false) final String
	 * sourceclassificationClass,
	 *
	 * @RequestParam(required = false) final String attribute,
	 *
	 * @RequestParam(required = false) final String targetClassficationClass, final HttpServletRequest request) throws
	 * DuplicateUidException, RequestParameterException, WebserviceValidationException {
	 * mmmClassificationMoveFacade.createClassAttributeAssignment(targetClassficationClass, attribute,
	 * sourceclassificationClass); }
	 */


	/*
	 * @RequestMapping(value = "/productMove", method = RequestMethod.POST)
	 * 
	 * @ResponseStatus(value = HttpStatus.CREATED) public void productMove(@RequestParam(required = false) final String
	 * sourceClass,
	 * 
	 * @RequestParam(required = false) final String attribute, @RequestParam(required = false) final String productCode,
	 * 
	 * @RequestParam(required = false) final String targetClass, final HttpServletRequest request) throws
	 * DuplicateUidException, RequestParameterException, WebserviceValidationException {
	 * //createClassAttributeAssignment(classificationClass, attribute, attributeType);
	 * threeMSampleDAOImpl.productMoveOnly(attribute, sourceClass, targetClass, productCode); }
	 */

    @RequestMapping(value = "/productMove", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.CREATED)
    public void productMove(@RequestParam(required = false) final String sourceClass,
                            @RequestParam(required = false) final String attribute, @RequestParam(required = false) final String productCode,
                            @RequestParam(required = false) final String targetClass, final HttpServletRequest request)
            throws DuplicateUidException, RequestParameterException, WebserviceValidationException {
        //createClassAttributeAssignment(classificationClass, attribute, attributeType);
        mmmClassificationMoveFacade.moveProductFacade(attribute, sourceClass, targetClass, productCode);
        //threeMSampleDAOImpl.productMoveOnly(attribute, sourceClass, targetClass, productCode);
    }


    /*
     * @RequestMapping(value = "/classAttributeJson", method = RequestMethod.POST)
     *
     * @Consumes(MediaType.APPLICATION_JSON)
     *
     * @ResponseBody public void ClassificationAttributeMove(@RequestBody final ClassificationJson classificationJson)
     * throws DuplicateUidException, RequestParameterException, WebserviceValidationException {
     *
     * System.out.println("classificationJson" + classificationJson.getAttribute() + "====" +
     * classificationJson.getSourceclassificationClass());
     * mmmClassificationMoveFacade.createClassAttribute(classificationJson.getTargetClassficationClass(),
     * classificationJson.getAttribute(), classificationJson.getSourceclassificationClass()); }
     */
    @Secured(
            {"ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_TRUSTED_CLIENT", "ROLE_CUSTOMERMANAGERGROUP"})
    @RequestMapping(value = "/{catalogId}/{catalogVersionId}/classAttribute", method = RequestMethod.POST)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseBody
    public MMMResult ClassificationAttributeMove(@PathVariable final String catalogId,
                                                 @PathVariable final String catalogVersionId, @RequestBody final Entries entries) throws DuplicateUidException,
            RequestParameterException, WebserviceValidationException {
        final MMMResult mmmresult = new MMMResult();
        final List<Data> dataList = new ArrayList();
        for (final Entry entry : entries.getEntries()) {

            final Collection<Value> values = entry.getValues();
            for (final Value value : values) {
                final Data data = new Data();
                final Collection<String> classificationAttributes = value.getClassificationAttributes();
                for (final String classificationAttribute : classificationAttributes) {
                    final String message = mmmClassificationMoveFacade.createClassAttributeAssignment(value.getToClassification(),
                            classificationAttribute, value.getFromClassification(), catalogId, catalogVersionId);
                    data.setCode(value.getCode());
                    data.setClassificationAttribute(classificationAttribute);
                    data.setFromClassification(value.getFromClassification());
                    data.setToClassification(value.getToClassification());
                    data.setMessage(message);
                    if (message.equalsIgnoreCase("success")) {
                        data.setStatus("Sucess");
                    } else {
                        data.setStatus("Failed");
                    }
                    dataList.add(data);
                }
            }

        }
        mmmresult.setData(dataList);
        mmmresult.setStatus("Success");
        return mmmresult;
    }


    protected static Set<CatalogOption> getOptions() {
        final Set<CatalogOption> opts = new HashSet<>();
        opts.add(CatalogOption.BASIC);
        opts.add(CatalogOption.CATEGORIES);
        opts.add(CatalogOption.SUBCATEGORIES);
        return opts;
    }

    protected int countRecurrecyLevel(final List<CatalogData> catalogDataList) {
        int recurrencyLevel = 1;
        int value;
        for (final CatalogData catalog : catalogDataList) {
            value = countRecurrencyForCatalogData(catalog);
            if (value > recurrencyLevel) {
                recurrencyLevel = value;
            }
        }
        return recurrencyLevel;
    }

    protected int countRecurrencyForCatalogData(final CatalogData catalog) {
        int retValue = 1;
        int value;
        for (final CatalogVersionData version : catalog.getCatalogVersions()) {
            value = countRecurrencyForCatalogVersionData(version);
            if (value > retValue) {
                retValue = value;
            }
        }
        return retValue;
    }

    protected int countRecurrencyForCatalogVersionData(final CatalogVersionData catalogVersion) {
        int retValue = 1;
        int value;
        for (final CategoryHierarchyData hierarchy : catalogVersion.getCategoriesHierarchyData()) {
            value = countRecurrencyForCategoryHierarchyData(1, hierarchy);
            if (value > retValue) {
                retValue = value;
            }
        }
        return retValue;
    }

    protected int countRecurrencyForCategoryHierarchyData(final int currentValue, final CategoryHierarchyData hierarchy) {
        int calculatedValue = currentValue + 1;
        int subcategoryRecurrencyValue;
        for (final CategoryHierarchyData subcategory : hierarchy.getSubcategories()) {
            subcategoryRecurrencyValue = countRecurrencyForCategoryHierarchyData(calculatedValue, subcategory);
            if (subcategoryRecurrencyValue > calculatedValue) {
                calculatedValue = subcategoryRecurrencyValue;
            }
        }
        return calculatedValue;
    }


}
