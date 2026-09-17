package ru.nikolay977.testitmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.testit.client.api.AttachmentsApi;
import ru.testit.client.api.AutoTestsApi;
import ru.testit.client.api.ConfigurationsApi;
import ru.testit.client.api.CustomAttributesApi;
import ru.testit.client.api.ParametersApi;
import ru.testit.client.api.ProjectConfigurationsApi;
import ru.testit.client.api.ProjectSectionsApi;
import ru.testit.client.api.ProjectTestPlansApi;
import ru.testit.client.api.ProjectsApi;
import ru.testit.client.api.SearchApi;
import ru.testit.client.api.SectionsApi;
import ru.testit.client.api.TagsApi;
import ru.testit.client.api.TestPlansApi;
import ru.testit.client.api.TestPointsApi;
import ru.testit.client.api.TestResultsApi;
import ru.testit.client.api.TestRunsApi;
import ru.testit.client.api.TestStatusesApi;
import ru.testit.client.api.TestSuitesApi;
import ru.testit.client.api.UsersApi;
import ru.testit.client.api.WorkItemsApi;
import ru.testit.client.api.WorkItemsCommentsApi;
import ru.testit.client.invoker.ApiClient;
import ru.testit.client.invoker.ApiException;
import ru.testit.client.invoker.JSON;
import ru.testit.client.model.AutoTestResultHistorySelectApiModel;
import ru.testit.client.model.ConfigurationFilterModel;
import ru.testit.client.model.CustomAttributeSearchQueryModel;
import ru.testit.client.model.DateTimeRangeSelectorModel;
import ru.testit.client.model.DeletionState;
import ru.testit.client.model.GlobalSearchRequest;
import ru.testit.client.model.ParametersFilterApiModel;
import ru.testit.client.model.ProjectTestPlansFilterModel;
import ru.testit.client.model.ProjectsFilterModel;
import ru.testit.client.model.SearchTestStatusesApiModel;
import ru.testit.client.model.TestPointFilterRequestModel;
import ru.testit.client.model.TestResultOutcome;
import ru.testit.client.model.TestResultsFilterApiModel;
import ru.testit.client.model.TestRunStatisticsFilterApiModel;
import ru.testit.client.model.TestSuiteWorkItemsSearchModel;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Все read-ручки TestIT: проекты, секции, конфигурации, теги, статусы,
 * тест-планы, сьюты, поинты, результаты, кейсы, автотесты, прогоны,
 * вложения, атрибуты, параметры, глобальный поиск.
 * Возвращает JSON-строки для MCP-тулов.
 */
public class TestItQueries {

    private static final int DEFAULT_TAKE = 50;
    private static final int MAX_TAKE = 200;
    private static final int MAX_JSON = 100_000;

    private final ObjectMapper json;
    private final UUID defaultProject;

    private final ProjectsApi projects;
    private final SectionsApi sections;
    private final ProjectSectionsApi projectSections;
    private final ConfigurationsApi configurations;
    private final ProjectConfigurationsApi projectConfigurations;
    private final TagsApi tags;
    private final UsersApi users;
    private final TestStatusesApi statuses;
    private final TestPlansApi plans;
    private final ProjectTestPlansApi projectPlans;
    private final TestSuitesApi suites;
    private final TestPointsApi points;
    private final TestResultsApi results;
    private final WorkItemsApi workItems;
    private final WorkItemsCommentsApi comments;
    private final AutoTestsApi autoTests;
    private final TestRunsApi runs;
    private final AttachmentsApi attachments;
    private final CustomAttributesApi attributes;
    private final SearchApi search;
    private final ParametersApi parameters;

    public TestItQueries(String url, String token, UUID defaultProject) {
        this.defaultProject = defaultProject;
        ApiClient apiClient = new ApiClient(true);
        apiClient.setBasePath(url.trim());
        apiClient.setApiKey(token.trim());
        apiClient.setApiKeyPrefix("PrivateToken");
        ObjectMapper mapper = new JSON().getMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.json = mapper;
        this.projects = new ProjectsApi(apiClient);
        this.sections = new SectionsApi(apiClient);
        this.projectSections = new ProjectSectionsApi(apiClient);
        this.configurations = new ConfigurationsApi(apiClient);
        this.projectConfigurations = new ProjectConfigurationsApi(apiClient);
        this.tags = new TagsApi(apiClient);
        this.users = new UsersApi(apiClient);
        this.statuses = new TestStatusesApi(apiClient);
        this.plans = new TestPlansApi(apiClient);
        this.projectPlans = new ProjectTestPlansApi(apiClient);
        this.suites = new TestSuitesApi(apiClient);
        this.points = new TestPointsApi(apiClient);
        this.results = new TestResultsApi(apiClient);
        this.workItems = new WorkItemsApi(apiClient);
        this.comments = new WorkItemsCommentsApi(apiClient);
        this.autoTests = new AutoTestsApi(apiClient);
        this.runs = new TestRunsApi(apiClient);
        this.attachments = new AttachmentsApi(apiClient);
        this.attributes = new CustomAttributesApi(apiClient);
        this.search = new SearchApi(apiClient);
        this.parameters = new ParametersApi(apiClient);
    }

