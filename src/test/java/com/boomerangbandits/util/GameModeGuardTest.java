package com.boomerangbandits.util;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.EnumSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GameModeGuardTest {

    @Mock
    private Client client;

    @InjectMocks
    private GameModeGuard guard;

    @Test
    public void isStandardWorld_standardWorld_returnsTrue() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));

        assertTrue(guard.isStandardWorld());
    }

    @Test
    public void isStandardWorld_leaguesWorld_returnsFalse() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));

        assertFalse(guard.isStandardWorld());
    }

    @Test
    public void isStandardWorld_deadmanWorld_returnsFalse() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.DEADMAN));

        assertFalse(guard.isStandardWorld());
    }

    @Test
    public void isStandardWorld_tournamentWorld_returnsFalse() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.TOURNAMENT_WORLD));

        assertFalse(guard.isStandardWorld());
    }

    @Test
    public void isStandardWorld_emptyWorldTypes_returnsFalse() {
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));

        assertFalse(guard.isStandardWorld());
    }

    @Test
    public void isStandardWorld_standardWorldWithExtraFlags_returnsTrue() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.HIGH_RISK));

        assertTrue(guard.isStandardWorld());
    }
}
