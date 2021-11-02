package com.wiligsi.plump.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

public class LockNameTests {

  @Test
  public void itShouldCreateALockNameWithValidName() {
    final String validName = "validName233";

    final LockName lockName = new LockName(validName);
    assertThat(lockName.getDisplayName()).isEqualTo(validName);
    assertThat(lockName.getInternalName()).isEqualTo(validName.toLowerCase());
  }

  @Test
  public void itShouldIgnoreCaseForEquals() {
    final String name = "testLock2";
    final String otherName = "TeStloCK2";

    final LockName lockName = new LockName(name);
    final LockName otherLockName = new LockName(otherName);
    assertThat(lockName.equals(otherLockName)).isTrue();
    assertThat(otherLockName.equals(lockName)).isTrue();
  }

  @Test
  public void itShouldThrowErrorOnShortName() {
    final String shortName = "no";
    assertThatIllegalArgumentException().isThrownBy(
        () -> new LockName(shortName)
    );
  }

  @Test
  public void itShouldThrowErrorOnLongName() {
    final String longName = "DangLongName234234234234";

    assertThatIllegalArgumentException().isThrownBy(
        () -> new LockName(longName)
    );
  }

  @Test
  public void itShouldThrowErrorOnSymbolName() {
    final String symbolName = "asd!@#  \t";

    assertThatIllegalArgumentException().isThrownBy(
        () -> new LockName(symbolName)
    );
  }
}
