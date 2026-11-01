package com.devops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalculatorTest {

    private final Calculator calc = new Calculator();

    @Test
    void addsTwoNumbers() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void multipliesTwoNumbers() {
        assertEquals(12, calc.multiply(3, 4));
    }

    @Test
    void dividesTwoNumbers() {
        assertEquals(2.5, calc.divide(10, 4), 0.0001);
    }

    @Test
    void divideByZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> calc.divide(1, 0));
    }
}
