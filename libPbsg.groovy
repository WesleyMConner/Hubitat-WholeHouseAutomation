// ---------------------------------------------------------------------------------
// P B S G   -   P U S H B U T T O N   S W I T C H   A P P
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

library (
  name: 'libPbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'The guts of a PBSG App instance.',
  category: 'general purpose'
)

//----
//---- CORE METHODS
//----   Methods that ARE NOT constrained to any specific execution context
//----   Button state is set by Vsw event handlers exclusively
//----

void pbsgConfigure (
    List<String> buttonNames,
    String defaultButton,
    String logThreshold,
    String turnOnButton = null
  ) {
  // This function refreshes the PBSG configuration, adjusting child devices
  // if necessary and refreshing cached data (e.g., on vs off VSWs).
  //-> PLACEHOLDER FOR REMOVING LEGACY STATE AND/OR SETTIGNS
  //--------
  Ltrace('pbsgConfigure()', 'SUSPENDING SUBSCRIPTIONS')
  _pbsgUnsubscribe()
  //--------
  Ltrace('pbsgConfigure()', 'UPDATING CORE STATE DATA')
  atomicState.vswDniPrefix = "${app.getLabel()}_"
  atomicState.buttonNames = buttonNames
  atomicState.defaultButtonName = defaultButton
  atomicState.logLevel = LogThresholdToLogLevel(logThreshold)
  if (atomicState.onButtons == null) {
    atomicState.onButtons = []
  }
  if (atomicState.offButtons == null) {
    atomicState.offButtons = buttonNames
  }
  //--------
  Ltrace('pbsgConfigure()', 'PROCESSING EXITING CHILD DEVICES')
  List<String> expectedDnis = atomicState.buttonNames
                                         .collect{ _pbsgButtonNameToDni(it) }
  List<String> actualOnButtons = []
  List<String> actualOffButtons = []
  List<DevW> foundDevices = app.getAllChildDevices()
  foundDevices.each{ d ->
    String dni = d.getDeviceNetworkId()
    if (d.hasCapability('Switch')) {
      if (expectedDnis.contains(dni)) {
        String buttonName = _pbsgDniToButtonName(dni)
        if (SwitchState(d) == 'on') {
          Ltrace(
            'pbsgConfigure()',
            "Observed on button: ${b(buttonName)}"
          )
          actualOnButtons += buttonName
        } else {
          Ltrace(
            'pbsgConfigure()',
            "Observed off button: ${b(buttonName)}"
          )
          actualOffButtons += buttonName
        }
      } else {
        Lwarn(
          'pbsgConfigure()',
          "Deleting Orphaned Switch: ${b(dni)}"
        )
        app.deleteChildDevice(dni)
      }
    } else {
      Lwarn(
        'pbsgConfigure()',
        "Deleting Non-Switch: ${b(dni)}"
      )
      app.deleteChildDevice(dni)
    }
  }
  Ltrace('pbsgConfigure', "actualOnButtons: ${actualOnButtons}")
  Ltrace('pbsgConfigure', "actualOffButtons: ${actualOffButtons}")
  //--------
  Ltrace('pbsgConfigure()', "ADDING MISSING DEVICES")
  List<String> missingDNIs = expectedDnis.collect{ it }
  missingDNIs.removeAll(foundDevices.collect{ it.getDeviceNetworkId() })
  missingDNIs.each{ dni ->
    String buttonName = _pbsgDniToButtonName(dni)
  //-> Ltrace('#107', "buttonName: ${buttonName}")
    Lwarn(
      'pbsgConfigure()',
      "Adding Device for Button: ${b(buttonName)}"
    )
    DevW vsw = addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni, name: dni]
    )
    vsw.off()
    actualOffButtons += buttonName
  //-> Ltrace('#120', "actualOffButtons: ${actualOffButtons}")
  }
  //--------
  Ltrace(
    'pbsgConfigure()',
    "RECONCILING OBSERVED BUTTON STATE TO CACHED BUTTON STATE"
  )
  //-----------------------------------------------------------------------
  // TBD
  //   - When re-populated onButton and offButton queues make no attempt
  //     to order items.
  //   - The device method 'Date getLastActivity()' could be used to
  //     order items in the future IF REQUIRED.
  //-----------------------------------------------------------------------
  List<String> onAgreement = atomicState.onButtons?.intersect(actualOnButtons)
  if ( onAgreement?.size() == atomicState.onButtons?.size()
       && onAgreement?.size() == actualOnButtons?.size() ) {
    Ltrace('pbsgConfigure()', 'Validated ')
  } else {
    Ldebug(
      'pbsgConfigure()',
      [
        '<b>Refreshing </b>',
        "Old : ${atomicState.onButtons}", //?.join(', ')
        "New : ${actualOnButtons}", //?.join(', ')
      ].join('<br/>&nbsp;&nbsp;')
    )
    atomicState.onButtons = actualOnButtons
  }
  // Step 4 - Reconcile actual off buttons to cache
  //-> Ltrace(
  //->   '#151',
  //->   [
  //->     '',
  //->     "atomicState.onButtons: ${atomicState.onButtons}",
  //->     "atomicState.offButtons: ${atomicState.offButtons}",
  //->   ].join('<br/>&nbsp;&nbsp;')
  //-> )
  List<String> offAgreement = atomicState.offButtons?.intersect(actualOffButtons)
  if ( offAgreement?.size() == atomicState.offButtons?.size()
       && offAgreement?.size() == actualOffButtons?.size() ) {
    Ltrace('pbsgConfigure()', 'Validated atomicState.offButtons')
  } else {
    Ldebug(
      'pbsgConfigure()',
      [
        '<b>Refreshing atomicState.offButtons</b>',
        "Old atomicState.offButtons: ${atomicState.offButtons}",
        "New atomicState.offButtons: ${actualOffButtons}",
      ].join('<br/>&nbsp;&nbsp;')
    )
    atomicState.offButtons = actualOffButtons
  }
  //--------
  Ltrace('pbsgConfigure()', 'RESUME SUBSCRIPTIONS')
  _pbsgSubscribe()
  pbsgTurnOnDefault()
}

