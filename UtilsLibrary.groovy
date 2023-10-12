// ---------------------------------------------------------------------------------
// U T I L S   L I B R A R Y
//
//  Copyright (C) 2023-Present Wesley M. Conner
//
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"); you may not use this file except in compliance with the
// License. You may obtain a copy of the License at
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub

library (
 name: 'UtilsLibrary',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'General-Purpose Methods that are Reusable across Hubitat Projects',
 category: 'general purpose',
 documentationLink: 'https://github.com/WesleyMConner/Hubitat-UtilsLibrary/README.adoc',
 importUrl: 'https://github.com/WesleyMConner/Hubitat-UtilsLibrary.git'
)

// -------------
// G L O B A L S
// -------------
BLACK = 'rgba(0, 0, 0, 1.0)'
BLUE = 'rgba(51, 92, 255, 1.0)'
LIGHT_GREY = 'rgba(100, 100, 100, 1.0)'
RED = 'rgba(255, 0, 0, 1.0)'

// ---------------------------------------
// P A R A G R A P H   F O R M A T T I N G
// ---------------------------------------
String heading(String s) {
  HEADING_CSS = 'font-size: 2em; font-weight: bold;'
  return """<span style="${HEADING_CSS}">${s}</span>"""
}

String important(String s) {
  IMPORTANT_CSS = "font-size: 1em; color: ${RED};"
  return """<span style="${IMPORTANT_CSS}">${s}</span>"""
}

String emphasis(String s) {
  EMPHASIS_CSS = "font-size: 1.3em; color: ${BLUE}; margin-left: 0px;"
  return """<span style="${EMPHASIS_CSS}">${s}</span>"""
}

String emphasis2(String s) {
  EMPHASIS2_CSS = "font-size: 1.1em; color: ${BLUE}; margin-left: 0px;"
  return """<span style="${EMPHASIS2_CSS}">${s}</span>"""
}

String normal(String s) {
  NORMAL_CSS = 'font-size: 1.1em;'
  return """<span style="${NORMAL_CSS}">${s}</span>"""
}

String bullet(String s) {
  BULLET_CSS = 'font-size: 1.0em; margin-left: 10px;'
  return """<span style="${BULLET_CSS}">&#x2022;&nbsp;&nbsp;${s}</span>"""
}

String comment(String s) {
  COMMENT_CSS = "font-size: 0.8em; color: ${LIGHT_GREY}; font-style: italic"
  return """<span style="${COMMENT_CSS}">${s}</span>"""
}

String red(String s) {
  RED_BOLD = "color: ${RED}; font-style: bold"
  return """<span style="${RED_BOLD}">${s}</span>"""
}

// -----------------------------
// G E N E R A L   M E T H O D S
// -----------------------------

void identifyLedButtonsForListItems(
  List<String> list,
  List<DevW> ledDevices,
  String prefix
  ) {
  // Keypad LEDs are used as a proxy for Keypad buttons.
  //   - The button's displayName is meaningful to clients.
  //   - The button's deviceNetworkId is <KPAD DNI> hyphen <BUTTON #>
  list.each{ item ->
    input(
      name: "${prefix}_${item}",
      type: 'enum',
      width: 6,
      title: emphasis("Identify LEDs/Buttons for <b>${item}</b>"),
      submitOnChange: true,
      required: false,
      multiple: true,
      options: ledDevices.collect{ d ->
        "${d.getLabel()}: ${d.getDeviceNetworkId()}"
      }?.sort()
    )
  }
}

