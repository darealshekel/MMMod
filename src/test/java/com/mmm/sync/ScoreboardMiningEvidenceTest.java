package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScoreboardMiningEvidenceTest
{
    @Test
    void rejectsNonMiningScoreboardValues()
    {
        assertFalse(ScoreboardParser.isMiningEvidence("Sprint Distance"));
        assertFalse(ScoreboardParser.hasMiningLabel("Iktsoi 64000000"));
        assertTrue(ScoreboardParser.isMiningEvidence("Total Digs"));
        assertTrue(ScoreboardParser.hasMiningLabel("Iktsoi blocks mined: 100"));
    }
}