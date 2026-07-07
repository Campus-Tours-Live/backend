# OpenAPI conventions (Contract B)

The backend documents **Contract B** — the REST API the BFF consumes. Unlike the BFF's
hand-written spec, this spec is **auto-generated at runtime by springdoc** from the
`@RestController` methods and DTO records, served at **`/v3/api-docs`** (JSON) and rendered by
Swagger UI at **`/swagger-ui.html`**. Because the spec is derived from the code, **there is no
drift** between them — so the governance here is a **completeness / quality gate**, not a drift
guard: every operation and every field must be documented, or CI fails.

## The auto-generation model

- springdoc scans controllers + DTOs and builds the OpenAPI document on demand. You never edit a
  spec file by hand; you annotate the Java.
- `OpenApiDocsExportTest` (a Testcontainers `@SpringBootTest`) boots the app during `./mvnw verify`,
  GETs `/v3/api-docs`, and writes it to **`target/openapi.json`**.
- CI then lints `target/openapi.json` with **Spectral** (`.spectral.yaml`). A missing summary,
  description, tag, response example, or schema-field description turns the build red.

## The model (shapes)

- **Success envelope.** Every 2xx payload is wrapped as `{ "data": <payload>, "meta": { requestId,
  timestamp } }` (`ApiEnvelope` / `Meta`). `data` may be an object, an array, or `null`.
- **Errors** are RFC 7807 `application/problem+json` (`{ title, status, detail?, type?, instance? }`),
  produced at runtime by `GlobalExceptionHandler` (Spring `ProblemDetail`). For docs we reference the
  annotated `web/dto/Problem` record so the error contract is described with examples.
- **Auth.** Every non-doc request needs a Google OIDC `id_token` as `Authorization: Bearer <jwt>`
  (security scheme `bearerAuth`, declared in `config/OpenApiConfig`). The doc paths
  (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) are `permitAll` in `security/SecurityConfig`
  so the spec/UI are reachable without a token — keep both of those intact.

## Annotation conventions

| Annotation | Where | What it documents |
| --- | --- | --- |
| `@Tag(name, description)` | on every `@RestController` class | groups the operations; every operation must carry ≥1 tag |
| `@Operation(summary, description)` | on every mapping method | a short summary **and** a specific description (roles/JWT/envelope) |
| `@Parameter(description)` | on each `@PathVariable` / `@RequestParam` | what the path/query param means; use `schema = @Schema(allowableValues = …)` for fixed sets |
| `@ApiResponse(responseCode, description, content = @Content(examples = @ExampleObject(...)))` | on every mapping method | the primary success response **plus** the 401/403/404/422 the endpoint actually returns — **each with an example** |
| `@Schema(description, example)` | on every DTO record component | per-field description + example; `allowableValues` (or a Java enum) for fixed options; `requiredMode` for required vs optional |

Reusable response-body examples live as compile-time constants in `web/doc/ApiExamples` and are
referenced from `@ExampleObject(value = ApiExamples.X)`. `springdoc.override-with-generic-response=false`
(in `application.properties`) stops springdoc from auto-injecting `GlobalExceptionHandler`-derived
error responses onto every operation, so each operation declares — and examples — only the errors it
really returns.

## Spectral rules (all `error`, in `.spectral.yaml`)

Extends `spectral:oas` and adds, as hard requirements: `operation-summary`, `operation-description`,
`operation-tags` / `operation-tag-defined`, `operation-has-tag`, `response-has-example` (every
response body needs an `example`/`examples`), and `schema-property-description` (every object-schema
property needs a `description`). `duplicated-entry-in-enum` is disabled (it can crash on OAS 3.1
nullable enums).

## Before you push

```bash
./mvnw verify                                      # tests + JaCoCo ≥90% + writes target/openapi.json
npx @stoplight/spectral-cli lint target/openapi.json   # 0 errors required (CI runs this too)
```

Both run in CI (`.github/workflows/ci.yml`, the `Unit & Integration` job). Adding or changing a
`@RestController` endpoint or a DTO regenerates the spec automatically — annotate it (`@Operation` /
`@Schema` / `@Parameter` / `@ApiResponse`), keep the `@SecurityScheme` bearer JWT and the doc-path
`permitAll` intact, and confirm the Spectral gate is green before opening the PR.
