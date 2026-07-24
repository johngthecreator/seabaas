# Seabaas

Dynamic backend-as-a-service. Define collections with custom schemas, then query and mutate records via a REST API. SQLite under the hood.

## Running

```bash
./mvnw compile exec:java -Dexec.mainClass="com.seabaas.SeabaasBackendMain"
```

Starts on `http://localhost:7070`.

## Endpoints

### Auth

| Method | Path | Description |
|---|---|---|
| `GET` | `/auth/login` | Placeholder login endpoint |

### Collections

| Method | Path | Description |
|---|---|---|
| `POST` | `/collections` | Create a new collection (table + schema) |
| `POST` | `/collections/{collection}/records` | Insert a record into a collection |
| `GET` | `/collections/{collection}/records` | Query records with filters, sorting, and pagination |
| `DELETE` | `/collections/{collection}/records` | Delete records by filter |

---

## Creating a Collection

`POST /collections`

**Request body:**

```json
{
  "name": "users",
  "fields": [
    { "name": "email", "type": "EMAIL", "required": true },
    { "name": "age", "type": "NUMBER", "required": false },
    { "name": "is_active", "type": "BOOLEAN", "required": false }
  ]
}
```

**Supported field types:**

| Type | SQL column |
|---|---|
| `TEXT` | `TEXT DEFAULT ''` |
| `EMAIL` | `TEXT DEFAULT ''` |
| `NUMBER` | `REAL DEFAULT 0.0` |
| `BOOLEAN` | `INTEGER DEFAULT 0` |
| `URL` | `TEXT DEFAULT ''` |
| `DATETIME` | `TEXT DEFAULT CURRENT_TIMESTAMP` |
| `JSON` | `TEXT DEFAULT '{}'` |

Every collection also gets `id INTEGER PRIMARY KEY AUTOINCREMENT` and `created_at TEXT DEFAULT CURRENT_TIMESTAMP` automatically.

**Response:**

```json
{
  "status": "success",
  "data": { "created_schema_id": 1 }
}
```

---

## Inserting a Record

`POST /collections/{collection}/records`

**Request body:**

```json
{
  "name": "users",
  "data": {
    "email": "john@test.com",
    "age": 30
  }
}
```

**Response:**

```json
{
  "status": "success",
  "data": { "row_created": 1 }
}
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

**Response:**

```json
{
  "status": "success",
  "data": [
    { "id": 1, "email": "john@test.com", "age": 30, "is_active": 1, "created_at": "2026-07-19" },
    { "id": 2, "email": "jane@test.com", "age": 25, "is_active": 1, "created_at": "2026-07-18" }
  ]
}
```

---

## Deleting Records

`DELETE /collections/{collection}/records`

Same filter syntax as querying — attaches `WHERE` clause to `DELETE`:

```
DELETE /collections/users/records?filter:age=<18
```

**Response:**

```json
{
  "status": "success",
  "message": "successfully deleted record."
}
```
