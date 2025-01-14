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

package com.liferay.portal.workflow.metrics.rest.internal.resource.v1_0;

import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.odata.entity.EntityModel;
import com.liferay.portal.search.aggregation.AggregationResult;
import com.liferay.portal.search.aggregation.Aggregations;
import com.liferay.portal.search.aggregation.bucket.Bucket;
import com.liferay.portal.search.aggregation.bucket.FilterAggregation;
import com.liferay.portal.search.aggregation.bucket.TermsAggregation;
import com.liferay.portal.search.aggregation.bucket.TermsAggregationResult;
import com.liferay.portal.search.aggregation.metrics.CardinalityAggregationResult;
import com.liferay.portal.search.aggregation.metrics.ValueCountAggregationResult;
import com.liferay.portal.search.engine.adapter.search.SearchRequestExecutor;
import com.liferay.portal.search.engine.adapter.search.SearchSearchRequest;
import com.liferay.portal.search.engine.adapter.search.SearchSearchResponse;
import com.liferay.portal.search.query.BooleanQuery;
import com.liferay.portal.search.query.Queries;
import com.liferay.portal.search.query.TermsQuery;
import com.liferay.portal.search.sort.FieldSort;
import com.liferay.portal.search.sort.SortOrder;
import com.liferay.portal.search.sort.Sorts;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.vulcan.pagination.Pagination;
import com.liferay.portal.vulcan.resource.EntityModelResource;
import com.liferay.portal.workflow.metrics.rest.dto.v1_0.AssigneeUser;
import com.liferay.portal.workflow.metrics.rest.internal.odata.entity.v1_0.AssigneeUserEntityModel;
import com.liferay.portal.workflow.metrics.rest.internal.resource.helper.ResourceHelper;
import com.liferay.portal.workflow.metrics.rest.resource.v1_0.AssigneeUserResource;
import com.liferay.portal.workflow.metrics.sla.processor.WorkflowMetricsSLAStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.MultivaluedMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * @author Inácio Nery
 */
