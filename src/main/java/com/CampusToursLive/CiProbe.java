package com.CampusToursLive;

/** Intentionally untested production code to exercise the patch-coverage gate. */
public class CiProbe {
  public int classify(int n) {
    if (n < 0) return -1;
    if (n == 0) return 0;
    if (n < 10) return 1;
    return 2;
  }
}
