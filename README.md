# testit-mcp

MCP server exposing TestIT over stdio (74 tools, read-only): test cases and full
scenarios for Playwright autotest generation, manager statistics, and every other
read endpoint of the TestIT API (projects, plans, suites, points, results, runs,
attachments, attributes, parameters).

Conventions: all tools return JSON (markdown for `testit_get_case` and the
`testit_stats_*` reports). List tools paginate with `take` (default 50, max 200).
`projectId` defaults to `TESTIT_PROJECT_ID`. Periods: `YYYY-MM-DD` or ISO 8601.

## Requirements

- Java 17+ to run and build (`java -version`)
- Maven to build
- A TestIT instance with a private token

## Build

```bash
mvn package -DskipTests
# -> target/testit-mcp-1.0.0.jar (fat jar)
```

## Run

```bash
TESTIT_URL=https://your.testit.host \
TESTIT_TOKEN=<private-token> \
TESTIT_PROJECT_ID=<project-uuid, optional> \
java -jar target/testit-mcp-1.0.0.jar
```

## Configuration (env)

| Var | Required | Description |
|---|---|---|
| `TESTIT_URL` | yes | Base URL of the TestIT instance, e.g. `https://team-nc7a.testit.software` |
| `TESTIT_TOKEN` | yes | PrivateToken for API access |
| `TESTIT_PROJECT_ID` | no | Project UUID to scope the search; omit to search all projects |

See `.env.example`.

## Use with opencode

`opencode.json`:

```json
{
  "mcp": {
    "testit": {
      "type": "local",
      "command": ["java", "-jar", "/path/to/testit-mcp-1.0.0.jar"],
      "enabled": true,
      "timeout": 15000,
      "environment": {
        "TESTIT_URL": "{env:TESTIT_URL}",
        "TESTIT_TOKEN": "{env:TESTIT_TOKEN}",
        "TESTIT_PROJECT_ID": "{env:TESTIT_PROJECT_ID}"
      }
    }
  }
}
```

## Tools (74)

Cases & stats:

| Tool | Description |
|---|---|
| `testit_search_cases` | Find test cases by name substring. Returns `globalId \| name \| uuid`. |
| `testit_get_case` | Full test case (markdown): description, pre/post-conditions, steps. |
| `testit_stats_coverage` | Manual/automated split + automation %, created in period. |
| `testit_stats_autotests` | Autotests created in period + last-run outcomes. |
| `testit_stats_runs` | Runs in period + total passed/failed/blocked/skipped. |
| `testit_case_versions` | Versions of a case. |
| `testit_case_history` | Change history of a case. |
| `testit_case_chronology` | Result chronology of a case. |
| `testit_case_comments` / `testit_case_comments_count` | Comments / their count. |
| `testit_case_autotests` | Autotests linked to a case. |
| `testit_case_iterations` | Iterations (parametrization) of a case. |
| `testit_case_results_history` | Result history of a case in a period. |
| `testit_sharedstep_refs` | Where a shared step is used. |

Projects, sections, configurations:

| Tool | Description |
|---|---|
| `testit_list_projects` / `testit_search_projects` / `testit_get_project` | Projects: list, search, one. |
| `testit_project_testplans` | Test plans of a project. |
| `testit_project_namespaces` | Autotest namespaces of a project with counts. |
| `testit_list_sections` / `testit_get_section` / `testit_section_cases` | Sections and their cases. |
| `testit_list_configurations` / `testit_get_configuration` / `testit_search_configurations` | Configurations. |
| `testit_search_tags` | Tags. |
| `testit_search_statuses` / `testit_get_status` | Result statuses. |
| `testit_user_exists` | Check user existence. |

Test plans, suites, points, results:

| Tool | Description |
|---|---|
| `testit_get_testplan` / `testit_search_testplans` | One plan / search in project. |
| `testit_testplan_suites` | Suite hierarchy of a plan. |
| `testit_testplan_analytics` / `testit_testplan_summary` | Analytics and summary (manager view). |
| `testit_testplan_history` / `testit_testplan_links` / `testit_testplan_configurations` | History, links, configurations. |
| `testit_testplan_lastresults` | Last point results (optional configuration). |
| `testit_testplan_runs` | Runs of a plan. |
| `testit_project_plans_analytics` | Project plans with analytics. |
| `testit_get_suite` / `testit_suite_points` / `testit_suite_results` / `testit_suite_configurations` / `testit_suite_cases` | Suite details. |
| `testit_search_points` | Points (requires `testPlanId` or `suiteId`). |
| `testit_point_case` / `testit_point_runs` | Case and runs of a point. |
| `testit_search_results` / `testit_get_result` / `testit_result_aggregated` / `testit_result_reruns` / `testit_result_attachments` | Results. |
| `testit_results_statistics` | Global result statistics for a period. |

Autotests, runs, misc:

| Tool | Description |
|---|---|
| `testit_get_autotest` / `testit_autotest_history` / `testit_autotest_chronology` | Autotest details and history. |
| `testit_autotest_runs` / `testit_autotest_cases` | Runs with it / linked cases. |
| `testit_autotest_avg_duration` / `testit_autotest_changed_links` | Avg duration / unapproved links. |
| `testit_get_run` / `testit_run_results` / `testit_run_statistics` / `testit_run_namespaces` | Run details. |
| `testit_attachment_info` / `testit_storage_size` | Attachment metadata / storage usage. |
| `testit_search_attributes` / `testit_get_attribute` / `testit_attribute_exists` | Custom attributes. |
| `testit_global_search` | Global TestIT search. |
| `testit_params_groups` / `testit_params_values` / `testit_search_params` | Parameters. |

Typical flows: `testit_search_cases` → `testit_get_case` for autotest generation;
`testit_stats_coverage`, `testit_testplan_analytics`, `testit_results_statistics` for reports.

> Notes: `testit_global_search` needs a TestIT version exposing
> `/api/v2/search/globalSearch`. On some servers `testResults/search`-based tools
> (`testit_search_results`, `testit_results_statistics`) may be slow — server-side.
