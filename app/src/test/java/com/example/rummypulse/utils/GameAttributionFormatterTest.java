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
        item.setCreatorName("Asik Mandal");
        item.setEditorUserId("uid-1");
        item.setEditorName("Asik Mandal");

        assertTrue(GameAttributionFormatter.isSameCreatorAndEditor(item));
    }

    @Test
    public void isSameCreatorAndEditor_differsByUserId() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Asik Mandal");
        item.setEditorUserId("uid-2");
        item.setEditorName("Jane Doe");

        assertFalse(GameAttributionFormatter.isSameCreatorAndEditor(item));
    }

    @Test
    public void isSameCreatorAndEditor_fallsBackToNameWhenUserIdsMissing() {
        GameItem item = new GameItem();
        item.setCreatorName("Asik Mandal");
        item.setEditorName("asik mandal");

        assertTrue(GameAttributionFormatter.isSameCreatorAndEditor(item));
    }

    @Test
    public void formatCreatorEditorPlainText_samePersonUsesFullName() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Asik Mandal");
        item.setEditorUserId("uid-1");
        item.setEditorName("Asik Mandal");

        assertEquals("Created & Edited by Asik Mandal",
                GameAttributionFormatter.formatCreatorEditorPlainText(item));
    }

    @Test
    public void formatCreatorEditorPlainText_differentPeopleUseFirstNames() {
        GameItem item = new GameItem();
        item.setCreatorUserId("uid-1");
        item.setCreatorName("Asik Mandal");
        item.setEditorUserId("uid-2");
        item.setEditorName("Rounak Sarkar");

        assertEquals("Created by Asik & Edited by Rounak",
                GameAttributionFormatter.formatCreatorEditorPlainText(item));
    }
}
