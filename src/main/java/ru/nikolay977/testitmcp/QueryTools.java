package ru.nikolay977.testitmcp;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.List;
import java.util.Map;

/**
 * Read-ручки TestIT (проекты, планы, прогоны, результаты, ...).
 * Все возвращают JSON. Пагинация: take по умолчанию 50, максимум 200.
 */
final class QueryTools {

    private static final String TAKE = "\"take\":{\"type\":\"integer\",\"description\":\"Максимум результатов (по умолчанию 50, максимум 200)\"}";
    private static final String PID =
            "\"projectId\":{\"type\":\"string\",\"description\":\"UUID проекта. По умолчанию TESTIT_PROJECT_ID\"}";
    private static final String FROM =
            "\"from\":{\"type\":\"string\",\"description\":\"Начало периода: YYYY-MM-DD или ISO 8601\"}";
    private static final String TO =
            "\"to\":{\"type\":\"string\",\"description\":\"Конец периода: YYYY-MM-DD или ISO 8601\"}";
    private static final String ID =
            "\"id\":{\"type\":\"string\",\"description\":\"UUID\"}";
    private static final String QUERY =
            "\"query\":{\"type\":\"string\",\"description\":\"Подстрока в названии\"}";

    @FunctionalInterface
    interface Q {
        String get(Map<String, Object> args) throws Exception;
    }

