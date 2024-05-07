// ---------------------------------------------------------------------------------
// R O O M   S C E N E S →
//
//   Copyright (C) 2023-Present Wesley M. Conner
//
//   LICENSE
//     Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//     "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//     not use this file except in compliance with the License. Unless
//     required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//     implied.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
import java.util.regex.Pattern
import java.util.regex.Matcher
// The Groovy Linter generates false positives on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsgV2

definition (
  parent: 'wesmc:WHA',
  name: 'RoomScenes',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Manage WHA Rooms for Whole House Automation',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: false
)

preferences {
  page(name: 'RoomScenesPage')
}

void clearManualOverride() {
  state.moDetected = [:]
}

Boolean isManualOverride() {
  return state.moDetected
}

Boolean isRoomOccupied() {
  // If any Motion sensor for a room detects occupancy (i.e., appears in the
  // following state variable array), then the room is occupied.
  // In the absence of a sensor, state.activeMotionSensors = [ true ]
  return state.activeMotionSensors
}

Boolean isSufficientLight() {
  // If any Lux sensor for a room has sufficient light (i.e., appears in the
  // following state variable array), then the room has sufficient light.
  // In the absence of a sensor, state.brightLuxSensors = [ ]
  return state.brightLuxSensors
}

String expectedScene() {
  return (isRoomOccupied() == false || isSufficientLight() == true)
    ? 'OFF' : state.activeScene
}

void pushRepeaterButton(String repeaterName, Long buttonNumber) {
  settings.repeaters.each { repeater ->
    if(repeater.getName() == repeaterName) {
      repeater.push(buttonNumber)
    }
  }
}

void setDeviceLevel(String deviceName, Long level) {
  settings.indDevices.each { device ->
    if (device.getName() == deviceName) {
      if (device.hasCommand('setLevel')) {
        logInfo('activateScene', "Setting ${b(deviceId)} to level ${b(level)}")
        // Some devices DO NOT support a level of 100.
        device.setLevel(level == 100 ? 99 : level)
      }
      if (level == 0) {
        logInfo('activateScene', "Setting ${b(deviceId)} to off")
        device.off()
      } else if (level == 100) {
        logInfo('activateScene', "Setting ${b(deviceId)} to on")
        device.on()
      }
    }
  }
}

void activateScene() {
  // expectedScene considers room occupancy and lux constraints
  String expectedScene = expectedScene()
  if (state.currScene != expectedScene) {
    logInfo('activateScene', "${state.currScene} -> ${expectedScene}")
    state.currScene = expectedScene
    // Decode and process the scene's per-device actions
    Map actions = state.scenes.get(state.currScene)
    actions.get('Rep').each { repeaterId, button ->
      logInfo('activateScene', "Pushing repeater (${repeaterId}) button (${button})")
      pushRepeaterButton(repeaterId, button)
    }
    actions.get('Ind').each { deviceId, value ->
      setDeviceLevel(deviceId, value)
    }
  }
}

void updateTargetScene() {
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  if (
    (state.activeButton == 'Automatic' && !state.activeScene)
    || (state.activeButton == 'Automatic' && !isManualOverride())
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    state.activeScene = getLocation().getMode() //settings["modeToScene^${mode}"]
    logInfo(
      'updateTargetScene',
      "Setting activeScene per getLocation().getMode() (${state.activeScene})"
    )
  } else {
    state.activeScene = state.activeButton
  }
}

void pbsg_ButtonOnCallback(String pbsgName) {
  // Pbsg/Dashboard/Alexa actions override Manual Overrides.
  pbsg = atomicState."${pbsgName}"
  if (pbsg) {
      String priorActiveButton = state.activeButton
      state.activeButton = pbsg.activeButton ?: 'Automatic'
      logInfo(
        'pbsg_ButtonOnCallback',
        "${b(priorActiveButton)} -> ${b(state.activeButton)}"
      )
      clearManualOverride()
      updateTargetScene()
      activateScene()
  } else {
    logError(
      'pbsg_ButtonOnCallback',
      "Could not find PBSG '${pbsgName}' via atomicState"
    )
  }
}

Boolean isDeviceType(String devTypeCandidate) {
  return ['Rep', 'Ind'].contains(devTypeCandidate)
}

Integer expectedSceneDeviceValue(String devType, String deviceId) {
  Integer retVal = null
  if (isDeviceType(devType)) {
    retVal = state.scenes?.get(state.activeScene)?.get(devType)?.get(deviceId)
  } else {
    logError('expectedSceneDeviceValue', "devType (${devType}) not recognized")
  }
  return retVal
}

