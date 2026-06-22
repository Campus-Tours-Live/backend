package com.CampusToursLive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CiFailTest {
  @Test
  void deliberateFailure() {
    assertEquals(1, 2, "intentional CI-protection check");
  }
}
