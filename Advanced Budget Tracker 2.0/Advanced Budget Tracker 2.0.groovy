/**
 * Advanced Budget Tracker 2.0
 *
 */

definition(
    name: "Advanced Budget Tracker 2.0",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "None",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    oauth: true
)

import groovy.json.JsonOutput
import java.text.SimpleDateFormat

preferences {
    page(name: "mainPage")
}

mappings {
    path("/ui") { action: [GET: "serveApp"] }
    path("/api/data") { action: [GET: "serveApiData"] }
    path("/api/backup") { action: [GET: "serveBackup"] }
    
    path("/api/tx/save") { action: [GET: "handleSaveTx"] }
    path("/api/tx/del") { action: [GET: "handleDelTx"] }
    path("/api/cat/save") { action: [GET: "handleSaveCat"] }
    path("/api/cat/del") { action: [GET: "handleDelCat"] }
    path("/api/chk/save") { action: [GET: "handleSaveChk"] }
    path("/api/chk/del") { action: [GET: "handleDelChk"] }
    path("/api/cc/save") { action: [GET: "handleSaveCC"] }
    path("/api/cc/del") { action: [GET: "handleDelCC"] }
    path("/api/ret/save") { action: [GET: "handleSaveRet"] }
    path("/api/ret/del") { action: [GET: "handleDelRet"] }
    path("/api/inv/save") { action: [GET: "handleSaveInv"] }
    path("/api/inv/del") { action: [GET: "handleDelInv"] }
    path("/api/loan/save") { action: [GET: "handleSaveLoan"] }
    path("/api/loan/del") { action: [GET: "handleDelLoan"] }
    path("/api/sav/save") { action: [GET: "handleSaveSav"] }
    path("/api/sav/del") { action: [GET: "handleDelSav"] }
    path("/api/sav/tx") { action: [GET: "handleSavTx"] }
    path("/api/plan/save") { action: [GET: "handleSavePlan"] }
    path("/api/plan/del") { action: [GET: "handleDelPlan"] }
    path("/api/rew/save") { action: [GET: "handleSaveRew"] }
    path("/api/rew/del") { action: [GET: "handleDelRew"] }
    path("/api/config/set") { action: [GET: "handleSetConfig"] }
}

def installed() { initialize() }
def updated() { initialize() }

def initialize() {
    if(atomicState.transactions == null) atomicState.transactions = []
    if(atomicState.categories == null) atomicState.categories = []
    if(atomicState.checking == null) {
        atomicState.checking = [[id: UUID.randomUUID().toString(), name: "Main Checking", balance: safeDouble(atomicState.startingBalance)]]
    }
    if(atomicState.creditCards == null) atomicState.creditCards = []
    if(atomicState.retirement == null) atomicState.retirement = []
    if(atomicState.investments == null) atomicState.investments = []
    if(atomicState.loans == null) atomicState.loans = []
    if(atomicState.savings == null) atomicState.savings = []
    if(atomicState.planned == null) atomicState.planned = []
    if(atomicState.rewards == null) atomicState.rewards = []
    if(atomicState.nwHistory == null) atomicState.nwHistory = [] 
    
    // Growth Histories
    if(atomicState.hsaHistory == null) atomicState.hsaHistory = []
    if(atomicState.retHistory == null) atomicState.retHistory = []
    if(atomicState.chkSavHistory == null) atomicState.chkSavHistory = []
    
    if(atomicState.users == null) atomicState.users = ["Shane", "Christy", "Leanne"]
    
    if(atomicState.monthStartBalance == null) atomicState.monthStartBalance = atomicState.startingBalance
    
    schedule("0 1 0 1 * ?", monthlyProcessing)
    schedule("0 0 1 * * ?", dailyAutoPayCheck)
}

// =======================================================================================
// --- LOGIC & HELPERS ---
// =======================================================================================

def fmt(val) { return val }

def safeDouble(val) {
    if (val == null || val == "") return 0.0
    try { return val.toString().toDouble() } catch (e) { return 0.0 }
}

def cleanName(str) {
    if(!str) return ""
    try { return java.net.URLDecoder.decode(str.toString(), "UTF-8") } catch(e) { return str }
}

def getSafeHubUID() { 
    if(settings.manualHubUID) return settings.manualHubUID.trim()
    try { if(location.hub?.id) return location.hub.id.toString() } catch(e){}
    try { if(location.hubs[0]?.id) return location.hubs[0].id.toString() } catch(e){}
    return "UNKNOWN_HUB_ID" 
}

// History Updaters
def updateHsaHistory() {
    def hsaStartBal = safeDouble(atomicState.hsaStartBal)
    def txs = atomicState.transactions ?: []
    def allHsaInc = txs.findAll { it.type == "hsa_income" }.sum { safeDouble(it.amount) } ?: 0
    def allHsaExp = txs.findAll { it.type == "hsa_expense" }.sum { safeDouble(it.amount) } ?: 0
    def curBal = hsaStartBal + allHsaInc - allHsaExp
    def hHist = atomicState.hsaHistory ?: []
    hHist << [date: new Date().format("MM/dd HH:mm"), val: curBal]
    if(hHist.size() > 30) hHist = hHist.takeRight(30)
    atomicState.hsaHistory = hHist
}

def updateRetHistory() {
    def rets = atomicState.retirement ?: []
    def invs = atomicState.investments ?: []
    def totalRet = (rets.sum { safeDouble(it.balance) } ?: 0.0) + (invs.sum { safeDouble(it.balance) } ?: 0.0)
    def rHist = atomicState.retHistory ?: []
    rHist << [date: new Date().format("MM/dd HH:mm"), val: totalRet]
    if(rHist.size() > 30) rHist = rHist.takeRight(30)
    atomicState.retHistory = rHist
}

def dailyAutoPayCheck() {
    def todayStr = new Date().format("yyyy-MM-dd")
    def cats = atomicState.categories ?: []
    def txs = atomicState.transactions ?: []
    def chk = atomicState.checking ?: []
    def catsChanged = false
    def txsChanged = false
    def chkChanged = false

    cats.each { c ->
        if((c.type == 'income' || c.type == 'hsa_income') && c.autoPay == true && c.nextPayDate == todayStr) {
            def monthlyExpected = safeDouble(c.expected)
            def installmentAmount = monthlyExpected
            if (c.payFreq == 'weekly') {
                installmentAmount = monthlyExpected / 4
            } else if (c.payFreq == 'biweekly') {
                 installmentAmount = monthlyExpected / 2
            }
            
            def fundId = chk.size() > 0 ? chk[0].id : "auto"

            txs << [
                id: UUID.randomUUID().toString(),
                catId: c.id,
                fundId: fundId,
                desc: "Auto-Contrib: ${cleanName(c.name)}",
                amount: installmentAmount,
                date: todayStr,
                type: c.type,
                want: false,
                txUser: "System",
                ts: new Date().getTime()
            ]
            txsChanged = true
            
            if (chk.size() > 0 && c.type == 'income') {
                chk[0].balance += installmentAmount
                chkChanged = true
            }
            
            def sdf = new SimpleDateFormat("yyyy-MM-dd")
            def dateObj = sdf.parse(c.nextPayDate)
            def cal = Calendar.getInstance()
            cal.setTime(dateObj)
            if(c.payFreq == 'weekly') cal.add(Calendar.DATE, 7)
             else if (c.payFreq == 'biweekly') cal.add(Calendar.DATE, 14)
            c.nextPayDate = sdf.format(cal.getTime())
            catsChanged = true
        }
    }
    if(txsChanged) {
        atomicState.transactions = txs
        updateHsaHistory()
    }
    if(catsChanged) atomicState.categories = cats
    if(chkChanged) atomicState.checking = chk
}

def monthlyProcessing() {
    def lastMonth = new Date().clone()
    lastMonth.setMonth(lastMonth.getMonth() - 1)
    def lastMonthStr = lastMonth.format("yyyy-MM")
    def currentMonthStr = new Date().format("yyyy-MM")
    
    // Roll over cumulative HSA Balance before purging old transactions
    def txs = atomicState.transactions ?: []
    def allHsaInc = txs.findAll { it.type == "hsa_income" }.sum { safeDouble(it.amount) } ?: 0
    def allHsaExp = txs.findAll { it.type == "hsa_expense" }.sum { safeDouble(it.amount) } ?: 0
    atomicState.hsaStartBal = safeDouble(atomicState.hsaStartBal) + allHsaInc - allHsaExp
    
    // Purge old transactions
    atomicState.transactions = (atomicState.transactions ?: []).findAll { 
        it.date && it.date.toString().startsWith(currentMonthStr) 
    }
    
    def cats = atomicState.categories ?: []
    cats = cats.collect { it.isPaid = false; return it }
    atomicState.categories = cats
    
    def cash = (atomicState.checking ?: []).sum { safeDouble(it.balance) } ?: 0
    def hsa = safeDouble(atomicState.hsaStartBal)
    def invest = (atomicState.retirement ?: []).sum{ safeDouble(it.balance) } ?: 0
    def portf = (atomicState.investments ?: []).sum{ safeDouble(it.balance) } ?: 0
    def savings = (atomicState.savings ?: []).sum{ safeDouble(it.balance) } ?: 0
    def badDebt = (atomicState.creditCards ?: []).sum{ safeDouble(it.balance) } ?: 0
    def secureDebt = (atomicState.loans ?: []).sum{ safeDouble(it.balance) } ?: 0
    def rews = (atomicState.rewards ?: []).sum{ safeDouble(it.balance) } ?: 0
    
    def assets = cash + hsa + invest + savings + portf + rews
    def liab = badDebt + secureDebt
    def nw = assets - liab
    
    def hist = atomicState.nwHistory ?: []
    hist << [date: lastMonthStr, val: nw]
    if(hist.size() > 12) hist = hist.takeRight(12)
    atomicState.nwHistory = hist
    
    // Track Checking + Savings history
    def chkSavTotal = cash + savings
    def csHist = atomicState.chkSavHistory ?: []
    csHist << [date: lastMonthStr, val: chkSavTotal]
    if(csHist.size() > 12) csHist = csHist.takeRight(12)
    atomicState.chkSavHistory = csHist
    
    atomicState.monthStartBalance = cash
}

def mainPage() {
    if (!state.accessToken) createAccessToken()
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        
        section("") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            def hubUID = getSafeHubUID()
            def linkStatus = (hubUID && hubUID != "UNKNOWN_HUB_ID") ? "<span style='color:green;'>Active</span>" : "<span style='color:#d9534f;'>Missing Hub ID</span>"
            def borderColor = (hubUID && hubUID != "UNKNOWN_HUB_ID") ? "#007bff" : "#d9534f"
         
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid ${borderColor};'>" +
                      "<b>Cloud Connection Status:</b> ${linkStatus}</div>"

            if (hubUID && hubUID != "UNKNOWN_HUB_ID") {
                def url = "https://cloud.hubitat.com/api/${hubUID}/apps/${app.id}/ui?access_token=${state.accessToken}"
                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table td { border: 1px solid #ccc; padding: 15px; text-align: center; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: center; font-size: 14px; color: #495057; }
                </style>
                <table class="dash-table">
                    <tbody>
                        <tr><td class="dash-subhead">Financial Overview Dashboard</td></tr>
                        <tr>
                            <td style="background-color: #f8f9fa;">
                                <a href='${url}' target='_blank' style='background:#4f46e5; color:white; padding:12px 24px; border-radius:5px; text-decoration:none; font-weight:bold; display: inline-block;'>OPEN BUDGET DASHBOARD</a>
                            </td>
                        </tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
            } else {
                paragraph "<i>Please enter your Hub ID in the section below to generate the cloud dashboard link.</i>"
            }
        }
        
        section("<b>Notifications & Alerts</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Route standard Hubitat push notifications to specific devices based on the type of expense updated.</div>"
            input "notifyDevicesBills", "capability.notification", title: "Devices for Fixed/Regular Bill Alerts:", multiple: true, required: false
            input "notifyDevicesCont", "capability.notification", title: "Devices for Continuous Expense Alerts (Gas, Food):", multiple: true, required: false
            input "notifyThreshold", "decimal", title: "Only notify if expense is over \$ (leave blank to alert on EVERY amount)", required: false
        }

        section("<b>App Control & Configuration</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Configure network parameters in the event automatic Cloud Link generation fails.</div>"
            input "manualHubUID", "text", title: "Manual Hub ID (If Cloud Link Fails)", submitOnChange: true, required: false
        }
        
        section("<b>Data Management (Admin)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Emergency actions for managing your ledger and correcting system encoding issues.</div>"
            input "btnWipe", "button", title: "🔥 Wipe All Financial Data"
            input "btnFixNames", "button", title: "🔧 Fix Broken Names (%20)"
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnWipe") {
        atomicState.transactions = []
        atomicState.categories = []
        atomicState.checking = []
        atomicState.creditCards = []
        atomicState.retirement = []
        atomicState.investments = []
        atomicState.loans = []
        atomicState.savings = []
        atomicState.planned = []
        atomicState.rewards = []
        atomicState.nwHistory = []
        atomicState.hsaHistory = []
        atomicState.retHistory = []
        atomicState.chkSavHistory = []
        atomicState.startingBalance = 0.0
        atomicState.hsaStartBal = 0.0
        atomicState.monthStartBalance = 0.0
    }
    if (btn == "btnFixNames") {
        def fixList = { list -> 
            if(!list) return [] 
             return list.collect { item -> 
                if(item.name) item.name = cleanName(item.name)
                return item 
            } 
        }
        atomicState.categories = fixList(atomicState.categories)
        atomicState.checking = fixList(atomicState.checking)
        atomicState.creditCards = fixList(atomicState.creditCards)
        atomicState.retirement = fixList(atomicState.retirement)
        atomicState.investments = fixList(atomicState.investments)
        atomicState.loans = fixList(atomicState.loans)
        atomicState.savings = fixList(atomicState.savings)
        atomicState.rewards = fixList(atomicState.rewards)
    }
}

