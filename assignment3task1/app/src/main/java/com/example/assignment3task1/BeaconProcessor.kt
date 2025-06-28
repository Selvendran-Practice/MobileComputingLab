package com.example.assignment3task1



object BeaconProcessor {

    fun process(scanRecord: ByteArray, rssi: Int, viewModel: BeaconViewModel) {
        when (EddystoneParser.getFrameType(scanRecord)) {
            EddystoneParser.FrameType.UID -> {
                UidFrame.parse(scanRecord, rssi)?.let { (id, distance) ->
                    viewModel.updateData("UID Frame:\nBeacon ID: $id\nDistance: ${"%.2f".format(distance)} m")
                }
            }
            EddystoneParser.FrameType.URL -> {
                UrlFrame.parse(scanRecord)?.let { url ->
                    viewModel.updateData("URL Frame:\n$url")
                }
            }
            EddystoneParser.FrameType.TLM -> {
                TlmFrame.parse(scanRecord)?.let { (voltage, temp) ->
                    viewModel.updateData("TLM Frame:\nVoltage: $voltage V\nTemperature: ${"%.2f".format(temp)} °C")
                }
            }
            else -> {}
        }
    }
}
