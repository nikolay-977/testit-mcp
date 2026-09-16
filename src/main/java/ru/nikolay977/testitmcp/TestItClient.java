package ru.nikolay977.testitmcp;

import ru.testit.client.api.WorkItemsApi;
import ru.testit.client.invoker.ApiClient;
import ru.testit.client.invoker.ApiException;
import ru.testit.client.model.StepModel;
import ru.testit.client.model.WorkItemFilterApiModel;
import ru.testit.client.model.WorkItemModel;
import ru.testit.client.model.WorkItemSelectApiModel;
import ru.testit.client.model.WorkItemShortApiResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Тонкая обёртка над TestIT api-client: только чтение тест-кейсов.
 * Настройки берутся из env: TESTIT_URL, TESTIT_TOKEN, TESTIT_PROJECT_ID (optional).
 */
public class TestItClient {

    private final WorkItemsApi workItemsApi;
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
}
