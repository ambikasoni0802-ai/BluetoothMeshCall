# BT Mesh Call — Android Project (real, buildable)

Ye ek asli Android Studio project hai, sirf mockup nahi. Neeche har feature ke saath
uska honest reality diya gaya hai.

## Kya genuinely kaam karega

### 1. Text chat — BLE mesh flood-relay (`ble/BleMeshManager.kt`)
- Har phone ek saath do role play karta hai: BLE peripheral (GATT server) aur central (scanner).
- Jab bhi koi naya phone range mein aata hai (jisme ye app hai), auto-connect ho jaata hai.
- Message bhejne par wo har directly-connected neighbour ko forward hota hai; har neighbour
  usko dekhta/dikhata hai (agar broadcast ya usko addressed hai) aur aage bhi relay kar deta hai — TTL khatam hone tak.
- Isse message **kai phones ke through hop kar sakta hai**, bina kisi internet/SIM ke —
  bilkul jaise ek jungle trail mein log line mein spread hon.
- Ye part **genuinely multi-hop** kaam karta hai.

### 2. Audio call — Classic Bluetooth RFCOMM (`call/BluetoothAudioCallManager.kt`)
- Do phones ke beech raw PCM audio real-time stream hota hai (mic → socket → speaker).
- **Sirf direct range mein** (1 hop) kaam karega — pehle Android Bluetooth settings se dono
  phone pair karne honge, phir app mein "Call" dabao.
- Mesh ke through (kisi third phone se hokar) audio call **iss project mein nahi hai** —
  jaisa pehle discuss kiya, real-time audio ko flood-relay karna practically kaam nahi karta.

### 3. Video call
- **Iss project mein shamil nahi hai.** Pure Bluetooth (na BLE, na Classic) real-time video
  ke liye bandwidth provide nahi karta jo lag-free ho — khaaskar agar mesh ke through jaana ho.
  Agar future mein chahiye, to WiFi Direct ya internet-based fallback add karna padega
  (jo phir "sirf Bluetooth" wale requirement se bahar chala jaata hai).

## Kaise run karein
1. Android Studio (latest stable) install karo.
2. Is poori `BluetoothMeshCall` folder ko "Open" karo Android Studio mein.
3. Gradle sync hone do (pehli baar internet chahiye hoga dependencies download karne ke liye).
4. **Kam se kam 2 real Android phones** chahiye (Bluetooth emulator mein sahi se test nahi hota).
   - Dono phones ko USB se connect karo ya APK banake install karo.
5. App open karte hi Bluetooth/location/mic permissions maango — allow karo.
6. Chat test: dono phones ek dusre ke paas rakho, "Open mesh broadcast chat" se message bhejo.
7. Call test: pehle Android Bluetooth Settings mein dono phone pair karo, phir app ke
   peer list mein doosre phone ka address dikhega — "Call" dabao.

## Minimum SDK
- `minSdk = 26` (Android 8.0+) — kyunki BLE peripheral/GATT server mode isse pehle theek se support nahi hota.

## Aage kya improve ho sakta hai
- Device ID ko SharedPreferences mein persist karna (abhi har app restart par naya ID banta hai).
- Message encryption (abhi plain text jaata hai mesh mein).
- Bluetooth off hone par user ko settings mein bhejna (abhi sirf message dikhata hai).
- Battery optimization — continuous BLE scan battery kaafi kharch karta hai.
