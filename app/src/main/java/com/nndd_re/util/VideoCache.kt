package com.nndd_re.util

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object VideoCache {
    @Volatile private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.applicationContext.cacheDir, "exo"),
                LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024), // 200MB
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { instance = it }
        }
}
