// ---------------------------------------------------------------------------------
// modePBSG (an instsantiation of libPbsgBase)
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
#include wesmc.libPbsgBase
#include wesmc.libUtils
#include wesmc.libLogAndDisplay

definition (
  parent: 'wesmc:wha',
  name: 'modePBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (extends libPbsgBase) designed for use in wha.groovy',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

preferences {
  page (name: 'modePbsgPage')
}

Map modePbsgPage () {
  // Norally, this page IS NOT presented.
  //   - This page can be viewed via an instance link from the main
  //     Hubitat Apps menu.
  //   - Instance state & settings are rendered on the parent App's page
  //     along with a button that can be used to force update() the App
  //     instance.
  return dynamicPage (
    name: 'modePbsgPage',
    install: true,
    uninstall: true
  ) {
    defaultPage()
  }
}

void installed () {
  Ltrace('installed()', 'At entry')
  modePbsgInit()
}

void updated () {
  Ltrace('updated()', 'At entry')
  modePbsgInit()
}

void uninstalled () {
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    Ldebug('uninstalled()', "Deleting '${device.deviceNetworkId}'")
    deleteChildDevice(device.deviceNetworkId)
  }
}

void modeVswEventHandler (Event e) {
  // Process events for Mode PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //   - When a Mode VSW turns on, change the Hubitat mode accordingly.
  //   - Clients DO NOT get a callback for this event.
  //   - Clients SHOULD subscribe to Hubitat Mode change events.
  //--DEEP-DEBUGGING-> Ltrace('modeVswEventHandler()', "eventDetails: ${eventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (state.previousVswDni == e.displayName) {
        Lerror(
          'modeVswEventHandler()',
          "The active Mode VSW '${state.activeVswDni}' did not change."
        )
      }
      state.previousVswDni = state.activeVswDni ?: state.defaultVswDni
      state.activeVswDni = e.displayName
      Ldebug(
        'modeVswEventHandler()',
        "${state.previousVswDni} -> ${state.activeVswDni}"
      )
      turnOnVswExclusively(state.activeVswDni)
      // Adjust the Hubitat mode.
      String mode = getModeNameForVswDni(e.displayName)
      Ldebug(
        'modeVswEventHandler()',
        "Setting mode to <b>${mode}</b>"
      )
      getLocation().setMode(mode)
    } else if (e.value == 'off') {
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'modeVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

void modePbsgInit() {
  Ltrace('modePbsgInit()', 'At entry')
  unsubscribe()
  List<DevW> vsws = getVsws()
  //--PRIVATE-> manageChildDevices()
  if (!vsws) {
    Lerror('modePbsgInit()', 'The child VSW instances are MISSING.')
  }
  vsws.each{ vsw ->
    Ltrace(
      'modePbsgInit()',
      "Subscribe <b>${vsw.dni} (${vsw.id})</b> to modeVswEventHandler()"
    )
    subscribe(vsw, "switch", modeVswEventHandler, ['filterEvents': false])
  }
  // The initially "on" PBSG VSW should be consistent with the Hubitat mode.
  String mode = getLocation().getMode()
  Ldebug(
    'modePbsgInit()',
    "Activating VSW for mode: <b>${mode}</b>"
  )
  Ltrace('modePbsgInit()', 'B E F O R E')
  turnOnVswExclusively(mode)
  Ltrace('modePbsgInit()', 'A F T E R')
}

/*
InstAppW getOrCreateModePbsg (
    String modePbsgName = 'whaModePbsg',
    String defaultMode = 'Day',
    String logThreshold = 'Debug'
  ) {
  // I M P O R T A N T
  //   - Functionally, a Mode PBSG is a singleton.
  //   - Any real work should occur in modePBSG.groovy (the App definition).
  //   - If modes are changed OR the pbsg's VSWs are manually, force a
  //     modePBSG.groovy update().
  //   - For efficienctm, DO NOT automatically invoke modePBSG.groovy update().
  List<String> modes = getLocation().getModes().collect{ it.name }
  if ( modes.contains(defaultMode) == false ) {
    Lerror(
      'getOrCreateModePbsg()',
      "The defaultMode (${defaultMode}) is not present in modes (${modes})"
    )
  }
  //-> Ltrace('createModePbsg()', "At entry")
  InstAppW modePbsg = getChildAppByLabel(modePbsgName)
  if (modePbsg) {
    Ltrace('getOrCreateModePbsg()', "Using existing '${getAppInfo(modePbsg)}'")
    //-> if (modePbsg.isPbsgHealthy() == false) {
    Ltrace(
      'getOrCreateModePbsg()',
      modePbsg.pbsgStateAndSettings('PEEK AT EXISTING MODE PBSG')
    )
    configPbsg(modePbsgName, modes, defaultMode, logThreshold)
    Ltrace(
      'getOrCreateModePbsg()',
      modePbsg.pbsgStateAndSettings('PEEK AFTER FRESH createModePbsg() CALL')
    )
    configPbsg(modePbsgName, modes, defaultMode, logThreshold)
  } else {
    modePbsg = app.addChildApp(
      'wesmc',      // See modePBSG.groovy 'definition.namespace'
      'modePBSG',   // See modePBSG.groovy 'definition.name'
      modePbsgName  // PBSG's label/name (id will be a generated integer)
    )
    Ldebug('getOrCreateModePbsg()', "created new '${getAppInfo(modePbsg)}'")
    configPbsg(modePbsgName, modes, defaultMode, logThreshold)
  }
  return modePbsg
}*/