//-- EXTERNAL METHODS

void pbsgAdjustLogLevel (String logThreshold) {
  atomicState.logLevel = LogThresholdToLogLevel(logThreshold)
}

void pbsgTurnOn (String buttonName) {
  DevW vsw = getChildDevice(_pbsgButtonNameToDni(buttonName))
  if (!vsw) {
    Lerror('pbsgTurnOn()', "Cannot find target button ${b(buttonName)}")
  } else {
    Linfo('pbsgTurnOn()', "Turning on ${b(buttonName)}")
    vsw.on()
  }
}

void pbsgTurnOnDefault () {
  if (atomicState.defaultButtonName) {
    pbsgTurnOn(atomicState.defaultButtonName)
  }
}

void pbsgTurnOff (String buttonName) {
  DevW vsw = getChildDevice(_pbsgButtonNameToDni(buttonName))
  if (!vsw) {
    Lerror('pbsgTurnOff()', "Cannot find target button ${b(buttonName)}")
  } else {
    Linfo('pbsgTurnOff()', "Turning off ${b(buttonName)}")
    vsw.off()
    Linfo('pbsgTurnOff()', "T B D - pbsgEnforceDefault() if no switches are on !!!")
    //_pbsgEnforceDefault()
  }
}

void pbsgToggle (String buttonName) {
  DevW vsw = getChildDevice(_pbsgButtonNameToDni(buttonName))
  if (!vsw) Lerror('pbsgToggle()', "Cannot find target button ${b(buttonName)}")
  String switchState = SwitchState(vsw)
  switch (switchState) {
    case 'on':
      Linfo('pbsgToggle()', "${b(buttonName)} on() -> off()")
      vsw.off()
      break;
    case 'off':
      Linfo('pbsgToggle()', "${b(buttonName)} off() -> on()")
      vsw.on()
      break;
    default:
      Lerror('pbsgToggle()', "${b(buttonName)} unexpected value: ${b(switchState)}")
  }
}

