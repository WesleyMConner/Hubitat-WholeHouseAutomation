// ---------------------------------------------------------------------------------
// R O O M   S C E N E   P B S G
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
#include wesmc.libFifo
#include wesmc.libHubExt
#include wesmc.libHubUI
#include wesmc.libPbsgCore

definition (
  parent: 'wesmc:RoomScenes',
  name: 'RoomScenesPbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A libPbsgCore instance rooted in a (WHA) Room Scenes instance',
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
  page(name: 'RoomScenesPbsgPage')
}

////-----------------------------------------------------------------------
//// EXTERNAL-FACING METHODS
////
////   Boolean pbsgConfigure (
////     List<String> buttons,
////     String defaultButton,
////     String activeButton,
////     String pbsgLogLevel = 'TRACE'
////   )
////
////   Boolean pbsgActivateButton (String button)
////
////   Boolean pbsgDeactivateButton (String button)
////
////   Boolean pbsgActivatePredecessor ()
////
////
//// PUBLISHED EVENT
////
////   Map event = [
////     name: 'PbsgActiveButton',                                   String
////     descriptionText: "Button <activeButton> is active",         String
////     value: [
////         'active': activeButton,                                 String
////       'inactive': inactiveButtonFifo,                     List<String>
////           'dflt': defaultButton                                 String
////-----------------------------------------------------------------------

//---- SYSTEM CALLBACKS

void installed () {
  pbsgCoreInstalled(app)
}

void updated () {
  pbsgCoreUpdated(app)
}

void uninstalled () {
  pbsgCoreUninstalled(app)
}

//---- RENDERING AND DISPLAY

Map RoomScenesPbsgPage () {
  return dynamicPage(
    name: 'RoomScenesPbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph "YOU ARE HERE"
    }
  }
}
