/**
 *  Alert on PowerSource Change
 *
 *  Copyright 2019 Chris Uthe
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


definition(
  name: "Alert on PowerSource Change",
  namespace: "cuthe",
  author: "Christopher Uthe",
  description: "Using the POWERSOURCE property of Devices to alert when the status changes.",
  iconUrl: "",
  iconX2Url: ""
)

preferences {
  section ("When these device(s) PowerSource Changes:") {
	input "ups", "capability.powerSource", title: "Device:", multiple: false, required: true
	}

section ("Send this message") {
    input "message", "text", title: "Notification message", description: "UPS now on Battery!", required: true
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
	subscribe(ups, "powerSource", handler)
  	subscribe(ups, "battery", batteryHandler)
  	atomicState.PowerSource = ups.currentValue("powerSource")
	logDebug "Initialized! ${atomicState.PowerSource}"
}

//This handles the event that pops when the battery% Changes
def batteryHandler(evt)
{
	def batteryPercent = ups.currentValue("battery")
	logDebug "Battery: ${batteryPercent}%"
}

//This Handles the Event that pops when the powerSource changes
def handler(evt) {
	def currentSource = ups.currentValue("powerSource")
	logDebug "PowerSource: ${currentSource}"
	if(atomicState.PowerSource != currentSource)
	{
		atomicState.PowerSource = currentSource
		send("${message} - ${currentSource}")
    	if(speechOut){speakMessage("${message} - ${currentSource}")}
    	if(player){musicPlayerTTS("${message} - ${currentSource}")}
	}
}

def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) 
	{
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