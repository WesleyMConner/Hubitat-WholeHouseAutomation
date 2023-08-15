// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N
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
  name: 'WholeHouseAutomation',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'whaPage', title: '', install: true, uninstall: true)
  page(name: 'roomScenesPage', title: '', install: false, uninstall: false)
}

// -----------------------------------
// W H A   P A G E   &   S U P P O R T
// -----------------------------------

void solictfocalRoomNames () {
  roomPicklist = app.getRooms().collect{it.name}.sort()
  collapsibleInput(
    blockLabel: 'Focal Rooms',
    name: 'focalRoomNames',
    type: 'enum',
    title: 'Select Participating Rooms',
    options: roomPicklist
  )
}

void solicitLutronTelnetDevice () {
  collapsibleInput (
    blockLabel: 'Lutron Telnet Device',
    name: 'lutronTelnet',
    title: 'Confirm Lutron Telnet Device<br/>' \
      + comment('Used to detect Main Repeater LED state changes'),
    type: 'device.LutronTelnet'
  )
}

void solicitLutronMainRepeaters () {
  collapsibleInput (
    blockLabel: 'Lutron Main Repeaters',
    name: 'lutronRepeaters',
    title: 'Identify Lutron Main Repeater(s)<br/>' \
      + comment('Used to invoke in-kind Lutron scenes'),
    type: 'device.LutronKeypad'
  )
}

void solicitLutronMiscellaneousKeypads () {
  collapsibleInput (
    blockLabel: 'Lutron Miscellaneous Keypads',
    name: 'lutronMiscKeypads',
    title: 'Identify participating Lutron Miscellaneous Devices<br/>' \
      + comment('used to trigger room scenes'),
    type: 'device.LutronKeypad'
  )
}

void solicitSeeTouchKeypads () {
  collapsibleInput (
    blockLabel: 'Lutron SeeTouch Keypads',
    name: 'seeTouchKeypad',
    title: 'Identify Lutron SeeTouch Keypads<br/>' \
      + comment('used to trigger room scenes.'),
    type: 'device.LutronSeeTouchKeypad'
  )
}

void solicitLutronLEDs () {
  collapsibleInput (
    blockLabel: 'Lutron LEDs',
    name: 'lutronLEDs',
    title: 'Select participating Lutron LEDs<br/>' \
      + comment('Used to trigger room scenes.'),
    type: 'device.LutronComponentSwitch'
  )
}

void solicitLutronPicos () {
  collapsibleInput (
    blockLabel: 'Lutron Picos',
    name: 'lutronPicos',
    title: 'Select participating Lutron Picos<br/>' \
      + comment('used to trigger room scenes'),
    type: 'device.LutronFastPico'
  )
}

void solicitSwitches () {
  collapsibleInput (
    blockLabel: 'Non-Lutron, Non-VSW Devices',
    name: 'switches',
    title: 'Select participating Non-Lutron, Non-VSW switches and dimmers',
    type: 'capability.switch'
 )
}

void manageChildApps(Boolean LOG = false) {
  // Abstract
  //   Manage child applications AND any required initialization data.
  //   Child applications are automatically created and given a "label".
  //   Any required initialization data is stored at state.<label> and
  //   exposed to child applications via getChildInit(<label>). Child
  //   applications house their own state data locally.
  // Design Notes
  //   Application state data managed by this method includes:
  //     - state.childAppsByRoom
  //     - state.<roomName>
  //     - state.pbsg_modes
  // Deliberately create noise for testing dups:
  //   addChildApp('wesmc', 'RoomScenes', 'Kitchen')
  //   addChildApp('wesmc', 'RoomScenes', 'Den')
  //   addChildApp('wesmc', 'RoomScenes', 'Kitchen')
  //   addChildApp('wesmc', 'RoomScenes', 'Puppies')
  //   addChildApp('wesmc', 'whaPBSG', 'Butterflies')
  if (LOG) log.trace (
    'manageChildApps() on entry getAllChildApps(): '
    + getAllChildApps().sort{ a, b ->
        a.getLabel() <=> b.getLabel() ?: a.getId() <=> b.getId()
      }.collect{ app ->
        "<b>${app.getLabel()}</b> -> ${app.getId()}"
      }?.join(', ')
  )
  // Child apps are managed by App Label, which IS NOT guaranteed to be
  // unique. The following method keeps only the latest (highest) App ID
  // per App Label.
  LinkedHashMap<String, InstAppW> childAppsByLabel \
    = keepOldestAppObjPerAppLabel(LOG)
  if (LOG) log.trace (
    'manageChildApps() after keepOldestAppObjPerAppLabel(): '
    + childAppsByLabel.collect{label, childObj ->
        "<b>${label}</b> -> ${childObj.getId()}"
      }?.join(', ')
  )
  // Ensure Room Scenes instances exist (no init data is required).
  LinkedHashMap<String, InstAppW> childAppsByRoom = \
    settings.focalRoomNames?.collectEntries{ roomName ->
      [
        roomName,
        getByLabel(childAppsByLabel, roomName)
          ?: addChildApp('wesmc', 'RoomScenes', roomName)
      ]
    }
  if (LOG) log.trace (
    'manageChildApps() after adding any missing Room Scene apps:'
    + childAppsByRoom.collect{ roomName, roomObj ->
        "<b>${roomName}</b> -> ${roomObj.getId()}"
      }?.join(', ')
  )
  state.roomNameToRoomScenes = childAppsByRoom
  // Ensure imutable PBSG init data is in place AND instance(s) exist.
  // The PBSG instance manages its own state data locally.
  state.pbsg_modes = Map.of(
    'sceneNames', modes.collect{it.name},
    'defaultScene', getGlobalVar('defaultMode')
  )
  InstAppW pbsgModesApp = getByLabel(childAppsByLabel, 'pbsg_modes')
                          ?: addChildApp('wesmc', 'whaPBSG', 'pbsg_modes')
  // Purge excess (remaining) Child Apps
  childAppsByLabel.each{ label, app ->
    if (LOG) log.trace(
      "manageChildApps() keySet()=${ childAppsByRoom.keySet() }"
      + "has label=${label} ? ${ childAppsByRoom.keySet().findAll{it == label} ? true : false}"
    )
    if (childAppsByRoom.keySet().findAll{it == label}) {
      // Skip, still in use
    } else if (label == 'pbsg_modes') {
      // Skip, still in use
    } else {
      if (LOG) log.trace "Deleting orphaned child app ${label} (${app.getId()})."
      deleteChildApp(app.getId())
    }
  }

}