    // ---------- helpers ----------

    private String toJson(Object value) {
        try {
            String s = json.writeValueAsString(value);
            if (s.length() > MAX_JSON) {
                return s.substring(0, MAX_JSON) + "\n…(truncated, too large)";
            }
            return s;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize response: " + e.getMessage(), e);
        }
    }

    private static int take(Integer take) {
        if (take == null) {
            return DEFAULT_TAKE;
        }
        return Math.min(Math.max(take, 1), MAX_TAKE);
    }

    private UUID pid(String projectIdRaw) {
        if (projectIdRaw != null && !projectIdRaw.isBlank()) {
            return UUID.fromString(projectIdRaw.trim());
        }
        if (defaultProject == null) {
            throw new IllegalArgumentException("projectId is required (TESTIT_PROJECT_ID is not set)");
        }
        return defaultProject;
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value.trim());
    }

    static DateTimeRangeSelectorModel dateRange(String from, String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            return null;
        }
        DateTimeRangeSelectorModel range = new DateTimeRangeSelectorModel();
        if (from != null && !from.isBlank()) {
            range.setFrom(parseDate(from.trim(), true));
        }
        if (to != null && !to.isBlank()) {
            range.setTo(parseDate(to.trim(), false));
        }
        return range;
    }

    private static OffsetDateTime parseDate(String value, boolean startOfDay) {
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            LocalDate day = LocalDate.parse(value);
            return startOfDay ? day.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
                    : day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().minusNanos(1);
        }
    }

    // ---------- projects ----------

    public String listProjects(Integer take) throws ApiException {
        return toJson(projects.getAllProjects(false, null, 0, take(take), null, null, null));
    }

    public String getProject(String id) throws ApiException {
        return toJson(projects.getProjectById(id.trim()));
    }

    public String projectTestPlans(String projectIdRaw) throws ApiException {
        return toJson(projects.getTestPlansByProjectId(pid(projectIdRaw).toString(), false));
    }

    public String projectNamespaces(String projectIdRaw) throws ApiException {
        return toJson(projects.getAutoTestsNamespaces(pid(projectIdRaw).toString()));
    }

    // ---------- sections ----------

    public String listSections(String projectIdRaw, Integer take) throws ApiException {
        return toJson(projectSections.getSectionsByProjectId(pid(projectIdRaw).toString(), 0, take(take), null, null, null));
    }

    public String getSection(String id) throws ApiException {
        return toJson(sections.getSectionById(uuid(id), DeletionState.ANY));
    }

    public String sectionCases(String sectionId, Integer take) throws ApiException {
        return toJson(sections.getWorkItemsBySectionId(uuid(sectionId), false, null, null, 0, take(take), null, null, null));
    }

    // ---------- configurations ----------

    public String listConfigurations(String projectIdRaw) throws ApiException {
        return toJson(projectConfigurations.getConfigurationsByProjectId(pid(projectIdRaw).toString()));
    }

    public String getConfiguration(String id) throws ApiException {
        return toJson(configurations.getConfigurationById(id.trim()));
    }

    public String searchConfigurations(String projectIdRaw, String query, Integer take) throws ApiException {
        ConfigurationFilterModel filter = new ConfigurationFilterModel();
        if (projectIdRaw != null && !projectIdRaw.isBlank()) {
            filter.setProjectIds(Set.of(UUID.fromString(projectIdRaw.trim())));
        }
        if (query != null && !query.isBlank()) {
            filter.setName(query.trim());
        }
        filter.setIsDeleted(false);
        return toJson(configurations.apiV2ConfigurationsSearchPost(0, take(take), null, null, null, filter));
    }

    // ---------- tags / statuses / users ----------

    public String searchTags(String query, Integer take) throws ApiException {
        int t = take(take);
        if (query != null && !query.isBlank()) {
            return toJson(tags.apiV2TagsSearchGet(0, t, null, "Name", query.trim()));
        }
        return toJson(tags.apiV2TagsSearchGet(0, t, null, null, null));
    }

    public String searchStatuses() throws ApiException {
        return toJson(statuses.apiV2TestStatusesSearchPost(new SearchTestStatusesApiModel()));
    }

    public String getStatus(String id) throws ApiException {
        return toJson(statuses.apiV2TestStatusesIdGet(uuid(id)));
    }

    public String userExists(String username) throws ApiException {
        return toJson(users.apiV2UsersExistsGet(username.trim()));
    }

    // ---------- test plans ----------

    public String getTestPlan(String id) throws ApiException {
        return toJson(plans.getTestPlanById(id.trim()));
    }

    public String testPlanSuites(String testPlanId) throws ApiException {
        return toJson(plans.getTestSuitesById(testPlanId.trim()));
    }

    public String testPlanAnalytics(String testPlanId) throws ApiException {
        return toJson(plans.apiV2TestPlansIdAnalyticsGet(testPlanId.trim()));
    }

    public String testPlanSummary(String testPlanId) throws ApiException {
        return toJson(plans.apiV2TestPlansIdSummariesGet(testPlanId.trim()));
    }

    public String testPlanHistory(String testPlanId, Integer take) throws ApiException {
        return toJson(plans.apiV2TestPlansIdHistoryGet(testPlanId.trim(), 0, take(take), null, null, null));
    }

    public String testPlanConfigurations(String testPlanId) throws ApiException {
        return toJson(plans.apiV2TestPlansIdConfigurationsGet(testPlanId.trim()));
    }

    public String testPlanLinks(String testPlanId, Integer take) throws ApiException {
        return toJson(plans.apiV2TestPlansIdLinksGet(testPlanId.trim(), 0, take(take), null));
    }

    public String testPlanLastResults(String testPlanId, String configurationId, Integer take) throws ApiException {
        UUID conf = (configurationId == null || configurationId.isBlank()) ? null : uuid(configurationId);
        return toJson(plans.apiV2TestPlansIdTestPointsLastResultsGet(testPlanId.trim(), conf, 0, take(take), null, null, null));
    }

    public String testPlanRuns(String testPlanId, Integer take) throws ApiException {
        return toJson(plans.apiV2TestPlansIdTestRunsGet(testPlanId.trim(), null, null, null, null, 0, take(take), null, null, null));
    }

    public String searchTestPlans(String projectIdRaw, String query, Integer take) throws ApiException {
        ProjectTestPlansFilterModel filter = new ProjectTestPlansFilterModel();
        if (query != null && !query.isBlank()) {
            filter.setName(query.trim());
        }
        return toJson(projectPlans.apiV2ProjectsProjectIdTestPlansSearchPost(
                pid(projectIdRaw).toString(), null, 0, take(take), null, null, null, filter));
    }

    public String projectPlansAnalytics(String projectIdRaw, Integer take) throws ApiException {
        return toJson(projectPlans.apiV2ProjectsProjectIdTestPlansAnalyticsGet(
                pid(projectIdRaw), null, null, 0, take(take), null, null, null));
    }

    // ---------- test suites ----------

    public String getSuite(String id) throws ApiException {
        return toJson(suites.getTestSuiteById(uuid(id)));
    }

    public String suitePoints(String suiteId) throws ApiException {
        return toJson(suites.getTestPointsById(uuid(suiteId)));
    }

    public String suiteResults(String suiteId) throws ApiException {
        return toJson(suites.getTestResultsById(uuid(suiteId)));
    }

    public String suiteConfigurations(String suiteId) throws ApiException {
        return toJson(suites.getConfigurationsByTestSuiteId(uuid(suiteId)));
    }

    public String suiteCases(String suiteId, String query, Integer take) throws ApiException {
        TestSuiteWorkItemsSearchModel model = new TestSuiteWorkItemsSearchModel();
        if (query != null && !query.isBlank()) {
            model.setName(query.trim());
        }
        return toJson(suites.searchWorkItems(uuid(suiteId), 0, take(take), null, null, null, model));
    }

    // ---------- test points ----------

    public String searchPoints(String testPlanId, String suiteId, String query, Integer take) throws ApiException {
        if ((testPlanId == null || testPlanId.isBlank()) && (suiteId == null || suiteId.isBlank())) {
            throw new IllegalArgumentException("Укажите testPlanId или suiteId: поиск поинтов без фильтра не поддерживается сервером TestIT");
        }
        TestPointFilterRequestModel filter = new TestPointFilterRequestModel();
        if (testPlanId != null && !testPlanId.isBlank()) {
            filter.setTestPlanIds(List.of(uuid(testPlanId)));
        }
        if (suiteId != null && !suiteId.isBlank()) {
            filter.setTestSuiteIds(List.of(uuid(suiteId)));
        }
        if (query != null && !query.isBlank()) {
            filter.setName(query.trim());
        }
        return toJson(points.apiV2TestPointsSearchPost(0, take(take), null, null, null, filter));
    }

    public String pointCase(String pointId) throws ApiException {
        return toJson(points.apiV2TestPointsIdWorkItemGet(uuid(pointId)));
    }

    public String pointRuns(String pointId) throws ApiException {
        return toJson(points.apiV2TestPointsIdTestRunsGet(uuid(pointId)));
    }

    // ---------- test results ----------

    public String searchResults(String outcome, String from, String to, Integer take) throws ApiException {
        TestResultsFilterApiModel filter = new TestResultsFilterApiModel();
        if (outcome != null && !outcome.isBlank()) {
            filter.setOutcomes(List.of(TestResultOutcome.valueOf(outcome.trim().toUpperCase())));
        }
        DateTimeRangeSelectorModel range = dateRange(from, to);
        if (range != null) {
            filter.setCompletedOn(range);
        }
        return toJson(results.apiV2TestResultsSearchPost(0, take(take), null, null, null, filter));
    }

    public String getResult(String id) throws ApiException {
        return toJson(results.apiV2TestResultsIdGet(uuid(id)));
    }

    public String resultAggregated(String id) throws ApiException {
        return toJson(results.apiV2TestResultsIdAggregatedGet(uuid(id)));
    }

    public String resultReruns(String id) throws ApiException {
        return toJson(results.apiV2TestResultsIdRerunsGet(uuid(id)));
    }

    public String resultAttachments(String id) throws ApiException {
        return toJson(results.apiV2TestResultsIdAttachmentsInfoGet(uuid(id)));
    }

    public String resultsStatistics(String outcome, String from, String to) throws ApiException {
        TestResultsFilterApiModel filter = new TestResultsFilterApiModel();
        if (outcome != null && !outcome.isBlank()) {
            filter.setOutcomes(List.of(TestResultOutcome.valueOf(outcome.trim().toUpperCase())));
        }
        DateTimeRangeSelectorModel range = dateRange(from, to);
        if (range != null) {
            filter.setCompletedOn(range);
        }
        return toJson(results.apiV2TestResultsStatisticsFilterPost(filter));
    }

    // ---------- work items (extra) ----------

    public String caseVersions(String id) throws ApiException {
        return toJson(workItems.getWorkItemVersions(id.trim(), null, null));
    }

    public String caseHistory(String id, Integer take) throws ApiException {
        return toJson(workItems.apiV2WorkItemsIdHistoryGet(uuid(id), 0, take(take), null, null, null));
    }

    public String caseChronology(String id) throws ApiException {
        return toJson(workItems.getWorkItemChronology(id.trim()));
    }

    public String caseComments(String id) throws ApiException {
        return toJson(comments.apiV2WorkItemsIdCommentsGet(id.trim()));
    }

    public String caseCommentsCount(String id) throws ApiException {
        return toJson(comments.apiV2WorkItemsIdCommentsCountGet(id.trim()));
    }

    public String caseAutotests(String id) throws ApiException {
        return toJson(workItems.getAutoTestsForWorkItem(id.trim()));
    }

    public String caseIterations(String id) throws ApiException {
        return toJson(workItems.getIterations(id.trim(), null, null));
    }

    public String caseResultsHistory(String id, String from, String to, Integer take) throws ApiException {
        DateTimeRangeSelectorModel range = dateRange(from, to);
        OffsetDateTime f = range == null ? null : range.getFrom();
        OffsetDateTime t = range == null ? null : range.getTo();
        int n = take(take);
        return toJson(workItems.apiV2WorkItemsIdTestResultsHistoryGet(
                uuid(id), f, t, null, null, null, null, null, null, null, null, 0, n, null, null, null));
    }

    public String sharedStepRefs(String sharedStepId) throws ApiException {
        return toJson(workItems.apiV2WorkItemsSharedStepsSharedStepIdReferencesGet(uuid(sharedStepId)));
    }

    // ---------- autotests (extra) ----------

    public String getAutotest(String id) throws ApiException {
        return toJson(autoTests.getAutoTestById(id.trim()));
    }

    public String autotestHistory(String id, Integer take) throws ApiException {
        return toJson(autoTests.apiV2AutoTestsIdTestResultsSearchPost(
                id.trim(), 0, take(take), null, null, null, new AutoTestResultHistorySelectApiModel()));
    }

    public String autotestChronology(String id) throws ApiException {
        return toJson(autoTests.getAutoTestChronology(id.trim()));
    }

    public String autotestRuns(String id) throws ApiException {
        return toJson(autoTests.getTestRuns(id.trim()));
    }

    public String autotestCases(String id) throws ApiException {
        return toJson(autoTests.getWorkItemsLinkedToAutoTest(id.trim(), false, false));
    }

    public String autotestAvgDuration(String id) throws ApiException {
        return toJson(autoTests.getAutoTestAverageDuration(id.trim()));
    }

    public String autotestChangedLinks(String id) throws ApiException {
        return toJson(autoTests.apiV2AutoTestsIdWorkItemsChangedIdGet(uuid(id)));
    }

    // ---------- test runs (extra) ----------

    public String getRun(String id) throws ApiException {
        return toJson(runs.getTestRunById(uuid(id)));
    }

    public String runResults(String runId) throws ApiException {
        return toJson(runs.apiV2TestRunsIdTestPointsResultsGet(uuid(runId)));
    }

    public String runStatistics(String runId) throws ApiException {
        return toJson(runs.apiV2TestRunsIdStatisticsFilterPost(uuid(runId), new TestRunStatisticsFilterApiModel()));
    }

    public String runNamespaces(String runId) throws ApiException {
        return toJson(runs.apiV2TestRunsIdAutoTestsNamespacesGet(uuid(runId)));
    }

    // ---------- attachments / attributes / search / parameters ----------

    public String attachmentInfo(String id) throws ApiException {
        return toJson(attachments.apiV2AttachmentsIdMetadataGet(uuid(id)));
    }

    public String storageSize() throws ApiException {
        return toJson(attachments.apiV2AttachmentsOccupiedFileStorageSizeGet());
    }

    public String searchAttributes(String projectIdRaw, String query, Integer take) throws ApiException {
        CustomAttributeSearchQueryModel model = new CustomAttributeSearchQueryModel();
        if (projectIdRaw != null && !projectIdRaw.isBlank()) {
            model.setProjectIds(Set.of(UUID.fromString(projectIdRaw.trim())));
        }
        if (query != null && !query.isBlank()) {
            model.setName(query.trim());
        }
        model.setIsDeleted(false);
        return toJson(attributes.apiV2CustomAttributesSearchPost(0, take(take), null, null, null, model));
    }

    public String getAttribute(String id) throws ApiException {
        return toJson(attributes.apiV2CustomAttributesIdGet(uuid(id)));
    }

    public String attributeExists(String name) throws ApiException {
        return toJson(attributes.apiV2CustomAttributesExistsGet(name.trim(), null));
    }

    public String globalSearch(String query, Integer take) throws ApiException {
        GlobalSearchRequest req = new GlobalSearchRequest();
        req.setQuery(query.trim());
        req.setTake(take(take));
        return toJson(search.apiV2SearchGlobalSearchPost(req));
    }

    public String paramsGroups(String projectIdRaw, Integer take) throws ApiException {
        Set<UUID> pids = (projectIdRaw == null || projectIdRaw.isBlank()) && defaultProject == null
                ? null : Set.of(pid(projectIdRaw));
        return toJson(parameters.apiV2ParametersGroupsGet(pids, null, null, null, 0, take(take), null, null, null));
    }

    public String paramsValues(String key) throws ApiException {
        return toJson(parameters.apiV2ParametersKeyValuesGet(key.trim()));
    }

    public String searchParams(String projectIdRaw, String query, Integer take) throws ApiException {
        ParametersFilterApiModel filter = new ParametersFilterApiModel();
        if (projectIdRaw != null && !projectIdRaw.isBlank()) {
            filter.setProjectIds(List.of(UUID.fromString(projectIdRaw.trim())));
        }
        if (query != null && !query.isBlank()) {
            filter.setName(query.trim());
        }
        filter.setIsDeleted(false);
        return toJson(parameters.apiV2ParametersSearchPost(0, take(take), null, null, null, filter));
    }

    public String searchProjects(String query, Integer take) throws ApiException {
        ProjectsFilterModel filter = new ProjectsFilterModel();
        if (query != null && !query.isBlank()) {
            filter.setName(query.trim());
        }
        filter.setIsDeleted(false);
        return toJson(projects.apiV2ProjectsSearchPost(0, take(take), null, null, null, filter));
    }
}
