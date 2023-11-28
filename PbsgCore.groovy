// ---------------------------------------------------------------------------------
// P B S G C O R E
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
#include wesmc.libFifo
#include wesmc.libHubExt
#include wesmc.libHubUI

definition (
  name: 'PbsgCore',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Implement PBSG functions and is authoritative for pushbutton state.',
  category: '',    // Not supported as of Q3'23
  iconUrl: '',     // Not supported as of Q3'23
  iconX2Url: '',   // Not supported as of Q3'23
  iconX3Url: '',   // Not supported as of Q3'23
  singleInstance: true
)

preferences {
  page(name: 'PbsgPage')
}

//---- CORE METHODS (External)

Boolean pbsgConfigure (
    List<String> buttons,
    String defaultButton,
    String activeButton,
    String pbsgLogLevel = 'TRACE' // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  ) {
  // Returns true if configuration is accepted, false otherwise.
  Boolean retVal = true
  settings.buttons = cleanStrings(buttons)
  if (settings.buttons != buttons) {
    Linfo('pbsgConfigure()', "buttons: (${buttons}) -> (${settings.buttons})")
  }
  settings.dfltButton = defaultButton ? defaultButton : null
  if (settings.dfltButton != defaultButton) {
    Linfo('pbsgConfigure()', "defaultButton: (${defaultButton}) -> (${settings.dfltButton})")
  }
  settings.activeButton = activeButton ? activeButton : null
  if (settings.activeButton != activeButton) {
    Linfo('pbsgConfigure()', "activeButton: (${activeButton}) -> (${settings.activeButton})")
  }
  settings.logLevel = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'].contains(pbsgLogLevel)
    ? pbsgLogLevel : 'TRACE'
  if (settings.logLevel != pbsgLogLevel) {
    Linfo('pbsgConfigure()', "pbsgLogLevel: (${pbsgLogLevel}) -> (${settings.logLevel})")
  }
  Integer buttonCount = settings.buttons?.size() ?: 0
  if (buttonCount < 2) {
    retVal = false
    Lerror('pbsgConfigure()', "Button count (${buttonCount}) must be two or more")
  }
  if (settings.dfltButton && settings.buttons?.contains(settings.dfltButton) == false) {
    retVal = false
    Lerror(
      'pbsgConfigure()',
      "defaultButton ${b(settings.dfltButton)} is not found among buttons (${settings.buttons})"
    )
  }
  if (settings.activeButton && settings.buttons?.contains(settings.activeButton) == false) {
    retVal = false
    Lerror(
      'pbsgConfigure()',
      "activeDni ${b(settings.activeButton)} is not found among buttons (${settings.buttons})")
  }
  if (retVal) updated()
  return retVal
}

Boolean pbsgActivateButton (String button) {
  //-> Ltrace('pbsgActivateButton()', [
  //->   "Called for button: ${b(button)}",
  //->   *appStateAsBullets()
  //-> ])
  _pbsgActivateDni(_buttonToDni(button))
}

Boolean pbsgDeactivateButton (String button) {
  //-> Ltrace('pbsgDeactivateButton()', [
  //->   "Called for button: ${b(button)}",
  //->   *appStateAsBullets()
  //-> ])
  _pbsgDeactivateDni(_buttonToDni(button))
}

Boolean pbsgActivatePredecessor () {
  Ltrace('pbsgActivatePredecessor()', appStateAsBullets(true))
  return _pbsgActivateDni(state.inactiveDnis.first())
}

//---- CORE METHODS (Internal)

String _buttonToDni (String button) {
  return "${app.getLabel()}_${app.getId()}_${button}"
}

String _dniToButton (String dni) {
  return dni ? dni.substring("${app.getLabel()}_${app.getId()}_".length()) : null
}

void _addDni (String dni) {
  if (dni) {
    if (!state.inactiveDnis) state.inactiveDnis = []
    FifoEnqueue(state.inactiveDnis, dni)
    DevW vsw = addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni]
    )
  }
}

void _dropDni (String dni) {
  // Drop without enforcing Default DNI.
  if (state.activeDni == dni) state.activeDni = null
  else FifoRemove(state.inactiveDnis, dni)
  deleteChildDevice(dni)
}

