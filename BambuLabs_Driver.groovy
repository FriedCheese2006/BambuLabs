/**
 *
 * Bambu Labs Printers
 *
 * Copyright 2023 Ryan Elliott
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * v0.1.0	RLE		Creation
 * v0.1.1   RLE     Added actuator capability for RM.
 * v0.1.2   RLE     Added print time left and print percent completion.
                    Added current print file.
                    Added rounding for fan speed %.
 * v0.1.3   RLE     Minor tweaks
 *
 *
 */

metadata {
    definition (name: "BambuLabs", namespace: "rle.bl", author: "FriedCheese2006", importUrl: "tbd") {
        capability "Initialize"
        capability "Health Check"
        capability "Presence Sensor"
        capability "Actuator"

        attribute "healthStatus", "ENUM", ["offline", "online","unknown"]
        attribute "bedTemperature", "NUMBER"
        attribute "partCoolingFanSpeed", "NUMBER"
        attribute "heatbreakFanSpeed", "NUMBER"
        attribute "chamberFanSpeed", "NUMBER"
        attribute "auxFanSpeed", "NUMBER"
        attribute "chamberTemperature", "NUMBER"
        attribute "nozzleTemperature", "NUMBER"
        attribute "printPercentComplete", "NUMBER"
        attribute "printTimeRemaining","NUMBER"
        attribute "currentPrintFile","STRING"
        attribute "currentPrintSpeed","STRING"


        command "connect"
        command "disconnect"
        command "subscribe"
        command "unsubscribe"
        // command "reset"
        command "barLightOn"
        command "barLightOff"
        command "logoLightOn"
        command "logoLightOff"
        command "unloadFilament"
        command "printSpeed", [[name: 'Adjust Printing Speed', type: 'ENUM', description: 'Update current print speed', constraints: ["Silent", "Standard", "Sport", "Ludicrous"]]]
        command "setNozzleTemp", [[name: 'Set the Nozzle Temperature', type: "NUMBER", description: "Enter the desired nozzle temp"]]
        command "setBedTemp", [[name: 'Set the Print Bed Temperature', type: "NUMBER", description: "Enter the desired bed temp"]]
        command "setPartCoolingFanSpeed", [[name: 'Set the Part Cooling Fan Speed (0-100)', type: "NUMBER", description: "Enter the desired fan speed"]]
        command "setAuxFanSpeed", [[name: 'Set the Aux Cooling Fan Speed (0-100)', type: "NUMBER", description: "Enter the desired fan speed"]]
        command "setChamberFanSpeed", [[name: 'Set the Chamber Cooling Fan Speed (0-100)', type: "NUMBER", description: "Enter the desired fan speed"]]
        command "attributeUpdateSchedule", [[name: 'How often should attributes be updated? (Default is 30 seconds)', type: "NUMBER", description: "How often (in seconds) should the attributes be updated?"]]
    }
}

preferences {
    input name: "deviceIP", type: "text", title: getFormat("header","Printer IP"), required: true, displayDuringSetup: true
    input name: "accessCode", type: "text", title: getFormat("header","Access Code"), description: getFormat("important","Provide the access code from network settings"), required: true, displayDuringSetup: true
    input name: "retry", type: "bool", title: getFormat("header","Automatically retry to connect when disconnected?"), description: getFormat("important","The driver will automatically try reconnecting every 5 seconds.<br>Turn off if you power down your printer between prints."), defaultValue: true
    input name: "infoOutput", type: "bool", title: getFormat("header","Enable info logging"), defaultValue: true
    input name: "debugOutput", type: "bool", title: getFormat("header","Enable debug logging"), defaultValue: true
    input name: "traceOutput", type: "bool", title: getFormat("header","Enable trace logging"), defaultValue: false
}

def installed() {
  initialize()
}

