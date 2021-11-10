/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Liferay Enterprise
 * Subscription License ("License"). You may not use this file except in
 * compliance with the License. You can obtain a copy of the License by
 * contacting Liferay, Inc. See the License for the specific language governing
 * permissions and limitations under the License, including but not limited to
 * distribution rights of the Software.
 *
 *
 *
 */

package com.liferay.search.experiences.internal.blueprint.parameter;

import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserGroupGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserGroupLocalService;
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.search.experiences.blueprint.parameter.DateSXPParameter;
import com.liferay.search.experiences.blueprint.parameter.DoubleSXPParameter;
import com.liferay.search.experiences.blueprint.parameter.FloatSXPParameter;
import com.liferay.search.experiences.blueprint.parameter.IntegerArraySXPParameter;
import com.liferay.search.experiences.blueprint.parameter.IntegerSXPParameter;
import com.liferay.search.experiences.blueprint.parameter.LongArraySXPParameter;
import com.liferay.search.experiences.blueprint.parameter.LongSXPParameter;
import com.liferay.search.experiences.blueprint.parameter.SXPParameter;
import com.liferay.search.experiences.blueprint.parameter.StringArraySXPParameter;
import com.liferay.search.experiences.blueprint.parameter.StringSXPParameter;
import com.liferay.search.experiences.internal.blueprint.parameter.contributor.ContextSXPParameterContributor;
import com.liferay.search.experiences.internal.blueprint.parameter.contributor.IpstackSXPParameterContributor;
import com.liferay.search.experiences.internal.blueprint.parameter.contributor.OpenWeatherMapSXPParameterContributor;
import com.liferay.search.experiences.internal.blueprint.parameter.contributor.SXPParameterContributor;
import com.liferay.search.experiences.internal.blueprint.parameter.contributor.TimeSXPParameterContributor;
import com.liferay.search.experiences.internal.blueprint.parameter.contributor.UserSXPParameterContributor;
import com.liferay.search.experiences.rest.dto.v1_0.Configuration;
import com.liferay.search.experiences.rest.dto.v1_0.Parameter;
import com.liferay.search.experiences.rest.dto.v1_0.SXPBlueprint;
import com.liferay.search.experiences.rest.dto.v1_0.ValueDefinition;
import com.liferay.segments.SegmentsEntryRetriever;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Petteri Karttunen
 */
@Component(immediate = true, service = SXPParameterDataCreator.class)
public class SXPParameterDataCreator {

	public SXPParameterData create(
		SearchContext searchContext, SXPBlueprint sxpBlueprint) {

		Set<SXPParameter> sxpParameters = new LinkedHashSet<>();

		String keywords = _addKeywordsSXPParameters(
			searchContext, sxpParameters);

		Configuration configuration = sxpBlueprint.getConfiguration();

		Map<String, Parameter> parameters = configuration.getParameters();

		if (!MapUtil.isEmpty(parameters)) {
			_addSXPParameters(parameters, searchContext, sxpParameters);
		}

		_contribute(searchContext, sxpBlueprint, sxpParameters);

		return new SXPParameterData(keywords, sxpParameters);
	}

	@Activate
	protected void activate() {
		_sxpParameterContributors = new SXPParameterContributor[] {
			new ContextSXPParameterContributor(
				_groupLocalService, _language, _layoutLocalService),
			new IpstackSXPParameterContributor(_configurationProvider),
			new OpenWeatherMapSXPParameterContributor(_configurationProvider),
			new TimeSXPParameterContributor(_language, _userLocalService),
			new UserSXPParameterContributor(
				_language, _roleLocalService, _segmentsEntryRetriever,
				_userGroupGroupRoleLocalService, _userGroupLocalService,
				_userGroupRoleLocalService, _userLocalService)
		};
	}

	private String _addKeywordsSXPParameters(
		SearchContext searchContext, Set<SXPParameter> sxpParameters) {

		String keywords = GetterUtil.getString(searchContext.getKeywords());

		sxpParameters.add(
			new StringSXPParameter("keywords.raw", true, keywords));

		if ((StringUtil.count(keywords, CharPool.QUOTE) % 2) != 0) {
			keywords = StringUtil.replace(
				keywords, CharPool.QUOTE, StringPool.BLANK);
		}

		keywords = keywords.replaceAll("/", "&#8725;");
		keywords = keywords.replaceAll("\"", "\\\\\"");
		keywords = keywords.replaceAll("\\[", "&#91;");
		keywords = keywords.replaceAll("\\\\", "&#92;");
		keywords = keywords.replaceAll("\\]", "&#93;");

		sxpParameters.add(new StringSXPParameter("keywords", true, keywords));

		return keywords;
	}

