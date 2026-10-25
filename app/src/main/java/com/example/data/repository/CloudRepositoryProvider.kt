package com.example.data.repository

import android.content.Context
import com.example.data.database.LexiTrioDatabase
import com.example.data.cloud.CloudApiFactory

object CloudRepositoryProvider {
    fun create(context: Context): CloudRepositoryImpl =
        CloudRepositoryImpl(
            context.applicationContext,
            LexiTrioDatabase.getInstance(context.applicationContext),
            CloudApiFactory.create()
        )
}
