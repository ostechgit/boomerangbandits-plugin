package com.boomerangbandits.services;

import com.boomerangbandits.util.GameModeGuard;
import net.runelite.api.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventAttendanceTrackerTest {

    @Mock
    private Client client;

    @Mock
    private GameModeGuard gameModeGuard;

    @InjectMocks
    private EventAttendanceTracker tracker;

    @Test
    public void startEvent_nonStandardWorld_doesNotStart() {
        when(gameModeGuard.isStandardWorld()).thenReturn(false);
        tracker.startEvent();
        assertFalse("Event should not start on non-standard world", tracker.isRunning());
    }

    @Test
    public void startEvent_standardWorld_starts() {
        when(gameModeGuard.isStandardWorld()).thenReturn(true);
        when(client.getTickCount()).thenReturn(100);
        tracker.startEvent();
        assertTrue("Event should start on standard world", tracker.isRunning());
    }
}
