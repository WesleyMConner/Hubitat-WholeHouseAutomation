/* groovylint-disable NglParseError */
// ---------------------------------------------------------------------------------
// M ( O D E )   P B S G
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

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsg

definition (
  parent: 'wesmc:WHA',
  name: 'MPbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A pbsgLibrary instance rooted in WHA instance',
  installOnOpen: false,
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: false
)

preferences {
  page(name: 'MPbsgPage')
}

void installed () {
  pbsgCoreInstalled()
}

void updated () {
  pbsgCoreUpdated()
}

void uninstalled () {
  pbsgCoreUninstalled()
}

Map MPbsgPage () {
  return dynamicPage(
    name: 'MPbsgPage',
    title: heading1(appInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph([
        heading1('Debug'),
        *appStateAsBullets(true)
      ].join('<br/>'))
    }
  }
}
