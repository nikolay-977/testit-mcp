package ru.nikolay977.testitmcp;

import ru.testit.client.api.AutoTestsApi;
import ru.testit.client.api.ProjectWorkItemsApi;
import ru.testit.client.api.TestRunsApi;
import ru.testit.client.api.WorkItemsApi;
import ru.testit.client.invoker.ApiClient;
import ru.testit.client.invoker.ApiException;
import ru.testit.client.model.AutoTestApiResult;
import ru.testit.client.model.AutoTestFilterApiModel;
import ru.testit.client.model.AutoTestSearchApiModel;
import ru.testit.client.model.DateTimeRangeSelectorModel;
import ru.testit.client.model.TestRunFilterApiModel;
import ru.testit.client.model.TestRunShortApiResult;
import ru.testit.client.model.StepModel;
import ru.testit.client.model.WorkItemFilterApiModel;
import ru.testit.client.model.WorkItemGroupGetModel;
import ru.testit.client.model.WorkItemGroupModel;
import ru.testit.client.model.WorkItemGroupType;
import ru.testit.client.model.WorkItemModel;
import ru.testit.client.model.WorkItemSelectApiModel;
import ru.testit.client.model.WorkItemShortApiResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Тонкая обёртка над TestIT api-client: чтение тест-кейсов и статистика.
 * Настройки берутся из env: TESTIT_URL, TESTIT_TOKEN, TESTIT_PROJECT_ID (optional, проект по умолчанию).
 */
public class TestItClient {

    private static final int PAGE = 100;
    private static final int MAX_ITEMS = 10_000;

    private final WorkItemsApi workItemsApi;
    private final ProjectWorkItemsApi projectWorkItemsApi;
    private final AutoTestsApi autoTestsApi;
    private final TestRunsApi testRunsApi;
    private final UUID projectId;