def updated() {
  log.info "Updated with $settings"
  if (debugOutput) runIn(1800,debugLogsOff)
  if (traceOutput) runIn(1800,traceLogsOff)
  initialize()
}

def initialize() {
    log.info "initialize() called"
    unschedule()
    if(deviceIP) connect()
    state._comment = """<div style='color:#660000;font-weight: bold;font-size: 24px'>Please visit the community thread for descriptions and how-to.</div>"""+"""<a href="https://community.hubitat.com/t/release-bambu-labs-3d-printer-integration/117942" style='font-size: 24px'>Bambu Labs Driver</a>"""
}

def uninstalled() {
  log.warn "Uninstalling"
  unschedule()
  disconnect()
}

def connect() {
	if (!interfaces.mqtt.isConnected()) {
        log.warn "Disconnected; attempting to reconnect"
		try {
            interfaces.mqtt.connect(
                "ssl://$deviceIP:$devicePort","42","bblp","$accessCode",
                tlsVersion: "1.2",
                ignoreSSLIssues: true)
        } catch(Exception e) {
			log.error "Error connecting: ${e}."
            if(retry) runIn(5,connect)
		}
	}
    if (interfaces.mqtt.isConnected()) {
        logDebug "Connected to printer"
        updateSchedule = state.updateSchedule ?: 30
        runIn(updateSchedule,subscribe)
    }
}

def disconnect() {
    unschedule()
    interfaces.mqtt.disconnect()
    logInfo "Attempting to disconnect from printer."
    pauseExecution(500)
    if(!interfaces.mqtt.isConnected()) {
        logInfo "Disconnected from printer."
    } else {
        disconnect()
    }
}

def barLightOn() {
    logInfo "Turning the bar light on"
    payload = """{"system":{"sequence_id":"2003","command":"ledctrl","led_node":"chamber_light","led_mode":"on","led_on_time": 500,"led_off_time": 500,"loop_times": 0,"interval_time":0},"user_id":"123456789"}"""
    sendCommand(payload)
}

def barLightOff() {
    logInfo "Turning the bar light off"
    payload = """{"system":{"sequence_id":"2003","command":"ledctrl","led_node":"chamber_light","led_mode":"off","led_on_time": 500,"led_off_time": 500,"loop_times": 0,"interval_time":0},"user_id":"123456789"}"""
    sendCommand(payload)
}