	private void _addSXPParameter(
		String name, Parameter parameter, SearchContext searchContext,
		Set<SXPParameter> sxpParameters) {

		Object object = searchContext.getAttribute(name);

		if (object == null) {
			return;
		}

		SXPParameter sxpParameter = _getSXPParameter(
			name, object, searchContext, parameter.getValueDefinition());

		if (sxpParameter == null) {
			return;
		}

		sxpParameters.add(sxpParameter);
	}

	private void _addSXPParameters(
		Map<String, Parameter> parameters, SearchContext searchContext,
		Set<SXPParameter> sxpParameters) {

		parameters.forEach(
			(name, parameter) -> _addSXPParameter(
				name, parameter, searchContext, sxpParameters));
	}

	private void _contribute(
		SearchContext searchContext, SXPBlueprint sxpBlueprint,
		Set<SXPParameter> sxpParameters) {

		if (ArrayUtil.isEmpty(_sxpParameterContributors)) {
			return;
		}

		for (SXPParameterContributor sxpParameterContributor :
				_sxpParameterContributors) {

			sxpParameterContributor.contribute(
				searchContext, sxpBlueprint, sxpParameters);
		}
	}

	private Double _fit(Double maxValue, Double minValue, Double value) {
		if ((minValue != null) && (value < minValue)) {
			return minValue;
		}

		if ((maxValue != null) && (value > maxValue)) {
			return maxValue;
		}

		return value;
	}

	private Float _fit(Float maxValue, Float minValue, Float value) {
		if ((minValue != null) && (value < minValue)) {
			return minValue;
		}

		if ((maxValue != null) && (value > maxValue)) {
			return maxValue;
		}

		return value;
	}

	private Integer _fit(Integer maxValue, Integer minValue, Integer value) {
		if ((minValue != null) && (value < minValue)) {
			return minValue;
		}

		if ((maxValue != null) && (value > maxValue)) {
			return maxValue;
		}

		return value;
	}

	private Long _fit(Long maxValue, Long minValue, Long value) {
		if ((minValue != null) && (value < minValue)) {
			return minValue;
		}

		if ((maxValue != null) && (value > maxValue)) {
			return maxValue;
		}

		return value;
	}

	private SXPParameter _getDateSXPParameter(
		String name, Object object, TimeZone timeZone,
		ValueDefinition valueDefinition) {

		String value = _getString(null, object);

		if (value == null) {
			return null;
		}

		LocalDate localDate = LocalDate.parse(
			value, DateTimeFormatter.ofPattern(valueDefinition.getFormat()));

		Calendar calendar = GregorianCalendar.from(
			localDate.atStartOfDay(timeZone.toZoneId()));

		Date date = calendar.getTime();

		if (date == null) {
			return null;
		}

		return new DateSXPParameter(name, true, date);
	}

