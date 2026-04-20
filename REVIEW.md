# Code review exercise — flawed snippet

**Context:** Teammate opened a PR adding a “quick” workflow runner.

## Snippet under review

```java
@Service
public class QuickRunService {
    @Autowired
    private WorkflowRepository workflows;
    @Autowired
    private RestTemplate http;

    public String run(String workflowId) {
        Workflow w = workflows.findById(UUID.fromString(workflowId)).get();
        Map data = (Map) w.getDefinition(); // stored as JSON
        String url = (String) data.get("url");
        return http.getForObject(url, String.class); // “run” the workflow
    }
}
```

## Feedback (as on a real PR)

1. **Security — SSRF / arbitrary URL fetch**  
   Loading `url` from stored JSON and issuing a server-side GET allows attackers (or compromised editors) to hit internal IPs, metadata endpoints, or `file://`. This must go through an allowlist, block private ranges, and respect the same URL policy as your HTTP step handler.

2. **Typing and validation**  
   Raw `Map` and unchecked casts will throw at runtime. Use a typed DTO, validate structure, and fail fast with clear errors.

3. **Error handling**  
   `Optional.get()` without `isPresent()` can throw; `UUID.fromString` can throw on bad input. Prefer `orElseThrow` with a domain exception and input validation on the API layer.

4. **Architecture**  
   A workflow is more than one HTTP GET; this bypasses the DAG engine, retries, timeouts, and audit trail. This code path should delegate to the existing orchestrator or be removed.

5. **Testing**  
   No unit tests for URL validation or negative cases. Add tests that prove blocked URLs cannot be fetched.

6. **Naming**  
   `QuickRunService` suggests a shortcut that will confuse future maintainers; either align with `WorkflowService`/`DagExecutor` naming or document why this is isolated.

**Verdict:** Request changes — do not merge until SSRF controls and orchestration consistency are addressed.
