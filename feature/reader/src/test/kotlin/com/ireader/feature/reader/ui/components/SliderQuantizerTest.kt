package com.ireader.feature.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderQuantizerTest {

    @Test
    fun `snapSliderValue should align line height values to 0_05 step`() {
        val range = 1.2f..2.0f

        assertEquals(1.25f, snapSliderValue(1.23f, range, 0.05f), 0.0001f)
        assertEquals(1.2f, snapSliderValue(0.8f, range, 0.05f), 0f)
        assertEquals(2.0f, snapSliderValue(2.4f, range, 0.05f), 0f)
    }

    @Test
    fun `snapSliderValue should align dp values to integer steps`() {
        val range = 0f..48f

        assertEquals(13f, snapSliderValue(13.49f, range, 1f), 0f)
        assertEquals(14f, snapSliderValue(13.5f, range, 1f), 0f)
    }

    @Test
    fun `sliderDiscreteSteps should match material slider semantics`() {
        assertEquals(15, sliderDiscreteSteps(1.2f..2.0f, 0.05f))
        assertEquals(21, sliderDiscreteSteps(10f..32f, 1f))
        assertEquals(0, sliderDiscreteSteps(0f..48f, 0f))
    }
}
