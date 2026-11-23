package com.mmm.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionBoardStateTest {
    @Test void inspectThenFollowWithoutChangingProgress() {
        CollectionBoardState board = new CollectionBoardState();
        assertEquals(2, board.displayedTier(1, 4));
        board.toggleTier(1);
        assertEquals(1, board.displayedTier(2, 4));
        board.toggleTier(1);
        assertEquals(3, board.displayedTier(2, 4));
        board.toggleTier(4);
        board.reset();
        assertEquals(0, board.inspectedTier());
        assertEquals(4, board.displayedTier(Integer.MAX_VALUE, 4));
        assertEquals(0, board.displayedTier(1, 0));
    }
    @Test void revealStaggersAndSettles() {
        assertEquals(0f, CollectionBoardState.reveal(16, 1));
        assertTrue(CollectionBoardState.reveal(25, 0) > CollectionBoardState.reveal(25, 1));
        assertEquals(1f, CollectionBoardState.reveal(345, 100));
    }
}
