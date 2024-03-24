package org.influxdb;

import org.influxdb.dto.Pong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import okhttp3.OkHttpClient;

/**
 * Test the InfluxDB Factory API.
 *
 * @author fujian1115 [at] gmail.com
 *
 */
@RunWith(JUnitPlatform.class)
public class InfluxDBFactoryTest {

	/**
	 * Test for a {@link InfluxDBFactory #connect(String)}.
	 */
	@Test
	public void testShouldNotUseBasicAuthWhenCreateInfluxDBInstanceWithoutUserNameAndPassword() {
		InfluxDB influxDB = InfluxDBFactory.connect("http://" + TestUtils.getInfluxIP() + ":" + TestUtils.getInfluxPORT(true));
		verifyInfluxDBInstance(influxDB);
	}

	@Test
	public void testShouldNotUseBasicAuthWhenCreateInfluxDBInstanceWithUserNameAndWithoutPassword() {
		InfluxDB influxDB = InfluxDBFactory.connect("http://" + TestUtils.getInfluxIP() + ":" + TestUtils.getInfluxPORT(true), "admin", null);
		verifyInfluxDBInstance(influxDB);
	}

	private void verifyInfluxDBInstance(InfluxDB influxDB) {
		Assertions.assertNotNull(influxDB);
		Pong pong = influxDB.ping();
		Assertions.assertNotNull(pong);
		Assertions.assertNotEquals(pong.getVersion(), "unknown");
	}

	/**
	 * Test for a {@link InfluxDBFactory #connect(String, okhttp3.OkHttpClient.Builder)}.
	 */
	@Test
	public void testCreateInfluxDBInstanceWithClientAndWithoutUserNameAndPassword() {
		InfluxDB influxDB = InfluxDBFactory.connect("http://" + TestUtils.getInfluxIP() + ":" + TestUtils.getInfluxPORT(true), new OkHttpClient.Builder());
		verifyInfluxDBInstance(influxDB);
	}

	@Test
	public void testShouldThrowIllegalArgumentWithInvalidUrl() {
		Assertions.assertThrows(IllegalArgumentException.class,() -> {
			 InfluxDBFactory.connect("invalidUrl");
		});
	}

	@Test
	public void testUrlNotContainsColon() {
		Assertions.assertThrows(IllegalArgumentException.class, () ->
				InfluxDBFactory.connect("http://" + TestUtils.getInfluxIP()));
	}

	@Test
	public void testUrlEndWithColon() {
		Assertions.assertThrows(IllegalArgumentException.class, () ->
				InfluxDBFactory.connect("http://" + TestUtils.getInfluxIP() + ":"));
	}
}
