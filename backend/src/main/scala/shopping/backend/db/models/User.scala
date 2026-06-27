package shopping.backend.db.models

// `state` drives access control (BLOCKED users can't log in). Defaulted so
// existing test constructions stay terse; the Live query always selects it.
case class User(
    userId: Long,
    password: String,
    accessToken: String,
    state: String = "ACTIVE"
)
