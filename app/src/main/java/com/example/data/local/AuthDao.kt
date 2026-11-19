package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(entity: LocalUserEntity): Long

    @Query("SELECT * FROM local_users WHERE usernameNormalized = :usernameNormalized LIMIT 1")
    suspend fun findUserByUsernameNormalized(usernameNormalized: String): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE isSignedIn = 1 LIMIT 1")
    suspend fun findSignedInUser(): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE isSignedIn = 1 LIMIT 1")
    fun observeSignedInUser(): Flow<LocalUserEntity?>

    @Query("UPDATE local_users SET isSignedIn = 0")
    suspend fun clearSignedInUsers()

    @Query("UPDATE local_users SET isSignedIn = 1, lastLoginAtMs = :loginAtMs WHERE id = :userId")
    suspend fun markUserSignedIn(userId: Long, loginAtMs: Long)

    @Query("SELECT COUNT(*) FROM local_users")
    suspend fun countUsers(): Int
}
