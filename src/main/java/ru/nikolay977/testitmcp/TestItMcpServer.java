package ru.nikolay977.testitmcp;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * MCP-сервер (stdio) с чтением тест-кейсов TestIT.
 * Env: TESTIT_URL, TESTIT_TOKEN, TESTIT_PROJECT_ID (optional).
 *
 * Tools:
 * - testit_search_cases {query, take?} — поиск кейсов по названию
 * - testit_get_case {id} — полный кейс со шагами в markdown
 */
public class TestItMcpServer {

    private static final Logger log = LoggerFactory.getLogger(TestItMcpServer.class);

    public static void main(String[] args) throws Exception {
        TestItClient testIt = new TestItClient();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        var transport = new StdioServerTransportProvider(jsonMapper);

        McpServer.sync(transport)
                .serverInfo("testit-mcp", "1.0.0")
                .instructions("Чтение тест-кейсов TestIT для написания Playwright-автотестов. " +
                        "Сначала testit_search_cases для поиска, затем testit_get_case для шагов кейса.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(List.of(searchCasesTool(jsonMapper, testIt), getCaseTool(jsonMapper, testIt)))
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

    private static McpSchema.CallToolResult callSafely(ThrowingSupplier<String> action) {
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
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