Boolean _pbsgActivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new dni is activated.
  Ltrace('_pbsgActivateDni()', [
    "DNI: ${b(dni)}",
    *appStateAsBullets()
  ])
  Boolean isStateChanged = false
  if (state.activeDni == dni) {
    // Nothing to do, dni is already active
  } else if (dni && !_pbsgGetDnis()?.contains(dni)) {
    Lerror(
      '_pbsgActivateDni()',
      "DNI >${dni}< does not exist in >${_pbsgGetDnis()}<"
    )
  } else {
    isStateChanged = true
    _pbsgIfActiveDniPushOntoInactiveFifo()
    FifoRemove(state.inactiveDnis, dni)
    // Adjust the activeDni and Vsw together
    state.activeDni = dni
    _pbsgAdjustVswsAndSendEvent()
  }
  return isStateChanged
}

Boolean _pbsgDeactivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Ltrace('_pbsgDeactivateDni()', [
    "DNI: ${b(dni)}",
    *appStateAsBullets()
  ])
  Boolean isStateChanged = false
  Ldebug('_pbsgDeactivateDni()', [ "Received dni: ${b(dni)}", *appStateAsBullets() ])
  if (state.inactiveDnis.contains(dni)) {
    Ldebug('_pbsgDeactivateDni()', "Nothing to do for dni: ${b(dni)}")
    // Nothing to do, dni is already inactive
  } else if (state.activeDni == state.dfltDni) {
    Linfo(
      '_pbsgDeactivateDni()',
      "Ignoring attempt to deactivate the dflt dni (${state.dfltDni})"
    )
  } else {
    Ldebug(
      '_pbsgDeactivateDni()',
      "Activating default ${b(state.dfltDni)}, which deacivates dni: ${b(dni)}"
    )
    isStateChange = _pbsgActivateDni(state.dfltDni)
  }
  return isStateChange
}

String _childVswStates () {
  List<String> results = []
  app.getChildDevices().each{ d ->
    if (SwitchState(d) == 'on') {
      results += "<b>${d.getDeviceNetworkId()}: on</b>"
    } else {
      results += "<i>${d.getDeviceNetworkId()}: off</i>"
    }
  }
  return results.join(', ')
}

void _adjustVsws () {
  if (state.activeDni) {
    // Make sure the correct VSW is on
    DevW onDevice = app.getChildDevice(state.activeDni)
    if (SwitchState(onDevice) != 'on') {
      Linfo('_adjustVsws()', "Turning on VSW ${state.activeDni}")
      onDevice.on()
    }
  }
  // Make sure other VSWs are off
  state.inactiveDnis.each{ offDni ->
    DevW offDevice = app.getChildDevice(offDni)
    if (SwitchState(offDevice) != 'off') {
      Linfo('_adjustVsw()', "Turning off VSW ${offDni}")
      offDevice.off()
    }
  }
}

void _pbsgAdjustVswsAndSendEvent() {
  Map<String, String> event = [
    name: 'PbsgActiveButton',
    descriptionText: "Button ${state.activeDni} is active",
    value: [
      'active': _dniToButton(state.activeDni),
      'inactive': state.inactiveDnis.collect{ _dniToButton(it) },
      'dflt': _dniToButton(state.dfltDni)
    ]
  ]
  Linfo('_pbsgAdjustVswsAndSendEvent()', [
    '<b>EVENT MAP</b>',
    Bullet2("<b>name:</b> ${event.name}"),
    Bullet2("<b>descriptionText:</b> ${event.descriptionText}"),
    Bullet2("<b>value.active:</b> ${event.value['active']}"),
    Bullet2("<b>value.inactive:</b> ${event.value['inactive']}"),
    Bullet2("<b>value.dflt:</b> ${event.value['dflt']}")
  ])
  // Update the state of child devices
  _adjustVsws()
  // Broadcast the state change to subscribers
  sendEvent(event)
}

List<String> _pbsgGetDnis () {
  return cleanStrings([ state.activeDni, *state.inactiveDnis ])
}

Boolean _pbsgIfActiveDniPushOntoInactiveFifo () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT (1) activate a dfltDni OR (2) publish an event change
  Boolean isStateChanged = false
  String dni = state.activeDni
  if (dni) {
    isStateChanged = true
    // Adjust inactiveDnis, activeDni and Vsw together
    state.inactiveDnis = [dni, *state.inactiveDnis]
    state.activeDni = null
    Ltrace(
      '_pbsgIfActiveDniPushOntoInactiveFifo()',
      "DNI ${b(dni)} pushed onto inactiveDnis ${state.inactiveDnis}"
    )
  }
  return isStateChanged
}