void subscribeToIndDeviceHandlerNoDelay() {
  settings.indDevices.each { d ->
    logInfo(
      'subscribeToIndDeviceHandlerNoDelay',
      "${state.ROOM_LABEL} subscribing to independentDevice ${d.getName()}"
    )
    subscribe(d, indDeviceHandler, ['filterEvents': true])
  }
}

void subscribeIndDevToHandler(Map data) {
  // USAGE:
  //   runIn(1, 'subscribeIndDevToHandler', [data: [device: d]])
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    'subscribeIndDevToHandler',
    "${state.ROOM_LABEL} subscribing ${data.device?.getName()}"
  )
  subscribe(device, indDeviceHandler, ['filterEvents': true])
}

void unsubscribeIndDevToHandler(DevW device) {
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    '_unsubscribeToIndDeviceHandler',
    "${state.ROOM_LABEL} unsubscribing ${device.getName()}"
  )
  unsubscribe(device)
}

void subscribeToMotionSensorHandler() {
  if (settings.motionSensors) {
    state.activeMotionSensors = []
    settings.motionSensors.each { d ->
      logInfo(
        'initialize',
        "${state.ROOM_LABEL} subscribing to Motion Sensor ${d.getName()}"
      )
      subscribe(d, motionSensorHandler, ['filterEvents': true])
      if (d.latestState('motion').value == 'active') {
        state.activeMotionSensors = cleanStrings([*state.activeMotionSensors, displayName])
        activateScene()
      } else {
        state.activeMotionSensors?.removeAll { it == displayName }
        activateScene()
      }
    }
  } else {
    state.activeMotionSensors = [ true ]
  }
}

void subscribeToLuxSensorHandler() {
  if (settings.luxSensors) {
    state.brightLuxSensors = []
    settings.luxSensors.each { d ->
      logInfo(
        'subscribeToLuxSensorHandler',
        "${state.ROOM_LABEL} subscribing to Lux Sensor ${d.getName()}"
      )
      subscribe(d, luxSensorHandler, ['filterEvents': true])
    }
  } else {
    state.brightLuxSensors = [ ]
  }
}

void indDeviceHandler(Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute state.activeScene.
  Integer reported
  // Only select events are considered for MANUAL OVERRIDE detection.
  if (e.name == 'level') {
    reported = safeParseInt(e.value)
  } else if (e.name == 'switch' && e.value == 'off') {
    reported = 0
  }
  String deviceId = extractDeviceIdFromLabel(e.displayName)
  Integer expected = expectedSceneDeviceValue('Ind', deviceId)
  if (reported == expected) {
    state.moDetected = state.moDetected.collect { key, value ->
      if (key != deviceId) { [key, value] }
    }
  } else {
    state.moDetected.put(deviceId, "${reported} (${expected})")
  }
}

void toggleButton(String button) {
  // Toggle the button's device and let activate and deactivate react.
  // This will result in delivery of the scene change via a callback.
  String dni = "${state.ROOM_LABEL}_${button}"
  DevW device = getChildDevice(dni)
  if (switchState(device) == 'on') {
    device.off()
  } else {
    devive.on()
  }
}

void room_ModeChange(String newMode) {
  // If the PBSG activeButton is 'Automatic', adjust the state.activeScene
  // per the newMode.
  if (state.activeButton == 'Automatic') {
    logInfo('room_ModeChange', "Automatic; so, ${b(state.activeScene)} -> '${b(newMode)}'")
    state.activeScene = newMode
    activateScene()
  } else {
    logInfo(
      'room_ModeChange',
      "Manual (${b(state.activeScene)}, ignoring new mode (${b(newMode)})"
    )
  }
}

void motionSensorHandler(Event e) {
  // It IS POSSIBLE to have multiple motion sensors per room.
  logDebug('motionSensorHandler', eventDetails(e))
  if (e.name == 'motion') {
    if (e.value == 'active') {
      logInfo('motionSensorHandler', "${e.displayName} is active")
      state.activeMotionSensors = cleanStrings([*state.activeMotionSensors, e.displayName])
      activateScene()
    } else if (e.value == 'inactive') {
      logInfo('motionSensorHandler', "${e.displayName} is inactive")
      state.activeMotionSensors?.removeAll { it == e.displayName }
      activateScene()
    } else {
      logWarn('motionSensorHandler', "Unexpected event value (${e.value})")
    }
  }
}

