/**
 *  Track Power On Time
 *
 *  Copyright 2021 Chris Uthe
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
definition(
  name: "Track Power On Time",
  namespace: "cuthe",
  author: "Christopher Uthe",
  description: "Using PowerMeter.power to track device on time.",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: ""
)
def mainPage() {
	dynamicPage(name: "mainPage", title: "Monitor Device On Time", install: true, uninstall: true) {
		//clever way to set the App name, credit to Hubitat example apps
        section (""){
            input "thisName", "text", title: "Name this Device Monitor", description: "", required: true, submitOnChange: true
            if(thisName) app.updateLabel("$thisName")
        }
        section ("Choose the Power Meter:") { 
	        input "monitor", "capability.powerMeter", title: "Device:", multiple: false, required: true
        	}
         section ("Set On Threshold:") {
	        input "startThreshold", "decimal", title: "Start a cycle timer when power goes above (W)", description: "", required: true
        	}
        section (title: "Logging"){
            input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
            input name: "createEvents", type: "bool", title: "Create Device on/off events", defaultValue: true
	        }
        //may want to split this out into it's own page and have a button to take you there once I get 24/36/48/7 day breakdowns going.
        section (title: "<b>Status:</b>"){
            if(state.on==true){
                paragraph "<b>Currently Running</b>"
            }
            paragraph "Number of cycles: ${state.cycleCount}"
            /*if the state.onTime is < 750 it's going to round down to zero with the indiv and toss a divide by zero error. since it's
            in milleseconds this will not be true frequently past the first run but checking cycleCount would mean we couldn't garuntee a lack of error.*/
            if(state.onTime > 750 && state.cycleCount > 0){
                paragraph "Number of minutes on: ${(state.onTime.intdiv(1000)).intdiv(60)}"
                paragraph "Average minutes per cycle: ${((state.onTime.intdiv(1000)).intdiv(60)) / (state.cycleCount)}"
            }
            if(state.offTime > 750 && state.cycleCount > 0){
                paragraph "Number of minutes off: ${(state.offTime.intdiv(1000)).intdiv(60)}"
                paragraph "Average minutes between cycle: ${((state.offTime.intdiv(1000)).intdiv(60)) / (state.cycleCount)}"
            }
            //The Button Handler below picks up every push of this button without any additional wire up.
            input "resetStats", "button", title: "Reset Stats"
            
        }
	}
}
preferences {
	page(name: "mainPage")
}

def installed() {
	logDebug "Installed with settings: ${settings}"
    state.on=false
    state.lastOff=now()
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
	subscribe(monitor, "power", powerChange)
    
  	//since this gets run every time we update the app (AKA hit "Done") we only want to reset these values when we manually push reset, we do want to make sure the exist the first time though.
    if(!state.onTime)
        state.onTime=0
    if(!state.cycleCount)
        state.cycleCount=0
    if(!state.timer)
        state.timer=0
    if(!state.on)
        state.on= false
    if(!state.lastOff)
        state.lastOff=now()
    if(!state.offTime)
        state.offTime=0
	logDebug "Initialized! ${state.onTime} minutes of on time"
    logDebug "${state.cycleCount} on/off cycles"
}

//This handles the event that pops when the power variable changes
def powerChange(evt)
{
	logDebug "Event"
    def powerNumber = monitor.currentValue("power")
    logDebug powerNumber
    def now = now()
    //This is where we start a new on/off cycle if the if checks true
    if(state.on==true && powerNumber > startThreshold){
        logDebug "Still On"
        return
    }
    else if(powerNumber > startThreshold && state.on==false)
    {
        logDebug "Power Above zero start timer"
        state.timer=now
        state.on=true  
        if(createEvents==true){
            sendEvent(name:thisName, value: "Power On", descriptionText:"Device State On")
        }
        logDebug"Seconds since last power on: " + ((now-state.lastOff)/1000)
        state.offTime=state.offTime+(now-state.lastOff)
    }
    else
    {
        //This stops a cycle if the if checks false, if we landed in the else but this if is false, the power is simply fluctuating during an on/off cycle
        if(state.on==true && powerNumber < startThreshold){
        logDebug "Power below threshold Stop timer!"
        logDebug "Now: ${now}"
        logDebug "Timer: ${state.timer}"
        state.cycleCount = state.cycleCount+1
        state.onTime= state.onTime + (now - state.timer)
        state.timer=0
        state.on=false
        state.lastOff=now
        if(createEvents==true){
            sendEvent(name:thisName, value: "Power Off", descriptionText:"Device State Off")
        }
        }
        logDebug "End of already powered IF"
    
    }
}
//Handle all Button Pushes
def appButtonHandler(btn) {
    //switch based on button name  
    switch(btn) {
          case "resetStats":
              state.onTime=0
              state.cycleCount=0    
              state.timer=0
              state.on=False
              state.lastOff=now()
              state.offTime=0
          break
      }
}

def logsOff(){
	log.warn "debug logging disabled..."
	app.updateSetting("debugOutput",[value:"false",type:"bool"])
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) 
	{
		log.debug "$msg"
	}
}
