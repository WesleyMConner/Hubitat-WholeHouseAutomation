// ---------------------------------------------------------------------------------
// R O O M   S C E N E S
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
//
//   Design Notes
//   - Multiple DevWL instances arise due to multiple input() statements.
//   - Initialization of 'state' includes making immutable copies of DeviveWrapper
//     instances, gathered from 'settings'.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DeviceWrapperList as DevWL
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.pbsgLibrary
#include wesmc.UtilsLibrary

definition(
  parent: 'wesmc:WholeHouseAutomation',
  name: 'RoomScenes',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Manage Room Scenes for Whole House Automation',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: false
)

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'roomScenesPage', title: '', install: true, uninstall: true)
}

// -----------------------------------------------------
// R O O M S   S C E N E S   P A G E   &   S U P P O R T
// -----------------------------------------------------

void solicitModesAsScenes (String roomName) {
  input(
    name: "${roomName}_modesAsScenes",
    type: 'enum',
    title: '<span style="margin-left: 10px;">' \
           + 'Select "Mode Names" to use as "Scene Names" <em>(optional)</em>' \
           + '</span>',
    submitOnChange: true,
    required: false,
    multiple: true,
    options: getLocation().getModes().collect{ mode -> mode.name }
  )
}

void solicitCustomScenes (String roomName) {
  String settingsKeyPrefix = "${roomName}_customScene"
  LinkedHashMap<String, String> slots = [
    "${settingsKeyPrefix}1": settings["${settingsKeyPrefix}1"],
    "${settingsKeyPrefix}2": settings["${settingsKeyPrefix}2"],
    "${settingsKeyPrefix}3": settings["${settingsKeyPrefix}3"],
    "${settingsKeyPrefix}4": settings["${settingsKeyPrefix}4"],
    "${settingsKeyPrefix}5": settings["${settingsKeyPrefix}5"],
    "${settingsKeyPrefix}6": settings["${settingsKeyPrefix}6"],
    "${settingsKeyPrefix}7": settings["${settingsKeyPrefix}7"],
    "${settingsKeyPrefix}8": settings["${settingsKeyPrefix}8"],
    "${settingsKeyPrefix}9": settings["${settingsKeyPrefix}9"]
  ]
  LinkedHashMap<String, String> filled = slots.findAll{it.value}
  // Only present 1 empty sceen "slot" at a time.
  LinkedHashMap<String, String> firstOpen = slots.findAll{!it.value}?.take(1)
  LinkedHashMap<String, String> custom = \
    firstOpen + filled.sort{ a, b -> a.value <=> b.value }
  paragraph 'Add Custom Scene Names <em>(optional)</em>'
  custom.each{ key, value ->
    input(
      name: key,
      type: 'text',
      title: "Custom Scene Name:",
      width: 2,
      submitOnChange: true,
      required: false,
      defaultValue: value
    )
  }
}

List<String> getRoomScenes (String roomName) {
  List<String> scenes = settings["${roomName}_modesAsScenes"]
  String settingsKeyPrefix = "${roomName}_customScene"
  List<String> customScenes = [
    settings["${settingsKeyPrefix}1"],
    settings["${settingsKeyPrefix}2"],
    settings["${settingsKeyPrefix}3"],
    settings["${settingsKeyPrefix}4"],
    settings["${settingsKeyPrefix}5"],
    settings["${settingsKeyPrefix}6"],
    settings["${settingsKeyPrefix}7"],
    settings["${settingsKeyPrefix}8"],
    settings["${settingsKeyPrefix}9"],
  ].findAll{it != null}
  scenes << customScenes
  scenes = scenes.flatten().sort().collect{scene -> "${roomName}-${scene}"}
  return scenes
}

void solicitSceneForRoomNameModeName (String roomName) {
  List<String> roomScenes = getRoomScenes(roomName)
  paragraph "Select scenes for per-mode automation"
  getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
    input(
      name: "${roomName}-${modeName}ToScene",
      type: 'enum',
      title: modeName,
      width: 2,
      submitOnChange: true,
      required: true,
      multiple: false,
      options: roomScenes,
      defaultValue: roomScenes.find{
        sceneName -> sceneName == "${roomName}-${modeName}"
      } ?: ''
    )
  }
}

Map<String, List<String>> getModeToScene (String roomName) {
  return getLocation().getModes()
            .collect{mode -> mode.name}
            .collectEntries{ modeName ->
              [modeName, settings["${roomName}-${modeName}ToScene"]]
            }
}

void solicitRepeatersForRoomScenes (String roomName) {
  collapsibleInput (
    blockLabel: "Repeaters for ${roomName} Scenes",
    name: "${roomName}-repeaters",
    title: 'Identify Repeater(s) supporting Room Scenes',
    type: 'enum',
    options: getMainRepeaters().collect{ d -> d.displayName }
  )
}