@Component(
	properties = "OSGI-INF/liferay/rest/v1_0/assignee-user.properties",
	scope = ServiceScope.PROTOTYPE, service = AssigneeUserResource.class
)
public class AssigneeUserResourceImpl
	extends BaseAssigneeUserResourceImpl implements EntityModelResource {

	@Override
	public EntityModel getEntityModel(MultivaluedMap multivaluedMap)
		throws Exception {

		return _entityModel;
	}

	@Override
	public Page<AssigneeUser> getProcessAssigneeUsersPage(
			Long processId, String keywords, Long[] roleIds, String[] taskKeys,
			Pagination pagination, Sort[] sorts)
		throws Exception {

		Set<Long> userIds = Collections.emptySet();

		if (Validator.isNotNull(keywords) || ArrayUtil.isNotEmpty(roleIds)) {
			userIds = _getUserIds(keywords, roleIds);

			if (userIds.isEmpty()) {
				return Page.of(Collections.emptyList());
			}
		}

		FieldSort fieldSort = _toFieldSort(sorts);

		Map<Long, AssigneeUser> assigneeUsersMap = _getAssigneeUsersMap(
			fieldSort, pagination, processId, taskKeys, userIds);

		if (!assigneeUsersMap.isEmpty()) {
			if (pagination == null) {
				return Page.of(assigneeUsersMap.values());
			}

			return Page.of(
				_getAssigneeUsers(
					assigneeUsersMap, fieldSort, processId, taskKeys, userIds,
					pagination),
				pagination,
				_getAssigneeUsersCount(processId, taskKeys, userIds));
		}

		return Page.of(Collections.emptyList());
	}

	private TermsQuery _createAssigneeIdTermsQuery(Set<Long> userIds) {
		TermsQuery termsQuery = _queries.terms("assigneeId");

		Stream<Long> stream = userIds.stream();

		termsQuery.addValues(
			stream.map(
				String::valueOf
			).toArray(
				Object[]::new
			));

		return termsQuery;
	}

	private BooleanQuery _createBooleanQuery(
		Set<Long> assigneeIds, long processId, String[] taskKeys) {

		BooleanQuery booleanQuery = _queries.booleanQuery();

		BooleanQuery slaTaskResultsBooleanQuery = _queries.booleanQuery();

		slaTaskResultsBooleanQuery.addFilterQueryClauses(
			_queries.term("_index", "workflow-metrics-sla-task-results"));
		slaTaskResultsBooleanQuery.addMustQueryClauses(
			_createSLATaskResultsBooleanQuery(
				assigneeIds, processId, taskKeys));

		BooleanQuery tokensBooleanQuery = _queries.booleanQuery();

		tokensBooleanQuery.addFilterQueryClauses(
			_queries.term("_index", "workflow-metrics-tokens"));
		tokensBooleanQuery.addMustQueryClauses(
			_createTokensBooleanQuery(assigneeIds, processId, taskKeys));

		return booleanQuery.addShouldQueryClauses(
			slaTaskResultsBooleanQuery, tokensBooleanQuery);
	}

	private BooleanQuery _createSLATaskResultsBooleanQuery(
		Set<Long> assigneeIds, long processId, String[] taskKeys) {

		BooleanQuery booleanQuery = _queries.booleanQuery();

		booleanQuery.addMustNotQueryClauses(
			_queries.term("instanceId", 0),
			_queries.term("status", WorkflowMetricsSLAStatus.COMPLETED),
			_queries.term("status", WorkflowMetricsSLAStatus.EXPIRED));

		if (taskKeys.length > 0) {
			booleanQuery.addMustQueryClauses(
				_createTaskNameTermsQuery(taskKeys));
		}

		return booleanQuery.addMustQueryClauses(
			_queries.term("companyId", contextCompany.getCompanyId()),
			_queries.term("deleted", Boolean.FALSE),
			_queries.term("processId", processId),
			_createAssigneeIdTermsQuery(assigneeIds));
	}

	private TermsQuery _createTaskNameTermsQuery(String[] taskNames) {
		TermsQuery termsQuery = _queries.terms("taskName");

		termsQuery.addValues(taskNames);

		return termsQuery;
	}

	private BooleanQuery _createTokensBooleanQuery(
		long processId, String[] taskKeys, Set<Long> userIds) {

		BooleanQuery booleanQuery = _queries.booleanQuery();

		booleanQuery.addMustNotQueryClauses(_queries.term("tokenId", "0"));

		if (taskKeys.length > 0) {
			booleanQuery.addMustQueryClauses(
				_createTaskNameTermsQuery(taskKeys));
		}

		if (!userIds.isEmpty()) {
			booleanQuery.addMustQueryClauses(
				_createAssigneeIdTermsQuery(userIds));
		}

		return booleanQuery.addMustQueryClauses(
			_queries.term("companyId", contextCompany.getCompanyId()),
			_queries.term("completed", Boolean.FALSE),
			_queries.term("deleted", Boolean.FALSE),
			_queries.term("processId", processId));
	}

	private BooleanQuery _createTokensBooleanQuery(
		Set<Long> assigneeIds, long processId, String[] taskKeys) {

		BooleanQuery booleanQuery = _queries.booleanQuery();

		booleanQuery.addMustNotQueryClauses(_queries.term("instanceId", 0));

		if (taskKeys.length > 0) {
			booleanQuery.addMustQueryClauses(
				_createTaskNameTermsQuery(taskKeys));
		}

		return booleanQuery.addMustQueryClauses(
			_queries.term("companyId", contextCompany.getCompanyId()),
			_queries.term("deleted", Boolean.FALSE),
			_queries.term("processId", processId),
			_createAssigneeIdTermsQuery(assigneeIds));
	}

	private Collection<AssigneeUser> _getAssigneeUsers(
			Map<Long, AssigneeUser> assigneeUsersMap, FieldSort fieldSort,
			Long processId, String[] taskKeys, Set<Long> userIds,
			Pagination pagination)
		throws Exception {

		List<AssigneeUser> assigneeUsers = new LinkedList<>();

		Set<Long> assigneIds = assigneeUsersMap.keySet();

		if (!userIds.isEmpty()) {
			assigneIds = SetUtil.intersect(userIds, assigneeUsersMap.keySet());
		}

		TermsAggregationResult slaTermsAggregationResult =
			_getSLATermsAggregationResult(
				assigneIds, fieldSort, pagination, processId, taskKeys);

		if (_isOrderByTaskCount(fieldSort.getField())) {
			assigneeUsersMap.forEach(
				(userId, assigneeUser) -> {
					Bucket bucket = slaTermsAggregationResult.getBucket(
						String.valueOf(userId));

					_populateAssigneeUserWithSLAMetrics(bucket, assigneeUser);

					assigneeUsers.add(assigneeUser);
				});
		}
		else {
			for (Bucket bucket : slaTermsAggregationResult.getBuckets()) {
				AssigneeUser assigneeUser = assigneeUsersMap.get(
					GetterUtil.getLong(bucket.getKey()));

				_populateAssigneeUserWithSLAMetrics(bucket, assigneeUser);

				assigneeUsers.add(assigneeUser);
			}
		}

		if (assigneeUsers.size() > pagination.getPageSize()) {
			return assigneeUsers.subList(0, assigneeUsers.size() - 1);
		}

		return assigneeUsers;
	}

	private long _getAssigneeUsersCount(
			long processId, String[] taskKeys, Set<Long> userIds)
		throws Exception {

		SearchSearchRequest searchSearchRequest = new SearchSearchRequest();

		searchSearchRequest.addAggregation(
			_aggregations.cardinality("assigneeId", "assigneeId"));
		searchSearchRequest.setIndexNames("workflow-metrics-tokens");
		searchSearchRequest.setQuery(
			_createTokensBooleanQuery(processId, taskKeys, userIds));

		SearchSearchResponse searchSearchResponse =
			_searchRequestExecutor.executeSearchRequest(searchSearchRequest);

		Map<String, AggregationResult> aggregationResultsMap =
			searchSearchResponse.getAggregationResultsMap();

		CardinalityAggregationResult cardinalityAggregationResult =
			(CardinalityAggregationResult)aggregationResultsMap.get(
				"assigneeId");

		return cardinalityAggregationResult.getValue();
	}

	private Map<Long, AssigneeUser> _getAssigneeUsersMap(
			FieldSort fieldSort, Pagination pagination, long processId,
			String[] taskKeys, Set<Long> userIds)
		throws Exception {

		SearchSearchRequest searchSearchRequest = new SearchSearchRequest();

		TermsAggregation termsAggregation = _aggregations.terms(
			"assigneeId", "assigneeId");

		termsAggregation.addChildrenAggregations(
			_aggregations.valueCount("taskCount", "taskId"));

		if ((fieldSort != null) && (pagination != null) &&
			_isOrderByTaskCount(fieldSort.getField())) {

			termsAggregation.addPipelineAggregation(
				_resourceHelper.createBucketSortPipelineAggregation(
					fieldSort, pagination));
		}

		termsAggregation.setSize(10000);

		searchSearchRequest.addAggregation(termsAggregation);

		searchSearchRequest.setIndexNames("workflow-metrics-tokens");
		searchSearchRequest.setQuery(
			_createTokensBooleanQuery(processId, taskKeys, userIds));

		Stream<AssigneeUser> stream = Stream.of(
			_searchRequestExecutor.executeSearchRequest(searchSearchRequest)
		).map(
			SearchSearchResponse::getAggregationResultsMap
		).map(
			aggregationResultsMap ->
				(TermsAggregationResult)aggregationResultsMap.get("assigneeId")
		).map(
			TermsAggregationResult::getBuckets
		).flatMap(
			Collection::stream
		).map(
			this::_toAssigneeUser
		).filter(
			Objects::nonNull
		);

		if (pagination == null) {
			stream = stream.sorted(
				Comparator.comparing(
					AssigneeUser::getName, String::compareToIgnoreCase));
		}

		return stream.collect(
			LinkedHashMap::new,
			(map, assignee) -> map.put(assignee.getId(), assignee),
			Map::putAll);
	}

	private TermsAggregationResult _getSLATermsAggregationResult(
		Set<Long> assigneeIds, FieldSort fieldSort, Pagination pagination,
		Long processId, String[] taskKeys) {

		SearchSearchRequest searchSearchRequest = new SearchSearchRequest();

		TermsAggregation termsAggregation = _aggregations.terms(
			"assigneeId", "assigneeId");

		FilterAggregation onTimeFilterAggregation = _aggregations.filter(
			"onTime", _resourceHelper.createMustNotBooleanQuery());

		onTimeFilterAggregation.addChildAggregation(
			_resourceHelper.
				createOnTimeTaskByAssigneeScriptedMetricAggregation());

		FilterAggregation overdueFilterAggregation = _aggregations.filter(
			"overdue", _resourceHelper.createMustNotBooleanQuery());

		overdueFilterAggregation.addChildAggregation(
			_resourceHelper.
				createOverdueTaskByAssigneeScriptedMetricAggregation());

		termsAggregation.addChildrenAggregations(
			onTimeFilterAggregation, overdueFilterAggregation);

		if ((fieldSort != null) && (pagination != null) &&
			(_isOrderByOnTimeTaskCount(fieldSort.getField()) ||
			 _isOrderByOverdueTaskCount(fieldSort.getField()))) {

			termsAggregation.addPipelineAggregation(
				_resourceHelper.createBucketSortPipelineAggregation(
					fieldSort, pagination));
		}

		termsAggregation.setSize(assigneeIds.size());

		searchSearchRequest.addAggregation(termsAggregation);

		searchSearchRequest.setIndexNames(
			"workflow-metrics-sla-task-results", "workflow-metrics-tokens");
		searchSearchRequest.setQuery(
			_createBooleanQuery(assigneeIds, processId, taskKeys));

		SearchSearchResponse searchSearchResponse =
			_searchRequestExecutor.executeSearchRequest(searchSearchRequest);

		Map<String, AggregationResult> aggregationResultsMap =
			searchSearchResponse.getAggregationResultsMap();

		return (TermsAggregationResult)aggregationResultsMap.get("assigneeId");
	}

	private long _getTaskCount(Bucket bucket) {
		if (bucket == null) {
			return 0;
		}

		ValueCountAggregationResult valueCountAggregationResult =
			(ValueCountAggregationResult)bucket.getChildAggregationResult(
				"taskCount");

		return GetterUtil.getLong(valueCountAggregationResult.getValue());
	}

	private Set<Long> _getUserIds(String keywords, Long[] roleIds) {
		LinkedHashMap<String, Object> params = new LinkedHashMap<>(1);

		if (ArrayUtil.isNotEmpty(roleIds)) {
			params.put("usersRoles", roleIds);
		}

		return Stream.of(
			_userLocalService.search(
				contextCompany.getCompanyId(), keywords,
				WorkflowConstants.STATUS_APPROVED, params, QueryUtil.ALL_POS,
				QueryUtil.ALL_POS, (OrderByComparator<User>)null)
		).flatMap(
			List::parallelStream
		).map(
			User::getUserId
		).collect(
			Collectors.toSet()
		);
	}

	private boolean _isOrderByOnTimeTaskCount(String fieldName) {
		if (StringUtil.equals(fieldName, "onTimeTaskCount") ||
			StringUtil.equals(
				fieldName,
				StringBundler.concat(
					"onTime", StringPool.GREATER_THAN, "taskCount.value"))) {

			return true;
		}

		return false;
	}

	private boolean _isOrderByOverdueTaskCount(String fieldName) {
		if (StringUtil.equals(fieldName, "overdueTaskCount") ||
			StringUtil.equals(
				fieldName,
				StringBundler.concat(
					"overdue", StringPool.GREATER_THAN, "taskCount.value"))) {

			return true;
		}

		return false;
	}

	private boolean _isOrderByTaskCount(String fieldName) {
		if (StringUtil.equals(fieldName, "taskCount")) {
			return true;
		}

		return false;
	}

	private void _populateAssigneeUserWithSLAMetrics(
		Bucket bucket, AssigneeUser assigneeUser) {

		_setOnTimeTaskCount(bucket, assigneeUser);
		_setOverdueTaskCount(bucket, assigneeUser);
	}

	private void _setOnTimeTaskCount(Bucket bucket, AssigneeUser assigneeUser) {
		if (bucket == null) {
			return;
		}

		assigneeUser.setOnTimeTaskCount(
			_resourceHelper.getOnTimeTaskCount(bucket));
	}

	private void _setOverdueTaskCount(
		Bucket bucket, AssigneeUser assigneeUser) {

		if (bucket == null) {
			return;
		}

		assigneeUser.setOverdueTaskCount(
			_resourceHelper.getOverdueTaskCount(bucket));
	}

	private AssigneeUser _toAssigneeUser(Bucket bucket) {
		try {
			User user = _userLocalService.getUserById(
				GetterUtil.getLong(bucket.getKey()));

			return new AssigneeUser() {
				{
					id = user.getUserId();
					name = user.getFullName();
					onTimeTaskCount = 0L;
					overdueTaskCount = 0L;

					setImage(
						() -> {
							if (user.getPortraitId() == 0) {
								return null;
							}

							ThemeDisplay themeDisplay = new ThemeDisplay() {
								{
									setPathImage(_portal.getPathImage());
								}
							};

							return user.getPortraitURL(themeDisplay);
						});

					setTaskCount(_getTaskCount(bucket));
				}
			};
		}
		catch (PortalException pe) {
			if (_log.isWarnEnabled()) {
				_log.warn(pe, pe);
			}

			return null;
		}
	}

	private FieldSort _toFieldSort(Sort[] sorts) {
		Sort sort = new Sort("taskCount", true);

		if (sorts != null) {
			sort = sorts[0];
		}

		String fieldName = sort.getFieldName();

		if (_isOrderByOnTimeTaskCount(fieldName) ||
			_isOrderByOverdueTaskCount(fieldName)) {

			fieldName = StringBundler.concat(
				StringUtil.extractFirst(fieldName, "TaskCount"),
				StringPool.GREATER_THAN, "taskCount.value");
		}

		FieldSort fieldSort = _sorts.field(fieldName);

		fieldSort.setSortOrder(
			sort.isReverse() ? SortOrder.DESC : SortOrder.ASC);

		return fieldSort;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		AssigneeUserResourceImpl.class);

	private static final EntityModel _entityModel =
		new AssigneeUserEntityModel();

	@Reference
	private Aggregations _aggregations;

	@Reference
	private Portal _portal;

	@Reference
	private Queries _queries;

	@Reference
	private ResourceHelper _resourceHelper;

	@Reference
	private SearchRequestExecutor _searchRequestExecutor;

	@Reference
	private Sorts _sorts;

	@Reference
	private UserLocalService _userLocalService;

}