List<String> _pbsgListVswDevices () {
  List<String> outputText = [ Heading2('DEVICES') ]
  List<InstAppW> devices = app.getChildDevices()
  devices.each{ d -> outputText += Bullet2(d.getDeviceNetworkId()) }
  return outputText
}

//---- SYSTEM CALLBACKS

void installed () {
  // Called on instance creation - i.e., before configuration, etc.
  state.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  state.activeDni = null                         // String
  state.inactiveDnis = []                        // List<String>
  state.dfltDni = null                           // String
  Linfo('installed()', appStateAsBullets(true))
  Ltrace('installed()', 'Calling TEST_pbsgCoreFunctionality()')
  TEST_pbsgCoreFunctionality()
}

void updated () {
  // Values are provided via these settings:
  //   - settings.buttons
  //   - settings.dfltButton
  //   - settings.activeButton
  //   - settings.logLevel
  // PROCESS SETTINGS (BUTTONS) INTO TARGET VSW DNIS
  List<String> prevDnis = _pbsgGetDnis() ?: []
  updatedDnis = settings.buttons.collect{ _buttonToDni(it) }
  updatedDfltDni = settings.dfltButton ? _buttonToDni(settings.dfltButton) : null
  updatedActiveDni = settings.activeButton ? _buttonToDni(settings.activeButton) : null
  //-> Ltrace('updated()', [
  //->   'Configuration Adjustments',
  //->   "Dnis: ${prevDnis} -> ${updatedDnis}",
  //->   "DfltDni: ${state.dfltDni} -> ${updatedDfltDni}",
  //->   "ActiveDni: ${state.activeDni} -> ${updatedActiveDni}"
  //-> ])
  // DETERMINE REQUIRED ADJUSTMENTS BY TYPE
  state.logLevel = LogThresholdToLogLevel(settings.logLevel)
  Map<String, List<String>> actions = CompareLists(prevDnis, updatedDnis)
  List<String> retainDnis = actions.retained // Used for accounting only
  List<String> dropDnis = actions.dropped
  List<String> addDnis = actions.added
  String requested = [
    "<b>dnis:</b> ${updatedDnis}",
    "<b>dfltDni:</b> ${updatedDfltDni}",
    "<b>activeDni:</b> ${updatedActiveDni}"
  ].join('<br/>')
  String analysis = [
    "<b>prevDnis:</b> ${prevDnis}",
    "<b>retainDnis:</b> ${retainDnis}",
    "<b>dropDnis:</b> ${dropDnis}",
    "<b>addDnis:</b> ${addDnis}"
  ].join('<br/>')
  Linfo('updated()', [
    [
      '<table style="border-spacing: 0px;" rules="all"><tr>',
      '<th>STATE</th><th style="width:3%"/>',
      '<th>Input Parameters</th><th style="width:3%"/>',
      '<th>Action Summary</th>',
      '</tr><tr>'
    ].join(),
    "<td>${appStateAsBullets(true).join('<br/>')}</td><td/>",
    "<td>${requested}</td><td/>",
    "<td>${analysis}</td></tr></table>"
  ])
  // Suspend ALL events, irrespective of type
  unsubscribe()
  state.dfltDni = updatedDfltDni
  dropDnis.each{ dni -> _dropDni(dni) }
  addDnis.each{ dni -> _addDni(dni) }
  // Leverage activation/deactivation methods for initial dni activation.
  if (updatedActiveDni) {
    Ltrace('updated()', "activating activeDni ${updatedActiveDni}")
    _pbsgActivateDni(updatedActiveDni)
  } else if (state.activeDni == null && state.dfltDni) {
    Ltrace('updated()', "activating dfltDni ${state.dfltDni}")
    _pbsgActivateDni(state.dfltDni)
  }
  Ltrace('updated()', _pbsgListVswDevices())
  List<DevW> childDevices = app.getChildDevices()
  // Avoid the List version of app.subscribe. It seems flaky.
  //-> app.subscribe(childDevices, VswEventHandler, ['filterEvents': true])
  childDevices.each{ d ->
    app.subscribe(d, VswEventHandler, ['filterEvents': true])
  }
  // Reconcile the PBSG / Child VSW state AND publish a first event.
  _pbsgAdjustVswsAndSendEvent()
}