String pbsgGetStateBullets () {
  // Include the state of the PBSG itself AND a summary of current VSW
  // switch values.
  List<String> result = []
  atomicState.sort().each{ k, v ->
    if (k == 'vswDnis') {
      result += Bullet1("${b(k)}")
      v.each{ dni ->
        DevW vsw = getChildDevice(dni)
        String vswState = vsw ? SwitchState(vsw) : null
        String vswWithState = vsw
          ? "→ ${vswState} - ${vsw.getLabel()}"
          : "Vsw w/ DNI ${b(dni)} DOES NOT EXIST"
        result += (vswState == 'on') ? "<b>${vswWithState}</b>" : "<i>${vswWithState}</i>"
      }
    } else {
      result += Bullet1("<b>${k}</b> → ${v}")
    }
  }
  return result.join('<br/>')
}

//-- INTERNAL METHODS

String _pbsgDniToButtonName (String vswDni) {
  String result = vswDni?.minus("${atomicState.vswDniPrefix}")
  //-> Ltrace(
  //->   '_pbsgDniToButtonName()',
  //->   [
  //->     '',
  //->     "vswDni: ${vswDni}",
  //->     "result: ${result}"
  //->   ].join('<br/>&nbsp;&nbsp;')
  //-> )
  return result
}

String _pbsgButtonNameToDni (String name) {
  return "${atomicState.vswDniPrefix}${name}"
}

void _pbsgTurnOffPeers (String buttonName) {
  List<String> peerButtons = atomicState.buttonNames?.findAll{ name ->
    name != buttonName
  }
  peerButtons.each{ peerName ->
    DevW peerVsw = app.getChildDevice(_pbsgButtonNameToDni(peerName))
    if (!peerVsw) {
      Lerror('_pbsgTurnOffPeers()', "Cannot find peer button ${b(peerName)}")
    }
    peerVsw.off()
  }
}

void _pbsgEnforceDefault () {
  if (_pbsgDevicesByBucket()?.getAt('on')?.size() == 0) {
    Linfo(
      '_pbsgEnforceDefault()',
      "Turning on default button name ${b(atomicState.defaultButtonName)}"
    )
    pbsgTurnOnDefault()
  }
}

Boolean _pbsgUnsubscribe () {
  app.unsubscribe('pbsgVswEventHandler')
}

