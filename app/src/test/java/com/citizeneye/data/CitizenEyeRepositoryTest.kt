package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CitizenInputValidatorTest {
    @Test fun validatesFrenchZipCodeShape() {
        assertEquals(true, CitizenInputValidator.isZipCode("75001"))
        assertEquals(false, CitizenInputValidator.isZipCode("7500"))
        assertEquals(false, CitizenInputValidator.isZipCode("75A01"))
    }

    @Test fun acceptsZipOrCitySearches() {
        assertEquals(true, CitizenInputValidator.canSearch("75001"))
        assertEquals(true, CitizenInputValidator.canSearch("Paris"))
        assertEquals(false, CitizenInputValidator.canSearch("P"))
    }
}