def logoLightOn() {
    logInfo "Turning the logo light on"
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M960 S5 P1 \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def logoLightOff() {
    logInfo "Turning the logo light off"
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M960 S5 P0 \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def setNozzleTemp(nozzleTemp) {
    if(nozzleTemp > 300) {
        log.warn "Nozzle temp set too high; adjusting to 300."
        nozzleTemp = 300
    }
    logDebug "Setting nozzle temp to $nozzleTemp"
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M104 S${nozzleTemp} \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def setBedTemp(bedTemp) {
    if(bedTemp > 110) {
        log.warn "Bed temp set too high; adjusting to 110."
        bedTemp = 110
    }
    logDebug "Setting bed temp to $bedTemp"
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M140 S${bedTemp} \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def printSpeed(String speed) {
    switch(speed) {
        case "Silent":
            speed = 1
            break;
        case "Standard" :
            speed = 2
            break;
        case "Sport":
            speed = 3
            break;
        case "Ludicrous":
            speed = 4
            break;
    }
    payload = """{"print":{"sequence_id":"2004","command":"print_speed","param":"${speed}"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def setPartCoolingFanSpeed(Number speed) {
    if(speed > 100) speed = 100
    logDebug "Setting part cooling fan to $speed%"
    speed = (speed*255)/100
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M106 P1 S${speed} \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def setAuxFanSpeed(Number speed) {
    if(speed > 100) speed = 100
    logDebug "Setting part cooling fan to $speed%"
    speed = (speed*255)/100
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M106 P2 S${speed} \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def setChamberFanSpeed(Number speed) {
    if(speed > 100) speed = 100
    logDebug "Setting part cooling fan to $speed%"
    speed = (speed*255)/100
    payload = """{"print":{"sequence_id":"2006","command":"gcode_line","param":"M106 P3 S${speed} \\n"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def unloadFilament() {
    logInfo "Unloading filament"
    payload = """{"print":{"sequence_id":"2027","command":"gcode_file","param":"/usr/etc/print/filament_unload.gcode"},"user_id":"1234567890"}"""
    sendCommand(payload)
}

def sendCommand(payload) {
    topic = "device/${state.deviceId}/request"
    interfaces.mqtt.publish("${topic}","${payload}")
}

def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)
    // logTrace message

    if(!state.deviceId) {
        def regex = /device\/(.*)\/report/
        def matcher = (message =~ regex)
        if (matcher.find()) {
            deviceId = matcher.group(1)
            if(deviceId != state.deviceId) {
            state.deviceId = deviceId
            }
        logTrace "Device ID: $state.deviceId"
        }
    }

    def json = new groovy.json.JsonSlurper().parseText(message.payload)
    logTrace json

    if(json.containsKey('print')) {
        if (json.print.containsKey('bed_temper')) {
            def bedTemp = json.print.bed_temper as Double
            sendEvent(name: 'bedTemperature', value: bedTemp, unit: '°C', displayed: true)
        }

        if (json.print.containsKey('nozzle_temper')) {
            def nozzleTemp = json.print.nozzle_temper as Double
            sendEvent(name: 'nozzleTemperature', value: nozzleTemp, unit: '°C', displayed: true)
        }

        if (json.print.containsKey('chamber_temper')) {
            def chamberTemp = json.print.chamber_temper as Double
            sendEvent(name: 'chamberTemperature', value: chamberTemp, unit: '°C', displayed: true)
        }

        if (json.print.containsKey('big_fan1_speed')) {
            def fan1Speed = json.print.big_fan1_speed as Integer
            fan1Speed = (fan1Speed*100)/15
            fan1Speed = new BigDecimal(fan1Speed).setScale(2,BigDecimal.ROUND_HALF_UP).toDouble()
            sendEvent(name: 'auxFanSpeed', value: fan1Speed, unit: '%', displayed: true)
        }

        if (json.print.containsKey('big_fan2_speed')) {
            def fan2Speed = json.print.big_fan2_speed as Integer
            fan2Speed = (fan2Speed*100)/15
            fan2Speed = new BigDecimal(fan2Speed).setScale(2,BigDecimal.ROUND_HALF_UP).toDouble()
            sendEvent(name: 'chamberFanSpeed', value: fan2Speed, unit: '%', displayed: true)
        }

        if (json.print.containsKey('heatbreak_fan_speed')) {
            def heatbreakFanSpeed = json.print.heatbreak_fan_speed as Integer
            heatbreakFanSpeed = (heatbreakFanSpeed*100)/15
            heatbreakFanSpeed = new BigDecimal(heatbreakFanSpeed).setScale(2,BigDecimal.ROUND_HALF_UP).toDouble()
            sendEvent(name: 'heatbreakFanSpeed', value: heatbreakFanSpeed, unit: '%', displayed: true)
        }

        if (json.print.containsKey('cooling_fan_speed')) {
            def coolingFanSpeed = json.print.cooling_fan_speed as Integer
            coolingFanSpeed = (coolingFanSpeed*100)/15
            coolingFanSpeed = new BigDecimal(coolingFanSpeed).setScale(2,BigDecimal.ROUND_HALF_UP).toDouble()
            sendEvent(name: 'partCoolingFanSpeed', value: coolingFanSpeed, unit: '%', displayed: true)
        }

        if (json.print.containsKey('mc_percent')) {
            def printPerc = json.print.mc_percent as Integer
            sendEvent(name: 'printPercentComplete', value: printPerc, unit: '%', displayed: true)
        }

        if (json.print.containsKey('mc_remaining_time')) {
            def timeRemaining = json.print.mc_remaining_time as Integer
            sendEvent(name: 'printTimeRemaining', value: timeRemaining, unit: 'Minutes', displayed: true)
        }

        if (json.print.containsKey('spd_lvl')) {
            def currentSpeed = json.print.spd_lvl as Integer
            switch(currentSpeed) {
                case 1:
                    currentPrintSpeed = Silent
                    break;
                case 2 :
                    currentPrintSpeed = "Standard"
                    break;
                case 3:
                    currentPrintSpeed = "Sport"
                    break;
                case 4:
                    currentPrintSpeed = "Ludicrous"
                    break;
            }
            sendEvent(name: 'currentPrintSpeed', value: currentPrintSpeed, displayed: true)
        }

        if (json.print.containsKey('subtask_name')) {
            def currentPrintFile = json.print.subtask_name as String
            sendEvent(name: 'currentPrintFile', value: currentPrintFile, displayed: true)
        }

        unsubscribe()
    }
}

def mqttClientStatus(message) {
    logDebug "Status is ${message}"
    logDebug "Connection status is ${interfaces.mqtt.isConnected()}"
    if(message.contains("Connection lost")) {
        setHealthStatusValue("offline")
        unschedule()
        runIn(5,connect)
    }
    if(message.contains("Connection succeeded")) {
        setHealthStatusValue("online")
    }
}

def subscribe() {
    if (!interfaces.mqtt.isConnected()) {
        connect()
    } else {
        logDebug "Subscribing"
        interfaces.mqtt.subscribe("#")
    }
}

def unsubscribe() {
    logDebug "Unsubscribing"
    interfaces.mqtt.unsubscribe("#")
    updateSchedule = state.updateSchedule ?: 30
    runIn(updateSchedule,subscribe)
}

def setHealthStatusValue(value) {
  logTrace "Health status is $value"
  if(value == "online") {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
    sendEvent(name: "presence", value: "present")
  } else {
      sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
      sendEvent(name: "presence", value: "not present")
      log.error "Printer is disconnected."
  }
}

def attributeUpdateSchedule(Number updateSchedule) {
    state.updateSchedule = updateSchedule.toLong()
    logInfo "Update schedule set to $updateSchedule"
    connect()
}

def logInfo(msg) {
  if (settings?.infoOutput) {
  log.info msg
  }
}

def logDebug(msg) {
  if (settings?.debugOutput) {
  log.debug msg
  }
}

def debugLogsOff(){
  log.warn "debug logging disabled..."
  device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def logTrace(msg) {
  if (settings?.traceOutput) {
  log.trace msg
  }
}

def traceLogsOff(){
  log.warn "debug logging disabled..."
  device.updateSetting("traceOutput",[value:"false",type:"bool"])
}

def getFormat(type, myText="") {
	if(type == "header") return "<div style='color:#660000;font-weight: bold'>${myText}</div>"
	if(type == "red") return "<div style='color:#660000'>${myText}</div>"
	if(type == "redBold") return "<div style='color:#660000;font-weight: bold;text-align: center;'>${myText}</div>"
	if(type == "importantBold") return "<div style='color:#32a4be;font-weight: bold'>${myText}</div>"
	if(type == "important") return "<div style='color:#32a4be'>${myText}</div>"
	if(type == "important2") return "<div style='color:#5a8200'>${myText}</div>"
	if(type == "important2Bold") return "<div style='color:#5a8200;font-weight: bold'>${myText}</div>"
	if(type == "lessImportant") return "<div style='color:green'>${myText}</div>"
	if(type == "rateDisplay") return "<div style='color:green; text-align: center;font-weight: bold'>${myText}</div>"
	if(type == "dull") return "<div style='color:black>${myText}</div>"
}

def reset() {
    state.clear()
}