/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.mapping.form.field.type.internal.select;

import com.liferay.dynamic.data.mapping.form.field.type.BaseDDMFormFieldTypeSettingsTestCase;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldOptionsFactory;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldOptions;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.render.DDMFormFieldRenderingContext;
import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.ResourceBundleLoader;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Matchers;
import org.mockito.Mock;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.mockito.expectation.PowerMockitoStubber;
import org.powermock.api.support.membermodification.MemberMatcher;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * @author Marcellus Tavares
 */
@PrepareForTest({PortalClassLoaderUtil.class, ResourceBundleUtil.class})
@RunWith(PowerMockRunner.class)
public class SelectDDMFormFieldTemplateContextContributorTest
	extends BaseDDMFormFieldTypeSettingsTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		setUpJSONFactory();
	}

	@Test
	public void testGetMultiple1() {
		String fieldName = "field";

		DDMFormField ddmFormField = new DDMFormField(fieldName, "select");

		ddmFormField.setProperty("multiple", "true");

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		ddmFormFieldRenderingContext.setProperty("changedProperties", null);

		Assert.assertEquals(
			true,
			_selectDDMFormFieldTemplateContextContributor.getMultiple(
				ddmFormField, ddmFormFieldRenderingContext));
	}

	@Test
	public void testGetMultiple2() {
		DDMFormField ddmFormField = new DDMFormField("field", "select");

		ddmFormField.setProperty("multiple", "true");

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		Map<String, Object> changedProperties = new HashMap<>();

		ddmFormFieldRenderingContext.setProperty(
			"changedProperties", changedProperties);

		Assert.assertEquals(
			true,
			_selectDDMFormFieldTemplateContextContributor.getMultiple(
				ddmFormField, ddmFormFieldRenderingContext));
	}

	@Test
	public void testGetMultiple3() {
		DDMFormField ddmFormField = new DDMFormField("field", "select");

		ddmFormField.setProperty("multiple", "false");

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		Map<String, Object> changedProperties =
			HashMapBuilder.<String, Object>put(
				"multiple", true
			).build();

		ddmFormFieldRenderingContext.setProperty(
			"changedProperties", changedProperties);

		Assert.assertEquals(
			true,
			_selectDDMFormFieldTemplateContextContributor.getMultiple(
				ddmFormField, ddmFormFieldRenderingContext));
	}

	@Test
	public void testGetOptions() {
		List<Object> expectedOptions = new ArrayList<>();

		expectedOptions.add(createOption("Label 1", "value 1"));
		expectedOptions.add(createOption("Label 2", "value 2"));
		expectedOptions.add(createOption("Label 3", "value 3"));

		DDMFormFieldOptions ddmFormFieldOptions = createDDMFormFieldOptions();

		List<Object> actualOptions = getActualOptions(
			ddmFormFieldOptions, LocaleUtil.US);

		Assert.assertEquals(expectedOptions, actualOptions);
	}

	@Test
	public void testGetParameters1() throws Exception {
		DDMFormField ddmFormField = new DDMFormField("field", "select");

		ddmFormField.setProperty("dataSourceType", "data-provider");

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		ddmFormFieldRenderingContext.setLocale(LocaleUtil.US);
		ddmFormFieldRenderingContext.setValue("[\"value 1\"]");

		setUpDDMFormFieldOptionsFactory(
			ddmFormField, ddmFormFieldRenderingContext);

		SelectDDMFormFieldTemplateContextContributor spy = createSpy();

		Map<String, Object> parameters = spy.getParameters(
			ddmFormField, ddmFormFieldRenderingContext);

		Assert.assertTrue(parameters.containsKey("dataSourceType"));
		Assert.assertEquals("data-provider", parameters.get("dataSourceType"));

		Assert.assertTrue(parameters.containsKey("multiple"));
		Assert.assertEquals(false, parameters.get("multiple"));

		Assert.assertTrue(parameters.containsKey("options"));

		List<Object> options = (List<Object>)parameters.get("options");

		Assert.assertEquals(options.toString(), 3, options.size());

		Map<String, String> optionMap = (Map<String, String>)options.get(0);

		Assert.assertEquals("Label 1", optionMap.get("label"));
		Assert.assertEquals("value 1", optionMap.get("value"));

		optionMap = (Map<String, String>)options.get(1);

		Assert.assertEquals("Label 2", optionMap.get("label"));
		Assert.assertEquals("value 2", optionMap.get("value"));

		optionMap = (Map<String, String>)options.get(2);

		Assert.assertEquals("Label 3", optionMap.get("label"));
		Assert.assertEquals("value 3", optionMap.get("value"));

		List<String> value = (List<String>)parameters.get("value");

		Assert.assertEquals(value.toString(), 1, value.size());
		Assert.assertTrue(value.toString(), value.contains("value 1"));
	}

	@Test
	public void testGetParameters2() throws Exception {
		DDMFormField ddmFormField = new DDMFormField("field", "select");

		ddmFormField.setProperty("dataSourceType", "manual");
		ddmFormField.setMultiple(true);

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		ddmFormFieldRenderingContext.setLocale(LocaleUtil.US);

		LocalizedValue predefinedValue = new LocalizedValue();

		predefinedValue.setDefaultLocale(LocaleUtil.US);
		predefinedValue.addString(LocaleUtil.US, "[\"value 2\",\"value 3\"]");

		ddmFormField.setPredefinedValue(predefinedValue);

		setUpDDMFormFieldOptionsFactory(
			ddmFormField, ddmFormFieldRenderingContext);

		SelectDDMFormFieldTemplateContextContributor spy = createSpy();

		Map<String, Object> parameters = spy.getParameters(
			ddmFormField, ddmFormFieldRenderingContext);

		Assert.assertTrue(parameters.containsKey("dataSourceType"));
		Assert.assertEquals("manual", parameters.get("dataSourceType"));

		Assert.assertTrue(parameters.containsKey("multiple"));
		Assert.assertEquals(true, parameters.get("multiple"));

		Assert.assertTrue(parameters.containsKey("options"));

		List<Object> options = (List<Object>)parameters.get("options");

		Assert.assertEquals(options.toString(), 3, options.size());

		Map<String, String> optionMap = (Map<String, String>)options.get(0);

		Assert.assertEquals("Label 1", optionMap.get("label"));
		Assert.assertEquals("value 1", optionMap.get("value"));

		optionMap = (Map<String, String>)options.get(1);

		Assert.assertEquals("Label 2", optionMap.get("label"));
		Assert.assertEquals("value 2", optionMap.get("value"));

		optionMap = (Map<String, String>)options.get(2);

		Assert.assertEquals("Label 3", optionMap.get("label"));
		Assert.assertEquals("value 3", optionMap.get("value"));

		List<String> predefinedValueParameter = (List<String>)parameters.get(
			"predefinedValue");

		Assert.assertEquals(
			predefinedValueParameter.toString(), 2,
			predefinedValueParameter.size());
		Assert.assertTrue(
			predefinedValueParameter.toString(),
			predefinedValueParameter.contains("value 2"));
		Assert.assertTrue(
			predefinedValueParameter.toString(),
			predefinedValueParameter.contains("value 3"));
	}

	@Test
	public void testGetValue1() {
		List<String> values =
			_selectDDMFormFieldTemplateContextContributor.getValue(
				"[\"a\",\"b\"]");

		Assert.assertTrue(values.toString(), values.contains("a"));
		Assert.assertTrue(values.toString(), values.contains("b"));
	}

	@Test
	public void testGetValue2() {
		List<String> values =
			_selectDDMFormFieldTemplateContextContributor.getValue(
				"INVALID_JSON");

		Assert.assertTrue(values.toString(), values.isEmpty());
	}

	protected DDMFormFieldOptions createDDMFormFieldOptions() {
		DDMFormFieldOptions ddmFormFieldOptions = new DDMFormFieldOptions();

		ddmFormFieldOptions.addOptionLabel("value 1", LocaleUtil.US, "Label 1");
		ddmFormFieldOptions.addOptionLabel("value 2", LocaleUtil.US, "Label 2");
		ddmFormFieldOptions.addOptionLabel("value 3", LocaleUtil.US, "Label 3");

		return ddmFormFieldOptions;
	}

	protected Map<String, String> createOption(String label, String value) {
		return HashMapBuilder.put(
			"label", label
		).put(
			"value", value
		).build();
	}

	protected SelectDDMFormFieldTemplateContextContributor createSpy() {
		SelectDDMFormFieldTemplateContextContributor spy = PowerMockito.spy(
			_selectDDMFormFieldTemplateContextContributor);

		PowerMockitoStubber powerMockitoStubber = PowerMockito.doReturn(
			LocaleUtil.US);

		powerMockitoStubber.when(
			spy
		).getDisplayLocale(
			Matchers.any(HttpServletRequest.class)
		);

		powerMockitoStubber = PowerMockito.doReturn(_resourceBundle);

		powerMockitoStubber.when(
			spy
		).getResourceBundle(
			Matchers.any(Locale.class)
		);

		return spy;
	}

	protected List<Object> getActualOptions(
		DDMFormFieldOptions ddmFormFieldOptions, Locale locale) {

		DDMFormFieldRenderingContext ddmFormFieldRenderingContext =
			new DDMFormFieldRenderingContext();

		return _selectDDMFormFieldTemplateContextContributor.getOptions(
			ddmFormFieldOptions, locale, ddmFormFieldRenderingContext);
	}

	protected void setUpDDMFormFieldOptionsFactory(
			DDMFormField ddmFormField,
			DDMFormFieldRenderingContext ddmFormFieldRenderingContext)
		throws Exception {

		MemberMatcher.field(
			SelectDDMFormFieldTemplateContextContributor.class,
			"ddmFormFieldOptionsFactory"
		).set(
			_selectDDMFormFieldTemplateContextContributor,
			_ddmFormFieldOptionsFactory
		);

		DDMFormFieldOptions ddmFormFieldOptions = createDDMFormFieldOptions();

		PowerMockito.when(
			_ddmFormFieldOptionsFactory.create(
				ddmFormField, ddmFormFieldRenderingContext)
		).thenReturn(
			ddmFormFieldOptions
		);
	}

	protected void setUpJSONFactory() throws Exception {
		MemberMatcher.field(
			SelectDDMFormFieldTemplateContextContributor.class, "jsonFactory"
		).set(
			_selectDDMFormFieldTemplateContextContributor, _jsonFactory
		);
	}

	protected JSONArray toJSONArray(String... strings) {
		JSONArray jsonArray = _jsonFactory.createJSONArray();

		for (String string : strings) {
			jsonArray.put(string);
		}

		return jsonArray;
	}

	@Mock
	private DDMFormFieldOptionsFactory _ddmFormFieldOptionsFactory;

	private final JSONFactory _jsonFactory = new JSONFactoryImpl();

	@Mock
	private ResourceBundle _resourceBundle;

	@Mock
	private ResourceBundleLoader _resourceBundleLoader;

	private final SelectDDMFormFieldTemplateContextContributor
		_selectDDMFormFieldTemplateContextContributor =
			new SelectDDMFormFieldTemplateContextContributor();

}