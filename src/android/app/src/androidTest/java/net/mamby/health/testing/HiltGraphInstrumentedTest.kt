package net.mamby.health.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.mamby.health.crypto.AesGcmVaultCipher
import net.mamby.health.crypto.VaultCipher
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltGraphInstrumentedTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var firstCipher: VaultCipher

    @Inject
    lateinit var secondCipher: VaultCipher

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun productionCipherBinding_isSingleton() {
        assertTrue(firstCipher is AesGcmVaultCipher)
        assertSame(firstCipher, secondCipher)
    }
}
