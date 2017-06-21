/**
 * SmartThings Device Type for Aeon Home Energy Monitor v1 (HEM v1)
 * (Goal of project is to) Displays individual values for each clamp (L1, L2) for granular monitoring
 * Example: Individual circuits (breakers) of high-load devices, such as HVAC or clothes dryer
 *  
 * Original Author: Copyright 2014 Barry A. Burke
 *
 * Modified HEM DH to include a virtual on/off switch to display dryer state.  However, on/off is controlled by webCore, not natively in the DH
 **/

metadata {
	// Automatically generated. Make future change here.
	definition (
		name: 		"Dryer Power Monitor", 
		namespace: 	"Jarretts Apps",
		category: 	"Convenience",
		author: 	"Jarrett Campbell"
	) 
	{
    	capability "Energy Meter"
	capability "Power Meter"
	capability "Configuration"
	capability "Sensor"
        capability "Refresh"
        capability "Polling"
        capability "Switch"
	capability "Actuator"

        
        attribute "energy", "string"
        attribute "power", "string"
        attribute "volts", "string"
        attribute "voltage", "string"		// We'll deliver both, since the correct one is not defined anywhere
        
        attribute "energyDisp", "string"
        attribute "energyOne", "string"
        attribute "energyTwo", "string"
        
        attribute "powerDisp", "string"
        attribute "powerOne", "string"
        attribute "powerTwo", "string"

        command "reset"
        command "configure"
        command "refresh"
        command "poll"
        
		fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"

//		fingerprint deviceId: "0x3101", inClusters: "0x70,0x32,0x60,0x85,0x56,0x72,0x86"
	}

	// simulator metadata
	simulator {
		for (int i = 0; i <= 10000; i += 1000) {
			status "power  ${i} W-ZZ": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 33, scale: 2, size: 4).incomingMessage()
		}
		for (int i = 0; i <= 100; i += 10) {
			status "energy  ${i} kWh-ZZ": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 33, scale: 0, size: 4).incomingMessage()
		}
        // TODO: Add data feeds for Volts and Amps
	}

	// tile definitions
	tiles {

	standardTile("button", "device.switch", width: 3, height: 3, canChangeIcon: true) {
		state "off", label: 'Idle', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#FFFFFF", nextState: "on"
		state "on", label: 'Running', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState: "off"
		}
    
    // Watts row
		valueTile("powerDisp", "device.power") {
			state (
				"default", 
				label:'${currentValue}', 
            	foregroundColors:[
            		[value: 1, color: "#000000"],
            		[value: 10000, color: "#ffffff"]
            	], 
            	foregroundColor: "#000000"
			)
		}
        valueTile("powerOne", "device.powerOne") {
        	state(
        		"default", 
        		label:'${currentValue}', 
            	foregroundColors:[
            		[value: 1, color: "#000000"],
            		[value: 10000, color: "#ffffff"]
            	], 
            	foregroundColor: "#000000"
			)
        }
        valueTile("powerTwo", "device.powerTwo") {
        	state(
        		"default", 
        		label:'${currentValue}', 
            	foregroundColors:[
            		[value: 1, color: "#000000"],
            		[value: 10000, color: "#ffffff"]
            	], 
            	foregroundColor: "#000000"
			)
        }

	

// Controls row
		standardTile("reset", "command.reset", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset', action:"reset", icon: "st.Health & Wellness.health7"
		}
		standardTile("refresh", "command.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh-icon"
		}
		standardTile("configure", "command.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action: "configure", icon:"st.secondary.configure"
		}


		main (["button"])
		details([
			"powerOne","powerTwo","powerDisp",
			"reset","refresh","configure"		
		])
	}
    preferences {
    	input "voltageValue", "number", title: "Voltage being monitored", /* description: "120", */ defaultValue: 120
    	input "c1Name", "string", title: "Clamp 1 Name", description: "Name of appliance Clamp 1 is monitoring", defaultValue: "Clamp 1" as String
    	input "c2Name", "string", title: "Clamp 2 Name", description: "Name of appliance Clamp 2 is monitoring", defaultValue: "Clamp 2" as String
    	input "kWhCost", "string", title: "\$/kWh (0.16)", description: "0.16", defaultValue: "0.16" as String
    	input "kwhDelay", "number", title: "Energy report seconds (60)", /* description: "120", */ defaultValue: 120
    	input "wDelay", "number", title: "Power report seconds (30)", /* description: "30", */ defaultValue: 30
    }
}