void luxSensorHandler(Event e) {
  // It IS POSSIBLE to have multiple lux sensors impacting a room. The lux
  // sensor(s) change values frequently. The activateScene() method is only
  // invoked if the aggregate light level changes materially (i.e., from
  // no sensor detecting sufficient light to one or more sensors detecting
  // sufficient light).
  if (e.name == 'illuminance') {
    if (e.value.toInteger() >= settings.lowLuxThreshold) {
      // Add sensor to list of sensors with sufficient light.
      state.brightLuxSensors = cleanStrings([*state.brightLuxSensors, e.displayName])
    } else {
      // Remove sensor from list of sensors with sufficient light.
      state.brightLuxSensors?.removeAll { it == e.displayName }
    }
    logTrace('luxSensorHandler', [
      "sensor name: ${e.displayName}",
      "illuminance level: ${e.value}",
      "sufficient light threshold: ${settings.lowLuxThreshold}",
      "sufficient light: ${state.brightLuxSensors}"
    ])
    activateScene()
  }
}

void picoHandler(Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
        // Check to see if the received button is assigned to a scene.
        String scene = state.picoButtonToTargetScene?.getAt(e.deviceId.toString())
                                                    ?.getAt(e.value.toString())
        if (scene) {
          logInfo(
            'picoHandler',
            "w/ ${e.deviceId.toString()}-${e.value} toggling ${scene}"
          )
          toggleButton(scene)
        } else if (e.value == '2') {  // Default "Raise" behavior
          logTrace('picoHandler', "Raising ${settings.indDevices}")
          settings.indDevices.each { d ->
            if (switchState(d) == 'off') {
              d.setLevel(5)
              //d.on()
            } else {
              d.setLevel(Math.min(
                (d.currentValue('level') as Integer) + changePercentage,
                100
              ))
            }
          }
        } else if (e.value == '4') {  // Default "Lower" behavior
          logTrace('picoHandler', "Lowering ${settings.indDevices}")
          settings.indDevices.each { d ->
            d.setLevel(Math.max(
              (d.currentValue('level') as Integer) - changePercentage,
              0
            ))
          }
        } else {
          logTrace(
            'picoHandler',
            "${state.ROOM_LABEL} picoHandler() w/ ${e.deviceId}-${e.value} no action."
          )
        }
        break
    }
  }
}

void installed() {
  logTrace('installed', 'At Entry')
  initialize()
}

void updated() {
  logTrace('updated', 'At Entry')
  unsubscribe()  // Suspend all events (e.g., child devices, mode changes).
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'At Entry')
}

void initialize() {
  logInfo(
    'initialize',
    "${state.ROOM_LABEL} initialize() of '${state.ROOM_LABEL}'. "
      //+ "Subscribing to modeHandler."
  )
  /*
  settings.each{ s ->
    String x = "${s}"
    logInfo('#658', x)
    if (
      x.contains('^ra2-1')
      || x.contains('^ra2-83')
      || x.contains('^pro2-1')
      || x.contains('^Ind-02')
      || x.contains('^Ind-0B')
      || x.contains('^Ind-Front')
      || x.contains('^Ind-Guest')
      || x.contains('^Ind-Primary')
      || x.contains('^Front')
      || x.contains('^Guest')
      || x.contains('^Primary')
    ) {
      app.removeSetting(x)
      String xAfter = "${settings.s}"
      logInfo('#673', "'${x}' -> '${xAfter}'")
    }
  }
  */
  state.brightLuxSensors = []
  populateStateScenesAssignValues()
  clearManualOverride()
  settings.indDevices.each { device -> unsubscribe(device) }
  //-> subscribeToRepHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  // ACTIVATION
  //   - If Automatic is already active in the PBSG, pbsg_ButtonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  Map pbsg = atomicState."${state.ROOM_LABEL}"
  if (pbsg) {
    pbsg_ActivateButton(pbsg.name, 'Automatic')
    pbsg_ButtonOnCallback(pbsg.name)
  } else {
    logWarn(
      'initialize',
      'The RSPbsg is pending additional configuration data.'
    )
  }
}

