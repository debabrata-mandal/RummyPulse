package com.example.rummypulse.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.ui.home.GameItem;

import org.junit.Test;

public class GameAttributionFormatterTest {

    @Test
    public void isSameCreatorAndEditor_matchesByUserId() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Alice Jones");
        item.setEditorUserId("uid-1");
        item.setEditorName("Alice Jones");

        assertTrue(GameAttributionFormatter.isSameCreatorAndEditor(item));
    }

    @Test
    public void isSameCreatorAndEditor_differsByUserId() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Alice Jones");
        item.setEditorUserId("uid-2");
        item.setEditorName("Jane Doe");

        assertFalse(GameAttributionFormatter.isSameCreatorAndEditor(item));
    }

    @Test
    public void isSameCreatorAndEditor_fallsBackToNameWhenUserIdsMissing() {
        GameItem item = new GameItem();
        item.setCreatorName("Alice Jones");
        item.setEditorName("alice jones");

        assertTrue(GameAttributionFormatter.isSameCreatorAndEditor(item));
    }

    @Test
    public void formatCreatorEditorPlainText_samePersonUsesFullName() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Alice Jones");
        item.setEditorUserId("uid-1");
        item.setEditorName("Alice Jones");

        assertEquals("Created & Edited by Alice Jones",
                GameAttributionFormatter.formatCreatorEditorPlainText(item));
    }

    @Test
    public void formatCreatorEditorPlainText_differentPeopleUseFirstNames() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Alice Jones");
        item.setEditorUserId("uid-2");
        item.setEditorName("Jane Doe");

        assertEquals("Created by Alice & Edited by Jane",
                GameAttributionFormatter.formatCreatorEditorPlainText(item));
    }
}
