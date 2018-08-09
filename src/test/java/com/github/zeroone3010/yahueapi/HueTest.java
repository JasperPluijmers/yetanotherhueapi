package com.github.zeroone3010.yahueapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.internal.Files;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HueTest {
  private static final String API_KEY = "abcd1234";
  public static final String API_BASE_PATH = "/api/" + API_KEY + "/";
  private static final String MOTION_SENSOR_NAME = "Hallway sensor";
  public static final String TEMPERATURE_SENSOR_NAME = "Hue temperature sensor 1";

  final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

  @BeforeEach
  void startServer() {
    wireMockServer.start();
  }

  @AfterEach
  void stopServer() {
    wireMockServer.stop();
  }

  private Hue createHueAndInitializeMockServer() {
    final String hueRoot = readFile("hueRoot.json");

    final ObjectMapper objectMapper = new ObjectMapper();
    final String light100;
    final String light101;
    final String light300;
    final String sensor15; // the temperature sensor
    final String sensor16; // the motion sensor

    try {
      final JsonNode jsonNode = objectMapper.readTree(hueRoot);
      light100 = objectMapper.writeValueAsString(jsonNode.get("lights").get("100"));
      light101 = objectMapper.writeValueAsString(jsonNode.get("lights").get("101"));
      light300 = objectMapper.writeValueAsString(jsonNode.get("lights").get("300"));
      sensor15 = objectMapper.writeValueAsString(jsonNode.get("sensors").get("15"));
      sensor16 = objectMapper.writeValueAsString(jsonNode.get("sensors").get("16"));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    wireMockServer.stubFor(get(API_BASE_PATH).willReturn(okJson(hueRoot)));
    wireMockServer.stubFor(get(API_BASE_PATH + "lights/100").willReturn(okJson(light100)));
    wireMockServer.stubFor(get(API_BASE_PATH + "lights/101").willReturn(okJson(light101)));
    wireMockServer.stubFor(get(API_BASE_PATH + "lights/300").willReturn(okJson(light300)));
    wireMockServer.stubFor(get(API_BASE_PATH + "sensors/15").willReturn(okJson(sensor15)));
    wireMockServer.stubFor(get(API_BASE_PATH + "sensors/16").willReturn(okJson(sensor16)));

    return new Hue("localhost:" + wireMockServer.port(), API_KEY);
  }

  @Test
  void testInitializationAndRefresh() {
    final Hue hue = createHueAndInitializeMockServer();
    wireMockServer.verify(0, getRequestedFor(urlEqualTo(API_BASE_PATH)));

    hue.refresh();
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH)));

    hue.refresh();
    wireMockServer.verify(2, getRequestedFor(urlEqualTo(API_BASE_PATH)));
  }

  @Test
  void testGetRooms() {
    final Hue hue = createHueAndInitializeMockServer();

    assertEquals(3, hue.getRooms().size());
    hue.getRooms();
    hue.getRooms();
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH)));
  }

  @Test
  void testGetRoomByName() {
    final Hue hue = createHueAndInitializeMockServer();

    assertEquals(2, hue.getRoomByName("Living room").get().getLights().size());
    assertEquals(1, hue.getRoomByName("Bedroom").get().getLights().size());
    assertFalse(hue.getRoomByName("No such room").isPresent());
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH)));
  }

  @Test
  void testLightTypes() {
    final Hue hue = createHueAndInitializeMockServer();
    assertEquals(LightType.EXTENDED_COLOR,
        hue.getRoomByName("Living room").get().getLightByName("LR 1").get().getType());
    assertEquals(LightType.COLOR_TEMPERATURE,
        hue.getRoomByName("Living room").get().getLightByName("LR 2").get().getType());
    assertEquals(LightType.COLOR,
        hue.getRoomByName("Hallway 1").get().getLightByName("LED strip 1").get().getType());
  }

  @Test
  void testGetLightStateWhenXyMode() {
    final Hue hue = createHueAndInitializeMockServer();
    final State state = hue.getRoomByName("Living room").get().getLightByName("LR 1").get().getState();
    assertFalse(state.getOn());
    assertEquals(0.3689f, state.getXy().get(0).floatValue());
    assertEquals(0.3719f, state.getXy().get(1).floatValue());
    assertEquals(254, state.getBri().intValue());
    assertNull(state.getHue());
    assertNull(state.getSat());
    assertNull(state.getCt());

    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH)));
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH + "lights/100")));
  }

  @Test
  void testGetLightStateWhenCtMode() {
    final Hue hue = createHueAndInitializeMockServer();
    final State state = hue.getRoomByName("Living room").get().getLightByName("LR 2").get().getState();
    assertFalse(state.getOn());
    assertNull(state.getXy());
    assertEquals(123, state.getBri().intValue());
    assertEquals(230, state.getCt().intValue());
    assertNull(state.getHue());
    assertNull(state.getSat());

    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH)));
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH + "lights/101")));
  }

  @Test
  void testGetLightStateWhenHsMode() {
    final Hue hue = createHueAndInitializeMockServer();
    final State state = hue.getRoomByName("Hallway 1").get()
        .getLightByName("LED strip 1").get().getState();

    assertTrue(state.getOn());
    assertNull(state.getXy());
    assertEquals(42, state.getBri().intValue());
    assertEquals(38677, state.getHue().intValue());
    assertEquals(61, state.getSat().intValue());
    assertNull(state.getCt());

    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH)));
    wireMockServer.verify(1, getRequestedFor(urlEqualTo(API_BASE_PATH + "lights/300")));
  }

  @Test
  void testGetUnknownSensors() {
    final Hue hue = createHueAndInitializeMockServer();
    final Collection<ISensor> sensors = hue.getUnknownSensors();
    assertEquals(4, sensors.size());
  }

  @Test
  void testGetTemperatureSensorByName() {
    final Hue hue = createHueAndInitializeMockServer();
    assertTrue(hue.getTemperatureSensorByName(TEMPERATURE_SENSOR_NAME).isPresent());
    assertFalse(hue.getTemperatureSensorByName("No such sensor").isPresent());
  }

  @Test
  void testGetMotionSensorByName() {
    final Hue hue = createHueAndInitializeMockServer();
    assertTrue(hue.getMotionSensorByName(MOTION_SENSOR_NAME).isPresent());
    assertFalse(hue.getMotionSensorByName("No such sensor").isPresent());
  }

  @Test
  void testMotionSensorLastUpdated() {
    final Hue hue = createHueAndInitializeMockServer();
    final ZonedDateTime actual = hue.getMotionSensorByName(MOTION_SENSOR_NAME).map(ISensor::getLastUpdated).get();
    final ZonedDateTime expected = ZonedDateTime.of(LocalDate.of(2018, Month.JULY, 29),
        LocalTime.of(6, 6, 6), ZoneId.of("UTC"));
    assertEquals(expected, actual);
  }

  @Test
  void testMotionSensorPresence() {
    final Hue hue = createHueAndInitializeMockServer();
    final boolean presence = hue.getMotionSensorByName(MOTION_SENSOR_NAME).map(IMotionSensor::isPresence).get();
    assertTrue(presence);
  }

  @Test
  void testTemperatureSensorTemperature() {
    final Hue hue = createHueAndInitializeMockServer();
    final double temperature = hue.getTemperatureSensorByName(TEMPERATURE_SENSOR_NAME)
        .map(ITemperatureSensor::getDegreesCelsius)
        .map(BigDecimal::doubleValue).get();
    assertEquals(29.53d, temperature);
  }

  private String readFile(final String fileName) {
    final ClassLoader classLoader = getClass().getClassLoader();
    final File file = new File(classLoader.getResource(fileName).getFile());
    try {
      return Files.read(file);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}