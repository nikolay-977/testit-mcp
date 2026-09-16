# testit-mcp

MCP server exposing TestIT test cases over stdio — lets an AI agent search test cases
and read full scenarios (steps, pre/post-conditions) to generate Playwright autotests.

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

## Tools (2)

| Tool | Description |
|---|---|
| `testit_search_cases` | Find test cases by name substring. Args: `query` (required), `take` (default 10). Returns lines `globalId \| name \| uuid`. |
| `testit_get_case` | Read a full test case by UUID: name, description, preconditions, steps (action / test data / expected), postconditions as markdown. |

Typical flow: `testit_search_cases` to locate a case, then `testit_get_case` for its steps.
