package com.github.pwittchen.reactivenetwork.library;

import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class) public class ReactiveNetworkTest {

  @Test public void testReactiveNetworkObjectShouldNotBeNull() {
    // given
    ReactiveNetwork reactiveNetwork;

    // when
    reactiveNetwork = new ReactiveNetwork();

    // then
    assertThat(reactiveNetwork).isNotNull();
  }
}
