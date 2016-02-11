package com.github.pwittchen.reactivenetwork.library;

import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import rx.functions.Func1;

import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class) public class ConnectivityStatusTest {

  @Test public void testStatusShouldBeEqualToGivenValue() {
    // given
    ConnectivityStatus givenStatus = ConnectivityStatus.WIFI_CONNECTED;

    // when
    Func1<ConnectivityStatus, Boolean> equalTo = ConnectivityStatus.isEqualTo(givenStatus);
    Boolean shouldBeEqualToGivenStatus = equalTo.call(givenStatus);

    // then
    assertThat(shouldBeEqualToGivenStatus).isTrue();
  }

  @Test public void testStatusShouldBeEqualToOneOfGivenMultipleValues() {
    // given
    ConnectivityStatus mobileConnected = ConnectivityStatus.MOBILE_CONNECTED;
    ConnectivityStatus givenStatuses[] =
        { ConnectivityStatus.WIFI_CONNECTED, ConnectivityStatus.MOBILE_CONNECTED };

    // when
    Func1<ConnectivityStatus, Boolean> equalTo = ConnectivityStatus.isEqualTo(givenStatuses);
    Boolean shouldBeEqualToGivenStatus = equalTo.call(mobileConnected);

    // then
    assertThat(shouldBeEqualToGivenStatus).isTrue();
  }

  @Test public void testStatusShouldNotBeEqualToGivenValue() {
    // given
    ConnectivityStatus oneStatus = ConnectivityStatus.WIFI_CONNECTED;
    ConnectivityStatus anotherStatus = ConnectivityStatus.MOBILE_CONNECTED;

    // when
    Func1<ConnectivityStatus, Boolean> notEqualTo = ConnectivityStatus.isNotEqualTo(oneStatus);
    Boolean shouldBeEqualToGivenStatus = notEqualTo.call(anotherStatus);

    // then
    assertThat(shouldBeEqualToGivenStatus).isTrue();
  }

  @Test public void testStatusShouldNotBeEqualToOneOfGivenMultipleValues() {
    // given
    ConnectivityStatus offline = ConnectivityStatus.OFFLINE;
    ConnectivityStatus givenStatuses[] =
        { ConnectivityStatus.WIFI_CONNECTED, ConnectivityStatus.MOBILE_CONNECTED };

    // when
    Func1<ConnectivityStatus, Boolean> notEqualTo = ConnectivityStatus.isNotEqualTo(givenStatuses);
    Boolean shouldBeEqualToGivenStatus = notEqualTo.call(offline);

    // then
    assertThat(shouldBeEqualToGivenStatus).isTrue();
  }
}
