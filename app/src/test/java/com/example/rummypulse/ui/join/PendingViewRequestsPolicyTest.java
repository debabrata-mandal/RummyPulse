package com.example.rummypulse.ui.join;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.data.GameAuth;
import com.example.rummypulse.ui.join.PendingViewRequestsPolicy.Access;

import org.junit.Test;

public class PendingViewRequestsPolicyTest {

    @Test
    public void appAdminManagesWithoutEditAccess() {
        assertEquals(Access.ADMIN,
                PendingViewRequestsPolicy.accessFor(true, null, null, "admin-uid"));
    }

    @Test
    public void appAdminStaysAdminWhileAlsoEditing() {
        assertEquals(Access.ADMIN,
                PendingViewRequestsPolicy.accessFor(true, true, auth("admin-uid"), "admin-uid"));
    }

    @Test
    public void activeEditorWithLocalEditAccessManages() {
        assertEquals(Access.ACTIVE_EDITOR,
                PendingViewRequestsPolicy.accessFor(false, true, auth("editor-uid"), "editor-uid"));
    }

    @Test
    public void editorWithoutLocalEditAccessCannotManage() {
        assertEquals(Access.NONE,
                PendingViewRequestsPolicy.accessFor(false, false, auth("editor-uid"), "editor-uid"));
        assertEquals(Access.NONE,
                PendingViewRequestsPolicy.accessFor(false, null, auth("editor-uid"), "editor-uid"));
    }

    @Test
    public void localEditAccessForAnotherActiveEditorCannotManage() {
        assertEquals(Access.NONE,
                PendingViewRequestsPolicy.accessFor(false, true, auth("other-uid"), "editor-uid"));
    }

    @Test
    public void missingAuthOrUserCannotManage() {
        assertEquals(Access.NONE,
                PendingViewRequestsPolicy.accessFor(false, true, null, "editor-uid"));
        assertEquals(Access.NONE,
                PendingViewRequestsPolicy.accessFor(false, true, auth("editor-uid"), null));
    }

    @Test
    public void firstManagedRequestStartsListener() {
        assertTrue(PendingViewRequestsPolicy.shouldRestartListener(
                null, Access.NONE, "GAME00001", Access.ACTIVE_EDITOR));
    }

    @Test
    public void sameGameAndAccessKeepsListener() {
        assertFalse(PendingViewRequestsPolicy.shouldRestartListener(
                "GAME00001", Access.ACTIVE_EDITOR, "GAME00001", Access.ACTIVE_EDITOR));
        assertFalse(PendingViewRequestsPolicy.shouldRestartListener(
                "GAME00001", Access.ADMIN, "GAME00001", Access.ADMIN));
    }

    @Test
    public void accessChangeRestartsListener() {
        assertTrue(PendingViewRequestsPolicy.shouldRestartListener(
                "GAME00001", Access.ACTIVE_EDITOR, "GAME00001", Access.ADMIN));
        assertTrue(PendingViewRequestsPolicy.shouldRestartListener(
                "GAME00001", Access.ADMIN, "GAME00001", Access.ACTIVE_EDITOR));
    }

    @Test
    public void gameChangeRestartsListener() {
        assertTrue(PendingViewRequestsPolicy.shouldRestartListener(
                "GAME00001", Access.ADMIN, "GAME00002", Access.ADMIN));
    }

    @Test
    public void unmanagedOrMissingGameNeverStartsListener() {
        assertFalse(PendingViewRequestsPolicy.shouldRestartListener(
                null, Access.NONE, "GAME00001", Access.NONE));
        assertFalse(PendingViewRequestsPolicy.shouldRestartListener(
                "GAME00001", Access.ADMIN, null, Access.ADMIN));
    }

    private static GameAuth auth(String activeEditorUserId) {
        GameAuth auth = new GameAuth();
        auth.setActiveEditorUserId(activeEditorUserId);
        return auth;
    }
}