	private Double _getDouble(Double defaultValue, Object object) {
		if (object != null) {
			return GetterUtil.getDouble(object);
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private SXPParameter _getDoubleSXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		Double value = _getDouble(
			valueDefinition.getDefaultValueDouble(), object);

		if (value == null) {
			return null;
		}

		return new DoubleSXPParameter(
			name, true,
			_fit(
				valueDefinition.getMaxValueDouble(),
				valueDefinition.getMinValueDouble(), value));
	}

	private Float _getFloat(Float defaultValue, Object object) {
		if (object != null) {
			return GetterUtil.getFloat(object);
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private SXPParameter _getFloatSXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		Float value = _getFloat(valueDefinition.getDefaultValueFloat(), object);

		if (value == null) {
			return null;
		}

		return new FloatSXPParameter(
			name, true,
			_fit(
				valueDefinition.getMaxValueFloat(),
				valueDefinition.getMinValueFloat(), value));
	}

	private Integer _getInteger(Integer defaultValue, Object object) {
		if (object != null) {
			return GetterUtil.getInteger(object);
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private Integer[] _getIntegerArray(Integer[] defaultValue, Object object) {
		if (object != null) {
			return ArrayUtil.toArray(GetterUtil.getIntegerValues(object));
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private SXPParameter _getIntegerArraySXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		Integer[] value = _getIntegerArray(
			valueDefinition.getDefaultValuesIntegerArray(), object);

		if (ArrayUtil.isEmpty(value)) {
			return null;
		}

		return new IntegerArraySXPParameter(name, true, value);
	}

	private SXPParameter _getIntegerSXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		Integer value = _getInteger(
			valueDefinition.getDefaultValueInteger(), object);

		if (value == null) {
			return null;
		}

		return new IntegerSXPParameter(
			name, true,
			_fit(
				valueDefinition.getMaxValueInteger(),
				valueDefinition.getMinValueInteger(), value));
	}

	private Long _getLong(Long defaultValue, Object object) {
		if (object != null) {
			return GetterUtil.getLong(object);
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private Long[] _getLongArray(Long[] defaultValue, Object object) {
		if (object != null) {
			return ArrayUtil.toArray(GetterUtil.getLongValues(object));
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private SXPParameter _getLongArraySXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		Long[] value = _getLongArray(
			valueDefinition.getDefaultValuesLongArray(), object);

		if (ArrayUtil.isEmpty(value)) {
			return null;
		}

		return new LongArraySXPParameter(name, true, value);
	}

	private SXPParameter _getLongSXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		Long value = _getLong(valueDefinition.getDefaultValueLong(), object);

		if (value == null) {
			return null;
		}

		return new LongSXPParameter(
			name, true,
			_fit(
				valueDefinition.getMaxValueLong(),
				valueDefinition.getMinValueLong(), value));
	}

	private String _getString(String defaultValue, Object object) {
		if (object != null) {
			return GetterUtil.getString(object);
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private String[] _getStringArray(String[] defaultValue, Object object) {
		if (object != null) {
			return GetterUtil.getStringValues(object);
		}

		if (defaultValue != null) {
			return defaultValue;
		}

		return null;
	}

	private SXPParameter _getStringArraySXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		String[] value = _getStringArray(
			valueDefinition.getDefaultValuesStringArray(), object);

		if (ArrayUtil.isEmpty(value)) {
			return null;
		}

		return new StringArraySXPParameter(name, true, value);
	}

	private SXPParameter _getStringSXPParameter(
		String name, Object object, ValueDefinition valueDefinition) {

		String value = _getString(
			valueDefinition.getDefaultValueString(), object);

		if (value == null) {
			return null;
		}

		return new StringSXPParameter(name, true, value);
	}

	private SXPParameter _getSXPParameter(
		String name, Object object, SearchContext searchContext,
		ValueDefinition valueDefinition) {

		ValueDefinition.Type type = valueDefinition.getType();

		if (type.equals(ValueDefinition.Type.DATE)) {
			return _getDateSXPParameter(
				name, object, searchContext.getTimeZone(), valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.DOUBLE)) {
			return _getDoubleSXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.FLOAT)) {
			return _getFloatSXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.INTEGER)) {
			return _getIntegerSXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.INTEGER_ARRAY)) {
			return _getIntegerArraySXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.LONG)) {
			return _getLongSXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.LONG_ARRAY)) {
			return _getLongArraySXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.STRING)) {
			return _getStringSXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.STRING_ARRAY)) {
			return _getStringArraySXPParameter(name, object, valueDefinition);
		}
		else if (type.equals(ValueDefinition.Type.TIME_RANGE)) {
			return _getTimeRangeSXPParameter(name, object);
		}

		throw new IllegalArgumentException();
	}

	private SXPParameter _getTimeRangeSXPParameter(String name, Object object) {
		String value = _getString(null, object);

		if (value == null) {
			return null;
		}

		Calendar calendar = Calendar.getInstance();

		if (value.equals("last-day")) {
			calendar.add(Calendar.DAY_OF_MONTH, -1);
		}
		else if (value.equals("last-hour")) {
			calendar.add(Calendar.HOUR_OF_DAY, -1);
		}
		else if (value.equals("last-month")) {
			calendar.add(Calendar.MONTH, -1);
		}
		else if (value.equals("last-week")) {
			calendar.add(Calendar.WEEK_OF_MONTH, -1);
		}
		else if (value.equals("last-year")) {
			calendar.add(Calendar.YEAR, -1);
		}

		return new DateSXPParameter(name, true, calendar.getTime());
	}

	@Reference
	private ConfigurationProvider _configurationProvider;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private Language _language;

	@Reference
	private LayoutLocalService _layoutLocalService;

	@Reference
	private RoleLocalService _roleLocalService;

	@Reference
	private SegmentsEntryRetriever _segmentsEntryRetriever;

	private SXPParameterContributor[] _sxpParameterContributors;

	@Reference
	private UserGroupGroupRoleLocalService _userGroupGroupRoleLocalService;

	@Reference
	private UserGroupLocalService _userGroupLocalService;

	@Reference
	private UserGroupRoleLocalService _userGroupRoleLocalService;

	@Reference
	private UserLocalService _userLocalService;

}