package ru.nikolay977.testitmcp;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP-сервер (stdio) с чтением тест-кейсов TestIT и статистикой.
 * Env: TESTIT_URL, TESTIT_TOKEN, TESTIT_PROJECT_ID (optional).
 *
 * Tools:
 * - testit_search_cases {query, take?} — поиск кейсов по названию
 * - testit_get_case {id} — полный кейс со шагами в markdown
 * - testit_stats_coverage / testit_stats_autotests / testit_stats_runs — статистика для менеджера
 * - testit_* — все read-ручки TestIT: проекты, секции, конфигурации, теги, статусы,
 *   тест-планы, сьюты, поинты, результаты, кейсы, автотесты, прогоны, вложения, атрибуты, параметры
 */
public class TestItMcpServer {

    private static final Logger log = LoggerFactory.getLogger(TestItMcpServer.class);

    public static void main(String[] args) throws Exception {
        TestItClient testIt = new TestItClient();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        var transport = new StdioServerTransportProvider(jsonMapper);

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>(List.of(
                searchCasesTool(jsonMapper, testIt), getCaseTool(jsonMapper, testIt),
                coverageTool(jsonMapper, testIt), autotestsTool(jsonMapper, testIt), runsTool(jsonMapper, testIt)));
        tools.addAll(QueryTools.all(jsonMapper, testIt));

        McpServer.sync(transport)
                .serverInfo("testit-mcp", "1.1.0")
                .instructions("Полный доступ на чтение к TestIT. Кейсы: testit_search_cases, затем testit_get_case. " +
                        "Статистика менеджера: testit_stats_coverage, testit_stats_autotests, testit_stats_runs, " +
                        "testit_testplan_analytics, testit_results_statistics. " +
                        "Остальное: testit_* инструменты по доменам (проекты, планы, сьюты, поинты, результаты, прогоны).")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(tools)
                .build();

        log.info("testit-mcp started, waiting for MCP client on stdio");
        Thread.currentThread().join();
    }

    private static McpServerFeatures.SyncToolSpecification searchCasesTool(
            JacksonMcpJsonMapper jsonMapper, TestItClient testIt) {
        var tool = McpSchema.Tool.builder()
                .name("testit_search_cases")
                .description("Найти тест-кейсы TestIT по подстроке в названии. " +
                        "Возвращает строки 'globalId | название | uuid'.")
                .inputSchema(jsonMapper, """
                        {"type":"object",
                         "properties":{
                           "query":{"type":"string","description":"Подстрока в названии кейса"},
                           "take":{"type":"integer","description":"Максимум результатов","default":10}},
                         "required":["query"]}""")
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callSafely(() -> {
                    Map<String, Object> args = request.arguments();
                    String query = String.valueOf(args.get("query"));
                    int take = args.get("take") instanceof Number n ? n.intValue() : 10;
                    return testIt.searchCases(query, take);
                }))
                .build();
    }

    private static McpServerFeatures.SyncToolSpecification getCaseTool(
            JacksonMcpJsonMapper jsonMapper, TestItClient testIt) {
        var tool = McpSchema.Tool.builder()
                .name("testit_get_case")
                .description("Прочитать полный тест-кейс TestIT по UUID: название, описание, " +
                        "предусловия, шаги (действие/данные/ожидание), постусловия.")
                .inputSchema(jsonMapper, """
                        {"type":"object",
                         "properties":{
                           "id":{"type":"string","description":"UUID кейса из testit_search_cases"}},
                         "required":["id"]}""")
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callSafely(() ->
                        testIt.getCase(String.valueOf(request.arguments().get("id")))))
                .build();
    }

    private static final String STATS_SCHEMA = """
            {"type":"object",
             "properties":{
               "projectId":{"type":"string","description":"UUID проекта. По умолчанию TESTIT_PROJECT_ID, без него — все проекты"},
               "from":{"type":"string","description":"Начало периода: YYYY-MM-DD или ISO 8601. По умолчанию без ограничения"},
               "to":{"type":"string","description":"Конец периода: YYYY-MM-DD или ISO 8601. По умолчанию без ограничения"}}}""";

    private static McpServerFeatures.SyncToolSpecification coverageTool(
            JacksonMcpJsonMapper jsonMapper, TestItClient testIt) {
        var tool = McpSchema.Tool.builder()
                .name("testit_stats_coverage")
                .description("Статистика кейсов: текущий срез ручные/автоматизированные + % автоматизации, " +
                        "и созданные за период. Для отчётности менеджера тестирования.")
                .inputSchema(jsonMapper, STATS_SCHEMA)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callSafely(() ->
                        testIt.coverageStats(str(request.arguments(), "projectId"),
                                str(request.arguments(), "from"), str(request.arguments(), "to"))))
                .build();
    }

    private static McpServerFeatures.SyncToolSpecification autotestsTool(
            JacksonMcpJsonMapper jsonMapper, TestItClient testIt) {
        var tool = McpSchema.Tool.builder()
                .name("testit_stats_autotests")
                .description("Статистика автотестов, созданных за период, с разбивкой по исходам последних запусков.")
                .inputSchema(jsonMapper, STATS_SCHEMA)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callSafely(() ->
                        testIt.autotestsStats(str(request.arguments(), "projectId"),
                                str(request.arguments(), "from"), str(request.arguments(), "to"))))
                .build();
    }

    private static McpServerFeatures.SyncToolSpecification runsTool(
            JacksonMcpJsonMapper jsonMapper, TestItClient testIt) {
        var tool = McpSchema.Tool.builder()
                .name("testit_stats_runs")
                .description("Тест-прогоны за период: список с исходами и суммарные passed/failed/blocked/skipped.")
                .inputSchema(jsonMapper, STATS_SCHEMA)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callSafely(() ->
                        testIt.runsStats(str(request.arguments(), "projectId"),
                                str(request.arguments(), "from"), str(request.arguments(), "to"))))
                .build();
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    static McpSchema.CallToolResult callSafely(ThrowingSupplier<String> action) {
        try {
            String text = action.get();
            return McpSchema.CallToolResult.builder()
                    .addTextContent(text)
                    .build();
        } catch (Exception e) {
            log.error("Tool call failed", e);
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Ошибка TestIT: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