String getSwitchState (DevW d) {
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

void displayInstantiatedPbsgHref(
  String pbsgName,           // state.MODE_PBSG_APP_NAME
  String pbsgInstType,       // 'modePBSG'
  String pbsgPageName,       // 'modePbsgPage'
  ArrayList switchNames,     // state.MODE_SWITCH_NAMES
  String defaultSwitchName   // state.DEFAULT_MODE_SWITCH_NAME
  ) {
  // Design Notes
  //   - Once a PGSB instance has been created and configured, it may be
  //     necessary to reconfigure the PBSG - e.g., if the switchNames list
  //     grows or shrinks.
  //   - The PBSG-LIB configure() can function as a re-configure() as it
  //     preserves existing VSWs that need to remain and prunes VSWs that
  //     leave scope.
  if (settings.log) log.trace(
    'UTILS pbsgChildAppDrilldown() '
    + "<b>pbsgName:</b> ${pbsgName}, "
    + "<b>pbsgInstType:</b> ${pbsgInstType}, "
    + "<b>pbsgPageName:</b> ${pbsgPageName}, "
    + "<b>switchNames:</b> ${switchNames}, "
    + "<b>defaultSwitchName:</b> ${defaultSwitchName}"
  )
  paragraph heading('PBSG Inspection')
  InstAppW pbsgApp = app.getChildAppByLabel(pbsgName)
  if (!pbsgApp || pbsgApp.getAllChildDevices().size() == 0) {
    if (pbsgApp) deleteChildDevice(pbsg.getDeviceNetworkId())
    pbsgApp = addChildApp('wesmc', pbsgInstType, pbsgName)
  }
  pbsgApp.configure(switchNames, defaultSwitchName, settings.log)
  href (
    name: pbsgName,
    width: 2,
    url: "/installedapp/configure/${pbsgApp.getId()}/${pbsgPageName}",
    style: 'internal',
    title: "Edit <b>${getAppInfo(pbsgApp)}</b>",
    state: null, //'complete'
  )
}

void configureLogging () {
  Boolean currentValue = settings.log ?: true
  input (
    name: 'log',
    type: 'bool',
    title: "${currentValue ? 'Logging ENABLED' : 'Logging DISABLED'}",
    defaultValue: currentValue,
    submitOnChange: true
  )
}

String displaySettings() {
  [
    '<b>SETTINGS</b>',
    settings.sort().collect{ k, v -> bullet("<b>${k}</b> → ${v}") }.join('<br/>')
  ].join('<br/>')
}

String displayState() {
  [
    '<b>STATE</b>',
    state.sort().collect{ k, v -> bullet("<b>${k}</b> → ${v}") }.join('<br/>')
  ].join('<br/>')
}

void populateStateKpadButtons (String prefix) {
  // Design Note
  //   The Keypad LEDs collected by selectForMode() function as a proxy for
  //   Keypad button presses. Settings data includes the user-friendly
  //   LED displayName and the LED device ID, which is comprised of 'Keypad
  //   Device Id' and 'Button Number', concatenated with a hyphen. This
  //   method populates "state.[<KPAD DNI>]?.[<KPAD Button #>] = mode".
  //
  // Sample Settings Data
  //     key: LEDs_Day,
  //   value: [Central KPAD 2 - DAY: 5953-2]
  //           ^User-Friendly Name
  //                                 ^Keypad DNI
  //                                      ^Keypad Button Number
  // The 'value' is first parsed into a list with two components:
  //   - User-Friendly Name
  //   - Button DNI               [The last() item in the parsed list.]
  // The Button DNI is further parsed into a list with two components:
  //   - Keypad DNI
  //   - Keypad Button number
  String stateKey = "${prefix}Map"
  state[stateKey] = [:]
  settings.each{ key, value ->
    if (key.contains("${prefix}_")) {
      String base = key.minus("${prefix}_")
      value.each{ item ->
        List<String> kpadDniAndButtons = item?.tokenize(' ')?.last()?.tokenize('-')
        if (kpadDniAndButtons.size() == 2 && base) {
          if (state[stateKey][kpadDniAndButtons[0]] == null) {
            state[stateKey][kpadDniAndButtons[0]] = [:]
          }
          state[stateKey][kpadDniAndButtons[0]][kpadDniAndButtons[1]] = base
        }
      }
    }
  }
}

String getAppInfo (InstAppW app) {
  return "${app.getLabel()} (${app.getId()})"
}

String getDeviceInfo (def device) {
  // Design Note:
  //   - The parameter is passed as 'def' in lieu of 'DevW'.
  //   - When devices are used from a LinkedHashMap (e.g., settings, state),
  //     the original DevW type is lost - resulting in method call fail that
  //     reports a type mismatch.
  return device ? "${device.displayName} (${device.id})" : null
}

void keepOldestAppObjPerAppLabel (List<String> keepLabels, Boolean LOG = false) {
  getAllChildApps()?.groupBy{ app -> app.getLabel() }.each{ label, appObjs ->
    if (LOG) log.trace(
      "UTILS keepOldestAppObjPerAppLabel(), "
      + "<b>label:</b> >${label}<, "
      + "<b>keepLabels:</b> >${keepLabels}<, "
      + "<b>keepLabels?.findAll{ it -> it == label }:</b> ${keepLabels?.findAll{ it -> it == label }}"
    )
    // NOTE: Using 'findALl{} since contains() DID NOT work.
    if (keepLabels?.findAll{ it -> it == label }) {
      appObjs.sort{}.reverse().eachWithIndex{ appObj, index ->
        if (index == 0) {
          if (LOG) log.trace "UTILS keepOldestAppObjPerAppLabel() keeping newer '${getAppInfo(appObj)}'"
        } else {
          if (LOG) log.trace "UTILS keepOldestAppObjPerAppLabel() deleting older '${getAppInfo(appObj)}'"
          deleteChildApp(appObj.getId())
        }
      }
    } else {
      appObjs.each{ appObj ->
        if (LOG) log.trace "UTILS keepOldestAppObjPerAppLabel() deleting orphaned '${getAppInfo(appObj)}')"
        deleteChildApp(appObj.getId())
      }
    }
  }
}

String eventDetails (Event e, Boolean DEEP = false) {
  String rows = """
    <tr>
      <th align='right'>descriptionText</th>
      <td>${e.descriptionText}</td>
    </tr>
    <tr>
      <th align='right'>displayName</th>
      <td>${e.displayName}</td>
    </tr>
    <tr>
      <th align='right'>deviceId</th>
      <td>${e.deviceId}</td>
    </tr>
    <tr>
      <th align='right'>name</th>
      <td>${e.name}</td>
    </tr>
    <tr>
      <th align='right'>value</th>
      <td>${e.value}</td>
    </tr>
    <tr>
      <th align='right'>isStateChange</th>
      <td>${e.isStateChange}</td>
    </tr>
    """
  if (DEEP) rows += """
    <tr>
      <th align='right'>date</th>
      <td>${e.date}</td>
    </tr>
    <tr>
      <th align='right'>class</th>
      <td>${e.class}</td>
    </tr>
    <tr>
      <th align='right'>unixTime</th>
      <td>${e.unixTime}</td>
    </tr>"""
  return "<table>${rows}</table>"
}