    static List<McpServerFeatures.SyncToolSpecification> all(JacksonMcpJsonMapper m, TestItClient client) {
        TestItQueries q = client.queries();
        return List.of(
                // проекты
                def(m, "testit_list_projects", "Список проектов", o(TAKE), null,
                        a -> q.listProjects(take(a))),
                def(m, "testit_search_projects", "Поиск проектов по названию", o(QUERY, TAKE), null,
                        a -> q.searchProjects(s(a, "query"), take(a))),
                def(m, "testit_get_project", "Проект по UUID", o(ID), req("id"),
                        a -> q.getProject(reqStr(a, "id"))),
                def(m, "testit_project_testplans", "Тест-планы проекта", o(PID), null,
                        a -> q.projectTestPlans(s(a, "projectId"))),
                def(m, "testit_project_namespaces", "Неймспейсы автотестов проекта с количеством", o(PID), null,
                        a -> q.projectNamespaces(s(a, "projectId"))),
                // секции
                def(m, "testit_list_sections", "Секции проекта", o(PID, TAKE), null,
                        a -> q.listSections(s(a, "projectId"), take(a))),
                def(m, "testit_get_section", "Секция по UUID", o(ID), req("id"),
                        a -> q.getSection(reqStr(a, "id"))),
                def(m, "testit_section_cases", "Кейсы секции", o(
                        "\"sectionId\":{\"type\":\"string\",\"description\":\"UUID секции\"}", TAKE), req("sectionId"),
                        a -> q.sectionCases(reqStr(a, "sectionId"), take(a))),
                // конфигурации
                def(m, "testit_list_configurations", "Конфигурации проекта", o(PID), null,
                        a -> q.listConfigurations(s(a, "projectId"))),
                def(m, "testit_get_configuration", "Конфигурация по UUID", o(ID), req("id"),
                        a -> q.getConfiguration(reqStr(a, "id"))),
                def(m, "testit_search_configurations", "Поиск конфигураций", o(PID, QUERY, TAKE), null,
                        a -> q.searchConfigurations(s(a, "projectId"), s(a, "query"), take(a))),
                // теги / статусы / пользователи
                def(m, "testit_search_tags", "Поиск тегов", o(QUERY, TAKE), null,
                        a -> q.searchTags(s(a, "query"), take(a))),
                def(m, "testit_search_statuses", "Все статусы результатов", o(), null,
                        a -> q.searchStatuses()),
                def(m, "testit_get_status", "Статус по UUID", o(ID), req("id"),
                        a -> q.getStatus(reqStr(a, "id"))),
                def(m, "testit_user_exists", "Проверка существования пользователя", o(
                        "\"username\":{\"type\":\"string\",\"description\":\"Имя пользователя\"}"), req("username"),
                        a -> q.userExists(reqStr(a, "username"))),
                // тест-планы
                def(m, "testit_get_testplan", "Тест-план по UUID", o(ID), req("id"),
                        a -> q.getTestPlan(reqStr(a, "id"))),
                def(m, "testit_testplan_suites", "Сьюты тест-плана (иерархия)", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}"), req("testPlanId"),
                        a -> q.testPlanSuites(reqStr(a, "testPlanId"))),
                def(m, "testit_testplan_analytics", "Аналитика тест-плана (исходы по поинтам)", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}"), req("testPlanId"),
                        a -> q.testPlanAnalytics(reqStr(a, "testPlanId"))),
                def(m, "testit_testplan_summary", "Сводка тест-плана", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}"), req("testPlanId"),
                        a -> q.testPlanSummary(reqStr(a, "testPlanId"))),
                def(m, "testit_testplan_history", "История изменений тест-плана", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}", TAKE), req("testPlanId"),
                        a -> q.testPlanHistory(reqStr(a, "testPlanId"), take(a))),
                def(m, "testit_testplan_configurations", "Конфигурации тест-плана", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}"), req("testPlanId"),
                        a -> q.testPlanConfigurations(reqStr(a, "testPlanId"))),
                def(m, "testit_testplan_links", "Ссылки тест-плана", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}", TAKE), req("testPlanId"),
                        a -> q.testPlanLinks(reqStr(a, "testPlanId"), take(a))),
                def(m, "testit_testplan_lastresults", "Последние результаты поинтов плана", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}",
                        "\"configurationId\":{\"type\":\"string\",\"description\":\"UUID конфигурации (опционально)\"}", TAKE),
                        req("testPlanId"),
                        a -> q.testPlanLastResults(reqStr(a, "testPlanId"), s(a, "configurationId"), take(a))),
                def(m, "testit_testplan_runs", "Прогоны тест-плана", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана\"}", TAKE), req("testPlanId"),
                        a -> q.testPlanRuns(reqStr(a, "testPlanId"), take(a))),
                def(m, "testit_search_testplans", "Поиск тест-планов в проекте", o(PID, QUERY, TAKE), null,
                        a -> q.searchTestPlans(s(a, "projectId"), s(a, "query"), take(a))),
                def(m, "testit_project_plans_analytics", "Тест-планы проекта с аналитикой", o(PID, TAKE), null,
                        a -> q.projectPlansAnalytics(s(a, "projectId"), take(a))),
                // сьюты
                def(m, "testit_get_suite", "Сьюта по UUID", o(ID), req("id"),
                        a -> q.getSuite(reqStr(a, "id"))),
                def(m, "testit_suite_points", "Тест-поинты сьюты", o(
                        "\"suiteId\":{\"type\":\"string\",\"description\":\"UUID сьюты\"}"), req("suiteId"),
                        a -> q.suitePoints(reqStr(a, "suiteId"))),
                def(m, "testit_suite_results", "Результаты сьюты", o(
                        "\"suiteId\":{\"type\":\"string\",\"description\":\"UUID сьюты\"}"), req("suiteId"),
                        a -> q.suiteResults(reqStr(a, "suiteId"))),
                def(m, "testit_suite_configurations", "Конфигурации сьюты", o(
                        "\"suiteId\":{\"type\":\"string\",\"description\":\"UUID сьюты\"}"), req("suiteId"),
                        a -> q.suiteConfigurations(reqStr(a, "suiteId"))),
                def(m, "testit_suite_cases", "Кейсы сьюты", o(
                        "\"suiteId\":{\"type\":\"string\",\"description\":\"UUID сьюты\"}", QUERY, TAKE), req("suiteId"),
                        a -> q.suiteCases(reqStr(a, "suiteId"), s(a, "query"), take(a))),
                // поинты
                def(m, "testit_search_points", "Поиск тест-поинтов", o(
                        "\"testPlanId\":{\"type\":\"string\",\"description\":\"UUID тест-плана (опционально)\"}",
                        "\"suiteId\":{\"type\":\"string\",\"description\":\"UUID сьюты (опционально)\"}", QUERY, TAKE), null,
                        a -> q.searchPoints(s(a, "testPlanId"), s(a, "suiteId"), s(a, "query"), take(a))),
                def(m, "testit_point_case", "Кейс тест-поинта", o(
                        "\"pointId\":{\"type\":\"string\",\"description\":\"UUID поинта\"}"), req("pointId"),
                        a -> q.pointCase(reqStr(a, "pointId"))),
                def(m, "testit_point_runs", "Прогоны тест-поинта", o(
                        "\"pointId\":{\"type\":\"string\",\"description\":\"UUID поинта\"}"), req("pointId"),
                        a -> q.pointRuns(reqStr(a, "pointId"))),
                // результаты
                def(m, "testit_search_results", "Поиск результатов (исход, период)", o(
                        "\"outcome\":{\"type\":\"string\",\"description\":\"PASSED, FAILED, SKIPPED, BLOCKED или IN_PROGRESS\"}",
                        FROM, TO, TAKE), null,
                        a -> q.searchResults(s(a, "outcome"), s(a, "from"), s(a, "to"), take(a))),
                def(m, "testit_get_result", "Результат по UUID", o(ID), req("id"),
                        a -> q.getResult(reqStr(a, "id"))),
                def(m, "testit_result_aggregated", "Агрегированный результат (дерево шагов)", o(ID), req("id"),
                        a -> q.resultAggregated(reqStr(a, "id"))),
                def(m, "testit_result_reruns", "Перезапуски результата", o(ID), req("id"),
                        a -> q.resultReruns(reqStr(a, "id"))),
                def(m, "testit_result_attachments", "Вложения результата", o(ID), req("id"),
                        a -> q.resultAttachments(reqStr(a, "id"))),
                def(m, "testit_results_statistics", "Глобальная статистика результатов за период", o(
                        "\"outcome\":{\"type\":\"string\",\"description\":\"Фильтр исхода: PASSED, FAILED, SKIPPED, BLOCKED\"}",
                        FROM, TO), null,
                        a -> q.resultsStatistics(s(a, "outcome"), s(a, "from"), s(a, "to"))),
                // кейсы (дополнительно)
                def(m, "testit_case_versions", "Версии кейса", o(ID), req("id"),
                        a -> q.caseVersions(reqStr(a, "id"))),
                def(m, "testit_case_history", "История изменений кейса", o(ID, TAKE), req("id"),
                        a -> q.caseHistory(reqStr(a, "id"), take(a))),
                def(m, "testit_case_chronology", "Хронология результатов кейса", o(ID), req("id"),
                        a -> q.caseChronology(reqStr(a, "id"))),
                def(m, "testit_case_comments", "Комментарии кейса", o(ID), req("id"),
                        a -> q.caseComments(reqStr(a, "id"))),
                def(m, "testit_case_comments_count", "Количество комментариев кейса", o(ID), req("id"),
                        a -> q.caseCommentsCount(reqStr(a, "id"))),
                def(m, "testit_case_autotests", "Автотесты, связанные с кейсом", o(ID), req("id"),
                        a -> q.caseAutotests(reqStr(a, "id"))),
                def(m, "testit_case_iterations", "Итерации (параметризация) кейса", o(ID), req("id"),
                        a -> q.caseIterations(reqStr(a, "id"))),
                def(m, "testit_case_results_history", "История результатов кейса за период", o(ID, FROM, TO, TAKE), req("id"),
                        a -> q.caseResultsHistory(reqStr(a, "id"), s(a, "from"), s(a, "to"), take(a))),
                def(m, "testit_sharedstep_refs", "Где используется общий шаг", o(
                        "\"sharedStepId\":{\"type\":\"string\",\"description\":\"UUID общего шага\"}"), req("sharedStepId"),
                        a -> q.sharedStepRefs(reqStr(a, "sharedStepId"))),
                // автотесты (дополнительно)
                def(m, "testit_get_autotest", "Автотест по UUID", o(ID), req("id"),
                        a -> q.getAutotest(reqStr(a, "id"))),
                def(m, "testit_autotest_history", "История запусков автотеста", o(ID, TAKE), req("id"),
                        a -> q.autotestHistory(reqStr(a, "id"), take(a))),
                def(m, "testit_autotest_chronology", "Хронология автотеста", o(ID), req("id"),
                        a -> q.autotestChronology(reqStr(a, "id"))),
                def(m, "testit_autotest_runs", "Прогоны с автотестом", o(ID), req("id"),
                        a -> q.autotestRuns(reqStr(a, "id"))),
                def(m, "testit_autotest_cases", "Кейсы, связанные с автотестом", o(ID), req("id"),
                        a -> q.autotestCases(reqStr(a, "id"))),
                def(m, "testit_autotest_avg_duration", "Средняя длительность автотеста", o(ID), req("id"),
                        a -> q.autotestAvgDuration(reqStr(a, "id"))),
                def(m, "testit_autotest_changed_links", "Неподтверждённые связки автотеста с кейсами", o(ID), req("id"),
                        a -> q.autotestChangedLinks(reqStr(a, "id"))),
                // прогоны (дополнительно)
                def(m, "testit_get_run", "Прогон по UUID", o(ID), req("id"),
                        a -> q.getRun(reqStr(a, "id"))),
                def(m, "testit_run_results", "Результаты поинтов прогона", o(
                        "\"runId\":{\"type\":\"string\",\"description\":\"UUID прогона\"}"), req("runId"),
                        a -> q.runResults(reqStr(a, "runId"))),
                def(m, "testit_run_statistics", "Статистика прогона с фильтром", o(
                        "\"runId\":{\"type\":\"string\",\"description\":\"UUID прогона\"}"), req("runId"),
                        a -> q.runStatistics(reqStr(a, "runId"))),
                def(m, "testit_run_namespaces", "Неймспейсы автотестов прогона", o(
                        "\"runId\":{\"type\":\"string\",\"description\":\"UUID прогона\"}"), req("runId"),
                        a -> q.runNamespaces(reqStr(a, "runId"))),
                // вложения / атрибуты / поиск / параметры
                def(m, "testit_attachment_info", "Метаданные вложения", o(ID), req("id"),
                        a -> q.attachmentInfo(reqStr(a, "id"))),
                def(m, "testit_storage_size", "Занятое файловое хранилище (байты)", o(), null,
                        a -> q.storageSize()),
                def(m, "testit_search_attributes", "Поиск кастомных атрибутов", o(PID, QUERY, TAKE), null,
                        a -> q.searchAttributes(s(a, "projectId"), s(a, "query"), take(a))),
                def(m, "testit_get_attribute", "Атрибут по UUID", o(ID), req("id"),
                        a -> q.getAttribute(reqStr(a, "id"))),
                def(m, "testit_attribute_exists", "Проверка существования атрибута", o(
                        "\"name\":{\"type\":\"string\",\"description\":\"Имя атрибута\"}"), req("name"),
                        a -> q.attributeExists(reqStr(a, "name"))),
                def(m, "testit_global_search", "Глобальный поиск по TestIT", o(
                        "\"query\":{\"type\":\"string\",\"description\":\"Поисковый запрос\"}", TAKE), req("query"),
                        a -> q.globalSearch(reqStr(a, "query"), take(a))),
                def(m, "testit_params_groups", "Группы параметров", o(PID, TAKE), null,
                        a -> q.paramsGroups(s(a, "projectId"), take(a))),
                def(m, "testit_params_values", "Значения параметра по ключу", o(
                        "\"key\":{\"type\":\"string\",\"description\":\"Ключ параметра\"}"), req("key"),
                        a -> q.paramsValues(reqStr(a, "key"))),
                def(m, "testit_search_params", "Поиск параметров", o(PID, QUERY, TAKE), null,
                        a -> q.searchParams(s(a, "projectId"), s(a, "query"), take(a))));
    }

    // ---------- plumbing ----------

    private static McpServerFeatures.SyncToolSpecification def(
            JacksonMcpJsonMapper m, String name, String description, String props, List<String> required, Q call) {
        StringBuilder schema = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        schema.append(props).append('}');
        if (required != null && !required.isEmpty()) {
            schema.append(",\"required\":[");
            for (int i = 0; i < required.size(); i++) {
                if (i > 0) {
                    schema.append(',');
                }
                schema.append('"').append(required.get(i)).append('"');
            }
            schema.append(']');
        }
        schema.append('}');
        var tool = io.modelcontextprotocol.spec.McpSchema.Tool.builder()
                .name(name).description(description).inputSchema(m, schema.toString()).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> TestItMcpServer.callSafely(() -> call.get(request.arguments())))
                .build();
    }

    private static String o(String... props) {
        return String.join(",", props);
    }

    private static List<String> req(String... keys) {
        return List.of(keys);
    }

    static String s(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    static String reqStr(Map<String, Object> args, String key) {
        String v = s(args, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter '" + key + "'");
        }
        return v;
    }

    static int take(Map<String, Object> args) {
        Object v = args.get("take");
        if (v == null) {
            return 50;
        }
        int t = v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString().trim());
        return Math.min(Math.max(t, 1), 200);
    }
}
