package id.umkmku.pos

import android.app.Application
import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.umkmku.pos.data.CatalogDatabase
import id.umkmku.pos.data.RoomCatalogRepository
import id.umkmku.pos.domain.CatalogRepository
import javax.inject.Singleton

@HiltAndroidApp
class WarungApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {
    @Binds abstract fun catalog(implementation: RoomCatalogRepository): CatalogRepository

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): CatalogDatabase =
            Room.databaseBuilder(context, CatalogDatabase::class.java, "umkm-catalog.db").build()
    }
}
