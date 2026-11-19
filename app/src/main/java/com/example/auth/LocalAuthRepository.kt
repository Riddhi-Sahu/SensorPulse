package com.example.auth

import android.content.Context
import com.example.data.local.AuthDao
import com.example.data.local.LocalUserEntity
import com.example.data.local.SensorPulseDatabase
import com.example.data.model.LocalUser
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalAuthRepository(
    context: Context,
    private val authDao: AuthDao = SensorPulseDatabase.getInstance(context).authDao()
) {
    private val _currentUser = MutableStateFlow<LocalUser?>(null)
    val currentUser: StateFlow<LocalUser?> = _currentUser.asStateFlow()

    suspend fun restoreSession() {
        val entity = withContext(Dispatchers.IO) {
            runCatching { authDao.findSignedInUser() }.getOrNull()
        }
        _currentUser.value = entity?.toModel()
    }

    suspend fun signUp(
        username: String,
        password: String,
        confirmPassword: String
    ): Result<LocalUser> {
        return try {
            val trimmedUser = username.trim()
            val normalized = trimmedUser.lowercase()
            require(trimmedUser.length <= MAX_USERNAME_LEN) {
                "Username must be at most $MAX_USERNAME_LEN characters"
            }
            require(isValidUsernameFormat(trimmedUser)) {
                "Username must follow name_xxx format (letters, underscore, and 3 numbers, e.g. alex_104)"
            }
            require(password.length in MIN_PASSWORD_LEN..MAX_PASSWORD_LEN) {
                "Password must be between $MIN_PASSWORD_LEN and $MAX_PASSWORD_LEN characters"
            }
            require(!password.any { it.isWhitespace() }) {
                "Password cannot contain spaces"
            }
            require(hasPasswordLetter(password)) {
                "Password must include at least 1 letter"
            }
            require(hasPasswordDigit(password)) {
                "Password must include at least 1 number"
            }
            require(hasPasswordSpecialSymbol(password)) {
                "Password must include at least 1 special symbol (e.g. !@#\$%)"
            }
            require(password == confirmPassword) {
                "Passwords do not match"
            }

            val existing = withContext(Dispatchers.IO) {
                authDao.findUserByUsernameNormalized(normalized)
            }
            if (existing != null) {
                return Result.failure(IllegalStateException("Username \"$trimmedUser\" is already taken"))
            }

            val saltHex = generateSaltHex()
            val hashHex = hashPassword(password, saltHex)
            val now = System.currentTimeMillis()
            val entity = LocalUserEntity(
                username = trimmedUser,
                usernameNormalized = normalized,
                passwordHash = hashHex,
                saltHex = saltHex,
                createdAtMs = now,
                lastLoginAtMs = now,
                isSignedIn = true
            )
            val insertedId = withContext(Dispatchers.IO) {
                authDao.clearSignedInUsers()
                authDao.insertUser(entity)
            }
            val user = LocalUser(id = insertedId, username = trimmedUser, createdAtMs = now)
            _currentUser.value = user
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(
        username: String,
        password: String
    ): Result<LocalUser> {
        return try {
            val trimmedUser = username.trim()
            val normalized = trimmedUser.lowercase()
            require(trimmedUser.isNotEmpty()) { "Enter your username" }
            require(password.isNotEmpty()) { "Enter your password" }

            val existing = withContext(Dispatchers.IO) {
                authDao.findUserByUsernameNormalized(normalized)
            } ?: return Result.failure(
                IllegalStateException("No account found for \"$trimmedUser\". Tap Create Account to register.")
            )

            val candidateHash = hashPassword(password, existing.saltHex)
            val matches = MessageDigest.isEqual(
                candidateHash.toByteArray(Charsets.UTF_8),
                existing.passwordHash.toByteArray(Charsets.UTF_8)
            )
            if (!matches) {
                return Result.failure(IllegalArgumentException("Incorrect password"))
            }

            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                authDao.clearSignedInUsers()
                authDao.markUserSignedIn(existing.id, now)
            }
            val user = existing.toModel()
            _currentUser.value = user
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        _currentUser.value = null
        withContext(Dispatchers.IO) {
            runCatching { authDao.clearSignedInUsers() }
        }
    }

    private fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$password".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "sensorpulse_auth_prefs"
        private const val KEY_ACTIVE_USERNAME = "active_username_norm"
        const val MAX_USERNAME_LEN = 32
        const val MIN_PASSWORD_LEN = 8
        const val MAX_PASSWORD_LEN = 20
        private val USERNAME_REGEX = Regex("^[A-Za-z]+_[0-9]{3}$")

        fun isValidUsernameFormat(username: String): Boolean {
            val trimmed = username.trim()
            return trimmed.length <= MAX_USERNAME_LEN && USERNAME_REGEX.matches(trimmed)
        }

        fun hasValidPasswordLength(password: String): Boolean =
            password.length in MIN_PASSWORD_LEN..MAX_PASSWORD_LEN

        fun hasPasswordLetter(password: String): Boolean =
            password.any { it.isLetter() }

        fun hasPasswordDigit(password: String): Boolean =
            password.any { it.isDigit() }

        fun hasPasswordSpecialSymbol(password: String): Boolean =
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    }
}