    public TestItClient() {
        String url = requiredEnv("TESTIT_URL");
        String token = requiredEnv("TESTIT_TOKEN");
        String projectIdRaw = System.getenv("TESTIT_PROJECT_ID");
        this.projectId = (projectIdRaw == null || projectIdRaw.isBlank()) ? null : UUID.fromString(projectIdRaw.trim());

        ApiClient apiClient = new ApiClient(true);
        apiClient.setBasePath(url.trim());
        apiClient.setApiKey(token.trim());
        apiClient.setApiKeyPrefix("PrivateToken");
        this.workItemsApi = new WorkItemsApi(apiClient);
        this.projectWorkItemsApi = new ProjectWorkItemsApi(apiClient);
        this.autoTestsApi = new AutoTestsApi(apiClient);
        this.testRunsApi = new TestRunsApi(apiClient);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Env variable " + name + " is required");
        }
        return value;
    }

    /** Поиск кейсов по подстроке в названии. Возвращает "globalId | name | uuid" построчно. */
    public String searchCases(String query, int take) throws ApiException {
        WorkItemSelectApiModel select = new WorkItemSelectApiModel();
        if (projectId != null) {
            WorkItemFilterApiModel filter = new WorkItemFilterApiModel();
            filter.setProjectIds(Set.of(projectId));
            select.setFilter(filter);
        }

        List<WorkItemShortApiResult> found =
                workItemsApi.apiV2WorkItemsSearchPost(0, take, null, "Name", query, select);
        if (found == null || found.isEmpty()) {
            return "Ничего не найдено по запросу: " + query;
        }
        StringBuilder sb = new StringBuilder();
        for (WorkItemShortApiResult item : found) {
            sb.append(item.getGlobalId())
                    .append(" | ").append(item.getName())
                    .append(" | ").append(item.getId())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    /** Полный кейс по UUID: описание, предусловия, шаги, постусловия — markdown для агента. */
    public String getCase(String id) throws ApiException {
        WorkItemModel item = workItemsApi.getWorkItemById(id.trim(), null, null);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(item.getName()).append('\n');
        sb.append("ID: ").append(item.getId());
        if (item.getGlobalId() != null) {
            sb.append(" (№").append(item.getGlobalId()).append(')');
        }
        sb.append('\n');
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            sb.append("\n## Описание\n").append(item.getDescription().strip()).append('\n');
        }
        appendSteps(sb, "Предусловия", item.getPreconditionSteps());
        appendSteps(sb, "Шаги", item.getSteps());
        appendSteps(sb, "Постусловия", item.getPostconditionSteps());
        return sb.toString().trim();
    }

    private static void appendSteps(StringBuilder sb, String title, List<StepModel> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        sb.append("\n## ").append(title).append('\n');
        int n = 1;
        for (StepModel step : steps) {
            sb.append(n++).append(". Действие: ").append(nullToEmpty(step.getAction())).append('\n');
            if (step.getTestData() != null && !step.getTestData().isBlank()) {
                sb.append("   Данные: ").append(step.getTestData().strip()).append('\n');
            }
            sb.append("   Ожидание: ").append(nullToEmpty(step.getExpected())).append('\n');
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    // ---------------- статистика ----------------

    /** Проект: явный параметр перекрывает TESTIT_PROJECT_ID; null — все проекты. */
    private UUID resolveProject(String projectIdRaw) {
        if (projectIdRaw != null && !projectIdRaw.isBlank()) {
            return UUID.fromString(projectIdRaw.trim());
        }
        return projectId;
    }

    private static DateTimeRangeSelectorModel dateRange(String from, String to) {
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

    private static String periodLabel(String from, String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            return "весь период";
        }
        return (from == null || from.isBlank() ? "…" : from.trim())
                + " — " + (to == null || to.isBlank() ? "…" : to.trim());
    }

    /**
     * Покрытие: текущий срез ручные/авто + созданные за период.
     * Срез по проекту — одним grouped-запросом, без проекта — пагинацией.
     */
    public String coverageStats(String projectIdRaw, String from, String to) throws ApiException {
        UUID pid = resolveProject(projectIdRaw);
        long manualNow;
        long autoNow;
        if (pid != null) {
            WorkItemGroupGetModel group = new WorkItemGroupGetModel();
            group.setGroupType(WorkItemGroupType.AUTOMATION_STATUS);
            manualNow = 0;
            autoNow = 0;
            for (WorkItemGroupModel g : projectWorkItemsApi
                    .apiV2ProjectsProjectIdWorkItemsSearchGroupedPost(pid.toString(), 0, 10, null, null, null, group)) {
                if (Boolean.TRUE.equals(asBoolean(g.getKey()))) {
                    autoNow = size(g);
                } else {
                    manualNow = size(g);
                }
            }
        } else {
            manualNow = countWorkItems(null, false, null, null);
            autoNow = countWorkItems(null, true, null, null);
        }
        long manualNew = countWorkItems(pid, false, from, to);
        long autoNew = countWorkItems(pid, true, from, to);

        long totalNow = manualNow + autoNow;
        long totalNew = manualNew + autoNew;
        double pct = totalNow == 0 ? 0 : 100.0 * autoNow / totalNow;

        StringBuilder sb = new StringBuilder();
        sb.append("# Покрытие тест-кейсами\n");
        sb.append("Проект: ").append(pid == null ? "все" : pid).append('\n');
        sb.append("Период: ").append(periodLabel(from, to)).append("\n\n");
        sb.append("## Текущий срез\n");
        sb.append("Всего: ").append(totalNow)
                .append(" (ручных: ").append(manualNow)
                .append(", автоматизированных: ").append(autoNow).append(")\n");
        sb.append("Автоматизация: ").append(String.format("%.1f", pct)).append("%\n\n");
        sb.append("## Создано за период\n");
        sb.append("Всего: ").append(totalNew)
                .append(" (ручных: ").append(manualNew)
                .append(", автоматизированных: ").append(autoNew).append(")\n");
        return sb.toString().trim();
    }

    private static boolean asBoolean(Object key) {
        return key instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(key));
    }

    private static long size(WorkItemGroupModel g) {
        return g.getSize() == null ? 0 : g.getSize();
    }

    /** Подсчёт кейсов пагинацией: проект (null — все), isAutomated, диапазон createdDate. */
    private long countWorkItems(UUID pid, boolean automated, String from, String to) throws ApiException {
        long total = 0;
        int skip = 0;
        while (total < MAX_ITEMS) {
            WorkItemFilterApiModel filter = new WorkItemFilterApiModel();
            if (pid != null) {
                filter.setProjectIds(Set.of(pid));
            }
            filter.setIsAutomated(automated);
            filter.setIsDeleted(false);
            DateTimeRangeSelectorModel range = dateRange(from, to);
            if (range != null) {
                filter.setCreatedDate(range);
            }
            WorkItemSelectApiModel select = new WorkItemSelectApiModel();
            select.setFilter(filter);
            List<WorkItemShortApiResult> page =
                    workItemsApi.apiV2WorkItemsSearchPost(skip, PAGE, null, null, null, select);
            if (page == null || page.isEmpty()) {
                break;
            }
            total += page.size();
            if (page.size() < PAGE) {
                break;
            }
            skip += PAGE;
        }
        return total;
    }

    /** Автотесты за период: всего + по исходам последних запусков. */
    public String autotestsStats(String projectIdRaw, String from, String to) throws ApiException {
        UUID pid = resolveProject(projectIdRaw);
        List<AutoTestApiResult> all = new ArrayList<>();
        int skip = 0;
        while (all.size() < MAX_ITEMS) {
            AutoTestFilterApiModel filter = new AutoTestFilterApiModel();
            if (pid != null) {
                filter.setProjectIds(Set.of(pid));
            }
            filter.setIsDeleted(false);
            DateTimeRangeSelectorModel range = dateRange(from, to);
            if (range != null) {
                filter.setCreatedDate(range);
            }
            AutoTestSearchApiModel select = new AutoTestSearchApiModel();
            select.setFilter(filter);
            List<AutoTestApiResult> page = autoTestsApi.apiV2AutoTestsSearchPost(skip, PAGE, null, null, null, select);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < PAGE) {
                break;
            }
            skip += PAGE;
        }

        Map<String, Long> byOutcome = new TreeMap<>();
        for (AutoTestApiResult at : all) {
            String outcome = at.getLastTestResultOutcome();
            if (outcome == null || outcome.isBlank()) {
                outcome = "без запусков";
            }
            byOutcome.merge(outcome, 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Автотесты\n");
        sb.append("Проект: ").append(pid == null ? "все" : pid).append('\n');
        sb.append("Период создания: ").append(periodLabel(from, to)).append("\n\n");
        sb.append("Всего: ").append(all.size()).append('\n');
        if (!byOutcome.isEmpty()) {
            sb.append("\n## По исходам последних запусков\n");
            for (Map.Entry<String, Long> e : byOutcome.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** Прогоны за период: список + суммарные исходы. */
    public String runsStats(String projectIdRaw, String from, String to) throws ApiException {
        UUID pid = resolveProject(projectIdRaw);
        List<TestRunShortApiResult> all = new ArrayList<>();
        int skip = 0;
        while (all.size() < MAX_ITEMS) {
            TestRunFilterApiModel filter = new TestRunFilterApiModel();
            if (pid != null) {
                filter.setProjectIds(List.of(pid));
            }
            filter.setIsDeleted(false);
            DateTimeRangeSelectorModel range = dateRange(from, to);
            if (range != null) {
                filter.setCreatedDate(range);
            }
            List<TestRunShortApiResult> page = testRunsApi.apiV2TestRunsSearchPost(skip, PAGE, null, null, null, filter);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < PAGE) {
                break;
            }
            skip += PAGE;
        }

        Map<String, Long> sum = new LinkedHashMap<>();
        for (String k : List.of("passed", "failed", "blocked", "skipped", "inProgress", "incomplete")) {
            sum.put(k, 0L);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Тест-прогоны\n");
        sb.append("Проект: ").append(pid == null ? "все" : pid).append('\n');
        sb.append("Период создания: ").append(periodLabel(from, to)).append("\n\n");
        sb.append("Всего прогонов: ").append(all.size()).append('\n');

        int shown = 0;
        for (TestRunShortApiResult run : all) {
            if (run.getStatistics() != null && run.getStatistics().getStatuses() != null) {
                var st = run.getStatistics().getStatuses();
                sum.merge("passed", nn(st.getPassed()) + nn(st.getSucceeded()), Long::sum);
                sum.merge("failed", nn(st.getFailed()), Long::sum);
                sum.merge("blocked", nn(st.getBlocked()), Long::sum);
                sum.merge("skipped", nn(st.getSkipped()), Long::sum);
                sum.merge("inProgress", nn(st.getInProgress()), Long::sum);
                sum.merge("incomplete", nn(st.getIncomplete()), Long::sum);
            }
            if (shown < 50) {
                sb.append("\n## ").append(run.getName()).append('\n');
                sb.append("Создан: ").append(run.getCreatedDate());
                if (run.getCompletedDate() != null) {
                    sb.append(", завершён: ").append(run.getCompletedDate());
                }
                if (run.getAutoTestsCount() != null) {
                    sb.append(", автотестов: ").append(run.getAutoTestsCount());
                }
                sb.append('\n');
                if (run.getStatistics() != null && run.getStatistics().getStatuses() != null) {
                    var st = run.getStatistics().getStatuses();
                    sb.append("passed=").append(nn(st.getPassed()) + nn(st.getSucceeded()))
                            .append(" failed=").append(nn(st.getFailed()))
                            .append(" blocked=").append(nn(st.getBlocked()))
                            .append(" skipped=").append(nn(st.getSkipped())).append('\n');
                }
                shown++;
            }
        }
        if (all.size() > shown) {
            sb.append("\n(показано ").append(shown).append(" из ").append(all.size()).append(")\n");
        }
        sb.append("\n## Итого исходы\n");
        for (Map.Entry<String, Long> e : sum.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    private static long nn(Integer v) {
        return v == null ? 0 : v;
    }
}
