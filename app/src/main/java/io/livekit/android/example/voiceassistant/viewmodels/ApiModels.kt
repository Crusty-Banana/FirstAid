package io.livekit.android.example.voiceassistant.data

// --- Auth Models ---
data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val first_name: String,
    val last_name: String
)

data class UserProfileResponse( // From Register endpoint
    val id: String,
    val email: String,
    val first_name: String,
    val last_name: String,
    val created_at: String
)

// Generic API Error structure (can be adapted to your backend's specifics)
data class ApiError(
    val message: String? = null,
    val error: String? = null,
    val errors: List<String>? = null, // For multiple validation errors
    val detail: String? = null // Some APIs use 'detail'
)

data class LogoutResponse(val message: String)

data class RefreshTokenRequest(
    val refresh_token: String
)

// Expected response from the refresh token endpoint
data class RefreshTokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "Bearer",
    val expires_in: Int? = null
)

// --- User Profile Models ---
data class UserProfile(
    val id: String,
    val first_name: String,
    val last_name: String,
    val date_of_birth: String?,
//    val medical_history_id: String,
//    val preferences: UserPreferences,
    val created_at: String,
    val updated_at: String
)

//data class UserPreferences(
//    val theme: String,
//    val notifications_enabled: Boolean
//)

data class UpdateUserProfileRequest(
    val first_name: String? = null,
    val last_name: String? = null,
    val date_of_birth: String? = null
)

//data class UpdateUserPreferencesRequest(
//    val theme: String? = null,
//    val notifications_enabled: Boolean? = null
//)

// --- Conversation Models ---
data class Conversation(
    val id: String,
    val user_id: String,
    val title: String,
    val metadata: Map<String, Any>?,
    val tags: List<String>?,
    val is_archived: Boolean,
    val created_at: String,
    val updated_at: String
)

data class CreateConversationRequest(
    val title: String,
    val metadata: Map<String, Any>? = null,
    val tags: List<String>? = null
)

data class UpdateConversationRequest(
    val title: String? = null,
    val metadata: Map<String, Any>? = null,
    val tags: List<String>? = null,
    val is_archived: Boolean? = null
)

// --- Message Models ---
data class Message(
    val id: String,
    val conversation_id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val message_type: String, // "text"
    val voice_url: String?,
    val metadata: Map<String, Any>?,
    val created_at: String
)

data class CreateMessageRequest(
    val role: String, // "user"
    val content: String,
    val message_type: String = "text",
)

// --- Voice Session Models ---
data class CreateVoiceSessionRequest(
    val conversation_id: String,
    val metadata: Map<String, Any>? = null
)

data class VoiceSessionResponse(
    val id: String, // session_id
    val user_id: String,
    val conversation_id: String,
    val status: String,
    val token: String, // This is the LiveKit token
    val metadata: Map<String, Any>?,
    val config: Map<String, Any>?,
    val created_at: String
)

// Assuming VoiceSessionStatusResponse is identical to VoiceSessionResponse based on API docs
// data class VoiceSessionStatusResponse(...)

data class DeleteVoiceSessionResponse(
    val message: String
)