void idMotionSensors() {
  input(
    name: 'motionSensors',
    title: [
      heading3('Identify Room Motion Sensors'),
      bullet2('The special scene OFF is Automatically added'),
      bullet2('OFF is invoked when the room is unoccupied')
    ].join('<br/>'),
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idLuxSensors() {
  input(
    name: 'luxSensors',
    title: [
      heading3('Identify Room Lux Sensors'),
      bullet2('The special scene OFF is Automatically added'),
      bullet2('OFF is invoked when no Lux Sensor is above threshold')
    ].join('<br/>'),
    type: 'capability.illuminanceMeasurement',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idLowLightThreshold() {
  input(
    name: 'lowLuxThreshold',
    title: [
      heading3('Identify Low-Light Lux Threshold')
    ].join('<br/>'),
    type: 'number',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

//--HOLD-> void nameCustomScene() {
//--HOLD->   input(
//--HOLD->     name: customScene,
//--HOLD->     type: 'text',
//--HOLD->     title: heading2('Custom Scene Name (na = not applicable)'),
//--HOLD->     width: 4,
//--HOLD->     submitOnChange: true,
//--HOLD->     required: true,
//--HOLD->     defaultValue: 'na'
//--HOLD->   )
//--HOLD-> }

void adjustStateScenesKeys() {
  ArrayList netScenes = getLocation().getModes()*.name
  logInfo('adjustStateScenesKeys#498', "netScenes: ${netScenes}")
  if (settings.customScene) {
    if (customScene != 'na') {
      netScenes << settings.customScene
    }
  }
  logInfo('adjustStateScenesKeys#502', "netScenes: ${netScenes}")
  if (settings.motionSensors || settings.luxSensors) {
    netScenes << 'OFF'
  }
  ArrayList currScenes = state.scenes?.collect{ k, v -> k }
  ArrayList missingScenes = netScenes.minus(currScenes)
  ArrayList droppedScenes = currScenes.minus(netScenes)
  logInfo('adjustStateScenesKeys#507', ['',
    "netScenes: >${netScenes.sort()}<",
    "currScenes: >${currScenes.sort()}<",
    "missingScenes: ${missingScenes.sort()}",
    "droppedScenes: ${droppedScenes.sort()}"
  ])
  Map sceneMap = state.scenes
  missingScenes.each { ms ->
    logWarn('#516', "Adding scene key ${ms}")
    state.scenes << [ms: [:]] }
  state.scenes = sceneMap.minus(
    droppedScenes.collectEntries { ds ->
      logWarn('#520', "Dropping scene key ${ds}")
      [ds: null]
    }
  )
}

Map getDeviceValues (String scene) {
  ArrayList allowedDeviceNames = []
  settings.indDevices.each{ d ->
    if (d.getName()) { allowedDeviceNames << d.getName()}
  }
  settings.repeaters.each{ r ->
    if (r.getName()) { allowedDeviceNames << r.getName()}
  }
  Map results = ['Rep': [:], 'Ind': [:]]
  settings.findAll { k1, v1 ->
    Pattern p = Pattern.compile(/scene\^(.*)\^(.*)\^(.*)/)
    Matcher m = p.matcher(k1)
    if (m.find()) {
      String sceneName = m.group(1)
      String dType = m.group(2)
      Boolean inscopeDType = ['Rep', 'Ind'].contains(dType)
      String dName =m.group(3)
      Boolean inscopeDName = allowedDeviceNames?.contains(dName)
      if (inscopeDType && inscopeDName) {
        // Only adjust/return devices for the current scene.
        if (sceneName == scene) {
          if (dType == 'Ind') {
            if (v1 > 100) {
              results.Ind << ["${dName}": 100]
            } else if (v1 < 0) {
              results.Ind << ["${dName}": 0]
            } else {
              results.Ind << ["${dName}": v1]
            }
          } else {  // DType = 'Rep'
            results.Rep << ["${dName}": v1]
          }
        }
      } else {
        // Scenes that reference stale device types or stale device names
        // are dropped irrespective of referenced scene name.
        logWarn('getDeviceValues', "Dropping >${k1}")
        app.removeSetting(k1)
      }
    }
  }
  logInfo('getDeviceValues', "scene: ${b(scene)}, results: ${results}")
  return results
}

void populateStateScenesAssignValues() {
  state.scenes = state.scenes.collectEntries { scene, map ->
    if (scene == 'INACTIVE') {
      Map M = getDeviceValues(scene)
      if (M) ['OFF', M]
    } else {
      Map M = getDeviceValues(scene)
      if (M) [scene, M]
    }
  }
}

void idRa2RepeatersImplementingScenes() {
  input(
    name: 'repeaters',
    title: heading3("Identify RA2 Repeaters with Integration Buttons for Room Scenes"),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idIndDevices() {
  input(
    name: 'indDevices',
    title: heading3("Identify the Room's Non-RA2 Devices (e.g., Lutron Caséta, Z-Wave)"),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void configureRoomScene() {
  // DESIGN NOTES
  //   There are three steps to populate "state.scenes" map.
  //   (1) adjustStateScenesKeys() creates a per-scene key and
  //       sets the value to [].
  //   (2) This method populates Settings keys "scene^SCENENAME^Ind|Rep^DNI".
  //       with Integer values:
  //         - 'light level' for Independent devices (Ind)
  //         - 'virtual button number' for RA2 Repeaters (RA2)
  //   (3) populateStateScenesAssignValues() harvests the settings from
  //       Step 2 to complete the "state.scenes" map.
  // VIRTUAL TABLE
  //   Hubitat page display logic simulates table cells.
  //     - Full-sized displays (computer monitors) are 12 cells wide.
  //     - Phone-sized displays are 4 cells wide.
  //   To ensure that each scene starts on a new row, this method adds
  //   empty cells (modulo 12) to ensure each scene begins in column 1.
  if (state.scenes) {
    ArrayList currSettingsKeys = []
    state.scenes?.sort().each { sceneName, ignoredValue ->
      if (sceneName == 'INACTIVE') { sceneName = 'OFF' }
      // Ignore the current componentList. Rebuilt it from scratch.
      Integer tableCol = 3
      paragraph("<br/><b>${sceneName} →</b>", width: 2)
      settings.indDevices?.each { d ->
        String inputName = "scene^${sceneName}^Ind^${d.getName()}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Level 0..100",
          width: 3,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.repeaters?.each {d ->
  //-> logInfo('configureRoomScene', "-->${d.getCapabilities()}<--")
        String inputName = "scene^${sceneName}^Rep^${d.getName()}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Button #",
          width: 3,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      // Pad the remainder of the table row with empty cells.
      while (tableCol++ % 12) {
        paragraph('', width: 1)
      }
    }
  }
}

Map RoomScenesPage() {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.label as state.ROOM_LABEL.
  return dynamicPage(
    name: 'RoomScenesPage',
    title: [
      heading1("${app.label} Scenes - ${app.id}"),
      bullet1('Tab to register changes.'),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true,
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    state.ROOM_LABEL = app.label  // WHA creates App w/ Label == Room Name
    state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    //->state.remove('sufficientLight')
    //->state.remove('targetScene')
    //->state.brightLuxSensors = []
    //->state.remove('RSPBSG_LABEL')
    //->state.remove('pbsgStore')
    //->app.removeSetting('pbsgLogThresh')
    //->app.removeSetting('modeToScene^Chill')
    //->app.removeSetting('modeToScene^Cleaning')
    //->app.removeSetting('modeToScene^Day')
    //->app.removeSetting('modeToScene^Night')
    //->app.removeSetting('modeToScene^Party')
    //->app.removeSetting('modeToScene^Supplement')
    //->app.removeSetting('modeToScene^TV')
    //->app.removeSetting('modesAsScenes')
    //->app.removeSetting('scene^INACTIVE^Rep^ra2-1')
    //->app.removeSetting('scene^INACTIVE^Rep^ra2-83')
    //->app.removeSetting('customScene1')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      //--HOLD-> nameCustomScene()
      //--HOLD-> if (settings.customScene) {
      adjustStateScenesKeys()
      //*********************************************************************
      if (state.scenes) {
        ArrayList scenes = state.scenes.collect{ k, v -> return k }
        // For now, state.ROOM_LABEL is redundant given atomicState.name.
        atomicState."${state.ROOM_LABEL}" = [
          'name': state.ROOM_LABEL,
          'allButtons': [ *scenes, 'Automatic' ].minus([ 'INACTIVE', 'OFF' ]),
          'defaultButton': 'Automatic',
          'instType': 'pbsg'  // Just a pbsg until elevated to 'room'
        ]
        Map rsPbsg = pbsg_BuildToConfig(state.ROOM_LABEL)
      } else {
        paragraph "Creation of the room's PBSG is pending identification of room scenes"
      }
      //*********************************************************************
      idRa2RepeatersImplementingScenes()
      idIndDevices()
      configureRoomScene()
    }
  }
}
