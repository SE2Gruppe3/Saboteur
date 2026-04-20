package com.aau.saboteur.viewModels

import org.junit.Assert.assertEquals
import org.junit.Test

class DummyViewModelTest {

    @Test
    fun testIncrement() {
        val viewModel = DummyViewModel()
        viewModel.increment()
        assertEquals(1, viewModel.count.value)
    }
}