void solicitKeypadsForRoomScenes (String roomName) {
  collapsibleInput (
    blockLabel: "Keypads for ${roomName} Scenes",
    name: "${roomName}-keypads",
    title: 'Identify Keypad(s) supporting Room Scenes',
    type: 'enum',
    options: getKeypads().collect{ d -> d.displayName }
  )
}

void solicitLedDevicesForRoomScenes (String roomName) {
  collapsibleInput (
    blockLabel: "LED Devices for ${roomName} Scenes",
    name: "${roomName}-leds",
    title: 'Identify LED Button(s) supporting Room Scenes',
    type: 'enum',
    options: getLedDevices().collect{ d -> d.displayName }
  )
}

void solicitKeypadButtonsForScene (String roomName) {
  // One slider to collapse all entries in this section.
  input (
    name: boolGroup,
    type: 'bool',
    title: "${settings[boolGroup] ? 'Hiding' : 'Showing'} Keypad Buttons for Scene",
    submitOnChange: true,
    defaultValue: false,
  )
  if (!settings[boolSwitchName]) {
    getRoomScenes(roomName).each{sceneName ->
      input(
        name: "${roomName}-${sceneName}-keypadButtons",
        type: 'enum',
        title: "Identify Keypad Buttons for ${sceneName}.",
        submitOnChange: true,
        required: true,
        multiple: true,
        options: settings["${roomName}-leds"]
      )
    }
  }
}

List<DevW> narrowDevicestoRoom (String roomName, DevWL devices) {
  // This function excludes devices that are not associated with any room.
  List<String> deviceIdsForRoom = app.getRooms()
                                  .findAll{it.name == roomName}
                                  .collect{it.deviceIds.collect{it.toString()}}
                                  .flatten()
  return devices.findAll{ d -> deviceIdsForRoom.contains(d.id.toString())
  }
}

void solicitNonLutronDevicesForRoomScenes (String roomName) {
  List<DevW> roomSwitches = narrowDevicestoRoom(roomName, settings?.switches)
                            .findAll{
                              it.displayName.toString().contains('lutron') == false
                            }
  collapsibleInput (
    blockLabel: "Non-Lutron Devices for ${roomName} Scenes",
    name: "${roomName}-nonLutron",
    title: 'Identify Non-Lutron devices supporting Room Scenes',
    type: 'enum',
    options: roomSwitches.collect{ d -> d.displayName }
  )
}

void solicitRoomScene (String roomName) {
  // Display may be full-sized (12-positions) or phone-sized (4-position).
  // For phone friendliness, work one scene at a time.
  getRoomScenes(roomName).each{sceneName ->
    Integer col = 1
    paragraph("<br/><b>${sceneName} →</b>", width: 1)
    settings["${roomName}-nonLutron"].each{deviceName ->
      col += 2
      input(
        name: "${sceneName}:${deviceName}",
        type: 'number',
        title: "<b>${deviceName}</b><br/>Level 0..100",
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        defaultValue: 0
      )
    }
    settings["${roomName}-repeaters"].each{deviceName ->
      col += 2
      input(
        name: "${sceneName}.${deviceName}",
        type: 'number',
        title: "<b>${deviceName}</b><br/>Button #",
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        defaultValue: 0
      )
    }
    // Fill to end of logical row
    while (col++ % 12) {
      paragraph('', width: 1)
    }
  }
}

def roomScenesPage (/* params */) {
  dynamicPage(name: 'roomScenesPage') {
    section {
      paragraph "You are in the ${app.getLabel()}"
      /*
      String roomName = params.roomName
      paragraph (
        heading("${roomName} Scenes<br/>")
        + comment(
            'Tab to register changes.<br/>'
            + 'If "Error: Cannot get property" appears, click "↻" to reload the page.'
          )
      )
      // Mode-named scenes appear as a single settings List<String>.
      // 0..9 custom scenes appear individually as settings prefix1..prefix9.
      solicitModesAsScenes(roomName)
      solicitCustomScenes (roomName)
      solicitSceneForRoomNameModeName (roomName)
      solicitRepeatersForRoomScenes(roomName)
      solicitKeypadsForRoomScenes(roomName)
      solicitLedDevicesForRoomScenes(roomName)

      solicitKeypadButtonsForScene (roomName)

      solicitNonLutronDevicesForRoomScenes(roomName)


      solicitRoomScene (roomName)
      paragraph(
        heading('Debug<br/>')
        + "<b>Debug ${roomName} Scenes:</b> ${getRoomScenes(roomName)}<br/>"
        + "<b>Debug ${roomName} Mode-to-Scene:</b> ${getModeToScene(roomName)}<br/>"
        + "<b>Repeaters:</b> ${settings["${roomName}-repeaters"]}<br/>"
        + "<b>Keypads:</b>${settings["${roomName}-keypads"]}<br/>"
        + "<b>LED Devices:</b>${settings["${roomName}-leds"]}<br/>"
        + "<b>Non-Lutron Devices:</b> ${settings["${roomName}-nonLutron"]}"
      )
      //----> Is it necessary to solicit Keypad nuttons that trigger scenes?
      */
    }
  }
}

