package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Person
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY created_at ASC")
    fun getAllPeople(): Flow<List<Person>>

    @Query("SELECT * FROM people ORDER BY created_at ASC")
    suspend fun getAllPeopleSync(): List<Person>

    @Query("SELECT * FROM people WHERE person_id = :personId LIMIT 1")
    fun getPersonById(personId: String): Flow<Person?>

    @Query("SELECT * FROM people WHERE person_id = :personId LIMIT 1")
    suspend fun getPersonByIdSync(personId: String): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person)

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)

    @Query("DELETE FROM people WHERE person_id = :personId")
    suspend fun deleteById(personId: String)

    @Query("DELETE FROM people")
    suspend fun clear()
}
