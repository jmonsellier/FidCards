package com.monselio.fidcards.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun barcodeFormatToString(format: BarcodeFormat): String = format.name

    @TypeConverter
    fun stringToBarcodeFormat(name: String): BarcodeFormat = BarcodeFormat.valueOf(name)
}

@Database(entities = [Card::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FidCardsDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    companion object {
        fun build(context: Context): FidCardsDatabase =
            Room.databaseBuilder(context, FidCardsDatabase::class.java, "fidcards.db")
                .build()
    }
}