// ------------------------------------------------------------------------
// M E T H O D S   B A S E D   O N   S E T T I N G S
//   Clients can use the following methods (which operate exclusively on
//   Parent'settings' when rendering data entry screens.
// ------------------------------------------------------------------------
//--xx-- String assignChildAppRoomName (Long childAppId) {
//--xx--   List<String> focalRooms = settings.focalRooms
//--xx--   List<InstAppW> kidApps = getChildApps()
//--xx--   Map<String, String> kidIdToRoomName =
//--xx--     kidApps.collectEntries{ kid ->
//--xx--       [ kid.id.toString(), focalRooms.contains(kid.label) ? kid.label : null ]
//--xx--   }
//--xx--   Map<String, Boolean> roomNameToKidId = focalRooms.collectEntries{[it, false]}
//--xx--   kidIdToRoomName.each{ kidId, roomName ->
//--xx--     if (roomName) roomNameToKidId[roomName] = kidId
//--xx--   }
//--xx--   return result = kidIdToRoomName[childAppId.toString()]
//--xx--                   ?: roomNameToKidId.findAll{!it.value}.keySet().first()
//--xx-- }

//--xx-- Main Repeater LEDs will be used in lieu of individual Lutron
//--xx-- devices to detect Manual overrides.
//--xx--
//--xx-- List<DevW> getLutronDevices (String room) {
//--xx--   return narrowDevicestoRoom(room, settings?.switches).findAll{it.displayName.contains('lutron') && ! it.displayName.contains('LED')}
//--xx-- }

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

/**********
void installed(Boolean LOG = false) {
  if (LOG) log.trace 'WHA installed()'
  initialize()
}

void updated(Boolean LOG = false) {
  if (LOG) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void testHandler (Event e, Boolean LOG = false) {
  // SAMPLE 1
  //   descriptionText  (lutron-80) TV Wall KPAD button 1 was pushed [physical]
  //          deviceId  5686
  //       displayName  (lutron-80) TV Wall KPAD
  if (LOG) log.trace "WHA testHandler() w/ event: ${e}"
  if (LOG) logEventDetails(e, false)
}

void initialize(Boolean LOG = false) {
  if (LOG) log.trace "WHA initialize()"
  if (LOG) log.trace "WHA subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  settings.lutronTelnet.each{ d ->
    DevW device = d
    if (LOG) log.trace "WHA subscribing ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (LOG) log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DevW device = d
    if (LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (LOG) log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DevW device = d
    if (LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }

  //ArrayList<LinkedHashMap> modes = getModes()
  // Rebuild the PBSG mode instance adjusting (i.e., reusing or dropping)
  // previously-created VSWs to align with current App modes.
  //if (state['pbsg_modes']) { deletePBSG(name: 'pbsg_modes', dropChildVSWs: false) }
  //createPBSG(
  //  name: 'pbsg_modes',
  //  sceneNames: modes.collect{it.name},
  //  defaultScene: 'Day'
  //)
}
**********/

// -----------
// U N U S E D
// -----------

//--MISSING->displayParticipatingDevices()
//--MISSING->displayAppInfoLink()

LinkedHashMap<String, InstAppW> getAllChildAppsByLabel () {
  return getAllChildApps().collectEntries{
    childApp -> [ childApp.getLabel(), childApp ]
  }
}

void displayCustomScenes () {
  paragraph(
    '<table>'
      + params.collect{ k, v -> "<tr><th>${k}</th><td>${v}</td></tr>" }.join()
      + '</table>'
  )
}

/*
  LinkedHashMap unpairedChildAppsByName = getChildAppsByName (Boolean LOG = false)

  //->removeUnpairedChildApps ()
  if (LOG) log.info "childApps: ${childApps.collect{it.getLabel()}.join(', ')}"

  // MapfocalRoomsToRoomSceneApps
  LinkedHashMap roomAppsByName = settings.focalRooms.collectEntries{
    room -> [room, unpairedChildIds.contains(room) ?: null]
  }

  // Prepare to capture the Mode PBSG child app.
  InstAppW pbsgModeApp = null

  // Prepare to remove unused child apps.
  List<String> unusedDeviceNetworkIds = []

  // Parse existing (discovered) Child Apps, removing unaffiliated children.
  List<InstAppW> childApps = getAllChildApps()
  //--
  childApps.each{ childApp ->
    String childLabel = childApp.getLabel()
    if (childLabel == 'pbsg-mode') {
      pbsgModeApp = childApp
    } else if (settings.focalRooms.contains(childLabel)) {
      roomAppsByName.putAt(childLabel, child)
    } else {
      unusedDeviceNetworkIds << childApp.deviceNetworkId
    }
  }
  unusedDeviceNetworkIds.each{ deviceNetworkId ->
    if (LOG) log.info "Removing stale childApps ${deviceNetworkId}"
    deleteChildDevice(deviceNetworkId)
  }
*/

