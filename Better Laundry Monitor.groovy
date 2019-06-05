/**
 *  Alert on Power Consumption
 *
 *  Copyright 2015 Kevin Tierney
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.time.*

definition(
  name: "Better Laundry Monitor",
  namespace: "tierneykev",
  author: "Kevin Tierney",
  description: "Using a switch with powerMonitor capability, monitor the laundry cycle and alert when it's done.",
  category: "Green Living",
  iconUrl: "https://s3.amazonaws.com/smartthings-device-icons/Appliances/appliances8-icn.png",
  iconX2Url: "https://s3.amazonaws.com/smartthings-device-icons/Appliances/appliances8-icn@2x.png")



preferences {
  section ("When this device stops drawing power") {
    input "meter", "capability.powerMeter", multiple: false, required: true
  }
section ("Power Thresholds", hidden: true, hideable: true) {
    input "startThreshold", "decimal", title: "start cycle when power raises above (W)", description: "8", required: true
    input "endThreshold", "decimal", title: "stop cycle when power drops below (W)", description: "4", required: true
    input "delayEnd", "number", title: "stop only after the power has been below the threashold for this many reportings:", description: "2", required: false
  }

section ("Send this message") {
    input "message", "text", title: "Notification message", description: "Laudry is done!", required: true
  }

  section (title: "Notification method") {
    input "sendPushMessage", "bool", title: "Send a push notification?"
    input "speechOut", "capability.speechSynthesis", title:"Speak Via: (Speech Synthesis)",multiple: true, required: false
    input "player", "capability.musicPlayer", title:"Speak Via: (Music Player -> TTS)",multiple: true, required: false
    input "phone", "phone", title: "Send a text message to:", required: false
  }
	section (title: "Logging"){
		 input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	}
}

def installed() {
  logDebug "Installed with settings: ${settings}"

  initialize()
}

def updated() {
  logDebug "Updated with settings: ${settings}"

  unsubscribe()
  initialize()
  unschedule()
  if (debugOutput) runIn(1800,logsOff)
}

def initialize() {
  subscribe(meter, "power", handler)
  atomicState.cycleOn = false
  atomicState.powerOffDelay = 0
    if(!delayEnd) {
        delayEnd = 0;
        logDebug "Set End Delay to Zero"
    }
  logDebug delayEnd
}

def handler(evt) {
  def latestPower = meter.currentValue("power")
  logDebug "Power: ${latestPower}W"
  logDebug "State: ${atomicState.cycleOn}"

  //Added latestpower < 1000 to deal with spikes that triggered false alarms
  if (!atomicState.cycleOn && latestPower >= startThreshold && latestPower < 10000) {
    atomicState.cycleOn = true   
    log.info "Cycle started."
  }
  //first timew we are below the threashhold, hold and wait for a second.
  else if (atomicState.cycleOn && latestPower < endThreshold && atomicState.powerOffDelay < delayEnd){
  	atomicState.powerOffDelay = atomicState.powerOffDelay + 1
      logDebug "We hit delay ${atomicState.powerOffDelay} times"
  }
    //Reset Delay if it only happened once
  else if (atomicState.cycleOn && latestPower >= endThreshold && atomicState.powerOffDelay != 0) {
      logDebug "We hit the delay ${atomicState.powerOffDelay} times but cleared it"
      atomicState.powerOffDelay = 0;
      
    }
  // If the washer stops drawing power for X times in a row, the cycle is complete, send notification.
  else if (atomicState.cycleOn && latestPower < endThreshold) {
    send(message)
    if(speechOut){speakMessage(message)}
    if(player){musicPlayerTTS(message)}
    atomicState.cycleOn = false
    atomicState.cycleEnd = now()
    atomicState.powerOffDelay = 0
    
	  log.info "State: ${atomicState.cycleOn} - Cycle Finished"
  }
}

def logsOff(){
log.warn "debug logging disabled..."
device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

private logDebug(msg) {
if (settings?.debugOutput || settings?.debugOutput == null) {
log.debug "$msg"
}
}

private send(msg) {
  if (sendPushMessage) {
    sendPush(msg)
  }

  if (phone) {
    sendSms(phone, msg)
  }

  logDebug msg
}

private speakMessage(msg) {
speechOut.speak(msg)
}
private musicPlayerTTS(msg) {
	player.playText(msg)
}

private hideOptionsSection() {
    (phone || switches || hues || color || lightLevel) ? false : true
}
