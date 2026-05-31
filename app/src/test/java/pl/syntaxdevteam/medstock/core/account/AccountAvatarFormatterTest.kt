package pl.syntaxdevteam.medstock.core.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountAvatarFormatterTest {

    @Test
    fun avatarLabelUsesFirstEmailCharacterUppercased() {
        assertEquals("S", AccountAvatarFormatter.avatarLabel("syntax.dev@example.com"))
    }

    @Test
    fun avatarLabelFallsBackWhenEmailIsBlank() {
        assertEquals("?", AccountAvatarFormatter.avatarLabel("   "))
    }
}
