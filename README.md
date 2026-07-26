# Snapbase

Dynamic backend-as-a-service. Define collections with custom schemas via the admin dashboard, then query and mutate records via a REST API. SQLite under the hood.

## Running

Requires `JWT_SECRET` in your environment:

```bash
export JWT_SECRET="your-secret-key"
```

Then start the server:

```bash
./mvnw compile exec:java -Dexec.mainClass="com.snapbase.SnapbaseBackendMain"
```

Starts on `http://localhost:7070`. The admin dashboard is at `/admin/dashboard.html`.

## API Response Format

All endpoints use a consistent JSON shape:

```json
// Success
{ "status": "success", "data": ... }

// Error
{ "status": "error", "message": "..." }
```

| HTTP Status | Meaning |
|---|---|
| `200` | Success |
| `401` | Unauthorized — missing or expired token |
| `403` | Forbidden — valid token but insufficient role |
| `404` | Not found |
| `500` | Internal server error |

## Endpoints

### Auth

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/signup` | Register a new user |
| `POST` | `/auth/login` | Authenticate and receive a JWT |

### Records

| Method | Path | Description |
|---|---|---|
| `POST` | `/collections/{collection}/records` | Insert a record |
| `GET` | `/collections/{collection}/records` | Query records with filters, sorting, and pagination |
| `PATCH` | `/collections/{collection}/records` | Update records by filter |
| `DELETE` | `/collections/{collection}/records` | Delete records by filter |

---

## Authentication

### Signup

`POST /auth/signup`

```json
{ "name": "Alice", "email": "alice@test.com", "password": "secret" }
```

Response:
```json
{ "status": "success" }
```

### Login

`POST /auth/login`

```json
{ "email": "alice@test.com", "password": "secret" }
```

Success response:
```json
{ "status": "success", "data": { "token": "eyJ..." } }
```

Error response (401):
```json
{ "status": "error", "message": "Authentication failed" }
```

All subsequent requests must include `Authorization: Bearer <token>`.

---

## Inserting a Record

`POST /collections/{collection}/records`

```json
{
  "name": "users",
  "data": { "email": "john@test.com", "age": 30 }
}
```

Response:
```json
{ "status": "success", "data": { "row_created": "abc123def456ghi" } }
```

---

## Querying Records

`GET /collections/{collection}/records`

### Filtering

Prefix query params with `filter:` followed by the column name and an operator + value:

```
?filter:age=>=18
?filter:email==john@test.com
```

Multiple filters combine with AND:

```
?filter:age=>=18&filter:is_active==1
```

### Supported operators

| Operator | Meaning |
|---|---|
| `=` | Equal |
| `!=` | Not equal |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `<` | Less than |
| `<=` | Less than or equal |
| `~` | Like / contains |
| `!~` | Not like |

### Sorting

```
?sort:created=DESC
?sort:age=ASC
```

### Pagination

```
?limit=10
?offset=20
```

### Full example

```
GET /collections/users/records?filter:age=>=18&filter:is_active==1&sort:created=DESC&limit=10
```

Response:
```json
{
  "status": "success",
  "data": [
    { "id": "abc123", "email": "john@test.com", "age": 30, "is_active": 1, "created_at": "2026-07-19" },
    { "id": "def456", "email": "jane@test.com", "age": 25, "is_active": 1, "created_at": "2026-07-18" }
  ]
}
```

---

## Updating Records

`PATCH /collections/{collection}/records`

```json
{
  "name": "users",
  "data": { "age": 31 },
  "filter": { "email": "=john@test.com" }
}
```

Response:
```json
{ "status": "success" }
```

Same filter syntax as querying.

---

## Deleting Records

`DELETE /collections/{collection}/records`

Same filter syntax as querying:

```
DELETE /collections/users/records?filter:age=<18
```

Response:
```json
{ "status": "success" }
```

## File Structure

```
src/main/java/com/snapbase/
├── SnapbaseBackendMain.java          # Entry point, Javalin config, routes
├── auth/
│   ├── JwtUtils.java                # JWT generation & validation
│   └── Role.java                    # Route roles: ANYONE, ALL, USER, ADMIN
├── controllers/
│   ├── AuthController.java          # Auth endpoints
│   └── CollectionController.java    # Collection CRUD endpoints
├── db/
│   └── Database.java                # SQLite via HikariCP + JDBI3
├── dtos/
│   ├── CreateCollectionDTO.java     # POST /collections request
│   ├── FieldDefinition.java         # Schema field definition
│   ├── InsertRecordDTO.java         # POST /collections/{name}/records request
│   ├── LoginDTO.java                # Login request
│   ├── SignupDTO.java               # Signup request
│   └── UpdateRecordDTO.java         # PATCH /collections/{name}/records request
├── enums/
│   └── DataTypeEnum.java            # TEXT, EMAIL, NUMBER, BOOLEAN, URL, DATETIME, JSON
├── exceptions/
│   └── ResponseException.java       # HTTP error with status code
├── factories/
│   ├── AuthFactory.java             # Wires auth dependencies
│   └── CollectionFactory.java       # Wires collection dependencies
├── models/
│   ├── CollectionModel.java         # Collections meta-table row
│   ├── UserModel.java               # Users table row
│   └── SuperUserModel.java          # Superusers table row
├── repositories/
│   ├── CollectionRepository.java    # Dynamic SQL: CRUD + schema management
│   ├── UserRepository.java          # User CRUD
│   └── SuperUserRepository.java     # Admin CRUD
├── services/
│   ├── AuthService.java             # Login/password verification
│   ├── CollectionService.java       # Collection business logic + API rules
│   └── AdminSetupService.java       # One-time admin account setup
└── utils/
    └── SqlUtils.java                # Identifier validation, quoting, ID generation
```

## Naming Conventions

| Layer | Pattern | Examples |
|---|---|---|
| **DTOs** | `{Action}{Resource}DTO` | `CreateCollectionDTO`, `InsertRecordDTO`, `UpdateRecordDTO`, `LoginDTO`, `SignupDTO` |
| **Sub-components** | Descriptive name (no DTO suffix) | `FieldDefinition` |
| **Repositories** | `{verb}{Noun}()` | `saveCollection()`, `findCollectionSchema()`, `collectionExists()`, `updateRecords()` |
| **Services** | Plural for multi-record ops | `findRecords()`, `deleteRecords()`, `updateRecords()` |
| **SQL builders** | `build{SqlType}()` (private) | `buildSelectSql()`, `buildUpdateSql()`, `buildDeleteSql()`, `buildColumnDef()` |
| **Controllers** | `try/catch` wrapping all endpoints | Every endpoint handles `Exception` and returns structured JSON |

## TODO

- [ ] Add password reset flow (admin dashboard)
- [ ] Add superuser delete endpoint
- [ ] Add user delete endpoint
- [ ] Add collection delete endpoint
- [ ] Add collection update (schema migration) endpoint
- [ ] Package as a standalone binary (native image)
- [ ] Make extensible via plugin/extension system
