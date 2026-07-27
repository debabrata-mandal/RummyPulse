package com.example.rummypulse.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GameOperationDatabaseTest {
    private GameOperationDatabase database;

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(
                        context, GameOperationDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void snapshotDraftAndOperationsSurviveIndependentReads() {
        GameOperationDao dao = database.operations();
        dao.upsertSnapshot(new GameSnapshotEntity(
                "game", "{}", 4, 2, 100));
        dao.upsertRoundDraft(new RoundScoreDraftEntity(
                "game", 2, "draft", 101));
        dao.insertOperation(new PendingGameOperation(
                "op",
                "game",
                2,
                "player",
                dao.nextSequence("game"),
                GameOperationType.RENAME_PLAYER.name(),
                "{\"name\":\"Debu\"}",
                GameOperationStatus.PENDING.name(),
                0,
                null,
                102));

        assertEquals(4, dao.getSnapshot("game").revision);
        assertEquals("draft", dao.getRoundDraft("game", 2).serializedDraft);
        assertNotNull(dao.getNextPending("game"));
        assertEquals(1, dao.activeOperationCount("game"));
    }
}