void displayRoomNameHrefs () {
  state.roomNameToRoomScenes.each{ roomName, roomApp ->
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.getId()}/roomScenesPage",
      style: 'internal',
      title: "Edit <b>${roomName}</b> Scenes (id=${roomApp?.getId()})",
      state: null, //'complete'
    )
  }
}

void removeAllChildApps (Boolean LOG = false) {
  getAllChildApps().each{ child ->
    if (LOG) log.trace "child: >${child.getId()}< >${child.getLabel()}<"
    deleteChildApp(child.getId())
  }
}

void pruneOrphanedChildApps (Boolean LOG = false) {
  //Initially, assume InstAppW supports instance equality tests -> values is a problem
  List<InstAppW> kids = getAllChildApps()
  if (LOG) log.info(
    "pruneOrphanedChildApps() processing ${kids.collect{it.getLabel()}.join(', ')}"
  )
  List<String> roomNames =
  kids.each{ kid ->
    if (settings.focalRoomNames?.contains(kid)) {
      if (LOG) log.info "pruneOrphanedChildApps() skipping ${kid.getLabel()} (room)"
    // Presently, PBSG IS NOT a child app, it is a contained instance.
    //} else if (kid == state['pbsg_modes'].name) {
    //  if (LOG) log.info "pruneOrphanedChildApps() skipping ${kid.getLabel()} (pbsg)"
    } else {
      if (LOG) log.info "pruneOrphanedChildApps() deleting ${kid.getLabel()} (orphan)"
      deleteChildApp(kid.getId())
    }
  }
}

void displayAppInfoLink () {
  paragraph comment('Whole House Automation - @wesmc, ' \
    + '<a href="https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation" ' \
    + 'target="_blank"><br/>Click for more information</a>')
}

Map whaPage() {
  return dynamicPage(name: 'whaPage') {
    section {
      app.updateLabel('Whole House Automation')
      paragraph heading('Whole House Automation<br/>') \
        + bullet('Select participating rooms and authorize device access.<br/>') \
        + bullet('Click <b>Done</b> to proceed to defining <b>Room Scene(s)</b>.')
      input (
        name: 'LOG',
        type: 'bool',
        title: "${settings.LOG ? 'Logging ENABLED' : 'Logging DISABLED'}",
        defaultValue: true,
        submitOnChange: true
      )
      solictfocalRoomNames()
      solicitLutronTelnetDevice()
      solicitLutronMainRepeaters()
      solicitLutronMiscellaneousKeypads()
      solicitSeeTouchKeypads()
      solicitLutronPicos()
      solicitLutronLEDs ()
      solicitSwitches()
      //->removeAllChildApps(settings.LOG)  // Clean after errant process
      paragraph heading('Room Scene Configuration')
      manageChildApps(settings.LOG)
      //displayRoomNameHrefs()
      //pruneOrphanedChildApps(settings.LOG)
      //displayAppInfoLink()
    }
  }
}

// -----------------------------
// " C H I L D "   S U P P O R T
// -----------------------------

List<DevW> getMainRepeaters () {
  return settings.lutronRepeaters
}

List<DevW> getKeypads() {
  return (settings.lutronMiscKeypads ?: []) \
         + (settings.seeTouchKeypad ?: []) \
         + (settings.lutronPicos ?: [])
}

List<DevW> getLedDevices () {
  return settings.lutronLEDs
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

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
}

// -----------
// U N U S E D
// -----------

void displayCustomScenes () {
  paragraph(
    '<table>'
      + params.collect{ k, v -> "<tr><th>${k}</th><td>${v}</td></tr>" }.join()
      + '</table>'
  )
}

//--MISSING->displayParticipatingDevices()




//--xx-- String assignChildAppRoomName (Long childAppId) {
//--xx--   List<String> focalRooms = settings.focalRoomNames
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
//--xx--   return narrowDevicestoRoom(room, settings.switches).findAll{it.displayName.contains('lutron') && ! it.displayName.contains('LED')}
//--xx-- }
