package com.srspassword.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordCardDao {

    @Query("SELECT * FROM password_cards ORDER BY nextDueAt ASC")
    fun getAllCards(): Flow<List<PasswordCard>>

    @Query("SELECT * FROM password_cards WHERE id = :id")
    suspend fun getCardById(id: String): PasswordCard?

    // Cards due now (nextDueAt <= current time)
    @Query("""
        SELECT * FROM password_cards 
        WHERE nextDueAt <= :nowMillis 
        ORDER BY nextDueAt ASC
    """)
    fun getDueCards(nowMillis: Long = System.currentTimeMillis()): Flow<List<PasswordCard>>

    @Query("""
        SELECT COUNT(*) FROM password_cards 
        WHERE nextDueAt <= :nowMillis
    """)
    fun getDueCardCount(nowMillis: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM password_cards WHERE state = 'NEW' ORDER BY createdAt ASC")
    fun getNewCards(): Flow<List<PasswordCard>>

    @Query("SELECT DISTINCT category FROM password_cards ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("""
        SELECT * FROM password_cards 
        WHERE category = :category 
        ORDER BY nextDueAt ASC
    """)
    fun getCardsByCategory(category: String): Flow<List<PasswordCard>>

    @Query("""
        SELECT * FROM password_cards 
        WHERE title LIKE '%' || :query || '%' 
           OR username LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchCards(query: String): Flow<List<PasswordCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: PasswordCard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<PasswordCard>)

    @Update
    suspend fun updateCard(card: PasswordCard)

    @Delete
    suspend fun deleteCard(card: PasswordCard)

    @Query("DELETE FROM password_cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("SELECT COUNT(*) FROM password_cards")
    fun getTotalCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM password_cards 
        WHERE state = 'REVIEW' 
        AND totalReviews > 0
    """)
    fun getMasteredCount(): Flow<Int>

    // Stats query
    @Query("""
        SELECT AVG(difficulty) FROM password_cards
    """)
    fun getAverageDifficulty(): Flow<Double?>
}
