package com.pocket.opencode

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigStoreTest {
    @Test
    fun `valid object accepted`() = assertNull(ConfigStore.validate("""{"model":"x"}"""))

    @Test
    fun `broken json rejected`() = assertNotNull(ConfigStore.validate("{"))

    @Test
    fun `non object rejected`() = assertNotNull(ConfigStore.validate("[]"))
}