Boolean _pbsgSubscribe () {
  // Returns false if an issue arises during subscriptions
  Boolean result = true
  atomicState.buttonNames.each{ buttonName ->
    DevW vsw = getChildDevice(_pbsgButtonNameToDni(buttonName))
    if (!vsw) {
      result = false
      Lerror('_pbsgSubscribe()', "Cannot find target button ${b(buttonName)}")
    } else {
      subscribe(vsw, pbsgVswEventHandler, ['filterEvents': true])
    }
  }
  return result
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----   See downstream instances (modePbsg, roomScenePbsg)

void installed () {
  Ltrace('installed()', "No install actions for ${AppInfo(app)}")
}

void updated () {
  Ltrace(
    'updated()',
    "Called for revised ${AppInfo(app)}"
  )
}

void uninstalled () {
  getAllChildDevices().collect{ device ->
    Lwarn('uninstalled()', "Deleting ${b(device.deviceNetworkId)}")
    deleteChildDevice(device.deviceNetworkId)
  }
}

void _pbsgEnforceMutualExclusion () {
  // Turn off all but one currently-on buttons
  // Adjusting the cached button state
  while (atomicState.onButtons.size() > 1) {
    String buttonName = FifoPop(atomicState.onButtons)
    FifoPush(atomicState.offButtons, buttonName)
    pbsgTurnOff(buttonName)
  }
}

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----

List<String> pbsgCacheState () {
  return [
    '',
    "<b>atomicState.onButtons:</b> ${b(atomicState.onButtons)}",
    "<b>atomicState.offButtons:</b> ${b(atomicState.offButtons)}"
    //"<b>atomicState.defaultButtonName:</b> ${b(atomicState.defaultButtonName)}"
  ]
}

void pbsgVswEventHandler (Event e) {
  // IMPORTANT
  //   - Downstream Apps SHOULD subscribe to PBSG events.
  //   - Downstream Apps SHOULD NOT subscribe to PBSG VSW events.
  DevW d = getChildDevice(e.displayName)

  if (e.isStateChange) {
    String buttonName = _pbsgDniToButtonName(e.displayName)
    if (e.value == 'on') {
      _pbsgEnforceMutualExclusion()
      FifoPush(atomicState.onButtons, buttonName)
      // PBSG EMITS AN EVENT FOR THE NEWLY TURNED ON BUTTON
      Linfo(
        'pbsgVswEventHandler()',
        [
          "PbsgCurrentButton -> ${b(buttonName)}",
          *pbsgCacheState()
        ].join('<br/>&nbsp;&nbsp;')
      )

  Ltrace('pbsgVswEventHandler', "BEFORE<br/>${pbsgCacheState().join('<br/>')}")
  Ltrace('pbsgVswEventHandler()', "${b(buttonName)} OffButtons -> OnButtons")
      FifoRemove(atomicState.offButtons, buttonName)
      FifoPushUnique(atomicState.offButtons, buttonName)
  Ltrace('pbsgVswEventHandler', "AFTER<br/>${pbsgCacheState().join('<br/>')}")

      sendEvent([
        name: 'PbsgCurrentButton',
        value: buttonName,
        descriptionText: "${b(buttonName)} is on (exclusively)"
      ])
    } else if (e.value == 'off') {
  Ltrace('pbsgVswEventHandler', "BEFORE<br/>${pbsgCacheState().join('<br/>')}")
  Ltrace('pbsgVswEventHandler()', "${b(buttonName)} OnButtons -> OffButtons")
      FifoRemove(atomicState.onButtons, buttonName)
      FifoPushUnique(atomicState.offButtons, buttonName)
  Ltrace('pbsgVswEventHandler', "AFTER<br/>${pbsgCacheState().join('<br/>')}")
      if (atomicState.offButtons.size() == 0) {
        if (atomicState.defaultButtonName) {
          // Adjust cache
  Ltrace('pbsgVswEventHandler', "BEFORE<br/>${pbsgCacheState().join('<br/>')}")
  Ltrace('pbsgVswEventHandler()', "${b(atomicState.defaultButtonName)} OffButtons -> OnButtons")
          FifoRemove(atomicState.offButtons, atomicState.defaultButtonName)
          FifoPush(atomicState.offButtons, atomicState.defaultButtonName)
  Ltrace('pbsgVswEventHandler', "BEFORE<br/>${pbsgCacheState().join('<br/>')}")
          Linfo(
            'pbsgVswEventHandler()',
            [
              "PbsgCurrentButton -> DEFAULT (${atomicState.defaultButtonName})",
              *pbsgCacheState()
            ].join('<br/>&nbsp;&nbsp;')
          )
          // No event is published until the handler receives the update.
          pbsgTurnOnDefault()
        } else {
          // PBSG EMITS A "null" EVENT WHEN NO DEFAULT BUTTON EXISTS
          Linfo(
            'pbsgVswEventHandler()',
            [
              'PbsgCurrentButton -> null',
              *pbsgCacheState()
            ].join('<br/>&nbsp;&nbsp;')
          )
          sendEvent([
            name: 'PbsgCurrentButton',
            value: null,
            descriptionText: 'null value (no default button exists)'
          ])
        }
      }
    } else {
      Lwarn('pbsgVswEventHandler()', "Ignoring '${e.descriptionText}'")
    }
  }
}

//----
//---- SCHEDULED ROUTINES
//----   Methods specific to this execution context
//----

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

void defaultPage () {
  section {
    paragraph (
      [
        Heading1('About this page...'),
        Bullet1('The parent App configures the log level for this PBSG'),
        Bullet1('Use your browser to return to the prior-page'),
        '',
        Heading1('STATE'),
        pbsgGetStateBullets() ?: Bullet1('<i>NO DATA AVAILABLE</i>'),
      ].join('<br/>')
    )
  }
}