def installed() {
	reset()						// The order here is important
	configure()					// Since reports can start coming in even before we finish configure()
	refresh()
}

def updated() {
	configure()
	resetDisplay()
	refresh()
}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	log.debug "Parse returned ${result?.descriptionText}"
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
    def dispValue
    def newValue
    def formattedValue
    
	//def timeString = new Date().format("h:mm a", location.timeZone)
    
    if (cmd.meterType == 33) {
		if (cmd.scale == 0) {
        	newValue = Math.round(cmd.scaledMeterValue * 100) / 100
        	if (newValue != state.energyValue) {
        		formattedValue = String.format("%5.2f", newValue)
    			dispValue = "Total\n${formattedValue}\nkWh"		// total kWh label
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "", descriptionText: "Display Energy: ${newValue} kWh", displayed: false)
                state.energyValue = newValue
                [name: "energy", value: newValue, unit: "kWh", descriptionText: "Total Energy: ${formattedValue} kWh"]

            }
		} 
		else if (cmd.scale==2) {
        	log.trace "Calculating Watts based on ${cmd.scaledMeterValue}"
            newValue = Math.round(cmd.scaledMeterValue*10)/10
            log.trace "newValue is ${newValue}"
            formattedValue = String.format("%5.1f", newValue)
            log.trace "formattedValue is ${formattedValue}"
        	//newValue = Math.round(cmd.scaledMeterValue)		// really not worth the hassle to show decimals for Watts
        	if (newValue != state.powerValue) {

    			dispValue = "Total\n"+newValue+"\nWatts"	// Total watts label
                sendEvent(name: "powerDisp", value: dispValue as String, unit: "", descriptionText: "Display Power: ${newValue} Watts", displayed: false)
                state.powerValue = newValue
                [name: "power", value: newValue, unit: "W", descriptionText: "Total Power: ${formattedValue} Watts"]

           }
		}
 	}     
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def dispValue
	def newValue
	def formattedValue

   	if (cmd.commandClass == 50) {    
   		def encapsulatedCommand = cmd.encapsulatedCommand([0x30: 1, 0x31: 1]) // can specify command class versions here like in zwave.parse
		if (encapsulatedCommand) {
			if (cmd.sourceEndPoint == 1) {
				if (encapsulatedCommand.scale == 2 ) {
                    newValue = Math.round(encapsulatedCommand.scaledMeterValue*10)/10
                    formattedValue = String.format("%5.1f", newValue)
					dispValue = "${c1Name}\n${formattedValue}\nWatts"	// L1 Watts Label
					if (newValue != state.powerL1) {
						state.powerL1 = newValue
						[name: "powerOne", value: dispValue, unit: "", descriptionText: "L1 Power: ${formattedValue} Watts"]
					}	
				} 
				else if (encapsulatedCommand.scale == 0 ){
					newValue = Math.round(encapsulatedCommand.scaledMeterValue * 100) / 100
					formattedValue = String.format("%5.2f", newValue)
					dispValue = "${c1Name}\n${formattedValue}\nkWh"		// L1 kWh label
					if (newValue != state.energyL1) {
						state.energyL1 = newValue
						[name: "energyOne", value: dispValue, unit: "", descriptionText: "L1 Energy: ${formattedValue} kWh"]
					}
				}
			} 
			else if (cmd.sourceEndPoint == 2) {
				if (encapsulatedCommand.scale == 2 ){
					newValue = Math.round(encapsulatedCommand.scaledMeterValue*10)/10
                    formattedValue = String.format("%5.1f", newValue)
                    dispValue = "${c2Name}\n${formattedValue}\nWatts"	// L2 Watts Label
					if (newValue != state.powerL1) {
						state.powerL2 = newValue
						[name: "powerTwo", value: dispValue, unit: "", descriptionText: "L2 Power: ${formattedValue} Wa1tts"]
                        state.powerValue = newValue
                        [name: "power", value: newValue, unit: "W", descriptionText: "Total Power: ${formattedValue} Watts"]
					}	
				} 
				else if (encapsulatedCommand.scale == 0 ){
					newValue = Math.round(encapsulatedCommand.scaledMeterValue * 100) / 100
					formattedValue = String.format("%5.2f", newValue)
					dispValue = "${c2Name}\n${formattedValue}\nkWh"		// L2 kWh label
					if (newValue != state.energyL2) {
						state.energyL2 = newValue
						[name: "energyTwo", value: dispValue, unit: "", descriptionText: "L2 Energy: ${formattedValue} kWh"]
					}
				} 
			}
		}
	}
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.unit = "%"
   
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} battery is low"
		map.isStateChange = true
	} 
	else {
		map.value = cmd.batteryLevel
	}
	log.debug map
	return map
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
    log.debug "Unhandled event ${cmd}"
	[:]
}

