package com.neverdrop.data.sync

import com.neverdrop.data.local.entity.TaskEntity
import com.neverdrop.data.preferences.UserPreferences
import com.neverdrop.data.repository.TaskRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cloud sync against Supabase (works the same for hosted `*.supabase.co` or a self-hosted
 * instance — only the URL differs). Offline-first: Room remains the source of truth for the
 * UI; this pushes locally-changed rows and pulls remote changes with last-write-wins.
 *
 * Auth bootstraps from the Google ID token the app already obtains at sign-in, exchanged for
 * a Supabase session (which the SDK then persists and refreshes). Every entry point is
 * best-effort and returns a [Result]; failures never crash the app.
 *
 * NOTE: the Supabase Kotlin SDK symbols below target supabase-kt 2.6.x. If you bump the SDK,
 * verify `createSupabaseClient` / `auth` / `from(...).upsert/select` / `IDToken` against that
 * version — they are the only version-sensitive surface in the app.
 */
class SupabaseSyncRepository(
    private val repository: TaskRepository,
    private val preferences: UserPreferences,
    /** Supplies a fresh Google ID token (e.g. from GoogleAuthManager) for first sign-in. */
    private val googleIdTokenProvider: () -> String?
) {

    data class SyncStats(val pushed: Int, val pulled: Int)

    @Volatile private var cachedClient: SupabaseClient? = null
    @Volatile private var cachedUrl: String? = null
    @Volatile private var cachedKey: String? = null

    private fun client(url: String, key: String): SupabaseClient {
        cachedClient?.let { if (cachedUrl == url && cachedKey == key) return it }
        val built = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Auth)
            install(Postgrest)
        }
        synchronized(this) {
            cachedClient = built
            cachedUrl = url
            cachedKey = key
        }
        return built
    }

    suspend fun syncAll(): Result<SyncStats> = withContext(Dispatchers.IO) {
        val url = preferences.supabaseUrl
        val key = preferences.supabaseAnonKey
        if (!preferences.cloudSyncEnabled || url.isNullOrBlank() || key.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Cloud sync not configured"))
        }
        try {
            val supabase = client(url, key)
            if (!ensureSession(supabase)) {
                return@withContext Result.failure(IllegalStateException("Not authenticated — sign in with Google first"))
            }
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: return@withContext Result.failure(IllegalStateException("No Supabase user"))

            // 1. Push local changes (including deletions).
            val dirty = repository.dirtyEntities()
            if (dirty.isNotEmpty()) {
                supabase.from(TABLE).upsert(dirty.map { it.toRow(uid) })
                repository.markSynced(dirty.map { it.uuid })
            }

            // 2. Pull remote changes since the last watermark.
            val since = preferences.lastCloudPullEpochMillis
            val remote = supabase.from(TABLE).select {
                filter { gt("updated_at_ms", since) }
            }.decodeList<TaskRow>()

            var maxUpdated = since
            for (row in remote) {
                repository.applyRemote(row.toEntity())
                if (row.updatedAtMs > maxUpdated) maxUpdated = row.updatedAtMs
            }
            preferences.lastCloudPullEpochMillis = maxUpdated

            Result.success(SyncStats(pushed = dirty.size, pulled = remote.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureSession(supabase: SupabaseClient): Boolean {
        if (supabase.auth.currentSessionOrNull() != null) return true
        val idToken = googleIdTokenProvider() ?: return false
        return try {
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            supabase.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TABLE = "tasks"
    }
}

/** Wire row matching the `public.tasks` table (see supabase/schema.sql). */
@Serializable
data class TaskRow(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("commitment_type") val commitmentType: String,
    @SerialName("priority") val priority: String,
    @SerialName("status") val status: String,
    @SerialName("related_person") val relatedPerson: String? = null,
    @SerialName("deadline_ms") val deadlineMs: Long? = null,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("completed_at_ms") val completedAtMs: Long? = null,
    @SerialName("snooze_count") val snoozeCount: Int = 0,
    @SerialName("last_snoozed_at_ms") val lastSnoozedAtMs: Long? = null,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
    @SerialName("deleted") val deleted: Boolean = false
) {
    fun toEntity(): TaskEntity = TaskEntity(
        id = 0,
        title = title,
        description = description,
        commitmentType = commitmentType,
        priority = priority,
        status = status,
        relatedPerson = relatedPerson,
        deadlineEpochMillis = deadlineMs,
        createdAtEpochMillis = createdAtMs,
        completedAtEpochMillis = completedAtMs,
        snoozeCount = snoozeCount,
        lastSnoozedAtEpochMillis = lastSnoozedAtMs,
        uuid = id,
        updatedAtEpochMillis = updatedAtMs,
        dirty = false,
        deleted = deleted
    )
}

private fun TaskEntity.toRow(userId: String): TaskRow = TaskRow(
    id = uuid,
    userId = userId,
    title = title,
    description = description,
    commitmentType = commitmentType,
    priority = priority,
    status = status,
    relatedPerson = relatedPerson,
    deadlineMs = deadlineEpochMillis,
    createdAtMs = createdAtEpochMillis,
    completedAtMs = completedAtEpochMillis,
    snoozeCount = snoozeCount,
    lastSnoozedAtMs = lastSnoozedAtEpochMillis,
    updatedAtMs = updatedAtEpochMillis,
    deleted = deleted
)
