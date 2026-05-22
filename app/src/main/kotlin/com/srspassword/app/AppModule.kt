package com.srspassword.app

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.srspassword.app.data.AppDatabase
import com.srspassword.app.data.MIGRATION_1_2
import com.srspassword.app.data.PasswordCardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "srs_password_vault.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePasswordCardDao(db: AppDatabase): PasswordCardDao = db.passwordCardDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
