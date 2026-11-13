package com.codeja.master_acdc.ordering.application;

/** Sonda de la prueba 6 del lab S16b: no debe llegar a main. */
final class NpeProbe {
  private NpeProbe() {}

  static int lengthOrZero(String value) {
    try {
      return value.length();
    } catch (NullPointerException exception) {
      return 0;
    }
  }
}
