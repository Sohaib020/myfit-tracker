package com.myfit.tracker.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.myfit.tracker.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R35: a database built from the last saved schema (app/schemas, v4) must open with today's entities + migrations.
 * Fails when an entity changes without a version bump + migration — the kind of mistake that wipes users' logs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SchemaTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun savedSchemaOpensWithCurrentCode() {
        val name = "schema-test.db"
        helper.createDatabase(name, 4).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        db.openHelper.writableDatabase   // Room checks the schema identity hash here
        db.close()
    }
}
