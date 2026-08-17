/**
 * Advanced Device Health Monitor  2.0
 *
 */
definition(
    name: "Advanced Device Health Monitor 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: " None",
    category: "Maintenance",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    ensureStateMaps()
    
    // Cross-reference logic for the Unmonitored Audit
    def auditList = []
    if (settings.auditActuators) auditList.addAll(settings.auditActuators)
    if (settings.auditSensors) auditList.addAll(settings.auditSensors)
    auditList = auditList.unique { it.id }
    
    def allMonitored = getAllMonitoredDevices()
    def allMonitoredIds = allMonitored.collect { it.id }
    def unmonitored = auditList.findAll { !allMonitoredIds.contains(it.id) }.sort { it.displayName?.toLowerCase() }

    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Dashboard"
            input "btnScanAlerts", "button", title: "🩺 Quick Scan Active Alerts"
            input "btnRunCheck", "button", title: "🩺 Force Full Health Scan"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff; margin-bottom: 15px;'>" +
                      "<b>System Status:</b> ${statusExplanation}</div>"

            def scanInProgressStr = state.isScanning ? "<span style='color: #3498db; font-weight:bold; float:right;'>🔄 Scan in Progress...</span>" : ""
            def lastCheckStr = state.lastCheckTime ?: "Never"
            
            def critCount = state.dashboardData?.count { it.status == "Red" || it.status == "Purple" } ?: 0
            def warnCount = state.dashboardData?.count { it.status == "Yellow" } ?: 0
            def healthyCount = state.dashboardData?.count { it.status == "Green" || it.status == "Blue" } ?: 0
            def totalCount = critCount + warnCount + healthyCount

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 30%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
                .stat-red { color: #e74c3c; font-weight: bold; }
                .stat-purple { color: #9b59b6; font-weight: bold; }
                .stat-yellow { color: #f1c40f; font-weight: bold; }
                .stat-green { color: #27ae60; font-weight: bold; }
            </style>
            
            <table class="dash-table">
                <thead><tr><th colspan="2">Global Health Overview ${scanInProgressStr}</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Last System Scan</td><td class="dash-val">${lastCheckStr}</td></tr>
                    <tr><td class="dash-hl">Total Devices Monitored</td><td class="dash-val">${totalCount}</td></tr>
                    <tr><td class="dash-hl stat-green">Healthy Devices</td><td class="dash-val stat-green">${healthyCount}</td></tr>
                    <tr><td class="dash-hl stat-yellow">Warnings</td><td class="dash-val stat-yellow">${warnCount}</td></tr>
                    <tr><td class="dash-hl stat-red">Critical & Flapping</td><td class="dash-val stat-red">${critCount}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>Active Device Alerts</b>", hideable: false, hidden: false) {
            def issues = state.dashboardData?.findAll { it.status == "Red" || it.status == "Purple" || it.status == "Yellow" }
            
            if (issues && issues.size() > 0) {
                def issuesHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Device Name</th><th>Status</th><th>Diagnostics</th><th>Last Active / Battery</th></tr></thead><tbody>"
                
                issues.each { issue ->
                    def statClass = ""
                    def statLabel = ""
                    if (issue.status == "Red") { statClass = "stat-red"; statLabel = "Critical" }
                    else if (issue.status == "Purple") { statClass = "stat-purple"; statLabel = "Flapping" }
                    else if (issue.status == "Yellow") { statClass = "stat-yellow"; statLabel = "Warning" }
                    
                    def actStr = issue.lastActive ?: "Unknown"
                    def battStr = (issue.battVal != null) ? " | 🔋 ${issue.battVal}%" : ""
                    
                    issuesHTML += "<tr><td style='text-align:left;'><b>${issue.name}</b></td><td class='${statClass}'>${statLabel}</td><td style='font-size:12px;'>${issue.messages.join('<br>')}</td><td style='font-size:12px; color:#555;'>${actStr}${battStr}</td></tr>"
                }
                issuesHTML += "</tbody></table>"
                paragraph issuesHTML
            } else {
                paragraph "<div style='padding: 15px; background: #f8f9fa; border: 1px solid #ccc; text-align: center; border-radius:4px; font-weight:bold; color: #27ae60;'>All Systems Nominal. No active alerts.</div>"
            }
        }
        
        section("<b>Recent Action History</b>", hideable: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No recent actions logged.</i>"
            }
            input "resetActionHistory", "button", title: "Clear Action History"
        }

        if (unmonitored.size() > 0) {
            section("<b>⚠️ Unmonitored Devices Audit (${unmonitored.size()})</b>", hideable: true, hidden: true) {
                paragraph "<div style='font-size:12px; color:#e74c3c; margin-bottom:5px;'>These devices are in your master audit list but are not assigned to any monitoring category below.</div>"
                def unHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Missed Device Name</th><th>Device Driver / Type</th></tr></thead><tbody>"
                unmonitored.each { u ->
                    def dType = "Unknown"
                    try { dType = u.typeName ?: "Unknown" } catch(e){}
                    unHTML += "<tr><td style='text-align:left;'><b>${u.displayName}</b></td><td style='font-size:12px; color:#555;'>${dType}</td></tr>"
                }
                unHTML += "</tbody></table>"
                paragraph unHTML
            }
        }

        section("<b>App Control</b>", hideable: true, hidden: true) {
            input "masterEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
        }

        section("<b>1. ⚙️ Core Setup & Notifications</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Controls how often the system checks for failures, handles auto-healing, and schedules notifications.</div>"
            
            input "scanInterval", "enum", title: "Automated Scan Interval", options: ["1 Hour", "3 Hours", "6 Hours", "12 Hours", "24 Hours"], defaultValue: "12 Hours", required: true
            input "scanChunkSize", "number", title: "Devices Processed per Async Chunk", defaultValue: 15, required: true
            
            paragraph "<hr style='border-top: 1px dashed #ccc;'>"
            
            paragraph "<b>Auto-Heal Power Cycling</b>"
            paragraph "<i>If a smart plug or switch goes offline, the system can attempt to toggle its physical power state to force it back onto the mesh (max once per 24hrs per device). Runs every 12 hours.</i>"
            input "enableAutoHeal", "bool", title: "Enable Auto-Healing?", defaultValue: false, submitOnChange: true
            if (enableAutoHeal) {
                input "autoHealModes", "mode", title: "Modes to ALLOW Auto-Healing", multiple: true, required: false, description: "Select modes where it's safe to briefly toggle switches."
            }

            paragraph "<hr style='border-top: 1px dashed #ccc;'>"
            
            paragraph "<b>Scheduled Health Report</b>"
            input "notifyFrequency", "enum", title: "Notification Frequency", options: ["Daily", "Every Other Day", "Weekly", "Never"], defaultValue: "Never", submitOnChange: true
            if (settings.notifyFrequency != null && settings.notifyFrequency != "Never") {
                input "notifyTime", "time", title: "Time to send report", required: true
                input "notifyDevices", "capability.notification", title: "Devices to notify", multiple: true, required: true
            }
        }

        section("<b>2. 🏗️ Device Selection</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Select all the devices in your home you want to monitor for health, battery, and inactivity.</div>"
            
            paragraph "<b>Infrastructure & Power</b>"
            input "actuatorDevices", "capability.actuator", title: "Hardwired / In-Wall Actuators (Locks, Motors)", multiple: true, required: false
            input "lightSwitches", "capability.switch", title: "In-Wall Light Switches & Dimmers", multiple: true, required: false
            input "smartPlugs", "capability.outlet", title: "Smart Plugs & Outlets", multiple: true, required: false
            input "hueLights", "capability.switchLevel", title: "Smart Bulbs & LED Strips", multiple: true, required: false
            
            paragraph "<b>Sensors</b>"
            input "smokeDetectors", "capability.smokeDetector", title: "Smoke / Carbon Monoxide Detectors", multiple: true, required: false
            input "waterSensors", "capability.waterSensor", title: "Water / Leak Sensors", multiple: true, required: false
            input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
            input "contactSensors", "capability.contactSensor", title: "Contact Sensors (Doors, Windows, Gates)", multiple: true, required: false
            input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors (Fobs, Mobile Apps)", multiple: true, required: false
            input "temperatureSensors", "capability.temperatureMeasurement", title: "Temperature Sensors", multiple: true, required: false
            input "genericSensors", "capability.sensor", title: "All Other / Generic Device Inputs", multiple: true, required: false
            
            paragraph "<b>Remotes (Sleepy Devices)</b>"
            input "buttonControllers", "capability.pushableButton", title: "Button Controllers & Remotes", multiple: true, required: false
        }
        
        section("<b>3. 🔍 Unmonitored Devices Audit</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> To find devices you missed, click inside both boxes below and choose <b>Select All</b>. The system will compare this master list against your configured monitoring groups and generate a table of any forgotten devices on the main dashboard.</div>"
            input "auditActuators", "capability.actuator", title: "Master List: Actuators (Check All)", multiple: true, required: false, submitOnChange: true
            input "auditSensors", "capability.sensor", title: "Master List: Sensors (Check All)", multiple: true, required: false, submitOnChange: true
        }

        section("<b>4. 📊 Monitoring Thresholds</b>", hideable: true, hidden: true) {
            paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px;'><b>Inactivity & Offline Detection</b></div>"
            input "enableInactivityCheck", "bool", title: "Enable Inactivity Monitoring?", defaultValue: true, submitOnChange: true
            if (enableInactivityCheck) {
                input "inactivityThreshold", "number", title: "Standard Inactivity Threshold (Hours)", defaultValue: 24, required: true
                input "buttonInactivityThreshold", "number", title: "Sleepy Device (Remotes/Buttons) Threshold (Hours)", defaultValue: 168, required: true
                input "ignoreMainsInactivity", "bool", title: "Ignore Inactivity for Mains-Powered Devices?", defaultValue: true
                input "ignoredInactivityDevices", "capability.sensor", title: "Ignore Inactivity completely for these Devices", multiple: true, required: false
            }
            
            paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>Battery Health</b></div>"
            input "enableBatteryCheck", "bool", title: "Enable Battery Monitoring?", defaultValue: true, submitOnChange: true
            if (enableBatteryCheck) {
                input "batteryThreshold", "number", title: "Global Critical Battery Threshold (%)", defaultValue: 20, range: "1..100", required: true
                input "batteryWarnThreshold", "number", title: "Global Warning Battery Threshold (%)", defaultValue: 30, range: "1..100", required: true
                input "enableParasiticCheck", "bool", title: "Enable Parasitic Battery Drain Detection?", defaultValue: true
                if (enableParasiticCheck) {
                    input "parasiticThreshold", "number", title: "Max Allowable Battery Drop / 24 Hours (%)", defaultValue: 10, range: "1..50", required: true
                }
            }
            
            paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>Stale State Detection (Stuck Sensors)</b></div>"
            input "enableStuckCheck", "bool", title: "Enable Stale State Detection?", defaultValue: true, submitOnChange: true
            if (enableStuckCheck) {
                input "stuckMotionHours", "number", title: "Stuck 'Active' Threshold (Hours)", defaultValue: 2, required: true
                input "stuckContactHours", "number", title: "Stuck 'Open' Threshold (Hours)", defaultValue: 24, required: true
                input "ignoredStuckDevices", "capability.sensor", title: "Ignore Stuck State for these Devices", multiple: true, required: false
            }
            
            paragraph "<div style='background-color:#e2e3e5; padding:8px; border-radius:4px; margin-top:10px;'><b>Signal Quality (Wi-Fi & Zigbee/Z-Wave)</b></div>"
            input "enableWifiSignalCheck", "bool", title: "Enable Wi-Fi RSSI Monitoring?", defaultValue: false
            if (enableWifiSignalCheck) {
                input "wifiRssiThreshold", "number", title: "Critical Minimum Wi-Fi RSSI (e.g. -75)", defaultValue: -75, range: "-100..0", required: true
            }
            input "enableSignalCheck", "bool", title: "Enable Zigbee/Z-Wave Monitoring?", defaultValue: false
            if (enableSignalCheck) {
                input "rssiThreshold", "number", title: "Critical Minimum RSSI Threshold (e.g. -85)", defaultValue: -85, range: "-120..0", required: true
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { 
    logAction("Installed")
    initialize() 
}

def updated() { 
    logAction("Updated")
    unsubscribe()
    unschedule()
    initialize() 
}

def ensureStateMaps() {
    if (state.actionHistory == null) state.actionHistory = []
    if (state.dashboardData == null) state.dashboardData = []
    if (state.lastCheckTime == null) state.lastCheckTime = "Never"
    
    if (state.lastBatteryLevels == null) state.lastBatteryLevels = [:]
    if (state.batteryChangeDates == null) state.batteryChangeDates = [:]
    if (state.batteryHistory == null) state.batteryHistory = [:]
    if (state.batteryEwma == null) state.batteryEwma = [:]
    if (state.lastBatteryTime == null) state.lastBatteryTime = [:]
    
    if (state.previousStatus == null) state.previousStatus = [:]
    if (state.flapHistory == null) state.flapHistory = [:]
    if (state.rssiHistory == null) state.rssiHistory = [:]
    
    if (state.lastPowerCycle == null) state.lastPowerCycle = [:]
    
    if (state.isScanning == null) state.isScanning = false
    if (state.scanQueue == null) state.scanQueue = []
    if (state.tempResults == null) state.tempResults = []
}

def initialize() {
    ensureStateMaps()
    
    def sInt = settings.scanInterval ?: "12 Hours"
    if (sInt == "1 Hour") runEvery1Hour("runHealthCheck")
    else if (sInt == "3 Hours") runEvery3Hours("runHealthCheck")
    else if (sInt == "12 Hours") schedule("0 0 0/12 * * ?", "runHealthCheck")
    else if (sInt == "24 Hours") schedule("0 0 0 * * ?", "runHealthCheck")
    else schedule("0 0 0/6 * * ?", "runHealthCheck")
    
    if (settings.notifyFrequency != null && settings.notifyFrequency != "Never" && settings.notifyTime != null) {
        schedule(settings.notifyTime, "sendScheduledNotification")
        logAction("Scheduled reporting configured for: ${settings.notifyFrequency}")
    }

    if (settings.enableAutoHeal) {
        schedule("0 0 0/12 * * ?", "autoHealNetwork")
    }
    
    if (masterEnableSwitch) subscribe(masterEnableSwitch, "switch", enableSwitchHandler)
    
    logAction("Advanced Health Monitor Initialized. Schedule: ${sInt}")
    runIn(2, "runHealthCheck")
}

def isSystemEnabled() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

String getHumanReadableStatus() {
    def status = ""
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") {
        status = "<span style='color:red;'><b>System Disabled:</b></span> Monitoring is turned off via the Master Switch."
    } else {
        status = "<span style='color:green;'><b>Active & Monitoring:</b></span> System is running health checks on schedule."
    }
    return status
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        logInfo("Dashboard data manually refreshed by user.")
    } else if (btn == "resetActionHistory") {
        state.actionHistory = []
        logInfo("Action logging history cleared.")
    } else if (btn == "btnRunCheck") {
        runHealthCheck()
    } else if (btn == "btnScanAlerts") {
        runQuickHealthCheck()
    }
}

def enableSwitchHandler(evt) { 
    if (evt.value == "off") logAction("App Disabled via Master Switch."); 
    else logAction("App Enabled via Master Switch.") 
}

def getAllMonitoredDevices() {
    def allDevices = []
    if (settings.actuatorDevices) allDevices.addAll(settings.actuatorDevices)
    if (settings.smartPlugs) allDevices.addAll(settings.smartPlugs)
    if (settings.lightSwitches) allDevices.addAll(settings.lightSwitches)
    if (settings.hueLights) allDevices.addAll(settings.hueLights)
    if (settings.motionSensors) allDevices.addAll(settings.motionSensors)
    if (settings.contactSensors) allDevices.addAll(settings.contactSensors)
    if (settings.presenceSensors) allDevices.addAll(settings.presenceSensors)
    if (settings.smokeDetectors) allDevices.addAll(settings.smokeDetectors)
    if (settings.waterSensors) allDevices.addAll(settings.waterSensors)
    if (settings.temperatureSensors) allDevices.addAll(settings.temperatureSensors)
    if (settings.genericSensors) allDevices.addAll(settings.genericSensors)
    if (settings.buttonControllers) allDevices.addAll(settings.buttonControllers)
    
    return allDevices.flatten().findAll { it != null }.unique { it.id }
}

def sendScheduledNotification() {
    if (!isSystemEnabled()) return
    if (!settings.notifyDevices || settings.notifyFrequency == "Never") return
    
    def tz = location.timeZone
    
    if (settings.notifyFrequency == "Every Other Day") {
        def dayOfYear = Calendar.getInstance(tz).get(Calendar.DAY_OF_YEAR)
        if (dayOfYear % 2 == 0) return 
    } else if (settings.notifyFrequency == "Weekly") {
        def dayOfWeek = Calendar.getInstance(tz).get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek != Calendar.MONDAY) return 
    }
    
    def issues = state.dashboardData?.findAll { it.status == "Red" || it.status == "Purple" || it.status == "Yellow" }
    
    if (issues && issues.size() > 0) {
        def offline = []
        def critBatt = []
        def lowBatt = []
        def parasitic = []
        def stuck = []
        def flapping = []
        def signal = []
        def other = []

        issues.each { issue ->
            issue.messages.each { m ->
                def formattedStr = "${issue.name}: ${m}"
                if (m.contains("Offline") || m.contains("OFFLINE") || m.contains("No Activity Data")) offline << formattedStr
                else if (m.contains("Battery Critical")) critBatt << formattedStr
                else if (m.contains("Battery Low")) lowBatt << formattedStr
                else if (m.contains("Parasitic")) parasitic << formattedStr
                else if (m.contains("Stuck")) stuck << formattedStr
                else if (m.contains("Flapping")) flapping << formattedStr
                else if (m.contains("Signal") || m.contains("Wi-Fi")) signal << formattedStr
                else other << formattedStr
            }
        }
        
        def critCount = issues.count { it.status == "Red" || it.status == "Purple" }
        def warnCount = issues.count { it.status == "Yellow" }
        
        // Build an array of distinct messages based on what lists are populated
        def messagesToSend = []
        messagesToSend << "🛠️ Device Health Report Summary:\nAlerts: ${critCount} Critical, ${warnCount} Warnings."
        
        if (offline) { messagesToSend << "🔌 OFFLINE / INACTIVE:\n" + offline.collect{"  • 💀 ${it}"}.join("\n") }
        if (critBatt) { messagesToSend << "🔋 CRITICAL BATTERY:\n" + critBatt.collect{"  • 🩸 ${it}"}.join("\n") }
        if (lowBatt) { messagesToSend << "🪫 LOW BATTERY:\n" + lowBatt.collect{"  • ⚠️ ${it}"}.join("\n") }
        if (parasitic) { messagesToSend << "🧛 PARASITIC DRAIN:\n" + parasitic.collect{"  • 📉 ${it}"}.join("\n") }
        if (stuck) { messagesToSend << "🚪 STUCK SENSORS:\n" + stuck.collect{"  • 📌 ${it}"}.join("\n") }
        if (flapping) { messagesToSend << "🌪️ UNSTABLE MESH:\n" + flapping.collect{"  • ⚡ ${it}"}.join("\n") }
        if (signal) { messagesToSend << "📶 WEAK SIGNAL:\n" + signal.collect{"  • 📡 ${it}"}.join("\n") }
        if (other) { messagesToSend << "⚠️ OTHER WARNINGS:\n" + other.collect{"  • 🚧 ${it}"}.join("\n") }
        
        // Loop through each configured notification device and send the separate messages
        settings.notifyDevices.each { dev ->
            messagesToSend.each { msg ->
                dev.deviceNotification(msg)
            }
        }
        
        logAction("Scheduled Report Sent: ${critCount} Critical, ${warnCount} Warnings (Split Notifications).")
    } else {
        logInfo("Scheduled report skipped: All devices healthy.")
    }
}

def runQuickHealthCheck() {
    if (!isSystemEnabled()) {
        logAction("QUICK SCAN: Aborted. Master switch is OFF.")
        return
    }
    
    def nowMs = new Date().time
    if (state.isScanning) {
        if (state.scanStartTime && (nowMs - state.scanStartTime < 90000)) {
            logInfo("Scan already in progress. Skipping duplicate request.")
            return
        } else {
            log.warn "Health Monitor: Previous scan appears stuck. Resetting lock."
        }
    }
    
    ensureStateMaps()
    
    def problemDevIds = state.dashboardData?.findAll { it.status == "Red" || it.status == "Purple" || it.status == "Yellow" }?.collect { it.id } ?: []
    
    if (problemDevIds.size() == 0) {
        logAction("QUICK SCAN: No active alerts to scan. All systems nominal.")
        return
    }
    
    state.isScanning = true
    state.scanStartTime = nowMs
    
    state.tempResults = state.dashboardData?.findAll { it.status == "Green" || it.status == "Blue" } ?: []
    state.scanQueue = problemDevIds.collect { [id: it] }
    
    logAction("QUICK SCAN: Starting targeted scan for ${problemDevIds.size()} active alerts.")
    runIn(1, "processScanChunk")
}

def runHealthCheck() {
    if (!isSystemEnabled()) {
        logAction("SCAN: Aborted. Master switch is OFF.")
        return
    }
    
    def nowMs = new Date().time
    if (state.isScanning) {
        if (state.scanStartTime && (nowMs - state.scanStartTime < 90000)) {
            logInfo("Scan already in progress. Skipping duplicate request.")
            return
        } else {
            log.warn "Health Monitor: Previous scan appears stuck. Resetting lock."
        }
    }
    
    ensureStateMaps()
    state.isScanning = true
    state.scanStartTime = nowMs
    
    state.scanQueue = []
    state.tempResults = []
    
    try {
        def allDevs = getAllMonitoredDevices()
        if (allDevs.size() > 0) {
            logAction("SCAN: Starting Async scan for ${allDevs.size()} devices.")
            state.scanQueue = allDevs.collect { [id: it.id] }
            runIn(1, "processScanChunk")
        } else {
            state.isScanning = false
            logAction("SCAN: No devices mapped. Scan aborted.")
        }
        
    } catch (e) {
        log.error "Health Monitor: Error initializing scan sequence - ${e}"
        state.isScanning = false
    }
}

def processScanChunk() {
    if (!state.isScanning) return
    
    def queue = state.scanQueue ?: []
    if (queue.size() == 0) {
        runIn(1, "finalizeScan")
        return
    }
    
    def chunkSize = (settings.scanChunkSize ?: 15).toInteger()
    def chunk = queue.take(chunkSize)
    state.scanQueue = queue.drop(chunkSize)
    
    def currentResults = state.tempResults ?: []
    def allDevs = getAllMonitoredDevices()
    def nowMs = new Date().time
    
    def ignoreListIds = settings.ignoredInactivityDevices?.collect { it.id.toString() } ?: []
    def ignoreStuckListIds = settings.ignoredStuckDevices?.collect { it.id.toString() } ?: []
    
    chunk.each { qItem ->
        def dev = allDevs.find { it.id == qItem.id }
        if (!dev) return 
        
        try {
            def devHealth = "Green"
            def msgs = []
            def rawBattVal = null
            
            long latestTime = 0
            def lastActiveDate = dev.getLastActivity()
            if (lastActiveDate) latestTime = lastActiveDate.time
            
            def isSleepyDev = settings.buttonControllers?.find { it.id == dev.id } != null
            def inactivityThreshHours = isSleepyDev ? (settings.buttonInactivityThreshold ?: 168) : (settings.inactivityThreshold ?: 24)
            def diffHours = (nowMs - latestTime) / 3600000.0
            
            if (diffHours >= (inactivityThreshHours * 0.75)) {
                try {
                    dev.currentStates?.each { st ->
                        if (st?.date && st.date.time > latestTime) {
                            latestTime = st.date.time
                        }
                    }
                } catch(e) {}
                
                try {
                    def evts = dev.events([max: 5])
                    if (evts) {
                        evts.each { evt ->
                            def isCommand = (evt.type?.toString()?.toLowerCase() == "command") || 
                                            (evt.name?.toString()?.toLowerCase()?.contains("command"))
                            if (!isCommand && evt?.date && evt.date.time > latestTime) {
                                latestTime = evt.date.time
                            }
                        }
                    }
                } catch(e) {}
            }
            
            lastActiveDate = latestTime > 0 ? new Date(latestTime) : null
            def lastActiveStr = lastActiveDate ? lastActiveDate.format("MM/dd/yy h:mm a", location.timeZone) : "Unknown"
            
            if (dev.hasAttribute("healthStatus")) {
                def hStat = dev.currentValue("healthStatus")
                if (hStat && hStat.toString().toLowerCase() == "offline") {
                    devHealth = "Red"
                    msgs << "System reports OFFLINE"
                }
            }
            
            def isMainsPowered = settings.actuatorDevices?.find { it.id == dev.id } != null || 
                                 settings.smartPlugs?.find { it.id == dev.id } != null || 
                                 settings.lightSwitches?.find { it.id == dev.id } != null || 
                                 settings.hueLights?.find { it.id == dev.id } != null
            
            if (settings.enableBatteryCheck && dev.hasAttribute("battery") && !isMainsPowered) {
                def batt = dev.currentValue("battery")
                if (batt != null && batt.toString().isNumber()) {
                    def bVal = batt.toInteger()
                    rawBattVal = bVal
                    
                    long changedMs = state.batteryChangeDates[dev.id] ?: nowMs
                    long lastTime = state.lastBatteryTime[dev.id] ?: changedMs
                    
                    if (state.lastBatteryLevels[dev.id] != null && bVal > (state.lastBatteryLevels[dev.id] + 5)) {
                        state.batteryChangeDates[dev.id] = nowMs
                        state.batteryEwma[dev.id] = null 
                    }
                    
                    state.lastBatteryLevels[dev.id] = bVal
                    state.lastBatteryTime[dev.id] = nowMs
                    
                    if (settings.enableParasiticCheck) {
                        if (!state.batteryHistory) state.batteryHistory = [:]
                        if (!state.batteryHistory[dev.id]) state.batteryHistory[dev.id] = []
                        
                        state.batteryHistory[dev.id] << [time: nowMs, val: bVal]
                        state.batteryHistory[dev.id] = state.batteryHistory[dev.id].findAll { nowMs - it.time <= 86400000 }
                        
                        if (state.batteryHistory[dev.id].size() > 1) {
                            def oldestReading = state.batteryHistory[dev.id].first()
                            def dropInDay = oldestReading.val - bVal
                            def maxDropAllowed = settings.parasiticThreshold ?: 10
                            
                            if (dropInDay >= maxDropAllowed) {
                                if (devHealth != "Red" && devHealth != "Purple") devHealth = "Yellow"
                                msgs << "Parasitic Drain (-${dropInDay}% / 24h)"
                            }
                        }
                    }

                    def thresh = settings.batteryThreshold ?: 20
                    def warnThresh = settings.batteryWarnThreshold ?: 30
                    if (bVal <= thresh) {
                        devHealth = "Red"
                        msgs << "Battery Critical"
                    } else if (bVal <= warnThresh) {
                        if (devHealth != "Red" && !msgs.any{it.contains("Parasitic")}) devHealth = "Yellow"
                        msgs << "Battery Low"
                    } else {
                        if (!msgs.any{it.contains("Parasitic")}) msgs << "Battery OK"
                    }
                }
            }
            
            def skipInactivity = ignoreListIds.contains(dev.id.toString()) || (settings.ignoreMainsInactivity && isMainsPowered)
            
            if (settings.enableInactivityCheck && !skipInactivity) {
                if (lastActiveDate) {
                    diffHours = (nowMs - lastActiveDate.time) / 3600000
                    
                    if (diffHours > inactivityThreshHours) {
                        devHealth = "Red"
                        msgs << "Offline (${diffHours.toInteger()} hrs)"
                    } else if (diffHours > (inactivityThreshHours * 0.75)) {
                        if (devHealth != "Red") devHealth = "Yellow"
                        msgs << "Inactive (${diffHours.toInteger()} hrs)"
                    } else {
                        if (msgs.size() == 0 || (msgs.size() == 1 && msgs[0].contains("Battery OK"))) {
                            if (!msgs.contains("Active")) msgs << "Active"
                        }
                    }
                } else {
                    devHealth = "Red"
                    msgs << "No Activity Data"
                }
            } else if (msgs.size() == 0 || (msgs.size() == 1 && msgs[0].contains("Battery OK"))) {
                if (!msgs.contains("Active")) msgs << "Monitoring Active"
            }
            
            if (settings.enableStuckCheck && !ignoreStuckListIds.contains(dev.id.toString())) {
                if (dev.hasAttribute("motion") && dev.currentValue("motion") == "active") {
                    def stateDate = dev.currentState("motion")?.date
                    if (stateDate) {
                        def diffHrs = (nowMs - stateDate.time) / 3600000
                        def thresh = settings.stuckMotionHours ?: 2
                        if (diffHrs > thresh) {
                            if (devHealth != "Red" && devHealth != "Purple") devHealth = "Yellow"
                            msgs << "Stuck Active (${diffHrs.toInteger()}h)"
                        }
                    }
                }
                if (dev.hasAttribute("contact") && dev.currentValue("contact") == "open") {
                    def stateDate = dev.currentState("contact")?.date
                    if (stateDate) {
                        def diffHrs = (nowMs - stateDate.time) / 3600000
                        def thresh = settings.stuckContactHours ?: 24
                        if (diffHrs > thresh) {
                            if (devHealth != "Red" && devHealth != "Purple") devHealth = "Yellow"
                            msgs << "Stuck Open (${diffHrs.toInteger()}h)"
                        }
                    }
                }
            }
            
            if (settings.enableWifiSignalCheck) {
                if (dev.hasAttribute("wifiSignal") || dev.hasAttribute("rssi")) {
                    if (!dev.hasAttribute("lqi")) { 
                        def wifiRssi = dev.currentValue("wifiSignal") ?: dev.currentValue("rssi")
                        if (wifiRssi != null && wifiRssi.toString().isNumber()) {
                            def rVal = wifiRssi.toInteger()
                            def thresh = settings.wifiRssiThreshold ?: -75
                            if (rVal <= thresh) {
                                devHealth = "Red"
                                msgs << "Weak Wi-Fi (${rVal} dBm)"
                            } else if (rVal <= thresh + 10) {
                                if (devHealth != "Red") devHealth = "Yellow"
                                msgs << "Fair Wi-Fi (${rVal} dBm)"
                            }
                        }
                    }
                }
            }

            if (settings.enableSignalCheck) {
                if (dev.hasAttribute("rssi") && dev.hasAttribute("lqi")) { 
                    def rssi = dev.currentValue("rssi")
                    if (rssi != null && rssi.toString().isNumber()) {
                        def rVal = rssi.toInteger()
                        def thresh = settings.rssiThreshold ?: -85
                        if (rVal <= thresh) {
                            devHealth = "Red"
                            msgs << "Weak Signal (${rVal} dBm)"
                        } else if (rVal <= thresh + 10) {
                            if (devHealth != "Red") devHealth = "Yellow"
                            msgs << "Fair Signal (${rVal} dBm)"
                        }
                    }
                }
            }
            
            def oldStatus = state.previousStatus[dev.id]
            if (devHealth == "Red" && oldStatus != "Red" && oldStatus != "Purple") {
                if (!state.flapHistory[dev.id]) state.flapHistory[dev.id] = []
                state.flapHistory[dev.id] << nowMs
            }
            
            if (state.flapHistory[dev.id]) {
                state.flapHistory[dev.id] = state.flapHistory[dev.id].findAll { nowMs - it < 86400000 }
                if (state.flapHistory[dev.id].size() >= 3) {
                    devHealth = "Purple"
                    msgs.add(0, "Flapping/Unstable")
                }
            }
            
            state.previousStatus[dev.id] = devHealth
            msgs.removeAll { it == "Battery OK" && msgs.size() > 1 } 
            
            currentResults << [
                id: dev.id,
                name: dev.displayName,
                status: devHealth,
                messages: msgs,
                lastActive: lastActiveStr,
                battVal: rawBattVal
            ]
            
        } catch (e) {
            log.warn "Health Monitor: Error scanning device ${dev.displayName} - ${e}"
        }
    }
    
    state.tempResults = currentResults
    
    if (state.scanQueue.size() > 0) {
        runIn(1, "processScanChunk") 
    } else {
        runIn(1, "finalizeScan")
    }
}

def finalizeScan() {
    def results = state.tempResults ?: []
    
    def order = ["Purple": 1, "Red": 2, "Yellow": 3, "Blue": 4, "Green": 5]
    results = results.sort { a, b -> 
        def c1 = order[a.status] <=> order[b.status]
        if (c1 != 0) return c1
        return a.name <=> b.name
    }
    
    state.dashboardData = results
    state.lastCheckTime = new Date().format("MM/dd/yyyy h:mm a", location.timeZone)
    
    state.isScanning = false
    state.scanStartTime = null
    state.tempResults = []
    state.scanQueue = []
    
    logAction("SCAN: Async scan sequence completed successfully.")
}

def autoHealNetwork() {
    if (!isSystemEnabled() || !settings.enableAutoHeal) return
    
    // Check Mode Restrictions
    def allowedModes = settings.autoHealModes
    if (allowedModes && !(allowedModes as List).contains(location.mode)) {
        logInfo("Auto-Heal skipped: House is not in an allowed mode.")
        return
    }
    
    def allDevs = getAllMonitoredDevices()
    def nowMs = new Date().time
    def problemDevIds = state.dashboardData?.findAll { it.status == "Red" || it.status == "Purple" || it.status == "Yellow" }?.collect { it.id } ?: []
    def healedCount = 0

    allDevs.each { dev ->
        if (problemDevIds.contains(dev.id)) {
            // Check if it's an actuator that we can cycle
            if (dev.hasCommand("on") && dev.hasCommand("off")) {
                def lastCycle = state.lastPowerCycle[dev.id] ?: 0
                
                // Only try to heal once every 24 hours (86400000 ms) per device
                if (nowMs - lastCycle > 86400000) { 
                    def currState = dev.currentValue("switch")
                    if (currState == "on") {
                        try {
                            dev.off()
                            runIn(5, "turnDevOn", [data: [id: dev.id]])
                            state.lastPowerCycle[dev.id] = nowMs
                            logAction("AUTO-HEAL: Power cycled offline actuator ${dev.displayName} (Off -> On).")
                            healedCount++
                        } catch(e) {}
                    } else {
                        try {
                            dev.on()
                            runIn(5, "turnDevOff", [data: [id: dev.id]])
                            state.lastPowerCycle[dev.id] = nowMs
                            logAction("AUTO-HEAL: Power cycled offline actuator ${dev.displayName} (On -> Off).")
                            healedCount++
                        } catch(e) {}
                    }
                }
            } else if (dev.hasCommand("refresh")) {
                try { dev.refresh(); healedCount++ } catch(e){} 
            } else if (dev.hasCommand("ping")) {
                try { dev.ping(); healedCount++ } catch(e){}
            }
        }
    }
    
    if (healedCount > 0) {
        // Trigger a fresh scan 30 seconds after sending heal commands to see if they recovered
        runIn(30, "runHealthCheck", [overwrite: true])
    }
}

def turnDevOn(data) { def dev = getAllMonitoredDevices().find{it.id == data.id}; if(dev) dev.on() }
def turnDevOff(data) { def dev = getAllMonitoredDevices().find{it.id == data.id}; if(dev) dev.off() }

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
