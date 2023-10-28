// ---------------------------------------------------------------------------------
// P B S G   -   ( B A S E D   O N   P U S H B U T T O N   S W I T C H )
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
import com.hubitat.hub.domain.Event as Event

library (
  name: 'libPbsgPrivate',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'PBSG (headless Pushbutton Switch Group)',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

//----
//---- C O N V E N I E N C E
//----

String getPbsgName () {
  return state.pbsgName
}

String vswNameToDni (String name) {
  return "${state.vswDniPrefix}${name}"
}

String vswDnitoName (String modeVswDni) {
  modeVswDni.minus("${vswDniPrefix()}")
}

//----
//---- C O N F I G U R E
//----

void removeLegacySettingsAndState () {
  settings.remove('log')
  state.remove('activeVswDni')
  state.remove('defaultVswDni')
  state.remove('inspectScene')
  state.remove('LOG_LEVEL1_ERROR')
  state.remove('LOG_LEVEL2_WARN')
  state.remove('LOG_LEVEL3_INFO')
  state.remove('LOG_LEVEL4_DEBUG')
  state.remove('LOG_LEVEL5_TRACE')
  state.remove('previousVswDni')
  state.remove('roomScene')
  state.remove('switchDnis')
}

void populateStateVswDnis () {
  state.vswDnis = state.vswNames.collect{ name -> vswNameToDni(name) }
  Ldebug('populateStateVswDnis()', "state.vswNames: ${state.vswNames} state.vswDnis: ${state.vswDnis}")
}

void configPbsg (
    String pbsgName,
    List<String> vswNames,
    String defaultVswName,
    String logLevel
  ) {
  // Set core instance fields immediately after PBSG instantiation.
  //   - The pbsgPrefix is used when naming/labeling applications and devices.
  Ltrace(
    'configPbsg()',
    [
      "<b>pbsgName:</b> ${pbsgName}",
      "<b>vswNames:</b> ${vswNames}",
      "<b>defaultVswName:</b> ${defaultVswName}",
      "<b>logLevel:</b> ${logLevel}"
    ].join('<br/>&nbsp;&nbsp;')
  )
  state.pbsgName = pbsgName
  state.vswDniPrefix = "${state.pbsgName}_"
  state.vswNames = vswNames
  populateStateVswDnis()  //--xx-> state.vswDnis = vswNames.collect{ name -> vswNameToDni(name) }
  state.defaultVswName = defaultVswName
  state.defaultVswDni = vswNameToDni(defaultVswName)
  settings.logThreshold = logLevel
  manageChildDevices()
}

void manageChildDevices () {
  Ltrace('manageChildDevices()', 'At entry')
  // Uncomment the following to test orphan child app removal.
  //==T E S T I N G   O N L Y==> addOrphanChild()
  if (!state.vswDnis) {
    Linfo('manageChildDevices()', 'state.vswDnis is null, re-populating')
    populateStateVswDnis()
  }
  if (!state.vswDnis) {
    // It is possible that a human has manually deleted ''.
    Lerror('manageChildDevices()', 'state.vswDnis is null')
  } else {
    List<DevW> entryDnis = getAllChildDevices().collect{ device ->
      device.deviceNetworkId
    }
    List<DevW> missingDnis = (state.vswDnis)?.minus(entryDnis)
    List<DevW> orphanDnis = entryDnis.minus(state.vswDnis)
    //-> USE THE FOLLOWING FOR HEAVY DEBUGGING ONLY
    //-> Ltrace(
    //->   'manageChildDevices()',
    //->   [
    //->     '<table>',
    //->     "<tr><th>entryDnis</th><td>${entryDnis}</td></tr>",
    //->     "<tr><th>state.vswDnis</th><td>${state.vswDnis}</td></tr>",
    //->     "<tr><th>missingDnis</th><td>${missingDnis}</td></tr>",
    //->     "<tr><th>orphanDnis:</th><td>${orphanDnis}</td></tr>",
    //->     '</table>'
    //->   ].join()
    //-> )
    missingDnis.each{ dni ->
      Ldebug('manageChildDevices()', "adding '<b>${dni}'</b>")
      addChildDevice(
        'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
      )}
    orphanDnis.each{ dni ->
      Ldebug('manageChildDevices()', "deleting orphaned <b>'${dni}'</b>")
      deleteChildDevice(dni)
    }
  }
}

void adjustLogLevel (String logThreshold) {
  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  setLogLevels(logThreshold)
}

//----
//---- O P E R A T I O N S
//----

void turnOffVsw (String vswName, passedVsw = null) {
  DevW vsw = passedVsw ?: app.getChildDevice(vswNameToDni(vswName))
  if (!vsw) Lerror('turnOffVsw()', "vsw named '${vswName}' is missing")
  vsw.off()
  enforceDefaultSwitch()
}

void turnOffVswPeers (String vswName, passedVsw = null) {
  state.vswNames?.findAll{ name -> name != vswName }.each{ peerName ->
    DevW peerVsw app.getChildDevice(vswNameToDni(peerName))
    if (!peerVsw) {
      Lerror('turnOffVswPeers()', "peerVsw named '${peerName}' is missing")
    }
    peerVsw.off()
  }
}

void turnOnVswExclusively (String vswName, passedVsw = null) {
  DevW vsw = passedVsw ?: app.getChildDevice(vswNameToDni(vswName))
  if (!vsw) {
    Lerror('turnOnVswExclusively()', "vsw named '${vswName}' is missing")
  }
  // Turn off peers BEFORE turning on vswDni!
  turnOffVswPeers(vswName, vsw)
  vsw.on()
}

void toggleVsw (String vswName, passedVsw = null) {
  DevW vsw = passedVsw ?: app.getChildDevice(vswNameToDni(vswName))
  if (!vsw) Lerror('toggleVsw()', "vsw named '${vswName}' is missing")
  String switchState = getSwitchState(vsw)
  switch (switchState) {
    case 'on':
      Ldebug('toggleVsw()', "vswName: ${vswName} on() -> off()")
      vsw.off()
      break;
    case 'off':
      Ldebug('toggleVsw()', "vswDni: ${vswDni} off() -> on()")
      vsw.on()
      //-> vsw.on()
      break;
    default:
      Lerror('toggleVsw()', "unexpected switchState: ${switchState}")
  }
}

DevW getVswByName (String vswName) {
  return getVswByDni(vswNameToDni(vswName))
}

DevW getVswByDni (String vswDni) {
  return app.getChildAppByLabel(vswDni)
}

List<DevW> getVsws (String option = null) {
  Ldebug('getVsws', "<br/>${pbsgStateAndSettings('INSIDE getVsws()')}")
  if (!state.vswDnis) {
    Ldebug('getVsws()', 'Missing state.vswDnis, re-populating')
    populateStateVswDnis()
  }
  if (!state.vswDnis) {
    Lerror('getVsws()', 'Missing state.vswDnis')
  } else {
    List<DevW> vsws = []
    state.vswDnis.collect{ vswDni ->
      DevW vsw = app.getChildDevice(vswDni)
      if (option == 'onOnly' && getSwitchState(vsw) == 'on') {
        vsws += vsw
      } else {
        vsws += vsw
      }
    }
    return vsws
  }
}

void enforceMutualExclusion () {
  Ltrace('enforceMutualExclusion()', 'At entry')
  List<DevW> onVsws = getVsws('onOnly')
  while (onVsws && onVsws.size() > 1) {
    DevW device = onVsws?.first()
    if (device) {
      Ldebug(
        'enforceMutualExclusion()',
        "With <b>onVsws:</b> ${onVsws} turning off <b>${getDeviceInfo(device)}</b>."
      )
      device.off()
      onVsws = onVsws.drop(1)
    } else {
       Ltrace('enforceMutualExclusion()', 'taking no action')
    }
  }
}

void enforceDefaultSwitch () {
  List<DevW> onVsws = getVsws('onOnly')
  if (state.defaultVswDni && !onVsws) {
    Ldebug(
      'enforceDefaultSwitch()',
      "turning on <b>${state.defaultVswDni}</b>"
    )
    turnOnVsw(state.defaultVswDni)
  } else {
    Ltrace(
      'enforceDefaultSwitch()',
      "taking no action for <b>onVsws:</b> ${onVsws}<"
    )
  }
}


//----
//---- D I S P L A Y
//----

void defaultPage () {
  // Abstract
  //   - Include this page content when instantiating PBSG instances.
  //   - Then, call configPbsg(), see below, to complete device configuration.
  // Forcibly remove unused settings and state, a missing Hubitat feature.
  removeLegacySettingsAndState()
  section {
    paragraph(
      [
        heading ("${app.getLabel()} a PBSG (Pushbutton Switch Group)<br/>"),
        emphasis(red('Use the browser back button to return to the parent page.'))
      ].join()
    )
    solicitLogThreshold()                                     // Fn provided by Utils
    paragraph pbsgStateAndSettings('DEBUG')
  }
}

String pbsgStateAndSettings (String title) {
  List<String> results = []
  results += heading(title)
  results += state ? '<b>STATE</b>' : 'N O   S T A T E'
  state.sort().collect{ k, v ->
    if (k == 'vswDnis') {
      if (!v) {
        Lerror('pbsgStateAndSettings()', 'At key vswDnis, encountered null.')
      }
      v.each{ vswDni ->
        DevW vsw = app.getChildDevice(vswDni)
        String state = getSwitchState(vsw)
        state = (state == 'on') ? "<b>on</b>" : "<i>${state}</i>"
        results += "&nbsp;&nbsp;${vswDni} ${state}"
      }
    } else {
      results += bullet("<b>${k}</b> → ${v}")
    }
  }
  results += settings ? displaySettings() : 'N O   S E T T I N G S'
  return results.join('<br/>')
}

//----
//---- T E S T I N G
//----

void addOrphanChild () {
  // T E S T I N G   O N L Y
  //   - This method supports orphan removal testing. See manageChildDevices().
  addChildDevice(
    'hubitat',         // namespace
    'Virtual Switch',  // typeName
    'bogus_device',
    [isComponent: true, name: 'bogus_device']
  )
}