def serveApp() {
    def css = """
    :root {
        --primary: #4f46e5;
        --gradient: #4f46e5;
        --hsa-gradient: #0d9488;
        --cc-gradient: #db2777;
        --ret-gradient: #ca8a04;
        --net-gradient: #1f2937;
        --rew-gradient: #8b5cf6;
        --bg: #f4f4f5;
        --card: #ffffff;
        --text: #1f2937;
        --border: #e5e7eb;
    }
    body { font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; background: var(--bg); margin: 0; padding: 0; color: var(--text); padding-bottom: 80px; -webkit-font-smoothing: antialiased; }

    .header { position: sticky; top: 0; z-index: 100; background: #ffffff; border-bottom: 1px solid var(--border); padding: 12px 16px; padding-top: max(12px, env(safe-area-inset-top)); display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; width: 100%; box-sizing: border-box; color: #111827; }

    .brand { display: flex; align-items: center; gap: 10px; }
    .header-actions { display:flex; gap:8px; flex-wrap:wrap; justify-content:flex-end; align-items:center; }

    .container { max-width: 600px; margin: 0 auto; padding: 0 16px; }
    .card { background: var(--card); border-radius: 0.75rem; margin-bottom: 16px; box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05); border: 1px solid var(--border); padding: 16px; }
    .section-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; border-bottom: 1px solid #f3f4f6; padding-bottom: 8px; }
    .section-title { margin: 0; font-size: 0.75rem; font-weight: 700; color: #9ca3af; text-transform: uppercase; letter-spacing: 0.05em; }
    .section-total { font-size: 0.75rem; font-weight: 600; background: #f3f4f6; padding: 4px 8px; border-radius: 0.375rem; color: #4b5563; }

    .item-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px solid #f3f4f6; cursor: pointer; }
    .item-row:last-child { border-bottom: none; }
    .item-icon { width: 36px; height: 36px; border-radius: 0.5rem; display: flex; align-items: center; justify-content: center; margin-right: 12px; font-size: 16px; }
    .item-left { display: flex; align-items: center; min-width: 0; }
    .item-name { font-weight: 600; font-size: 0.875rem; color: #111827; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: flex; align-items: center; }
    .item-sub { font-size: 0.75rem; color: #6b7280; font-weight: 500; margin-top: 2px; }
    .item-right { text-align: right; font-weight: 700; font-size: 0.875rem; }

    .pg-track { height: 6px; background: #f3f4f6; border-radius: 9999px; margin-top: 8px; overflow: hidden; }
    .pg-fill { height: 100%; border-radius: 9999px; transition: width 0.3s ease; }

    .c-green { color: #10b981; } .bg-green { background: #10b981; }
    .c-red { color: #ef4444; } .bg-red { background: #ef4444; }
    .c-teal { color: #14b8a6; } .bg-teal { background: #14b8a6; }
    .bg-orange { background: #f59e0b; } .bg-grey { background: #9ca3af; }
    .bg-blue { background: #4f46e5; } .bg-gold { background: #eab308; }
    .bg-transparent { background: transparent; }

    .fab { position: fixed; bottom: 24px; right: 24px; width: 56px; height: 56px; background: var(--primary); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white; font-size: 24px; box-shadow: 0 8px 20px rgba(0,0,0,0.3); cursor: pointer; z-index: 200; transition: transform 0.2s; }
    .fab:active { transform: scale(0.95); }

    .modal-overlay { position: fixed; inset: 0; background: rgba(17, 24, 39, 0.6); backdrop-filter: blur(4px); z-index: 1000; display: none; align-items: center; justify-content: center; padding: 16px; }
    .modal { background: white; width: 100%; max-width: 24rem; padding: 24px; border-radius: 1rem; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04); animation: popIn 0.2s ease-out; max-height: 90vh; overflow-y: auto; }
    .modal h2 { font-size: 1.25rem; font-weight: 700; margin-bottom: 16px; color: #111827; }
    @keyframes popIn { from { transform: scale(0.95) translateY(10px); opacity: 0; } to { transform: scale(1) translateY(0); opacity: 1; } }

    input, select, textarea { width: 100%; padding: 8px 12px; margin-bottom: 12px; border-radius: 0.5rem; border: 1px solid #d1d5db; background: #f9fafb; box-sizing: border-box; font-weight: 400; font-size: 0.875rem; outline: none; color: #111827; transition: border-color 0.15s; }
    input:focus, select:focus, textarea:focus { border-color: #4f46e5; }
    label { display: block; font-size: 0.75rem; font-weight: 600; color: #6b7280; margin-bottom: 4px; }
    .chk-group { display:flex; align-items:center; gap:8px; font-size:0.875rem; font-weight:600; margin-bottom:12px; color: #374151; }
    .chk-group input { width:auto; margin:0; }

    .btn { width: 100%; padding: 10px 16px; border: none; border-radius: 0.5rem; font-weight: 600; color: white; cursor: pointer; font-size: 0.875rem; margin-top: 8px; background: var(--primary); transition: background 0.2s; box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .btn:hover { background: #4338ca; }
    .btn-del { background: #fef2f2; color: #dc2626; border: 1px solid #fee2e2; }
    .btn-del:hover { background: #fee2e2; }
    .btn-sub { background: #ffffff; color: #374151; border: 1px solid #d1d5db; box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
    .btn-sub:hover { background: #f9fafb; }

    .tabs { display: flex; margin-bottom: 16px; background: rgba(229, 231, 235, 0.6); border-radius: 0.5rem; padding: 4px; overflow-x: auto; scrollbar-width: none; }
    .tabs::-webkit-scrollbar { display: none; }
    .tab { flex: 1; text-align: center; padding: 8px 12px; border-radius: 0.375rem; font-size: 0.875rem; font-weight: 500; color: #6b7280; cursor: pointer; white-space: nowrap; transition: all 0.2s; }
    .tab.active { background: #ffffff; color: #4f46e5; box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); font-weight: 600; }

    .icon-btn { width: 32px; height: 32px; border-radius: 0.375rem; background: #f3f4f6; display: flex; align-items: center; justify-content: center; color: #4b5563; cursor: pointer; font-size: 14px; margin-left: 8px; border: 1px solid var(--border); transition: 0.2s; }
    .icon-btn:hover { background: #e5e7eb; }

    .chart-box { display: flex; align-items: flex-end; justify-content: space-around; height: 200px; padding-bottom: 8px; margin-top: 16px; border-bottom: 1px solid var(--border); position:relative; }
    .bar-group { display: flex; flex-direction: column; align-items: center; width: 14%; height: 100%; justify-content: flex-end; }
    .bar { width: 90%; border-radius: 0.25rem 0.25rem 0 0; transition: height 1s cubic-bezier(0.34, 1.56, 0.64, 1); min-height: 4px; position:relative; }
    .bar-val { position:absolute; top: -20px; width: 100%; text-align: center; font-weight: 700; font-size: 0.65rem; color: #6b7280; white-space:nowrap; }
    .bar-label { margin-top: 8px; font-weight: 600; font-size: 0.65rem; color: #9ca3af; text-transform: uppercase; letter-spacing: 0.05em; text-align:center; }
    .bar-asset { background: #10b981; }
    .bar-sav { background: #3b82f6; }
    .bar-hsa { background: #14b8a6; }
    .bar-liq { background: #eab308; }
    .bar-ret { background: #8b5cf6; }
    .bar-inv { background: #64748b; }
    .bar-bad { background: #ef4444; }

    .tag-pill { display:block; font-size: 0.55rem; background:#f3f4f6; color:#6b7280; border-radius:0.25rem; padding: 2px 4px; margin-top:4px; font-weight:700; }
    .tag-liq { background:#ecfdf5; color:#10b981; }
    .tag-loc { background:#f5f3ff; color:#8b5cf6; }

    .pie-container { display:flex; justify-content:center; align-items:center; margin: 24px 0; position:relative; }
    .pie-chart { width: 180px; height: 180px; border-radius: 50%; background: conic-gradient(var(--pie-grad)); position: relative; box-shadow: 0 4px 6px rgba(0,0,0,0.05); }
    .pie-hole { width: 110px; height: 110px; background: white; border-radius: 50%; position: absolute; display: flex; align-items: center; justify-content: center; flex-direction: column; }
    .pie-total-label { font-size: 0.65rem; font-weight: 700; color: #9ca3af; text-transform: uppercase; }
    .pie-total-val { font-size: 1.125rem; font-weight: 800; color: #111827; }
    .legend { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; margin-top: 16px; }
    .leg-item { display: flex; align-items: center; font-size: 0.75rem; font-weight: 600; color: #4b5563; background: #f9fafb; border: 1px solid var(--border); padding: 4px 8px; border-radius: 0.375rem; }
    .leg-dot { width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
    """

    def js = """
            const API = "api/";
            const TOKEN = "${state.accessToken}";
            let DATA = null;
            let confirmCallback = null;
            
            function appAlert(msg, type="info") {
                el('alertIcon').innerHTML = type === 'error' ? '&#10060;' : '&#8505;'; 
                el('alertTitle').innerText = type === 'error' ? 'Error' : 'Notice';
                el('alertMsg').innerText = msg;
                el('btnAlertOk').style.display = 'block';
                el('btnAlertYes').style.display = 'none';
                el('btnAlertNo').style.display = 'none';
                el('mAlert').style.display = 'flex';
            }
            
            function appConfirm(msg, cb) {
                el('alertIcon').innerHTML = '&#10067;';
                el('alertTitle').innerText = 'Confirmation';
                el('alertMsg').innerText = msg;
                confirmCallback = cb; 
                el('btnAlertOk').style.display = 'none';
                el('btnAlertYes').style.display = 'block';
                el('btnAlertNo').style.display = 'block';
                el('mAlert').style.display = 'flex';
            }
            
            function handleConfirmYes() {
                if (confirmCallback) confirmCallback();
                closeModals();
            }
            
            function fmt(n) { return parseFloat(n).toLocaleString('en-US', {style:'currency', currency:'USD', maximumFractionDigits:0}); }
            function el(id) { return document.getElementById(id); }
            
            function loadData() { fetch(`\${API}data?access_token=\${TOKEN}`).then(r => r.json()).then(d => { DATA = d; render(); }); }
            
            function getH(val, max) {
                if(val <= 0) return 1;
                let vLog = Math.log10(val + 10);
                let mLog = Math.log10(max + 10);
                return Math.max((vLog / mLog) * 100, 5);
            }
            
            function renderTimeline(currentNw) {
                let hist = DATA.nwHistory || [];
                let nowStr = new Date().toISOString().slice(0, 7);
                if (hist.length === 0 || hist[hist.length-1].date !== nowStr) {
                    hist.push({date: nowStr, val: currentNw});
                }
                let h = "";
                let minVal = Math.min(...hist.map(x=>x.val));
                if(minVal > 0) minVal = 0;
                let maxVal = Math.max(...hist.map(x=>x.val), 100);
                let range = maxVal - minVal;
                hist.forEach(pt => {
                    let hPct = ((pt.val - minVal) / range) * 100;
                    hPct = Math.max(hPct, 5); 
                    let col = pt.val >= 0 ? "linear-gradient(180deg, #10b981 0%, #14b8a6 100%)" : "linear-gradient(180deg, #f87171 0%, #ef4444 100%)";
                    h += `<div style="display:flex; flex-direction:column; align-items:center; width:10%;"><div style="width:8px; height:\${hPct}%; background:\${col}; border-radius:4px;" title="\${fmt(pt.val)}"></div><div style="font-size:8px; margin-top:5px; color:#9ca3af;">\${pt.date.split('-')[1]}</div></div>`;
                });
                el('timelineChart').innerHTML = h;
            }
            
            function renderMiniTimeline(elId, hist, colorStr) {
                let e = el(elId);
                if (!e) return;
                if (!hist || hist.length === 0) {
                    e.innerHTML = '<div style="width:100%; text-align:center; opacity:0.5; font-size:12px; padding:20px;">No history yet. Update accounts to see growth.</div>';
                    return;
                }
                let h = "";
                let minVal = Math.min(...hist.map(x=>x.val));
                if(minVal > 0) minVal = 0;
                let maxVal = Math.max(...hist.map(x=>x.val), 100);
                let range = maxVal - minVal;
                if (range === 0) range = 1;
                hist.forEach(pt => {
                    let hPct = ((pt.val - minVal) / range) * 100;
                    hPct = Math.max(hPct, 5);
                    h += `<div style="display:flex; flex-direction:column; align-items:center; flex:1; margin:0 2px;">
                             <div style="width:100%; max-width:14px; height:\${hPct}%; background:\${colorStr}; border-radius:4px 4px 0 0;" title="\${fmt(pt.val)}"></div>
                             <div style="font-size:7px; margin-top:5px; color:#9ca3af; white-space:nowrap;">\${pt.date.split(' ')[0]}</div>
                          </div>`;
                });
                e.innerHTML = h;
            }
            
            function render() {
                let cash = DATA.checking.reduce((a,b)=>a+b.balance,0);
                let savings = DATA.savings.reduce((a,b)=>a+b.balance,0);
                let totalLiquid = cash + savings;
                let hsa = DATA.totals.hsaBalance;
                let invest = DATA.retirement.reduce((a,b)=>a+b.balance,0);
                let portf = DATA.investments.reduce((a,b)=>a+b.balance,0);
                let badDebt = DATA.creditCards.reduce((a,b)=>a+b.balance,0);
                let secureDebt = DATA.loans.reduce((a,b)=>a+b.balance,0);
                let rews = DATA.rewards.reduce((a,b)=>a+b.balance,0);
                let assets = cash + hsa + invest + savings + portf + rews;
                let liabilities = badDebt + secureDebt;
                let netWorth = assets - liabilities;
                let netWorthNoSec = assets - badDebt;
                
                el('dispNetWorth').innerText = fmt(netWorth);
                el('dispSecDebtTotal').innerText = fmt(secureDebt);
                el('dispNetWorthNoSec').innerText = fmt(netWorthNoSec);
                
                el('valBud').innerText = fmt(cash);
                el('valSav').innerText = fmt(savings);
                el('valHsa').innerText = fmt(hsa);
                el('valLiq').innerText = fmt(totalLiquid);
                el('valBad').innerText = fmt(badDebt);
                el('valRet').innerText = fmt(invest);
                el('valInv').innerText = fmt(portf);
                let max = Math.max(totalLiquid, hsa, badDebt, invest, portf) || 1;
                el('barBud').style.height = getH(cash, max) + '%';
                el('barSav').style.height = getH(savings, max) + '%';
                el('barHsa').style.height = getH(hsa, max) + '%';
                el('barLiq').style.height = getH(totalLiquid, max) + '%';
                el('barBad').style.height = getH(badDebt, max) + '%';
                el('barRet').style.height = getH(invest, max) + '%';
                el('barInv').style.height = getH(portf, max) + '%';
                
                let actualExpenses = DATA.totals.expMonth;
                let planInc = DATA.categories.filter(c => c.type === 'income').reduce((a,b)=>a+b.expected,0);
                let planExp = DATA.categories.filter(c => c.type === 'expense').reduce((a,b)=>a+b.expected,0);
                
                renderLoanList();
                renderPlanList(totalLiquid);
                renderInvList();
                renderChkList(cash);
                renderSavList();
                renderRewList();
                
                let totPlanInc = sumExp('income');
                let actInc = DATA.totals.incMonth;
                let actExp = DATA.totals.expMonth;
                
                // --- Variance Calculation ---
                let incDiff = actInc - totPlanInc;
                let incCol = incDiff >= 0 ? '#10b981' : '#ef4444';
                let incSign = incDiff >= 0 ? '+' : '';
                el('totInc').innerHTML = `Plan: \${fmt(totPlanInc)} <span style="opacity:0.5">|</span> Act: \${fmt(actInc)} <span style="font-weight:700; color:\${incCol}">(\${incSign}\${fmt(incDiff)})</span>`;

                let expPlan = sumExp('expense');
                let expDiff = expPlan - actExp;
                let expCol = expDiff >= 0 ? '#10b981' : '#ef4444';
                let expSign = expDiff >= 0 ? '+' : '';
                el('totExp').innerHTML = `Plan: \${fmt(expPlan)} <span style="opacity:0.5">|</span> Act: \${fmt(actExp)} <span style="font-weight:700; color:\${expCol}">(\${expSign}\${fmt(expDiff)})</span>`;
                
                renderAnalytics();
                renderTimeline(netWorth);
                
                el('dispHsaBal').innerText = fmt(DATA.totals.hsaBalance);
                el('dispCcBal').innerText = fmt(badDebt);
                renderCCList();
                el('dispRetBal').innerText = fmt(invest + portf); 
                renderRetList();
                
                // Dynamic Mini-Timelines (Charts)
                renderMiniTimeline('chartRet', DATA.retHistory, 'linear-gradient(180deg, #ca8a04 0%, #a16207 100%)');
                renderMiniTimeline('chartHsa', DATA.hsaHistory, 'linear-gradient(180deg, #14b8a6 0%, #0f766e 100%)');
                renderMiniTimeline('chartChkSav', DATA.chkSavHistory, 'linear-gradient(180deg, #3b82f6 0%, #2563eb 100%)');
                
                // Render custom segmented expense lists and simple single lists
                renderList('income', 'listIncome'); 
                renderExpenses();
                renderList('hsa_income', 'listHsaIncome'); 
                renderList('hsa_expense', 'listHsaExpense');
                
                // Render HSA Transactions ledger
                let hsaTxs = DATA.history.filter(tx => tx.type && tx.type.includes('hsa'));
                let hHsaTx = "";
                hsaTxs.forEach(tx => {
                    let cat = DATA.categories.find(c => c.id == tx.catId);
                    
                    // Fallback names in case they delete a category but still have history
                    let catName = cat ? cat.name : 'Unknown HSA Item';
                    if (!cat && tx.catId === 'system-hsa-exp') catName = 'HSA Medical Expense';
                    if (!cat && tx.catId === 'system-hsa-inc') catName = 'HSA Contribution';

                    let isInc = tx.type === 'hsa_income';
                    let col = isInc ? 'c-teal' : 'c-red';
                    hHsaTx += `<div class="item-row" onclick="editTx('\${tx.id}')"><div class="item-left"><div class="item-icon" style="background:#f0fdfa; color:#14b8a6;"><i class="fas \${isInc?'fa-hand-holding-medical':'fa-file-medical'}"></i></div><div><div class="item-name">\${catName}</div><div class="item-sub">\${tx.desc} &bull; \${tx.date}</div></div></div><div class="item-right \${col}">\${isInc?'+':'-'}\${fmt(tx.amount)}</div></div>`;
                });
                el('listHsaTx').innerHTML = hHsaTx || '<div style="text-align:center; opacity:0.5; padding:12px;">No HSA transactions recorded this month.</div>';

                let hTx = "";
                DATA.history.forEach(tx => { 
                    let cat = DATA.categories.find(c => c.id == tx.catId); 
                    
                    // Fallback names for old system transactions
                    let catName = cat ? cat.name : 'Unknown';
                    if (!cat) {
                        if (tx.catId === 'system-interest') catName = 'Bank Interest';
                        if (tx.catId === 'system-other') catName = 'Other Income';
                        if (tx.catId === 'system-hsa-exp') catName = 'HSA Medical Expense';
                        if (tx.catId === 'system-hsa-inc') catName = 'HSA Contribution';
                    }

                    let fundInfo = "";
                    if (tx.fundId) {
                        let fChk = DATA.checking.find(x => x.id === tx.fundId);
                        let fRew = DATA.rewards.find(x => x.id === tx.fundId);
                        if(fRew) fundInfo = `<span class="tag-pill tag-loc" style="display:inline-block; margin-left:6px;"><i class="fas fa-gift"></i> \${fRew.name}</span>`;
                        else if(fChk) fundInfo = `<span class="tag-pill" style="display:inline-block; margin-left:6px;"><i class="fas fa-money-check"></i> \${fChk.name}</span>`;
                    }
                    let isInc = tx.type.includes('income'); 
                    let col = isInc ? 'c-green' : 'c-red'; 
                    let wantBadge = tx.want ? '<i class="fas fa-gem" style="color:#ef4444; font-size:10px; margin-left:6px;"></i>' : '';
                    let userBadge = tx.txUser && tx.txUser !== "Unknown" ? `<span class="tag-pill" style="display:inline-block; margin-left:6px;">\${tx.txUser}</span>` : '';
                    if(tx.type.includes('hsa')) col = isInc ? 'c-teal' : 'c-red'; 
                    hTx += `<div class="item-row" onclick="editTx('\${tx.id}')"><div class="item-left"><div class="item-icon" style="background:#f3f4f6; color:#6b7280;"><i class="fas \${isInc?'fa-arrow-down':'fa-arrow-up'}"></i></div><div><div class="item-name">\${catName} \${wantBadge}</div><div class="item-sub">\${tx.desc} &bull; \${tx.date} \${userBadge} \${fundInfo}</div></div></div><div class="item-right \${col}">\${isInc?'+':'-'}\${fmt(tx.amount)}</div></div>`; 
                });
                el('listTx').innerHTML = hTx || '<div style="text-align:center; opacity:0.5; padding:20px;">No transactions for current month.</div>';
            }
            
            function sumExp(type) { return DATA.categories.filter(c=>c.type===type).reduce((a,b)=>a+b.expected,0); }
            
            function renderAnalytics() {
                el('dispNoSpend').innerText = DATA.totals.noSpendDays;
                el('dispSpendDays').innerText = DATA.totals.spendDays;
            
                let monthPrefix = new Date().toISOString().slice(0, 7);
                let txs = DATA.history.filter(t => t.date.startsWith(monthPrefix));
                let inc = txs.filter(t => t.type === 'income').reduce((a,b)=>a+b.amount,0);
                let expTxs = txs.filter(t => t.type === 'expense');
                let exp = expTxs.reduce((a,b)=>a+b.amount,0);
                let net = inc - exp;
                
                el('pieNet').innerText = fmt(net);
                let flowTotal = inc + exp;
                if(flowTotal > 0) {
                    let pInc = (inc / flowTotal) * 100;
                    let pExp = (exp / flowTotal) * 100;
                    el('pieFlow').style.background = `conic-gradient(#10b981 0deg \${pInc * 3.6}deg, #ef4444 \${pInc * 3.6}deg 360deg)`;
                    el('legFlow').innerHTML = `<div class="leg-item"><div class="leg-dot" style="background:#10b981"></div>Income (\${Math.round(pInc)}%)</div><div class="leg-item"><div class="leg-dot" style="background:#ef4444"></div>Expense (\${Math.round(pExp)}%)</div>`;
                }
                
                let wants = expTxs.filter(t => t.want).reduce((a,b)=>a+b.amount,0);
                let needs = expTxs.filter(t => !t.want).reduce((a,b)=>a+b.amount,0);
                el('anaWants').innerText = fmt(wants);
                el('anaNeeds').innerText = fmt(needs);
                let grouping = {}; let total = 0;
                expTxs.forEach(t => { let cat = DATA.categories.find(c => c.id == t.catId); let name = cat ? cat.name : "Other"; if(!grouping[name]) grouping[name] = 0; grouping[name] += t.amount; total += t.amount; });
                el('pieTotal').innerText = fmt(total);

                if(total > 0) { 
                    let colors = ['#4f46e5', '#3b82f6', '#8b5cf6', '#f59e0b', '#10b981', '#ec4899', '#14b8a6', '#64748b'];
                    let gradientStr = ""; let currentDeg = 0; let legendHtml = ""; let i = 0;
                    for (const [name, val] of Object.entries(grouping)) { let pct = (val / total) * 100;
                    let deg = (val / total) * 360; let color = colors[i % colors.length];
                    gradientStr += `\${color} \${currentDeg}deg \${currentDeg + deg}deg, `; currentDeg += deg; legendHtml += `<div class="leg-item"><div class="leg-dot" style="background:\${color}"></div>\${name} (\${Math.round(pct)}%)</div>`; i++; }
                    el('pieChart').style.background = `conic-gradient(\${gradientStr.slice(0, -2)})`;
                    el('pieLegend').innerHTML = legendHtml;
                } else {
                    el('pieChart').style.background = '#e5e7eb'; el('pieLegend').innerHTML = '<div style="width:100%;text-align:center;color:#9ca3af;font-size:12px;">No spending</div>';
                }
                
                // Leaderboard Logic
                let userStats = {};
                DATA.users.forEach(u => { if(u) userStats[u] = { spent: 0, count: 0 }; });
                expTxs.forEach(t => {
                    let u = t.txUser || 'Unknown';
                    if (u === 'System' || u === 'Auto' || u === 'Unknown') return; // Ignore automated system transactions
                    if (!userStats[u]) userStats[u] = { spent: 0, count: 0 };
                    userStats[u].spent += t.amount;
                    userStats[u].count += 1;
                });
                let leadHtml = "";
                let maxSpender = Object.keys(userStats).reduce((a, b) => userStats[a].spent > userStats[b].spent ? a : b, Object.keys(userStats)[0]);
                for (const [u, stats] of Object.entries(userStats)) {
                    if (u === 'Unknown' && stats.count === 0) continue;
                    let isLead = (u === maxSpender && stats.spent > 0) ? '<i class="fas fa-crown" style="color:#f59e0b; margin-left:6px;" title="Highest Spender"></i>' : '';
                    leadHtml += `<div class="item-row"><div class="item-left"><div class="item-icon" style="background:#f3f4f6; color:#4f46e5;"><i class="fas fa-user"></i></div><div><div class="item-name">\${u}\${isLead}</div><div class="item-sub">\${stats.count} Transactions</div></div></div><div class="item-right c-red">\${fmt(stats.spent)}</div></div>`;
                }
                el('listLeaderboard').innerHTML = leadHtml || '<div style="text-align:center; opacity:0.5; padding:12px;">No household data</div>';
            }
            
            function renderChkList(totalCash) {
                let html = "";
                DATA.checking.forEach(b => {
                    html += `<div class="item-row"><div class="item-left" onclick="editChk('\${b.id}')"><div class="item-icon" style="background:#e0e7ff; color:#4f46e5;"><i class="fas fa-money-check"></i></div><div><div class="item-name">\${b.name}</div><div class="item-sub">Checking Account</div></div></div><div style="display:flex; align-items:center;"><div class="item-right" style="margin-right:12px;">\${fmt(b.balance)}</div></div></div>`;
                });
                el('listChk').innerHTML = html || '<div style="text-align:center;opacity:0.5;padding:12px;">No checking accounts.</div>';
                
                let remExp = 0;
                DATA.categories.filter(c=>c.type==='expense').forEach(c => {
                    let left = c.expected - c.actual;
                    if(left > 0) remExp += left;
                });
                let safeSpend = totalCash - remExp;

                el('dispChkTotal').innerText = fmt(totalCash);
                el('dispRemBudget').innerText = fmt(remExp);
                el('dispSafeSpend').innerText = fmt(safeSpend);
            }

            function renderSavList() {
                let html = "";
                DATA.savings.forEach(b => {
                    html += `<div class="item-row"><div class="item-left" onclick="editSav('\${b.id}')"><div class="item-icon" style="background:#eff6ff; color:#3b82f6;"><i class="fas fa-piggy-bank"></i></div><div><div class="item-name">\${b.name}</div><div class="item-sub">Savings</div></div></div><div style="display:flex; align-items:center;"><div class="item-right" style="margin-right:12px;">\${fmt(b.balance)}</div><div class="icon-btn" onclick="openSavTx('\${b.id}', '\${b.name}')"><i class="fas fa-plus"></i></div><div class="icon-btn" onclick="openSavTx('\${b.id}', '\${b.name}')"><i class="fas fa-minus"></i></div></div></div>`;
                });
                el('listSav').innerHTML = html || '<div style="text-align:center;opacity:0.5;padding:12px;">No savings accounts.</div>';
            }
            
            function renderRewList() {
                let html = "";
                let tot = 0;
                DATA.rewards.forEach(r => {
                    tot += r.balance;
                    html += `<div class="item-row" onclick="editRew('\${r.id}')"><div class="item-left"><div class="item-icon" style="background:#f3e8ff; color:#9333ea;"><i class="fas fa-gift"></i></div><div><div class="item-name">\${r.name}</div><div class="item-sub">Cash Back / Points</div></div></div><div class="item-right c-green">\${fmt(r.balance)}</div></div>`;
                });
                el('listRew').innerHTML = html || '<div style="text-align:center;opacity:0.5;padding:12px;">No rewards added yet.</div>';
                if(el('dispRewBal')) el('dispRewBal').innerText = fmt(tot);
            }
            
            function renderInvList() {
                let h="";
                DATA.investments.forEach(i=>{
                    h+=`<div class="item-row" onclick="editInv('\${i.id}')"><div class="item-left"><div class="item-icon" style="background:#f1f5f9;color:#475569"><i class="fas fa-chart-line"></i></div><div><div class="item-name">\${i.name}</div><div class="item-sub">Portfolio</div></div></div><div class="item-right c-green">\${fmt(i.balance)}</div></div>`;
                });
                el('listInv').innerHTML = h || '<div style="text-align:center;opacity:0.5;padding:12px;">No investments added.</div>';
            }
            function renderLoanList(){
                let h="";
                DATA.loans.forEach(l=>{
                    let paid = l.orig > 0 ? l.orig - l.balance : 0;
                    let p = l.orig > 0 ? (paid / l.orig) * 100 : 0;
                    p = Math.min(Math.max(p,0),100);
                    let payDate = ""; 
                    if(l.payment && l.payment > 0 && l.balance > 0) {
                        let months = l.balance / l.payment;
                        let d = new Date();
                        d.setMonth(d.getMonth() + Math.ceil(months));
                        payDate = d.toLocaleDateString('en-US', {month:'short', year:'numeric'});
                    }
                    h+=`<div class="item-row" onclick="editLoan('\${l.id}')" style="display:block; padding:16px 0;"><div style="display:flex; justify-content:space-between; margin-bottom:6px;"><div class="item-name">\${l.name}</div><div style="font-size:0.75rem; font-weight:600; color:#9ca3af;">\${Math.round(p)}% Paid Off</div></div><div class="pg-track" style="height:8px; background:#f3f4f6; margin-bottom:10px;"><div class="pg-fill bg-green" style="width:\${p}%;"></div></div><div style="display:flex; justify-content:space-between; font-size:0.75rem; font-weight:700;"><div>Orig: \${fmt(l.orig)}</div><div style="color:#ef4444;">Owe: \${fmt(l.balance)}</div></div><div style="text-align:right; font-size:0.65rem; color:#4f46e5; margin-top:6px; font-weight:700;">\${l.payment > 0 ? '<i class="fas fa-calendar"></i> Free: ' + payDate : ''}</div></div>`;
                });
                el('listLoans').innerHTML=h||'<div style="text-align:center;opacity:0.5;padding:12px;">No loans.</div>';
            }
            function renderPlanList(liquid) {
                let h="";
                 DATA.planned.forEach(p => {
                    let cost = p.cost || 0;
                    let saved = p.saved || 0;
                    let pct = cost > 0 ? (saved / cost) * 100 : 0;
                    pct = Math.min(Math.max(pct,0),100);
                    let diff = new Date().getTime() - p.ts;
                    let hours = Math.floor(diff / (1000 * 60 * 60));
                    let timeStr = hours < 1 ? "Just added" : `Added \${hours}h ago`;
                    let barColor = pct >= 100 ? "bg-green" : "bg-blue";
                    h+=`<div class="item-row" onclick="editPlan('\${p.id}')" style="display:block; padding:16px 0;"><div style="display:flex; justify-content:space-between;"><div class="item-name">\${p.name}</div><div class="item-sub" style="font-weight:600;">\${fmt(saved)} / \${fmt(cost)}</div></div><div class="pg-track" style="height:8px; margin-top:8px;"><div class="pg-fill \${barColor}" style="width:\${pct}%"></div></div><div style="text-align:right; margin-top:6px; font-size:0.65rem; color:#9ca3af; font-weight:600;">\${timeStr}</div></div>`;
                });
                el('listPlan').innerHTML = h || '<div style="text-align:center; opacity:0.5; padding:12px;">No planned purchases.</div>';
            }
            function renderCCList(){let h="";DATA.creditCards.forEach(c=>{let u=c.limit>0?(c.balance/c.limit)*100:0;let cl=u>70?'bg-red':(u>30?'bg-orange':'bg-green');let payoffStr="";let intStr="";let apr=c.apr||0;if(c.balance>0&&c.payment>0&&apr>0){let r=(apr/100)/12;let minPay=c.balance*r;if(c.payment<=minPay){payoffStr=`<span style="color:#ef4444;">Payment too low! (Interest > Pay)</span>`}else{let n=-Math.log(1-(r*c.balance)/c.payment)/Math.log(1+r);let totalPay=n*c.payment;let totalInt=totalPay-c.balance;let d=new Date();d.setMonth(d.getMonth()+Math.ceil(n));payoffStr=`<i class="fas fa-calendar-check"></i> Debt Free: \${d.toLocaleDateString('en-US',{month:'short',year:'numeric'})}`;intStr=`Est. Interest: <span style="color:#ef4444">\${fmt(totalInt)}</span>`}}else if(c.balance>0){payoffStr="Set Pay & APR to see forecast"}else{payoffStr=`<span style="color:#10b981">Paid Off! Good job.</span>`}h+=`<div class="item-row" onclick="editCC('\${c.id}')" style="display:block; padding:16px 0;"><div style="display:flex; justify-content:space-between; align-items:center;"><div class="item-left"><div class="item-icon" style="background:#fdf2f8;color:#db2777"><i class="fas fa-credit-card"></i></div><div><div class="item-name">\${c.name}</div><div class="item-sub">Limit: \${fmt(c.limit)} \${apr>0?'| APR: '+apr+'%':''}</div></div></div><div class="item-right"><div>\${fmt(c.balance)}</div><div style="font-size:0.65rem;color:#9ca3af">\${Math.round(u)}% Util</div></div></div><div class="pg-track" style="margin-top:12px;"><div class="pg-fill \${cl}" style="width:\${u}%"></div></div><div style="display:flex; justify-content:space-between; margin-top:8px; font-size:0.75rem; font-weight:600; color:#4b5563;"><div>\${payoffStr}</div><div>\${intStr}</div></div></div>`});el('listCC').innerHTML=h||'<div style="text-align:center;opacity:0.5;padding:12px;">No cards.</div>'}
            function renderRetList(){let h="";DATA.retirement.forEach(r=>{h+=`<div class="item-row" onclick="editRet('\${r.id}')"><div class="item-left"><div class="item-icon" style="background:#fefce8;color:#ca8a04"><i class="fas fa-piggy-bank"></i></div><div><div class="item-name">\${r.name}</div><div class="item-sub">Investments</div></div></div><div class="item-right c-green">\${fmt(r.balance)}</div></div>`});el('listRet').innerHTML=h||'<div style="text-align:center;opacity:0.5;padding:12px;">No accounts.</div>'}
            
            function buildCatHtml(list, t) {
                let h="";
                let isInc=t.includes('income');
                let isHsa=t.includes('hsa');
                let ic=isInc?'fa-money-bill-wave':'fa-shopping-cart';
                if(isHsa)ic=isInc?'fa-hand-holding-medical':'fa-file-medical';
                let bg=isInc?'#ecfdf5':'#fef2f2';
                let c=isInc?'#10b981':'#ef4444';
                if(isHsa&&isInc){bg='#f0fdfa';c='#14b8a6'}
                
                list.forEach(x=>{
                    let w=0, b='', diff=0, paidIcon = '', vText = "";
                    if(isInc) {
                        diff = x.actual - x.expected; 
                        w = x.expected > 0 ? (x.actual/x.expected)*100 : 0;
                        b = isHsa ? 'bg-teal' : 'bg-green';
                        let sign = diff >= 0 ? '+' : '';
                        let vCol = diff >= 0 ? '#10b981' : '#ef4444'; 
                        vText = `<span style="font-size:0.75rem; font-weight:700; color:\${vCol}; margin-left:6px;">(\${sign}\${fmt(diff)})</span>`;
                    } else {
                        diff = x.expected - x.actual; 
                        w = x.expected > 0 ? (x.actual / x.expected) * 100 : 0;
                        if(diff > 0) {
                            b = 'bg-green';
                        } else if (diff < 0) {
                            w = 100; b = 'bg-red';
                        } else {
                            b = 'bg-transparent';
                        }
                        
                        if (x.isContinuous) {
                            if (diff < 0) {
                                paidIcon = '<i class="fas fa-times-circle" style="color:#ef4444; margin-left:6px; font-size:14px;" title="Over Budget!"></i>';
                            } else if (diff === 0 && x.expected > 0) {
                                paidIcon = '<i class="fas fa-check-circle" style="color:#10b981; margin-left:6px; font-size:14px;" title="Depleted"></i>';
                            } else {
                                paidIcon = '<i class="fas fa-cash-register" style="color:#6b7280; margin-left:6px; font-size:14px;" title="Continuous Expense"></i>';
                            }
                        } else {
                            if (diff < 0) {
                                paidIcon = '<i class="fas fa-times-circle" style="color:#ef4444; margin-left:6px; font-size:14px;" title="Over Budget!"></i>';
                            } else if ((diff === 0 && x.expected > 0) || x.isPaid) {
                                paidIcon = '<i class="fas fa-check-circle" style="color:#10b981; margin-left:6px; font-size:14px;" title="Funds Depleted / Paid"></i>';
                            }
                        }
                        
                        let vCol = diff >= 0 ? '#10b981' : '#ef4444';
                        let label = diff >= 0 ? 'Left' : 'Over';
                        vText = `<span style="font-size:0.75rem; font-weight:700; color:\${vCol}; margin-left:6px;">(\${fmt(Math.abs(diff))} \${label})</span>`;
                    }
                    w = Math.min(Math.max(w,0),100);
                    
                    let s = `Plan: \${fmt(x.expected)}`;
                    if(x.autoPay) s += ' (Auto)';
                    
                    h+=`<div class="item-row" onclick="openCatOpts('\${t}','\${x.id}')">
                        <div class="item-left">
                            <div class="item-icon" style="background:\${bg};color:\${c}"><i class="fas \${ic}"></i></div>
                            <div><div class="item-name">\${x.name}\${paidIcon}</div><div class="item-sub">\${s}</div></div>
                        </div>
                        <div class="item-right">
                            <div>\${fmt(x.actual)}</div>
                            \${vText}
                        </div>
                    </div>
                    <div class="pg-track" style="margin-top:-6px; margin-bottom: 12px;"><div class="pg-fill \${b}" style="width:\${w}%"></div></div>`;
                });
                return h || '<div style="text-align:center;opacity:0.5;padding:12px;font-size:0.75rem;">No items in this category.</div>';
            }
            
            function renderList(t, e) {
                let items = DATA.categories.filter(x => x.type === t);
                el(e).innerHTML = buildCatHtml(items, t);
            }

            function renderExpenses() {
                let exps = DATA.categories.filter(x => x.type === 'expense');
                let cont = [], paid = [], unpaid = [];
                
                exps.forEach(x => {
                    let diff = x.expected - x.actual;
                    if (x.isContinuous) {
                        cont.push(x);
                    } else if (x.isPaid || (x.expected > 0 && diff <= 0)) {
                        paid.push(x);
                    } else {
                        unpaid.push(x);
                    }
                });
                
                el('listExpenseCont').innerHTML = buildCatHtml(cont, 'expense');
                el('listExpenseUnpaid').innerHTML = buildCatHtml(unpaid, 'expense');
                el('listExpensePaid').innerHTML = buildCatHtml(paid, 'expense');
            }

            function logHsaTx(isExp) {
                let targetType = isExp ? 'hsa_expense' : 'hsa_income';
                let hsaCats = DATA.categories.filter(c => c.type === targetType);
                if (hsaCats.length === 0) {
                    appAlert("Please create an HSA category first by clicking the '+' next to Medical Expense Categories.", "error");
                    return;
                }
                let catId = hsaCats[0].id;
                editTx(null, { catId: catId, desc: isExp ? "Medical Purchase" : "HSA Contribution", amt: "" });
            }

            function openCatOpts(t, id) {
                el('catOptId').value = id;
                el('catOptType').value = t;
                el('catOptTitle').innerText = "Category Options";
                el('mCatOpts').style.display = "flex";
            }
            function openCatEdit() {
                let id = el('catOptId').value;
                let t = el('catOptType').value;
                closeModals();
                editCat(t, id);
            }
            function openCatHistory() {
                let id = el('catOptId').value;
                let txs = DATA.history.filter(tx => tx.catId === id);
                let h = "";
                txs.forEach(tx => {
                    let isInc = tx.type.includes('income'); 
                    let col = isInc ? 'c-green' : 'c-red';
                    let userBadge = tx.txUser && tx.txUser !== "Unknown" ? `<span class="tag-pill" style="display:inline-block; margin-left:6px;">\${tx.txUser}</span>` : '';
                    h += `<div class="item-row"><div class="item-left"><div class="item-name">\${tx.desc}</div><div class="item-sub">\${tx.date} \${userBadge}</div></div><div class="item-right \${col}">\${fmt(tx.amount)}</div></div>`;
                });
                el('catTxList').innerHTML = h || '<div style="text-align:center; opacity:0.5; padding:12px;">No history for this category in the active window.</div>';
                closeModals();
                el('mCatTx').style.display = "flex";
            }
            function editTx(id, prefill) { 
                if(DATA.categories.length===0) { appAlert("Add categories first!", "error"); return; } 
                el('txId').value = id || ""; 
                el('txTitle').innerText = id ? "Edit Transaction" : "Add Transaction"; 
                
                let uSel = el('txUser'); 
                uSel.innerHTML = "<option value='Auto'>Auto/Other</option>";
                DATA.users.forEach(u => { if(u) uSel.innerHTML += `<option value="\${u}">\${u}</option>`; });
                
                let sel=el('txCatId'); 
                sel.innerHTML=""; 
                let g={Main:[],HSA:[]}; 
                DATA.categories.forEach(c=>{ let k=c.type.includes('hsa')?'HSA':'Main'; let s=c.type.includes('income')?'+':'-'; g[k].push(`<option value="\${c.id}" data-plan="\${c.expected}">(\${s}) \${c.name}</option>`); });
                if(g.Main.length) sel.innerHTML+=`<optgroup label="Main">\${g.Main.join('')}</optgroup>`; 
                if(g.HSA.length) sel.innerHTML+=`<optgroup label="HSA">\${g.HSA.join('')}</optgroup>`; 

                let fSel = el('txFundId');
                fSel.innerHTML = `<option value="hsa-account" style="font-weight:bold; color:#0f766e;">HSA Account</option>`;
                DATA.checking.forEach(c => {
                    fSel.innerHTML += `<option value="\${c.id}">Checking: \${c.name}</option>`;
                });
                if(DATA.rewards && DATA.rewards.length > 0) {
                    fSel.innerHTML += `<optgroup label="Rewards / Cash Back">`;
                    DATA.rewards.forEach(r => {
                        fSel.innerHTML += `<option value="\${r.id}">Rewards: \${r.name}</option>`;
                    });
                    fSel.innerHTML += `</optgroup>`;
                }
                
                el('txCatId').onchange = function() {
                    let selOpt = this.options[this.selectedIndex];
                    if (!selOpt) return;
                    let isHsa = selOpt.parentNode.label === "HSA";
                    if(isHsa) {
                        el('txFundId').value = "hsa-account";
                        el('txFundId').disabled = true;
                    } else {
                        if(el('txFundId').value === "hsa-account") {
                            el('txFundId').value = DATA.checking.length > 0 ? DATA.checking[0].id : "";
                        }
                        el('txFundId').disabled = false;
                    }
                };
                
                if(id){ 
                    let tx = DATA.history.find(t=>t.id===id); 
                    if(tx){ 
                        el('txCatId').value=tx.catId; 
                        el('txWant').checked=tx.want; 
                        el('txDesc').value=tx.desc; 
                        el('txAmt').value=tx.amount; 
                        el('txDate').value=tx.date; 
                        if (tx.txUser) el('txUser').value = tx.txUser;
                        if (tx.fundId) el('txFundId').value = tx.fundId;
                        el('btnDelTx').style.display='block'; 
                    }
                } else{ 
                    if(prefill) { 
                        el('txDesc').value=prefill.desc || ""; 
                        el('txAmt').value=prefill.amt || ""; 
                        if(prefill.catId) el('txCatId').value=prefill.catId; 
                        el('txWant').checked=false; 
                    } else { 
                        el('txDesc').value=""; 
                        el('txWant').checked=false; 
                        el('txAmt').value=""; 
                    } 
                    el('txDate').value=new Date().toISOString().split('T')[0]; 
                    el('btnDelTx').style.display='none'; 
                } 
                setTimeout(() => el('txCatId').onchange(), 10);
                el('mTx').style.display='flex'; 
            }
            function editCat(t,i){ 
                el('catType').value=t; el('catId').value=i||""; 
                el('catAutoPaySec').style.display=(t==='income' || t==='hsa_income' || t==='expense')?'block':'none'; 
                el('catTitle').innerText=i?"Edit":"Add"; 
                if(i){ 
                    let c=DATA.categories.find(x=>x.id===i); 
                    el('catName').value=c.name; 
                    el('catPlan').value=c.expected; 
                    el('catIsPaid').checked=c.isPaid===true;
                    el('catContinuous').checked=c.isContinuous===true;
                    el('btnDelCat').style.display='block'; 
                    el('catAutoPay').checked=c.autoPay; 
                    el('catFixed').checked=c.isFixed; 
                    el('catFreq').value=c.payFreq||'weekly'; 
                    el('catNextPay').value=c.nextPayDate||''; 
                } else{ 
                    el('catName').value=""; 
                    el('catPlan').value=""; 
                    el('catIsPaid').checked=false;
                    el('catContinuous').checked=false;
                    el('btnDelCat').style.display='none'; 
                } 
                el('mCat').style.display='flex'; 
            }
            function editChk(i){ el('chkId').value=i||""; el('chkTitle').innerText=i?"Edit":"Add Checking"; if(i){ let c=DATA.checking.find(x=>x.id===i); el('chkName').value=c.name; el('chkBal').value=c.balance; el('btnDelChk').style.display='block'; } else{ el('chkName').value=""; el('chkBal').value=""; el('btnDelChk').style.display='none'; } el('mChk').style.display='flex'; }
            function editCC(i){ el('ccId').value=i||""; el('ccTitle').innerText=i?"Edit":"Add"; if(i){ let c=DATA.creditCards.find(x=>x.id===i); el('ccName').value=c.name; el('ccLimit').value=c.limit; el('ccBal').value=c.balance; el('ccPay').value=c.payment||""; el('ccApr').value=c.apr||""; el('btnDelCC').style.display='block'; } else{ el('ccName').value=""; el('ccLimit').value=""; el('ccBal').value=""; el('ccPay').value=""; el('ccApr').value=""; el('btnDelCC').style.display='none'; } el('mCC').style.display='flex'; }
            function editRet(i){ if(!i && DATA.retirement.length>=2) { appAlert("Max 2", "error"); return; } el('retId').value=i||""; el('retTitle').innerText=i?"Edit":"Add"; if(i){ let c=DATA.retirement.find(x=>x.id===i); el('retName').value=c.name; el('retBal').value=c.balance; el('btnDelRet').style.display='block'; } else{ el('retName').value=""; el('retBal').value=""; el('btnDelRet').style.display='none'; } el('mRet').style.display='flex'; }
            function editLoan(i){ el('loanId').value=i||""; el('loanTitle').innerText=i?"Edit":"Add"; if(i){ let c=DATA.loans.find(x=>x.id===i); el('loanName').value=c.name; el('loanOrig').value=c.orig; el('loanBal').value=c.balance; el('loanPay').value=c.payment||""; el('btnDelLoan').style.display='block'; } else{ el('loanName').value=""; el('loanOrig').value=""; el('loanBal').value=""; el('loanPay').value=""; el('btnDelLoan').style.display='none'; } el('mLoan').style.display='flex'; }
            function editSav(i){ el('savId').value=i||""; el('savTitle').innerText=i?"Edit":"Add"; if(i){ let c=DATA.savings.find(x=>x.id===i); el('savName').value=c.name; el('savBal').value=c.balance; el('btnDelSav').style.display='block'; } else{ el('savName').value=""; el('savBal').value=""; el('btnDelSav').style.display='none'; } el('mSav').style.display='flex'; }
            function editPlan(i){ el('planId').value=i||""; el('planTitle').innerText=i?"Edit":"Add Item"; if(i){ let c=DATA.planned.find(x=>x.id===i); el('planName').value=c.name; el('planCost').value=c.cost; el('planSaved').value=c.saved||0; el('planPriority').value=c.priority||""; el('btnDelPlan').style.display='block'; } else { el('planName').value=""; el('planCost').value=""; el('planSaved').value=""; el('planPriority').value=""; el('btnDelPlan').style.display='none'; } el('mPlan').style.display='flex'; }
            function editInv(i){ el('invId').value=i||""; el('invTitle').innerText=i?"Edit":"Add Investment"; if(i){ let c=DATA.investments.find(x=>x.id===i); el('invName').value=c.name; el('invBal').value=c.balance; el('btnDelInv').style.display='block'; } else { el('invName').value=""; el('invBal').value=""; el('btnDelInv').style.display='none'; } el('mInv').style.display='flex'; }
            function editRew(i){ el('rewId').value=i||""; el('rewTitle').innerText=i?"Edit Rewards":"Add Rewards"; if(i){ let c=DATA.rewards.find(x=>x.id===i); el('rewName').value=c.name; el('rewBal').value=c.balance; el('btnDelRew').style.display='block'; } else{ el('rewName').value=""; el('rewBal').value=""; el('btnDelRew').style.display='none'; } el('mRew').style.display='flex'; }
            
            function openSavTx(id, name) { el('savTxId').value=id; el('savTxName').innerText=name; el('savTxAmt').value=""; el('mSavTx').style.display='flex'; }
            function saveTx() { let p={id:el('txId').value, catId:el('txCatId').value, fundId:el('txFundId').value, txUser:el('txUser').value, want:el('txWant').checked, desc:el('txDesc').value, amt:el('txAmt').value, date:el('txDate').value}; fetch(`\${API}tx/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveCat() { 
                let p={
                    id:el('catId').value, type:el('catType').value, name:encodeURIComponent(el('catName').value), 
                    plan:el('catPlan').value, isPaid:el('catIsPaid').checked, 
                    auto:el('catAutoPay').checked, isFixed:el('catFixed').checked, 
                    isCont:el('catContinuous').checked,
                    freq:el('catFreq').value, next:el('catNextPay').value
                }; 
                if(!p.name) { appAlert("Name req", "error"); return; } 
                fetch(`\${API}cat/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); 
            }
            function saveChk() { let p={id:el('chkId').value, name:encodeURIComponent(el('chkName').value), bal:el('chkBal').value}; fetch(`\${API}chk/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveCC() { let p={id:el('ccId').value, name:encodeURIComponent(el('ccName').value), limit:el('ccLimit').value, bal:el('ccBal').value, pay:el('ccPay').value, apr:el('ccApr').value}; fetch(`\${API}cc/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveRet() { let p={id:el('retId').value, name:encodeURIComponent(el('retName').value), bal:el('retBal').value}; fetch(`\${API}ret/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveLoan() { let p={id:el('loanId').value, name:encodeURIComponent(el('loanName').value), orig:el('loanOrig').value, bal:el('loanBal').value, pay:el('loanPay').value}; fetch(`\${API}loan/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveSav() { let p={id:el('savId').value, name:encodeURIComponent(el('savName').value), bal:el('savBal').value}; fetch(`\${API}sav/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function savePlan() { let p={id:el('planId').value, name:encodeURIComponent(el('planName').value), cost:el('planCost').value, saved:el('planSaved').value, priority:el('planPriority').value}; fetch(`\${API}plan/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveInv() { let p={id:el('invId').value, name:encodeURIComponent(el('invName').value), bal:el('invBal').value}; fetch(`\${API}inv/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function saveRew() { let p={id:el('rewId').value, name:encodeURIComponent(el('rewName').value), bal:el('rewBal').value}; fetch(`\${API}rew/save?access_token=\${TOKEN}&`+new URLSearchParams(p)).then(r=>r.json()).then(()=>{closeModals();loadData();}); }
            function savTx(t){let a=el('savTxAmt').value;if(!a)return;let r="false",d="Manual Update";if(t==='sub')a=-Math.abs(a);else if(t==='interest'){a=Math.abs(a);r="true";d="Bank Interest: "+el('savTxName').innerText}fetch(`\${API}sav/tx?access_token=\${TOKEN}&id=\${el('savTxId').value}&amt=\${a}&track=\${r}&desc=\${encodeURIComponent(d)}`).then(r=>r.json()).then(()=>{closeModals();loadData()})}
            function saveSettings() { 
                let u1 = encodeURIComponent(el('setU1').value);
                let u2 = encodeURIComponent(el('setU2').value);
                let u3 = encodeURIComponent(el('setU3').value);
                fetch(`\${API}config/set?access_token=\${TOKEN}&start=\${el('setStart').value}&hsaStart=\${el('setHsaStart').value}&u1=\${u1}&u2=\${u2}&u3=\${u3}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); 
            }
            function backupData() { window.open(`\${API}backup?access_token=\${TOKEN}`, '_blank'); }
            function delTx() { appConfirm("Delete this transaction permanently?", () => { fetch(`\${API}tx/del?access_token=\${TOKEN}&id=\${el('txId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delCat() { appConfirm("Delete this category? This will not delete historical transactions.", () => { fetch(`\${API}cat/del?access_token=\${TOKEN}&id=\${el('catId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delChk() { appConfirm("Delete this checking account?", () => { fetch(`\${API}chk/del?access_token=\${TOKEN}&id=\${el('chkId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delCC() { appConfirm("Delete this credit card?", () => { fetch(`\${API}cc/del?access_token=\${TOKEN}&id=\${el('ccId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delRet() { appConfirm("Delete this retirement account?", () => { fetch(`\${API}ret/del?access_token=\${TOKEN}&id=\${el('retId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delLoan() { appConfirm("Delete this loan?", () => { fetch(`\${API}loan/del?access_token=\${TOKEN}&id=\${el('loanId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delSav() { appConfirm("Delete this savings account?", () => { fetch(`\${API}sav/del?access_token=\${TOKEN}&id=\${el('savId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delPlan() { appConfirm("Delete this sinking fund goal?", () => { fetch(`\${API}plan/del?access_token=\${TOKEN}&id=\${el('planId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delInv() { appConfirm("Delete this investment portfolio?", () => { fetch(`\${API}inv/del?access_token=\${TOKEN}&id=\${el('invId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function delRew() { appConfirm("Delete this rewards account?", () => { fetch(`\${API}rew/del?access_token=\${TOKEN}&id=\${el('rewId').value}`).then(r=>r.json()).then(()=>{closeModals();loadData();}); }); }
            function openSettings() { 
                el('setStart').value=DATA.totals.startingBalance; 
                el('setHsaStart').value=DATA.totals.hsaStartBal||0; 
                el('setU1').value=DATA.users[0]||"";
                el('setU2').value=DATA.users[1]||"";
                el('setU3').value=DATA.users[2]||"";
                el('mSet').style.display='flex'; 
            }
            function closeModals() { document.querySelectorAll('.modal-overlay').forEach(m => m.style.display = 'none'); }
            function setTab(t) { ['net','budget','ana','credit','rewards','hsa','retire','history'].forEach(x => { el('v-'+x).style.display=t===x?'block':'none'; el('t-'+x).className=t===x?'tab active':'tab'; }); }
            loadData();
    """

    def html = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
        <title>Advanced Budget Tracker</title>
        <script src="https://cdn.tailwindcss.com"></script>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        <style>${css}</style>
    </head>
    <body class="bg-gray-100 text-gray-800 font-sans">
        <div class="header">
            <div class="brand">
                <div>
                    <div class="brand-title" style="font-size: 0.75rem; font-weight: 700; color: #6b7280; text-transform: uppercase;">Household</div>
                    <div class="brand-subtitle" style="font-size: 1.125rem; font-weight: 800; color: #111827;"><i class="fas fa-chart-pie" style="color: #4f46e5; margin-right: 4px;"></i> Advanced Budget</div>
                </div>
            </div>
            <div class="header-actions">
                <div onclick="openSettings()" style="cursor:pointer; margin-left:8px; color: #6b7280; transition: color 0.2s;" onmouseover="this.style.color='#111827'" onmouseout="this.style.color='#6b7280'"><i class="fas fa-cog fa-lg"></i></div>
            </div>
        </div>
        <div class="container">
            <div class="tabs">
                <div class="tab active" onclick="setTab('net')" id="t-net">Overview</div>
                <div class="tab" onclick="setTab('budget')" id="t-budget">Budget</div>
                <div class="tab" onclick="setTab('ana')" id="t-ana">Analytics</div>
                <div class="tab" onclick="setTab('credit')" id="t-credit">Credit</div>
                <div class="tab" onclick="setTab('rewards')" id="t-rewards">Rewards</div>
                <div class="tab" onclick="setTab('hsa')" id="t-hsa">HSA</div>
                <div class="tab" onclick="setTab('retire')" id="t-retire">401k/Inv</div>
                <div class="tab" onclick="setTab('history')" id="t-history">History</div>
            </div>
            <div id="v-net">
                <div class="card" style="background:var(--net-gradient); color:white; border-color: #374151;">
                    <div style="display:flex; justify-content:space-between; align-items:flex-start;">
                        <div>
                            <div style="opacity:0.8; font-size:0.75rem; font-weight:700; text-transform:uppercase;">Total Net Worth</div>
                            <div id="dispNetWorth" style="font-size:2.25rem; font-weight:800; margin:4px 0; line-height: 1;">--</div>
                            <div style="opacity:0.6; font-size:0.75rem;">(Assets - Liabilities)</div>
                            <div style="margin-top:16px; padding-top:12px; border-top:1px solid rgba(255,255,255,0.1);">
                                 <div style="opacity:0.9; font-size:0.75rem; font-weight:700; text-transform:uppercase; color:#6ee7b7;">Net Worth (No Mortgage)</div>
                                 <div id="dispNetWorthNoSec" style="font-size:1.5rem; font-weight:800; color:#6ee7b7; margin-top:4px;">--</div>
                            </div>
                        </div>
                        <div style="text-align:right;">
                            <div style="opacity:0.8; font-size:0.65rem; font-weight:700; text-transform:uppercase;">Secured Debt</div>
                            <div id="dispSecDebtTotal" style="font-size:1.125rem; font-weight:800; color:#fca5a5;">--</div>
                            <div style="opacity:0.6; font-size:0.65rem;">(Mortgage/Auto)</div>
                        </div>
                    </div>
                </div>
                <div class="card">
                      <h3 class="section-title">Wealth Timeline</h3>
                    <div id="timelineChart" style="height:150px; display:flex; align-items:flex-end; justify-content:space-between; padding-top:20px; padding-bottom:10px;">
                        <div style="width:100%; text-align:center; color:#9ca3af; font-size:0.75rem; padding-top:50px;">Collecting History...</div>
                    </div>
                </div>
                <div class="card">
                    <h3 class="section-title">Financial Health</h3>
                    <div class="chart-box">
                        <div class="bar-group"><div class="bar bar-asset" id="barBud" style="height:10%;"><div class="bar-val" id="valBud">--</div></div><div class="bar-label">Check</div><span class="tag-pill tag-liq">LIQ</span></div>
                        <div class="bar-group"><div class="bar bar-sav" id="barSav" style="height:10%;"><div class="bar-val" id="valSav">--</div></div><div class="bar-label">Savings</div><span class="tag-pill tag-liq">LIQ</span></div>
                        <div class="bar-group"><div class="bar bar-hsa" id="barHsa" style="height:10%;"><div class="bar-val" id="valHsa">--</div></div><div class="bar-label">HSA</div><span class="tag-pill tag-loc">LOC</span></div>
                        <div class="bar-group"><div class="bar bar-liq" id="barLiq" style="height:10%;"><div class="bar-val" id="valLiq">--</div></div><div class="bar-label">Total</div><span class="tag-pill tag-liq">LIQ</span></div>
                        <div class="bar-group"><div class="bar bar-ret" id="barRet" style="height:10%;"><div class="bar-val" id="valRet">--</div></div><div class="bar-label">401k</div><span class="tag-pill tag-loc">LOC</span></div>
                        <div class="bar-group"><div class="bar bar-inv" id="barInv" style="height:10%;"><div class="bar-val" id="valInv">--</div></div><div class="bar-label">Portf</div><span class="tag-pill tag-loc">LOC</span></div>
                        <div class="bar-group"><div class="bar bar-bad" id="barBad" style="height:10%;"><div class="bar-val" id="valBad">--</div></div><div class="bar-label">Debt</div></div>
                    </div>
                </div>
                <div class="card"><div class="section-head"><h3 class="section-title">Secured Debt (Mortgage/Auto)</h3><i class="fas fa-plus-circle" style="color:#f59e0b; font-size:18px; cursor:pointer; margin-left:10px;" onclick="editLoan(null)"></i></div><div id="listLoans"></div></div>
            </div>
            <div id="v-ana" style="display:none;">
                <div class="card">
                    <h3 class="section-title">Household Spenders</h3>
                    <div id="listLeaderboard" style="margin-top:12px;"></div>
                </div>
                <div class="card">
                    <h3 class="section-title">No-Spend Challenge</h3>
                    <div style="display:flex; justify-content:space-between; margin-top:16px;">
                        <div style="text-align:center; width:48%; background:#ecfdf5; padding:16px 0; border-radius:0.5rem; border:1px solid #d1fae5;">
                            <div style="font-size:2rem; font-weight:800; color:#10b981; line-height:1;" id="dispNoSpend">--</div>
                            <div style="font-size:0.65rem; font-weight:700; color:#059669; text-transform:uppercase; margin-top:4px;">No-Spend Days</div>
                        </div>
                        <div style="text-align:center; width:48%; background:#fef2f2; padding:16px 0; border-radius:0.5rem; border:1px solid #fee2e2;">
                            <div style="font-size:2rem; font-weight:800; color:#ef4444; line-height:1;" id="dispSpendDays">--</div>
                            <div style="font-size:0.65rem; font-weight:700; color:#b91c1c; text-transform:uppercase; margin-top:4px;">Spend Days</div>
                        </div>
                    </div>
                </div>
                <div class="card">
                    <h3 class="section-title">Ins & Outs (Cash Flow)</h3>
                    <div class="pie-container"><div class="pie-chart" id="pieFlow"><div class="pie-hole"><div class="pie-total-label">Net Save</div><div class="pie-total-val" id="pieNet">--</div></div></div></div>
                    <div class="legend" id="legFlow"></div>
                </div>
                <div class="card">
                    <h3 class="section-title">Spending Breakdown</h3>
                    <div class="pie-container"><div class="pie-chart" id="pieChart"><div class="pie-hole"><div class="pie-total-label">Total Spent</div><div class="pie-total-val" id="pieTotal">--</div></div></div></div>
                    <div class="legend" id="pieLegend"></div>
                </div>
                <div class="card">
                    <h3 class="section-title">Wants vs Needs</h3>
                    <div style="display:flex; justify-content:space-between; margin-top:16px; border-bottom:1px dashed #e5e7eb; padding-bottom:12px;">
                        <div><div style="font-size:0.75rem; font-weight:700; color:#10b981; text-transform:uppercase;">Needs</div><div id="anaNeeds" style="font-size:1.25rem; font-weight:800;">--</div></div>
                         <div style="text-align:right;"><div style="font-size:0.75rem; font-weight:700; color:#ef4444; text-transform:uppercase;">Wants</div><div id="anaWants" style="font-size:1.25rem; font-weight:800;">--</div></div>
                    </div>
                </div>
            </div>
            <div id="v-budget" style="display:none;">
                <div class="card" style="background:var(--net-gradient); color:white; border-color: #374151;">
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <div>
                            <div style="opacity:0.8; font-size:0.75rem; font-weight:700; text-transform:uppercase;">Total Checking</div>
                            <div id="dispChkTotal" style="font-size:2.25rem; font-weight:800; margin:4px 0; line-height: 1;">--</div>
                        </div>
                    </div>
                    <div style="margin-top:16px; padding-top:12px; border-top:1px solid rgba(255,255,255,0.1); display:flex; justify-content:space-between;">
                        <div>
                            <div style="opacity:0.9; font-size:0.75rem; font-weight:700; text-transform:uppercase; color:#fca5a5;">Remaining Budget</div>
                            <div id="dispRemBudget" style="font-size:1.25rem; font-weight:800; color:#fca5a5; margin-top:4px;">--</div>
                        </div>
                        <div style="text-align:right;">
                            <div style="opacity:0.9; font-size:0.75rem; font-weight:700; text-transform:uppercase; color:#6ee7b7;">Safe To Spend</div>
                            <div id="dispSafeSpend" style="font-size:1.25rem; font-weight:800; color:#6ee7b7; margin-top:4px;">--</div>
                        </div>
                    </div>
                </div>
                <div class="card" style="margin-top:16px;">
                    <h3 class="section-title">Monthly Cash History (Check + Sav)</h3>
                    <div id="chartChkSav" style="height:120px; display:flex; align-items:flex-end; justify-content:space-between; padding-top:20px; padding-bottom:10px;"></div>
                </div>
                <div class="card"><div class="section-head"><h3 class="section-title">Checking Accounts</h3><i class="fas fa-plus-circle" style="color:#4f46e5; font-size:18px; cursor:pointer; margin-left:10px;" onclick="editChk(null)"></i></div><div id="listChk"></div></div>
                <div class="card"><div class="section-head"><h3 class="section-title">Savings Accounts</h3><i class="fas fa-plus-circle" style="color:#3b82f6; font-size:18px; cursor:pointer; margin-left:10px;" onclick="editSav(null)"></i></div><div id="listSav"></div></div>
                <div class="card"><div class="section-head"><h3 class="section-title">Income</h3><div class="section-total" id="totInc">Plan: \$0</div><i class="fas fa-plus-circle" style="color:#10b981; font-size:18px; cursor:pointer; margin-left:10px;" onclick="editCat('income', null)"></i></div><div id="listIncome"></div></div>
                <div class="card">
                    <div class="section-head"><h3 class="section-title">Expenses</h3><div class="section-total" id="totExp">Plan: \$0</div><i class="fas fa-plus-circle" style="color:#ef4444; font-size:18px; cursor:pointer; margin-left:10px;" onclick="editCat('expense', null)"></i></div>
                    <div style="font-size:0.65rem; font-weight:700; color:#9ca3af; text-transform:uppercase; margin-bottom:4px; margin-top:8px;">Continuous Flow</div>
                    <div id="listExpenseCont"></div>
                    <div style="font-size:0.65rem; font-weight:700; color:#9ca3af; text-transform:uppercase; margin-bottom:4px; margin-top:16px;">Unpaid Bills</div>
                    <div id="listExpenseUnpaid"></div>
                    <div style="font-size:0.65rem; font-weight:700; color:#9ca3af; text-transform:uppercase; margin-bottom:4px; margin-top:16px;">Paid Bills</div>
                    <div id="listExpensePaid"></div>
                </div>
                <div class="card">
                    <div class="section-head"><h3 class="section-title">Sinking Funds (Goals)</h3><i class="fas fa-plus-circle" style="color:#111827; font-size:18px; cursor:pointer; margin-left:10px;" onclick="editPlan(null)"></i></div>
                    <div id="listPlan"></div>
                </div>
            </div>
            
            <div id="v-rewards" style="display:none;">
                <div class="card" style="background:var(--rew-gradient);color:white; border-color: #6d28d9;">
                    <div style="opacity:0.9;font-size:0.75rem;font-weight:600;">Total Rewards Value</div>
                    <div id="dispRewBal" style="font-size:2.25rem;font-weight:800;">--</div>
                </div>
                <div class="card">
                    <div class="section-head"><h3 class="section-title">Points & Cash Back</h3><i class="fas fa-plus-circle" style="color:#8b5cf6;font-size:18px;cursor:pointer;margin-left:10px;" onclick="editRew(null)"></i></div>
                    <div id="listRew"></div>
                </div>
            </div>
            
            <div id="v-hsa" style="display:none;">
                <div class="card" style="background:var(--hsa-gradient);color:white; border-color: #0f766e;"><div style="opacity:0.9;font-size:0.75rem;font-weight:600;">HSA Balance</div><div id="dispHsaBal" style="font-size:2.25rem;font-weight:800;">--</div></div>
                <div class="card"><h3 class="section-title">HSA Growth History</h3><div id="chartHsa" style="height:120px; display:flex; align-items:flex-end; justify-content:space-between; padding-top:20px; padding-bottom:10px;"></div></div>
                <div class="card">
                    <div class="section-head"><h3 class="section-title">HSA Transactions</h3><i class="fas fa-plus-circle" style="color:#14b8a6;font-size:18px;cursor:pointer;margin-left:10px;" title="Log HSA Purchase" onclick="logHsaTx(true)"></i></div>
                    <div id="listHsaTx"></div>
                </div>
                <div class="card"><div class="section-head"><h3 class="section-title">Contribution Categories</h3><i class="fas fa-plus-circle" style="color:#14b8a6;font-size:18px;cursor:pointer;margin-left:10px;" title="Add HSA Income Category" onclick="editCat('hsa_income',null)"></i></div><div id="listHsaIncome"></div></div>
                <div class="card"><div class="section-head"><h3 class="section-title">Medical Expense Categories</h3><i class="fas fa-plus-circle" style="color:#ef4444;font-size:18px;cursor:pointer;margin-left:10px;" title="Add HSA Expense Category" onclick="editCat('hsa_expense',null)"></i></div><div id="listHsaExpense"></div></div>
            </div>
            <div id="v-credit" style="display:none;">
                <div class="card" style="background:var(--cc-gradient);color:white; border-color: #be185d;"><div style="opacity:0.9;font-size:0.75rem;font-weight:600;">Total CC Debt</div><div id="dispCcBal" style="font-size:2.25rem;font-weight:800;">--</div></div>
                <div class="card"><div class="section-head"><h3 class="section-title">My Cards</h3><i class="fas fa-plus-circle" style="color:#db2777;font-size:18px;cursor:pointer;margin-left:10px;" onclick="editCC(null)"></i></div><div id="listCC"></div></div>
            </div>
            <div id="v-retire" style="display:none;">
                <div class="card" style="background:var(--ret-gradient);color:white; border-color: #a16207;"><div style="opacity:0.9;font-size:0.75rem;font-weight:600;">Portfolio Value</div><div id="dispRetBal" style="font-size:2.25rem;font-weight:800;">--</div></div>
                <div class="card"><h3 class="section-title">401k/Inv Growth History</h3><div id="chartRet" style="height:120px; display:flex; align-items:flex-end; justify-content:space-between; padding-top:20px; padding-bottom:10px;"></div></div>
                <div class="card"><div class="section-head"><h3 class="section-title">401k Accounts</h3><i class="fas fa-plus-circle" style="color:#ca8a04;font-size:18px;cursor:pointer;margin-left:10px;" onclick="editRet(null)"></i></div><div id="listRet"></div></div>
                <div class="card"><div class="section-head"><h3 class="section-title">Investment Portfolio</h3><i class="fas fa-plus-circle" style="color:#64748b;font-size:18px;cursor:pointer;margin-left:10px;" onclick="editInv(null)"></i></div><div id="listInv"></div></div>
            </div>
             <div id="v-history" style="display:none;"><div class="card"><h3 class="section-title">Current Month Transactions</h3><div id="listTx" style="margin-top:16px;"></div></div></div>
        </div>
        <div class="fab" onclick="editTx(null)"><i class="fas fa-plus"></i></div>
        
        <div id="mTx" class="modal-overlay">
            <div class="modal">
                <h2 id="txTitle">Add Transaction</h2>
                <input type="hidden" id="txId">
                <select id="txUser"></select>
                <select id="txCatId"></select>
                
                <label style="color:#4f46e5; margin-top:8px;">Funding Source</label>
                <select id="txFundId"></select>
                
                <div class="chk-group" style="margin-top:8px; margin-bottom:12px; justify-content:flex-end;">
                    <input type="checkbox" id="txWant">
                    <label style="margin-bottom:0;">Is this a Want?</label>
                </div>
                <input type="text" id="txDesc" placeholder="Description/Store">
                <input type="number" id="txAmt" placeholder="0.00">
                <input type="date" id="txDate">
                <button class="btn" onclick="saveTx()">Save</button>
                <button id="btnDelTx" class="btn btn-del" onclick="delTx()" style="display:none;">Delete</button>
                <button class="btn btn-sub" onclick="closeModals()">Cancel</button>
            </div>
        </div>
        
        <div id="mCat" class="modal-overlay">
            <div class="modal">
                <h2 id="catTitle">Edit Category</h2>
                <input type="hidden" id="catId">
                <input type="hidden" id="catType">
                <input type="text" id="catName" placeholder="Name">
                <input type="number" id="catPlan" placeholder="Amount">
                <div class="chk-group" style="margin-top:12px;">
                    <input type="checkbox" id="catIsPaid">
                    <label style="margin-bottom:0; color:#10b981;">Mark as Paid <i class="fas fa-check-circle"></i></label>
                </div>
                <div id="catAutoPaySec" style="display:none;border-top:1px dashed #e5e7eb;margin-top:12px;padding-top:12px;">
                    <div class="chk-group"><input type="checkbox" id="catFixed"><label style="margin-bottom:0;">Fixed Cost (Need)</label></div>
                    <div class="chk-group"><input type="checkbox" id="catContinuous"><label style="margin-bottom:0;">Continuous Exp (Gas, Food)</label></div>
                    <div style="font-weight:700;color:#10b981;margin-bottom:6px; margin-top:10px; font-size: 0.75rem;">AUTO-PAY</div>
                    <div class="chk-group"><input type="checkbox" id="catAutoPay"><label style="margin-bottom:0;">Enable</label></div>
                    <select id="catFreq"><option value="weekly">Weekly</option><option value="biweekly">Bi-Weekly</option></select>
                    <input type="date" id="catNextPay">
                </div>
                <button class="btn" onclick="saveCat()">Save</button>
                <button id="btnDelCat" class="btn btn-del" onclick="delCat()" style="display:none;">Delete</button>
                <button class="btn btn-sub" onclick="closeModals()">Cancel</button>
            </div>
        </div>
        <div id="mChk" class="modal-overlay"><div class="modal"><h2 id="chkTitle">Checking Account</h2><input type="hidden" id="chkId"><input type="text" id="chkName" placeholder="Account Name"><label>Current Balance</label><input type="number" id="chkBal" placeholder="0.00"><button class="btn" onclick="saveChk()">Save Account</button><button id="btnDelChk" class="btn btn-del" onclick="delChk()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        
        <div id="mRew" class="modal-overlay"><div class="modal"><h2 id="rewTitle">Rewards Account</h2><input type="hidden" id="rewId"><input type="text" id="rewName" placeholder="Card/Account Name"><label>Current Cash Back/Points Value</label><input type="number" id="rewBal" placeholder="0.00"><button class="btn" onclick="saveRew()">Save Rewards</button><button id="btnDelRew" class="btn btn-del" onclick="delRew()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        
        <div id="mCatOpts" class="modal-overlay">
            <div class="modal">
                <h2 id="catOptTitle">Category Options</h2>
                <input type="hidden" id="catOptId">
                <input type="hidden" id="catOptType">
                <button class="btn" onclick="openCatEdit()">Edit Category</button>
                <button class="btn" style="background:#4f46e5; margin-top:10px;" onclick="openCatHistory()">View Transactions</button>
                <button class="btn btn-sub" onclick="closeModals()">Cancel</button>
            </div>
        </div>
        <div id="mCatTx" class="modal-overlay">
            <div class="modal">
                <h2 id="catTxTitle">History</h2>
                <div id="catTxList" style="max-height:300px; overflow-y:auto; margin-bottom:16px;"></div>
                <button class="btn btn-sub" onclick="closeModals()">Close</button>
            </div>
        </div>
        <div id="mInv" class="modal-overlay"><div class="modal"><h2 id="invTitle">Investment</h2><input type="hidden" id="invId"><input type="text" id="invName" placeholder="Name (e.g. Stock, Crypto)"><input type="number" id="invBal" placeholder="Current Value"><button class="btn" onclick="saveInv()">Save</button><button id="btnDelInv" class="btn btn-del" onclick="delInv()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        <div id="mPlan" class="modal-overlay">
            <div class="modal">
                <h2 id="planTitle">Sinking Fund / Goal</h2>
                <input type="hidden" id="planId">
                <input type="text" id="planName" placeholder="Goal Name">
                <div style="display:flex; gap:12px;">
                    <input type="number" id="planPriority" placeholder="Priority (1=High)" style="width:40%">
                    <input type="number" id="planCost" placeholder="Target Cost">
                </div>
                <label style="color:#4f46e5;">Saved So Far</label>
                <input type="number" id="planSaved" placeholder="0.00">
                <button class="btn" onclick="savePlan()">Save Goal</button>
                <button id="btnDelPlan" class="btn btn-del" onclick="delPlan()" style="display:none;">Delete</button>
                <button class="btn btn-sub" onclick="closeModals()">Cancel</button>
            </div>
        </div>
        <div id="mCC" class="modal-overlay"><div class="modal"><h2 id="ccTitle">Credit Card</h2><input type="hidden" id="ccId"><input type="text" id="ccName" placeholder="Card Name"><input type="number" id="ccLimit" placeholder="Limit"><input type="number" id="ccBal" placeholder="Balance"><label style="color:#ef4444">Monthly Payment (for projection)</label><input type="number" id="ccPay" placeholder="0.00"><label style="color:#ef4444">APR % (Interest Rate)</label><input type="number" id="ccApr" placeholder="e.g. 24.99"><button class="btn" onclick="saveCC()">Save</button><button id="btnDelCC" class="btn btn-del" onclick="delCC()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        <div id="mRet" class="modal-overlay"><div class="modal"><h2 id="retTitle">401k Account</h2><input type="hidden" id="retId"><input type="text" id="retName" placeholder="Account Name"><input type="number" id="retBal" placeholder="Balance"><button class="btn" onclick="saveRet()">Save</button><button id="btnDelRet" class="btn btn-del" onclick="delRet()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        <div id="mLoan" class="modal-overlay"><div class="modal"><h2 id="loanTitle">Secured Loan</h2><input type="hidden" id="loanId"><input type="text" id="loanName" placeholder="Name (e.g., Home)"><label>Original Loan Amount</label><input type="number" id="loanOrig"><label>Current Balance Due</label><input type="number" id="loanBal"><label style="color:#ef4444">Monthly Payment</label><input type="number" id="loanPay"><button class="btn" onclick="saveLoan()">Save Loan</button><button id="btnDelLoan" class="btn btn-del" onclick="delLoan()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        <div id="mSav" class="modal-overlay"><div class="modal"><h2 id="savTitle">Savings Account</h2><input type="hidden" id="savId"><input type="text" id="savName" placeholder="Account Name"><label>Current Balance</label><input type="number" id="savBal" placeholder="0.00"><button class="btn" onclick="saveSav()">Save Account</button><button id="btnDelSav" class="btn btn-del" onclick="delSav()" style="display:none;">Delete</button><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        <div id="mSavTx" class="modal-overlay"><div class="modal"><h2>Update Savings</h2><input type="hidden" id="savTxId"><div style="text-align:center;margin-bottom:16px;font-weight:700;color:#4f46e5;" id="savTxName"></div><input type="number" id="savTxAmt" placeholder="Amount"><div style="display:flex;flex-direction:column;gap:10px;"><div style="display:flex;gap:10px;"><button class="btn" style="background:#10b981;" onclick="savTx('add')">Deposit (+)</button><button class="btn" style="background:#ef4444;" onclick="savTx('sub')">Withdraw (-)</button></div><button class="btn" style="background:#eab308;color:#1f2937;" onclick="savTx('interest')"><i class="fas fa-percentage"></i> Add Interest Income</button></div><button class="btn btn-sub" onclick="closeModals()">Cancel</button></div></div>
        <div id="mSet" class="modal-overlay">
            <div class="modal">
                <h2>Settings</h2>
                <label>Legacy Start Balance Override</label>
                <input type="number" id="setStart">
                <label>HSA Start Bal</label>
                <input type="number" id="setHsaStart">
                <label style="margin-top:12px; color:#4f46e5;">Household Users</label>
                <div style="display:flex; gap:8px; margin-bottom:16px;">
                    <input type="text" id="setU1" placeholder="User 1" style="margin-bottom:0;">
                    <input type="text" id="setU2" placeholder="User 2" style="margin-bottom:0;">
                    <input type="text" id="setU3" placeholder="User 3" style="margin-bottom:0;">
                </div>
                <button class="btn" onclick="saveSettings()">Save</button>
                <div style="margin-top:24px; border-top:1px dashed #e5e7eb; padding-top:16px;">
                    <label>Backup Data</label>
                    <button class="btn" style="background:#8b5cf6;" onclick="backupData()">Download JSON</button>
                </div>
                <button class="btn btn-sub" onclick="closeModals()">Close</button>
            </div>
        </div>
        <div id="mAlert" class="modal-overlay" style="z-index:2000;">
            <div class="modal" style="text-align:center; max-width:320px;">
                <div style="margin-bottom:16px; font-size:40px;" id="alertIcon"></div>
                <h2 id="alertTitle" style="margin-bottom:10px;">Notice</h2>
                <div id="alertMsg" style="margin-bottom:24px; line-height:1.5; color:#4b5563; font-size: 0.875rem;"></div>
                <div id="alertBtns" style="display:flex; gap:10px; justify-content:center;">
                    <button class="btn" id="btnAlertOk" onclick="closeModals()" style="display:none;">OK</button>
                    <button class="btn btn-del" id="btnAlertYes" onclick="handleConfirmYes()" style="display:none;">Yes, Delete</button>
                    <button class="btn btn-sub" id="btnAlertNo" onclick="closeModals()" style="display:none;">Cancel</button>
                </div>
            </div>
        </div>
        <script>${js}</script>
    </body>
    </html>
    """
    render contentType: "text/html", data: html
}

def serveApiData() {
    def cats = atomicState.categories ?: []
    def txs = atomicState.transactions ?: []
    def ccs = atomicState.creditCards ?: []
    def chk = atomicState.checking ?: []
    def rets = atomicState.retirement ?: []
    def invs = atomicState.investments ?: []
    def loans = atomicState.loans ?: []
    def users = atomicState.users ?: ["Shane", "Christy", "Leanne"]
    def rewards = atomicState.rewards ?: []
    def savings = atomicState.savings ?: []
    def planned = atomicState.planned ?: []
    def hsaStartBal = safeDouble(atomicState.hsaStartBal)
    def nwHist = atomicState.nwHistory ?: []

    txs = txs.sort { a, b -> b.date <=> a.date }
    def nowStr = new Date().format("yyyy-MM")
    def monthTxs = txs.findAll { it.date && it.date.toString().startsWith(nowStr) }
    
    // Calculate Spend vs No-Spend Days
    def currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    def spendDates = monthTxs.findAll { it.type == "expense" }.collect { it.date }.unique()
    def spendDaysCount = spendDates.size()
    def noSpendDaysCount = currentDay - spendDaysCount
    if (noSpendDaysCount < 0) noSpendDaysCount = 0
    
    def monInc = monthTxs.findAll { it.type == "income" }.sum { safeDouble(it.amount) } ?: 0
    def monExp = monthTxs.findAll { it.type == "expense" }.sum { safeDouble(it.amount) } ?: 0
    
    def allHsaInc = txs.findAll { it.type == "hsa_income" }.sum { safeDouble(it.amount) } ?: 0
    def allHsaExp = txs.findAll { it.type == "hsa_expense" }.sum { safeDouble(it.amount) } ?: 0
    def monHsaInc = monthTxs.findAll { it.type == "hsa_income" }.sum { safeDouble(it.amount) } ?: 0
    def monHsaExp = monthTxs.findAll { it.type == "hsa_expense" }.sum { safeDouble(it.amount) } ?: 0
    
    cats = cats.collect { it.name = cleanName(it.name); return it }
    ccs = ccs.collect { it.name = cleanName(it.name); return it }
    chk = chk.collect { it.name = cleanName(it.name); return it }
    rets = rets.collect { it.name = cleanName(it.name); return it }
    invs = invs.collect { it.name = cleanName(it.name); return it }
    loans = loans.collect { it.name = cleanName(it.name); return it }
    savings = savings.collect { it.name = cleanName(it.name); return it }
    planned = planned.collect { it.name = cleanName(it.name); return it }
    rewards = rewards.collect { it.name = cleanName(it.name); return it }

    def catData = cats.collect { c ->
        def cTxs = monthTxs.findAll { it.catId == c.id }
        def actual = cTxs.sum { safeDouble(it.amount) } ?: 0
        [id: c.id, name: c.name, type: c.type, expected: safeDouble(c.expected), actual: actual, autoPay: c.autoPay, payFreq: c.payFreq, nextPayDate: c.nextPayDate, isPaid: c.isPaid, isContinuous: c.isContinuous]
    }
    
    def sysInt = [id: "system-interest", name: "Checking Interest", type: "income", expected: 0.0, actual: 0.0, autoPay: false]
    def sysOth = [id: "system-other", name: "Other Income", type: "income", expected: 0.0, actual: 0.0, autoPay: false]
    
    sysInt.actual = monthTxs.findAll { it.catId == "system-interest" }.sum { safeDouble(it.amount) } ?: 0
    sysOth.actual = monthTxs.findAll { it.catId == "system-other" }.sum { safeDouble(it.amount) } ?: 0
    
    if (!catData.find{it.id == "system-interest"}) catData << sysInt
    if (!catData.find{it.id == "system-other"}) catData << sysOth

    renderJson([
        totals: [incMonth: monInc, expMonth: monExp, hsaBalance: hsaStartBal + allHsaInc - allHsaExp, hsaStartBal: hsaStartBal, hsaIncMonth: monHsaInc, hsaExpMonth: monHsaExp, spendDays: spendDaysCount, noSpendDays: noSpendDaysCount],
        categories: catData, checking: chk, creditCards: ccs, retirement: rets, investments: invs, loans: loans, savings: savings, planned: planned, rewards: rewards, 
        nwHistory: nwHist, 
        retHistory: atomicState.retHistory ?: [], 
        hsaHistory: atomicState.hsaHistory ?: [], 
        chkSavHistory: atomicState.chkSavHistory ?: [], 
        users: users, history: monthTxs.take(100),
        thisAppId: app.id
    ])
}

def serveBackup() {
    def backup = [
        transactions: atomicState.transactions,
        categories: atomicState.categories,
        checking: atomicState.checking,
        creditCards: atomicState.creditCards,
        retirement: atomicState.retirement,
        investments: atomicState.investments,
        loans: atomicState.loans,
        savings: atomicState.savings,
        planned: atomicState.planned,
        rewards: atomicState.rewards,
        nwHistory: atomicState.nwHistory,
        hsaStartBal: atomicState.hsaStartBal,
        users: atomicState.users
    ]
    render contentType: "application/json", data: JsonOutput.toJson(backup)
}

def handleSaveTx() { 
    def txs = atomicState.transactions ?: []
    def chk = atomicState.checking ?: []
    def rews = atomicState.rewards ?: []
    
    def originalTxAmount = 0.0
    def originalTxType = ""
    def originalFundId = ""
    def isNew = false
    
    if (params.id && params.id != "null" && params.id != "") {
        def existing = txs.find { it.id == params.id }
        if (existing) {
            originalTxAmount = safeDouble(existing.amount)
            originalTxType = existing.type
            originalFundId = existing.fundId
            txs = txs.findAll { it.id != params.id }
        }
    } else {
        isNew = true
    }
    
    def type = "expense"
    def cat = (atomicState.categories ?: []).find { it.id == params.catId }
    if (cat) { type = cat.type }
    else if (params.catId && params.catId.startsWith("system-")) { type = "income" }
    
    def newAmt = safeDouble(params.amt)
    def reqFundId = params.fundId ?: ""
    
    // Reverse original transaction logic
    if (!isNew && originalFundId && !originalTxType.contains("hsa")) {
        def oldRewAcct = rews.find { it.id == originalFundId }
        if (oldRewAcct) {
            if (originalTxType == "expense") oldRewAcct.balance += originalTxAmount
            else if (originalTxType == "income") oldRewAcct.balance -= originalTxAmount
        } else if (chk.size() > 0) {
            if (originalTxType == "expense") chk[0].balance += originalTxAmount
            else if (originalTxType == "income") chk[0].balance -= originalTxAmount
        }
    }

    // Apply new transaction logic
    if (reqFundId && !type.contains("hsa")) {
        def newRewAcct = rews.find { it.id == reqFundId }
        if (newRewAcct) {
            if (type == "expense") newRewAcct.balance -= newAmt
            else if (type == "income") newRewAcct.balance += newAmt
        } else if (chk.size() > 0) {
            if (type == "expense") chk[0].balance -= newAmt
            else if (type == "income") chk[0].balance += newAmt
        }
    } else if (chk.size() > 0 && !type.contains("hsa")) {
        // Fallback for empty fundId
        reqFundId = chk[0].id
        if (type == "expense") chk[0].balance -= newAmt
        else if (type == "income") chk[0].balance += newAmt
    }
    
    def newTxId = params.id ?: UUID.randomUUID().toString()
    def txDesc = params.desc ?: ""
    def txDate = params.date ?: new Date().format("yyyy-MM-dd")
    def txUser = params.txUser ? cleanName(params.txUser) : "Unknown"
    
    txs << [id: newTxId, catId: params.catId, fundId: reqFundId, want: params.want == 'true', desc: txDesc, amount: newAmt, date: txDate, type: type, txUser: txUser, ts: new Date().getTime()]
    
    atomicState.transactions = txs
    atomicState.checking = chk
    atomicState.rewards = rews
    
    // --- NOTIFICATION LOGIC ---
    if (type == "expense") {
        def threshold = safeDouble(settings.notifyThreshold)
        if (newAmt >= threshold) {
            def isCont = cat?.isContinuous == true
            def targetDevices = isCont ? settings.notifyDevicesCont : settings.notifyDevicesBills
            
            if (targetDevices) {
                def catName = cat ? cleanName(cat.name) : "Uncategorized"
                def storeDesc = txDesc ? cleanName(txDesc) : "an unspecified store/item"
                def msg = ""
                
                def spender = (txUser == "Auto") ? "the Household (Auto)" : txUser
                
                if (isNew) {
                    msg = "💸 New expense logged by ${spender}: \$${String.format('%.2f', newAmt)} at ${storeDesc} (${catName})"
                } else {
                    msg = "✏️ Expense UPDATED by ${spender}: \$${String.format('%.2f', newAmt)} at ${storeDesc} (${catName})"
                }
                
                targetDevices*.deviceNotification(msg)
            }
        }
    }
    
    // Capture HSA history point on update
    if (type.contains("hsa")) {
        updateHsaHistory()
    }
    
    renderJson([status:"ok"])
}

def handleDelTx() { 
    def txs = atomicState.transactions ?: []
    def chk = atomicState.checking ?: []
    def rews = atomicState.rewards ?: []
    def toDelete = txs.find { it.id == params.id }
    
    if(toDelete) {
        if (!toDelete.type.contains("hsa")) {
            def amt = safeDouble(toDelete.amount)
            def targetRew = rews.find { it.id == toDelete.fundId }
            
            if (targetRew) {
                if (toDelete.type == "expense") targetRew.balance += amt
                else if (toDelete.type == "income") targetRew.balance -= amt
            } else if (chk.size() > 0) {
                if (toDelete.type == "expense") chk[0].balance += amt
                else if (toDelete.type == "income") chk[0].balance -= amt
            }
        }
        atomicState.transactions = txs.findAll { it.id != params.id }
        atomicState.checking = chk
        atomicState.rewards = rews
        
        // Capture HSA history point on delete
        if (toDelete.type.contains("hsa")) {
            updateHsaHistory()
        }
    }
    renderJson([status:"ok"]) 
}

def handleSaveCat() { 
    def cats=atomicState.categories?:[]; 
    if(params.id) cats=cats.findAll{it.id!=params.id}; 
    def name = java.net.URLDecoder.decode(params.name, "UTF-8");
    cats<<[
        id:params.id?:UUID.randomUUID().toString(), 
        name:name, 
        type:params.type, 
        expected:safeDouble(params.plan), 
        isPaid:params.isPaid=="true", 
        autoPay:params.auto=="true", 
        isContinuous:params.isCont=="true",
        payFreq:params.freq, 
        nextPayDate:params.next
    ]; 
    atomicState.categories=cats; 
    renderJson([status:"ok"]); 
}
def handleDelCat() { atomicState.categories=(atomicState.categories?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSaveChk() { def chk=atomicState.checking?:[]; if(params.id) chk=chk.findAll{it.id!=params.id};
def name = java.net.URLDecoder.decode(params.name, "UTF-8");
chk<<[id:params.id?:UUID.randomUUID().toString(), name:name, balance:safeDouble(params.bal)]; atomicState.checking=chk; renderJson([status:"ok"]); }
def handleDelChk() { atomicState.checking=(atomicState.checking?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSaveRew() { def rews=atomicState.rewards?:[]; if(params.id) rews=rews.findAll{it.id!=params.id};
def name = java.net.URLDecoder.decode(params.name, "UTF-8");
rews<<[id:params.id?:UUID.randomUUID().toString(), name:name, balance:safeDouble(params.bal)]; atomicState.rewards=rews; renderJson([status:"ok"]); }
def handleDelRew() { atomicState.rewards=(atomicState.rewards?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSaveCC() { def ccs=atomicState.creditCards?:[];
if(params.id) ccs=ccs.findAll{it.id!=params.id}; 
def name = java.net.URLDecoder.decode(params.name, "UTF-8");
ccs<<[id:params.id?:UUID.randomUUID().toString(), name:name, limit:safeDouble(params.limit), balance:safeDouble(params.bal), payment:safeDouble(params.pay), apr:safeDouble(params.apr)]; atomicState.creditCards=ccs; renderJson([status:"ok"]); }
def handleDelCC() { atomicState.creditCards=(atomicState.creditCards?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSaveRet() { 
    def rets=atomicState.retirement?:[]; 
    if(params.id) rets=rets.findAll{it.id!=params.id};
    def name = java.net.URLDecoder.decode(params.name, "UTF-8");
    rets<<[id:params.id?:UUID.randomUUID().toString(), name:name, balance:safeDouble(params.bal)]; 
    atomicState.retirement=rets; 
    updateRetHistory();
    renderJson([status:"ok"]); 
}
def handleDelRet() { 
    atomicState.retirement=(atomicState.retirement?:[]).findAll{it.id!=params.id}; 
    updateRetHistory();
    renderJson([status:"ok"]); 
}

def handleSaveInv() { 
    def invs=atomicState.investments?:[]; 
    if(params.id) invs=invs.findAll{it.id!=params.id}; 
    def name = java.net.URLDecoder.decode(params.name, "UTF-8");
    invs<<[id:params.id?:UUID.randomUUID().toString(), name:name, balance:safeDouble(params.bal)];
    atomicState.investments=invs; 
    updateRetHistory();
    renderJson([status:"ok"]); 
}
def handleDelInv() { 
    atomicState.investments=(atomicState.investments?:[]).findAll{it.id!=params.id}; 
    updateRetHistory();
    renderJson([status:"ok"]); 
}

def handleSaveLoan() { def loans=atomicState.loans?:[]; if(params.id) loans=loans.findAll{it.id!=params.id}; 
def name = java.net.URLDecoder.decode(params.name, "UTF-8");
loans<<[id:params.id?:UUID.randomUUID().toString(), name:name, orig:safeDouble(params.orig), balance:safeDouble(params.bal), payment:safeDouble(params.pay)]; atomicState.loans=loans; renderJson([status:"ok"]); }
def handleDelLoan() { atomicState.loans=(atomicState.loans?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSaveSav() { def savs=atomicState.savings?:[]; if(params.id) savs=savs.findAll{it.id!=params.id};
def name = java.net.URLDecoder.decode(params.name, "UTF-8");
savs<<[id:params.id?:UUID.randomUUID().toString(), name:name, balance:safeDouble(params.bal)]; atomicState.savings=savs; renderJson([status:"ok"]); }
def handleDelSav() { atomicState.savings=(atomicState.savings?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSavTx() { 
    def savs = atomicState.savings ?: []
    def s = savs.find { it.id == params.id }
    if(s) { 
        def amt = safeDouble(params.amt)
        s.balance += amt
        atomicState.savings = savs
        if (params.track == "true") {
            def txs = atomicState.transactions ?: []
            def cats = atomicState.categories ?: []
            def chk = atomicState.checking ?: []
            def cat = cats.find { it.name.toLowerCase().contains("interest") && it.type == "income" }
            if (!cat) cat = cats.find { it.type == "income" }
            txs << [
                id: UUID.randomUUID().toString(), catId: cat ? cat.id : "system-interest", want: false, desc: params.desc ?: "Bank Interest", amount: amt, date: new Date().format("yyyy-MM-dd"), type: "income", txUser: "System", ts: new Date().getTime()
            ]
            if (chk.size() > 0) {
                chk[0].balance += amt
                atomicState.checking = chk
            }
            atomicState.transactions = txs
        }
        renderJson([status:"ok"]) 
    } else { 
        renderJson([status:"error"]) 
    } 
}

def handleSavePlan() { def plans=atomicState.planned?:[];
if(params.id) plans=plans.findAll{it.id!=params.id}; 
def name = java.net.URLDecoder.decode(params.name, "UTF-8");
plans<<[id:params.id?:UUID.randomUUID().toString(), name:name, cost:safeDouble(params.cost), saved:safeDouble(params.saved), ts:new Date().getTime()]; atomicState.planned=plans; renderJson([status:"ok"]); }
def handleDelPlan() { atomicState.planned=(atomicState.planned?:[]).findAll{it.id!=params.id}; renderJson([status:"ok"]); }

def handleSetConfig() { 
    atomicState.startingBalance=safeDouble(params.start);
    atomicState.hsaStartBal=safeDouble(params.hsaStart); 
    atomicState.users = [
        java.net.URLDecoder.decode(params.u1 ?: "", "UTF-8"), 
        java.net.URLDecoder.decode(params.u2 ?: "", "UTF-8"), 
        java.net.URLDecoder.decode(params.u3 ?: "", "UTF-8")
    ]
    updateHsaHistory();
    renderJson([status:"ok"]); 
}
private renderJson(data) { render contentType: "application/json", data: JsonOutput.toJson(data) }
