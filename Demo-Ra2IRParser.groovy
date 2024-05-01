// ---------------------------------------------------------------------------------
// ( L U T R O N )   R A 2
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

import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lRa2IRParser

definition (
  name: 'Demo-Ra2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview lRa2 functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'Ra2Page')
}

void solicitRa2IntegrationReport() {
  input(
    name: 'ra2IntegReport',
    title: 'Paste in the Lutron RA2 Integration Report',
    type: 'textarea',
    rows: 5,
    submitOnChange: true,
    required: true,
    multiple: false
  )
}

Map Ra2Page() {
  return dynamicPage(
    name: 'Ra2Page',
    title: [
      heading1("Ra2 Integration Report Demo Page - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> atomicState.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('Demo-Ra2IRParser')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      atomicState.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      solicitRa2IntegrationReport()
    }
  }
}

void installed() {
  unsubscribe()
  initialize()
}

void uninstalled() {
}

void updated() {
  unsubscribe()
  initialize()
}

void initialize() {
  logInfo('initialize', 'Calling parseRa2IntegRpt')
  //Map irMap = ra2IR_init(settings.ra2IntegReport)
  Map results = parseRa2IntegRpt(settings.ra2IntegReport)
  //logInfo('initialize', 'Parse complete')
  //logMapUI(results, 0)
  logInfo('initialize', "results.ra2Devices: ${results.ra2Devices}")
  logInfo('initialize', "results.kpads: ${results.kpads}")
  logInfo('initialize', "results.ra2Rooms: ${results.ra2Rooms}")
  logInfo('initialize', "results.circuits: ${results.circuits}")
  logInfo('initialize', "results.timeclock: ${results.timeclock}")
  logInfo('initialize', "results.green: ${results.green}")
}
