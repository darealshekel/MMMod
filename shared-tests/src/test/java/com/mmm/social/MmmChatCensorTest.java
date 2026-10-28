package com.mmm.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MmmChatCensorTest
{
    @Test
    void loadsMultilingualTerms()
    {
        assertTrue(MmmChatCensor.loadedTermCount() >= 30);
    }

    @Test
    void censorsCaseLeetAndSeparatorObfuscation()
    {
        assertEquals("******", MmmChatCensor.censor("N1GG3R"));
        assertEquals("*****", MmmChatCensor.censor("f.a.g"));
    }

    @Test
    void censorsTermsAcrossWritingSystems()
    {
        assertEquals("*****", MmmChatCensor.censor("пидор"));
        assertEquals("****", MmmChatCensor.censor("כושי"));
        assertEquals("***", MmmChatCensor.censor("支那人"));
    }

    @Test
    void leavesNormalConversationUnchanged()
    {
        String message = "Class starts now; meet at the mining hall.";
        assertEquals(message, MmmChatCensor.censor(message));
    }

    @Test
    void doesNotCensorBlockedTextInsideLongerWords()
    {
        assertEquals("retardation", MmmChatCensor.censor("retardation"));
    }
}