void uninstalled () {
  Ldebug('uninstalled()', 'No action')
}

//---- RENDERING AND DISPLAY

Map PbsgPage () {
  return dynamicPage(
    name: 'PbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph "YOU ARE HERE"
    }
  }
}

void VswEventHandler (Event e) {
  // Design Notes
  //   - Events can arise from:
  //       1. Methods in this App that change state
  //       2. Manual manipulation of VSWs (via dashboards or directly)
  //       3. Remote manipulation of VSWs (via Amazon Alexa)
  //   - Let downstream functions discard redundant state information
  // ==================================================================
  // == PREFIX APP METHODS WITH 'app.'                               ==
  // ==                                                              ==
  // == This IS NOT an instance method; so, there is no implied app. ==
  // == This IS a standalone method !!!                              ==
  // ==================================================================
  Linfo('VswEventHandler()', [
    e.descriptionText,
    app.appStateAsBullets().join('<br/>'),
    app._childVswStates().join(', ')
  ])
  if (e.isStateChange) {
    String dni = e.displayName
    if (e.value == 'on') {
      app._pbsgActivateDni(dni)
    } else if (e.value == 'off') {
      app._pbsgDeactivateDni(dni)
    } else {
      Ldebug(
        'VswEventHandler()',
        "Unexpected value (${e.value}) for DNI (${dni}")
    }
  } else {
    Ldebug('VswEventHandler()', "Unexpected event: ${EventDetails(e)}")
  }
}

//---- TEST SUPPORT

void TEST_pbsgConfigure (
    Integer n,
    List<String> list,
    String dflt,
    String on,
    String forcedError = null
  ) {
  // Logs display newest to oldest; so, write logs backwards
  List<String> logMsg = []
  if (forcedError) logMsg += forcedError
  logMsg += "dnis=${b(list)}, dfltButton=${b(dflt)}, activeDni=${b(on)}"
  logMsg += (forcedError ? REDBAR() : GREENBAR())
  Linfo("TEST ${n} CONFIG", logMsg)

  // Simulate a Page update (GUI settings) via the System updated() callback.
  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE' .. 'TRACE' for HEAVY DEBUG
  pbsgConfigure(list, dflt, on, 'TRACE')
}

void TEST_PbsgActivation (
    Integer n,
    String description,
    String forcedError = null
  ){
  // Logs display newest to oldest; so, write logs backwards
  List<String> logMsg = []
  logMsg += description
  if (forcedError) { logMsg += forcedError }
  logMsg += (forcedError ? REDBAR() : GREENBAR())
  Linfo("TEST ${n} ACTION", logMsg)
}