def refresh() {			// Request HEMv2 to send us the latest values for the 4 we are tracking
	log.debug "refresh()"
    
	delayBetween([
		zwave.meterV2.meterGet(scale: 0).format(),		// Change 0 to 1 if international version
		zwave.meterV2.meterGet(scale: 2).format(),
	])
    resetDisplay()
}

def poll() {
	log.debug "poll()"
	refresh()
}

def resetDisplay() {
	log.debug "resetDisplay()"
	
    sendEvent(name: "powerDisp", value: "Total\n" + state.powerValue + "\nWatts", unit: "W")	
    sendEvent(name: "energyDisp", value: "Total\n" + state.energyValue + "\nkWh", unit: "kWh")
	sendEvent(name: "powerOne", value: c1Name + "\n" + state.powerL1 + "\nWatts", unit: "W")    
    sendEvent(name: "energyOne", value: c1Name + "\n" + state.energyL1 + "\nkWh", unit: "kWh")	
    sendEvent(name: "powerTwo", value: c2Name + "\n" + state.powerL2 + "\nWatts", unit: "W")
    sendEvent(name: "energyTwo", value: c2Name + "\n" + state.energyL2 + "\nkWh", unit: "kWh")
}

def reset() {
	log.debug "reset()"
	
    state.energyValue = ""
	state.powerValue = ""
    state.energyL1 = ""
    state.energyL2 = ""
    state.powerL1 = ""
    state.powerL2 = ""
	
    resetDisplay()
    
	return [
		zwave.meterV2.meterReset().format(),
		zwave.meterV2.meterGet(scale: 0).format()
	]
    
    configure()
}

def configure() {
	log.debug "configure()"
    
	Long kwhDelay = settings.kWhDelay as Long
    Long wDelay = settings.wDelay as Long
    
    if (kwhDelay == null) {		// Shouldn't have to do this, but there seem to be initialization errors
		kwhDelay = 15
	}

	if (wDelay == null) {
		wDelay = 15
	}
    
	def cmd = delayBetween([
    	zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, scaledConfigurationValue: voltageValue).format(),		// assumed voltage
		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 0).format(),			// Disable (=0) selective reporting
		zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: 1).format(),			// Don't send whole HEM unless watts have changed by 30
		zwave.configurationV1.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: 1).format(),			// Don't send L1 Data unless watts have changed by 15
		zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: 1).format(),			// Don't send L2 Data unless watts have changed by 15 
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: 5).format(),			// Or by 5% (whole HEM)
		zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: 5).format(),			// Or by 5% (L1)
	    zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 5).format(),			// Or by 5% (L2)

		zwave.configurationV1.configurationSet(parameterNumber: 100, size: 4, scaledConfigurationValue: 1).format(),		// reset to defaults
        zwave.configurationV1.configurationSet(parameterNumber: 110, size: 4, scaledConfigurationValue: 1).format(),		// reset to defaults
		zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 772).format(),		// watt
		zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: wDelay).format(), 	// every %Delay% seconds
        zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 6152).format(),   	// kwh
		zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: kwhDelay).format(), // Every %Delay% seconds
		zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 1).format(),		// battery
		zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 10).format() 		// every hour
	], 2000)
	
    
	log.debug cmd
	cmd
}



def on() {
	sendEvent(name: "switch", value: "on")
}

def off() {
	sendEvent(name: "switch", value: "off")
}