String TEST_pbsgHasExpectedState (
    String activeButton,
    List<String> inactiveButtons,
    String dfltButton
  ) {
  String activeDni = activeButton ? _buttonToDni(activeButton) : null
  List<String> inactiveDnis = inactiveButtons ? inactiveButtons.collect{ _buttonToDni(it) } : null
  String dfltDni = dfltButton ? _buttonToDni(dfltButton) : null
  Boolean result = true
  Integer actualInactiveDnisSize = state.inactiveDnis?.size() ?: 0
  Integer expectedInactiveDnisSize = inactiveDnis?.size() ?: 0
  if (state.dfltDni != dfltDni) {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      "dfltDni ${state.dfltDni} != ${dfltDni}"
    )
  } else if (state.activeDni != activeDni) {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      "activeDni ${state.activeDni} != ${activeDni}"
    )
  } else if (actualInactiveDnisSize != expectedInactiveDnisSize) {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      [
        "inActiveDnis size ${actualInactiveDnisSize} != ${expectedInactiveDnisSize}",
        "expected: ${inactiveDnis }} got: ${state.inactiveDnis}"
      ]
    )
  } else {
    state.inactiveDnis.eachWithIndex{ dni, index ->
      String expectedDni = inactiveDnis[index]
      if (dni != expectedDni) {
        result = false
        Linfo(
          'TEST_pbsgHasExpectedState()',
          "At ${index}: inactiveDni ${dni} != ${expectedDni}"
        )
      }
    }
  }
  // Check VSW state
  if (activeButton && SwitchState(getChildDevice(_buttonToDni(activeButton))) != 'on') {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      "Device for activeButton ${activeButton} IS NOT 'on'"
    )
  }
  inactiveBUttons.each{ offButton ->
    if (SwitchState(getChildDevice(_buttonToDni(offButton))) != 'off') {
      result = false
      Linfo(
        'TEST_pbsgHasExpectedState()',
        "Device for offButton ${offButton} IS NOT 'off'"
      )
    }
  }
  List<String> results = [result ? 'true' : '<b>FALSE</b>']
  if (state.activeDni == activeDni) {
    results += "<i>activeDni: ${state.activeDni}</i>"
  } else {
    results += "<i>activeDni: ${state.activeDni}</i> => <b>expected: ${activeDni}</b>"
  }
  if ((state.inactiveDnis == inactiveDnis) || (!state.inactiveDnis && !inactiveDnis)) {
    results += "<i>inactiveDnis: ${state.inactiveDnis}</i>"
  } else {
    results += "<i>inactiveDnis:</b> ${state.inactiveDnis}</i> => <b>expected: ${inactiveDnis}</b>"
  }
  if(state.dfltDni == dfltDni) {
    results += "<i>dfltDni: ${state.dfltDni}</i>"
  } else {
    results += "<i>dfltDni: ${state.dfltDni}</i> => <b>expected: ${dfltDni}</b>"
  }
  return results.join('<br/>')
}

void TEST_pbsgCoreFunctionality () {
  //-> FifoTest()
  //----
  TEST_pbsgConfigure(1, [], 'A', 'B', '<b>Forced Error:</b> "Inadequate parameters"')
  Linfo('TEST1', TEST_pbsgHasExpectedState(null, [], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(2, ['A', 'B', 'C', 'D', 'E'], '', null)
  Linfo('TEST2', TEST_pbsgHasExpectedState(null, ['A', 'B', 'C', 'D', 'E'], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(3, ['A', 'B', 'C', 'D', 'E'], 'B', null)
  Linfo('TEST3', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in DNIs"')
  // TEST4 state is unchanged from TEST3 state
  Linfo('TEST4', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  Linfo('TEST5', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in DNIs"')
  Linfo('TEST6', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  Linfo('TEST7', TEST_pbsgHasExpectedState('G', ['B', 'F', 'I'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  TEST_PbsgActivation(8, "With 'G', ['*B', 'F', 'I'], Activate F")
  pbsgActivateButton('F')
  Linfo('TEST8', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(9, "With 'F', ['G', '*B', 'I'], Activate Q", '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateButton('Q')
  Linfo('TEST9', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(10, "With 'F', ['G', '*B', 'I'], Deactivate F")
  pbsgDeactivateButton('F')
  Linfo('TEST10', TEST_pbsgHasExpectedState('B', ['F', 'G', 'I'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(11, "With '*B', ['F', 'G', 'I'], Activate I")
  pbsgActivateButton('I')
  Linfo('TEST11', TEST_pbsgHasExpectedState('I', ['B', 'F', 'G'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(12, "With 'I', ['*B', 'F', 'G'], Activate Predecessor")
  pbsgActivatePredecessor()
  Linfo('TEST12', TEST_pbsgHasExpectedState('B', ['I', 'F', 'G'], 'B'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(13, ['B', 'X', 'C', 'E', 'Z'], '', 'C')
  Linfo('TEST13', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(14, "With 'C', ['B', 'X', 'E', 'Z'], Deactivate C")
  pbsgDeactivateButton('C')
  Linfo('TEST14', TEST_pbsgHasExpectedState(null, ['C', 'B', 'X', 'E', 'Z'], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(15, "With null, ['C', 'B', 'X', 'E', 'Z'], Activate Predecessor")
  pbsgActivatePredecessor()
  Linfo('TEST15', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(16, ['B', '', null, 'A', 'G', 'X', null, 'A'], 'X', '')
  Linfo('TEST16', TEST_pbsgHasExpectedState('X', ['B', 'A', 'G'], 'X'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(17, ['B', 'A', 'G', 'X'], 'X', 'G')
  Linfo('TEST17', TEST_pbsgHasExpectedState('G', ['X', 'B', 'A'], 'X'))
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
}
