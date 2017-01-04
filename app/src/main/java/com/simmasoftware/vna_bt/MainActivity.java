/*
    This is an example application for transferring messages to and from the
    Simma Software VNA-Bluetooth. This application is meant only as an example
    of how to perform the basic required operations.
*/

package com.simmasoftware.vna_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.net.Uri;
import android.net.Uri.Builder;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.net.URL;
import java.net.HttpURLConnection;

import android.os.StrictMode;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VNA-BT";

    private static final byte PORT_0 = 0;

    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 1;
    private static final UUID sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final byte RS232_FLAG = (byte) 0xC0;
    private static final byte RS232_ESCAPE = (byte) 0xDB;
    private static final byte RS232_ESCAPE_FLAG = (byte) 0xDC;
    private static final byte RS232_ESCAPE_ESCAPE = (byte) 0xDD;
    private static final String DEGREE = " \u00b0F";

    private static final byte VNA_MSG_ACK           = (byte) 0;     // ack
    private static final byte VNA_MSG_FA_J1939      = (byte) 1;     // pgn filter add
    private static final byte VNA_MSG_FD_J1939      = (byte) 2;     // pgn filter delete
    private static final byte VNA_MSG_FA_J1708      = (byte) 3;     // pid filter add
    private static final byte VNA_MSG_FD_J1708      = (byte) 4;     // pid filter delete
    private static final byte VNA_MSG_TX_J1939      = (byte) 5;     // pgn tx
    private static final byte VNA_MSG_RX_J1939      = (byte) 6;     // pgn rx
    private static final byte VNA_MSG_PX_J1939      = (byte) 7;     // pgn tx - periodic
    private static final byte VNA_MSG_TX_J1708      = (byte) 8;     // pid tx
    private static final byte VNA_MSG_RX_J1708      = (byte) 9;     // pid rx
    private static final byte VNA_MSG_PX_J1587      = (byte) 10;    // pid tx - periodic
    private static final byte VNA_MSG_PAMODE_SET    = (byte) 18;    // passall mode config
    private static final byte VNA_MSG_STATS         = (byte) 23;    // stats msg - 1 sec
    private static final byte VNA_MSG_ACONN         = (byte) 25;    // obd2 auto connect
    private static final byte VNA_MSG_VNA_ID        = (byte) 34;    // vna id and version
    private static final byte VNA_MSG_FA_I15765     = (byte) 40;    // pid filter add
    private static final byte VNA_MSG_FD_I15765     = (byte) 41;    // pid filter delete
    private static final byte VNA_MSG_TX_I15765     = (byte) 42;    // pid tx
    private static final byte VNA_MSG_RX_I15765     = (byte) 43;    // pid rx
    private static final byte VNA_MSG_PX_I15765     = (byte) 44;    // pid tx - periodic
    private static final byte VNA_MSG_ODOMETER      = (byte) 46;    // odometer
    private static final byte VNA_MSG_ACONN_EXT     = (byte) 52;    // auto connect extended
    private static final byte VNA_MSG_CHIRPCON      = (byte) 64;    // chirp control
    private static final byte VNA_MSG_GPS           = (byte) 69;    // gps info
    private static final byte VNA_MSG_REQ           = (byte) 255;   // request vna_msg

    private static final int NET_TYPE_OBD2_11       = (1 << 0);
    private static final int NET_TYPE_OBD2_29       = (1 << 1);
    private static final int NET_TYPE_OBD2          = (NET_TYPE_OBD2_29 | NET_TYPE_OBD2_11);
    private static final int NET_TYPE_J1939         = (1 << 2);
    private static final int NET_TYPE_RAW_11        = (1 << 3);
    private static final int NET_TYPE_RAW_29        = (1 << 4);
    private static final int NET_TYPE_RAW           = (NET_TYPE_RAW_29 | NET_TYPE_RAW_11);
    private static final int NET_TYPE_AUTOFAILED    = (1 << 31);

    private static final int NET_SPD_UNKNOWNERR     = (1 << 29);
    private static final int NET_SPD_AUTOFAILED     = (1 << 30);
    private static final int NET_SPD_INITSTATE      = (1 << 31);

    private static final byte PID_RPM               = (byte) 0x0C;
    private static final byte PID_SPEED             = (byte) 0x0D;

    private static final double KM_TO_MI = 0.621371;
    private static final double L_TO_GAL = 0.264172;
    private static final double KPA_TO_PSI = 0.145037738;
    private static final double KW_TO_HP = 1.34102209;
    private static final Integer MAX_16 = 0xffff;
    private static final Integer MAX_32 = 0xffffffff;
    private static final Integer MAX_8 = 0xff;
    private MenuItem connect_button = null;
    private boolean connected = false;
    private BluetoothSocket bluetoothSocket;
    private byte[] m_buffer;
    private int m_count;
    private boolean isInvalid;
    private boolean isStuffed;
    private int m_size;
    private HashMap<String, String> newData;
    private HashMap<String, Integer> monitorFields;

    private int network_type = NET_TYPE_AUTOFAILED;
    private int network_speed = 0;
    private int speed = 0;
    private int rpm = 0;
    private double odometer = 0;
    private double latInDegrees = 0;
    private double lonInDegrees = 0;
    private int noofSatellites = 0;

    private Handler handlerPeriodic = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initTextViews();

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            connect_button = item;

            if (item.getTitle().toString().compareToIgnoreCase("Disconnect") == 0) {
                item.setTitle("Connect");

                disconnect();
            } else {
                item.setTitle("Connecting...");

                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a bluetoothDevice to connect
                if (resultCode == Activity.RESULT_OK) {
                    final String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    Thread connectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            connectDevice(address, 0);
                        }
                    });
                    connectThread.start();
                }
                break;
        }
    }

    private void initTextViews() {
        newData = new HashMap<>();
        monitorFields = new HashMap<>();

        newData.put("Network Info", "");
        monitorFields.put("Network Info", R.id.NetworkInfoField);
        newData.put("Frames", "");
        monitorFields.put("Frames", R.id.CANFramesField);
        newData.put("Speed", "");
        monitorFields.put("Speed", R.id.SpeedField);
        newData.put("RPM", "");
        monitorFields.put("RPM", R.id.RPMField);
        newData.put("Odometer", "");
        monitorFields.put("Odometer", R.id.OdometerField);
        newData.put("Latitude", "");
        monitorFields.put("Latitude", R.id.LatitudeField);
        newData.put("Longitude", "");
        monitorFields.put("Longitude", R.id.LongitudeField);
        newData.put("Satellites", "");
        monitorFields.put("Satellites", R.id.SatellitesField);
    }

    private final Runnable readRun = new Runnable() {
        public void run() {
            receiveDataFromBT(bluetoothSocket);
        }
    };
    private Thread readThread;

    private BluetoothSocket connectDevice(String address, int i) {
        // Get the BluetoothDevice object

        BluetoothDevice bluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        connected = false;
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(sppUUID);
            bluetoothSocket.connect();

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_SHORT).show();
                    if (connect_button != null) connect_button.setTitle("Disconnect");
                    newData.put("Frames", "-");
                    updateLabels();
                }
            });
            connected = true;

            m_buffer = new byte[4096];
            m_count = 0;

            tenths = 0;
            seconds = 0;
            network_type = NET_TYPE_AUTOFAILED;
            network_speed = 0;
            handlerPeriodic.postDelayed(runnablePeriodic, 1000);

            if (readThread != null && readThread.isAlive()) {
                readThread.interrupt();
                while (readThread.isAlive()) Thread.yield();
            } else {
                readThread = new Thread(readRun);
                readThread.setPriority(4);
                readThread.start();
            }

        } catch (Exception ioex) {
            Log.e(TAG, "", ioex);
        }

        if (!connected) {
            // TODO: understand this
            if (i < 2) {
                connectDevice(address, i + 1);
            } else {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Bluetooth connection error, try again", Toast.LENGTH_SHORT).show();
                        if (connect_button != null) connect_button.setTitle("Connect");
                    }
                });

                disconnect();
            }
        }

        return (bluetoothSocket);
    }


    private void reconnect() {
        if (bluetoothSocket != null) {
            String address = bluetoothSocket.getRemoteDevice().getAddress();
            connected = false;
            try {
                bluetoothSocket.close();
            } catch (IOException e) { /* That's really too bad. */ }
            connectDevice(address, 1);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth connection lost. Please reconnect.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void disconnect() {
        try {
            if (readThread != null) readThread.interrupt();
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            /* We don't really care about the reconnect exceptions */
            Log.e(TAG, "In reconnect", e);
        }
        connected = false;
    }

    private void receiveDataFromBT(BluetoothSocket socket) {
        try {
            byte[] buffer = new byte[1024];
            int buf_len = 0;


            if (socket == null) {
                return;
            }

            InputStream inputStream = socket.getInputStream();

            while (true) {
                try {
                    if (Thread.interrupted()) {
                        inputStream.close();
                        return;
                    }
                    // Read from the InputStream
                    if (inputStream != null && socket != null) {
                        buf_len = inputStream.read(buffer);
                    }
                    Thread.sleep(1);

                    if (buf_len == -1) {
                        inputStream.close();
                        break;
                    }
                    parseMessage(buffer, buf_len);

                } catch (IOException e) {
                    if (Thread.interrupted()) {
                        inputStream.close();
                        return;
                    }
                    reconnect();
                    break;
                } catch (InterruptedException e) {
                    inputStream.close();
                    Log.e(TAG, "Interrupted read", e);
                    return;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
    }

    private void parseMessage(byte[] buf, int len) {
        for (int i = 0; i < len; i++) {
            processCharFromBus(buf[i]);
        }
    }

    private void processCharFromBus(byte val) {
        try {
            //Is it the start of the message?
            if (val == RS232_FLAG) {
                isInvalid = false;
                isStuffed = false;
                m_size = -1;
                m_count = 0;
            } else if (!isInvalid) {
                if (val == RS232_ESCAPE) {
                    isStuffed = true;
                } else {
                    //If previous byte was an escape, then decode current byte
                    if (isStuffed) {
                        isStuffed = false;
                        if (val == RS232_ESCAPE_FLAG) {
                            val = RS232_FLAG;
                        } else if (val == RS232_ESCAPE_ESCAPE) {
                            val = RS232_ESCAPE;
                        } else {
                            isInvalid = true;
                            // Invalid byte after escape, must abort
                            return;
                        }
                    }
                    //At this point data is always un-stuffed
                    if (m_count < m_buffer.length) {
                        m_buffer[m_count] = val;
                        m_count++;
                    } else {
                        //Full buffer
                    }

                    //At 2 bytes, we have enough info to calculate a real message length
                    if (m_count == 2) {
                        m_size = ((m_buffer[0] << 8) | m_buffer[1]) + 2;
                    }

                    //Have we received the entire message? If so, is it valid?
                    if (m_count == m_size) {
                        if (m_buffer[m_count - 1] == cksum(m_buffer, (m_count - 1))) {
                            byte[] payload = new byte[m_count - 3];
                            System.arraycopy(m_buffer, 2, payload, 0, payload.length);
                            processPacket(payload);
                        }
                        else {
                            Log.v(TAG, "Wrong checksum!");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getStackTrace()[0]);
        }
    }

    private void processPacket(byte[] packet) {
        String logOutput = String.format(Locale.US, "packet[%d]:", packet.length);
        for (byte b : packet) {
            logOutput += String.format(Locale.US, " %02X", b);
        }
        Log.v(TAG, logOutput);

        switch (packet[0] & 0xFF) {
            case VNA_MSG_ACK:
                // Byte 0:          message ID
                // Byte 1:          acknowledged message ID

                switch (packet[1] & 0xFF) {
                    case VNA_MSG_VNA_ID:
                        Log.v(TAG, "VNA_MSG_ACK -> VNA_MSG_VNA_ID");

                        sendCommand(requestSetPassAllMode(PORT_0));
                        break;

                    case VNA_MSG_PAMODE_SET:
                        Log.v(TAG, "VNA_MSG_ACK -> VNA_MSG_PAMODE_SET");

                        sendCommand(requestSetOdometer(0));
                        break;

                    case VNA_MSG_ODOMETER:
                        Log.v(TAG, "VNA_MSG_ACK -> VNA_MSG_ODOMETER");

                        sendCommand(requestChirpControl());
                        break;

                    case VNA_MSG_CHIRPCON:
                        Log.v(TAG, "VNA_MSG_ACK -> VNA_MSG_CHIRPCON");

                        sendCommand(requestAutoConnect(PORT_0));
                        break;

                    case VNA_MSG_ACONN_EXT:
                        Log.v(TAG, "VNA_MSG_ACK -> VNA_MSG_ACONN_EXT");

                        sendCommand(requestAutoConnectResult(PORT_0));
                        break;

                    default:
                        Log.v(TAG, "VNA_MSG_ACK -> ?");
                        break;
                }
                break;

            case VNA_MSG_RX_J1939:
                Log.v(TAG, "VNA_MSG_RX_J1939");

                final Integer pgn = ((packet[2] & 0xFF) << 16) | ((packet[3] & 0xFF) << 8) | (packet[4] & 0xFF);
                Double d;
                Integer i;
                String out;
                switch (pgn) {
                    case 61444:
                        i = ((packet[12] & 0xFF) << 8) | (packet[11] & 0xFF);
                        if (i.equals(MAX_16)) break;
                        newData.put("RPM", (i * 0.125 + "rpm")); /* SPN 190 */
                        break;
                    case 65262:
//                        i = (packet[8] & 0xFF);
//                        if(i.equals(MAX_8)) break;
//                        d = (i - 40) * 9 / 5.0 + 32;
//                        out = String.format(Locale.US, "%.1f%s",d,DEGREE);
//                        newData.put("Coolant",out); /* SPN 110 */
                        break;
                    case 65263:
//                        i = (packet[11] & 0xFF);
//                        if (i.equals(MAX_8)) break;
//                        d = i * 4 * KPA_TO_PSI;
//                        out = String.format(Locale.US, "%.2f psi", d);
//                        newData.put("Oil Pressure", out); /* SPN 100 */
                        break;
                }
                break;

            case VNA_MSG_PAMODE_SET:
                Log.v(TAG, "VNA_MSG_PAMODE_SET");

                break;

            case VNA_MSG_STATS:
                Log.v(TAG, "VNA_MSG_STATS");

                Long canFramesCount = (long) (((packet[9] & 0xFF) << 24) | ((packet[10] & 0xFF) << 16) | ((packet[11] & 0xFF) << 8) | (packet[12] & 0xFF));
                newData.put("Frames", canFramesCount + " frames");
                break;

            case VNA_MSG_VNA_ID:
                Log.v(TAG, "VNA_MSG_VNA_ID");

                break;

            case VNA_MSG_ACONN_EXT:
                Log.v(TAG, "VNA_MSG_ACONN_EXT");

                // Byte 0:          message ID
                // Byte 1:          port
                // Byte 2,3,4,5:    network type
                // Byte 6,7,8,9:    network speed

                out = String.format(Locale.US, "Port %d", (packet[1] & 0xFF));

                network_type = ((packet[2] & 0xFF) << 24) | ((packet[3] & 0xFF) << 16) | ((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF);
                switch (network_type) {
                    case NET_TYPE_OBD2_11:
                        out += " / 11bit-OBD2";
                        break;

                    case NET_TYPE_OBD2_29:
                        out += " / 29bit-OBD2";
                        break;

                    case NET_TYPE_J1939:
                        out += " / J1939";
                        break;

                    case NET_TYPE_AUTOFAILED:
                        out += " / auto-connect failed";
                        break;
                }

                network_speed = ((packet[6] & 0xFF) << 24) | ((packet[7] & 0xFF) << 16) | ((packet[8] & 0xFF) << 8) | (packet[9] & 0xFF);
                switch (network_speed) {
                    case 0:
                    case 250000:
                        out += " / 250kbps";
                        break;

                    case 1:
                    case 500000:
                        out += " / 500kbps";
                        break;

                    case NET_SPD_INITSTATE:
                        out += " / initial state - no attempt";
                        break;
                }

                newData.put("Network Info", out);
                break;

            case VNA_MSG_RX_I15765:
                Log.v(TAG, "VNA_MSG_RX_I15765");

                // Is this a powertrain response (0x41)?
                if ((packet[6] & 0xFF) == 0x41) {
                    switch (packet[7] & 0xFF) {
                        case PID_RPM:
                            // 1/4 rpm per bit
                            rpm = (((packet[8] & 0xFF) << 8) | (packet[9] & 0xFF)) / 4;
                            newData.put("RPM", rpm + "rpm");
                            break;

                        case PID_SPEED:
                            // 1 km/h per bit
                            speed = (int) (((float) (packet[8] & 0xFF) * KM_TO_MI) + 0.5);
                            newData.put("Speed", speed + "mph");
                            break;
                    }
                }
                break;

            case VNA_MSG_ODOMETER:
                Log.v(TAG, "VNA_MSG_ODOMETER");

                // Byte 0:          message ID
                // Byte 1,2,3,4:    odometer in tenths of miles

                odometer = (double) (((packet[1] & 0xFF) << 24) | ((packet[2] & 0xFF) << 16) | ((packet[3] & 0xFF) << 8) | (packet[4] & 0xFF)) / 10;
                newData.put("Odometer", String.format(Locale.US, "%.1f", odometer) + " miles");
                break;

            case VNA_MSG_GPS:
                Log.v(TAG, "VNA_MSG_GPS");

                // Byte 0:          message ID
                // Byte 1:          lat degrees
                // Byte 2:          lat minutes
                // Byte 3,4,5:      lat decimal minutes
                // Byte 6:          N or S (negative if South)
                // Byte 7:          lon degrees
                // Byte 8:          lon minutes
                // Byte 9,10,11:    lon decimal minutes
                // Byte 12:         E or W (negative if West)
                // Byte 13:         Number of satellites in view

                // Latitude = Degrees + (Minutes + .minutes) / 60
                latInDegrees = ((packet[1] & 0xFF)
                        + (((packet[2] & 0xFF)
                        + ((double) (((packet[3] & 0xFF) << 16)
                        | ((packet[4] & 0xFF) << 8)
                        | (packet[5] & 0xFF)) / 100000)) / 60))
                        * (((packet[6] & 0xFF) == 'S') ? -1 : 1);
                newData.put("Latitude", String.format(Locale.US, "%.7f", latInDegrees));

                // Longitude = Degrees + (Minutes + .minutes) / 60
                lonInDegrees = ((packet[7] & 0xFF)
                        + (((packet[8] & 0xFF)
                        + ((double) (((packet[9] & 0xFF) << 16)
                        | ((packet[10] & 0xFF) << 8)
                        | (packet[11] & 0xFF)) / 100000)) / 60))
                        * (((packet[12] & 0xFF) == 'W') ? -1 : 1);
                newData.put("Longitude", String.format(Locale.US, "%.7f", lonInDegrees));

                noofSatellites = packet[13];
                newData.put("Satellites", noofSatellites + "");
                break;

            default:
                Log.v(TAG, "Unknown!");
                break;
        }

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateLabels();
            }
        });
    }

    private void updateLabels() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f != null && f.getClass().equals(MainActivityFragment.class)) {
                for (Map.Entry<String, Integer> entry : monitorFields.entrySet()) {
                    Integer tv = entry.getValue();
                    String label = newData.get(entry.getKey());
                    if (!label.equals("")) {
                        if (tv != null) {
                            ((MainActivityFragment) f).update(tv, label);
                        }
                    }
                }
            }
        }
    }

    private void sendCommand(TxStruct command) {
        try {
            bluetoothSocket.getOutputStream().write(command.getBuf(), 0, command.getLen());
        } catch (IOException e) {
            Log.e(TAG, "Socket is closed", e);
        }
    }

    private byte[] buildMessage(byte[] payload) {
        byte[] message = new byte[2 + payload.length + 1];

        message[0] = (byte) (((payload.length + 1) >> 8) & 0xFF);
        message[1] = (byte) ((payload.length + 1) & 0xFF);
        System.arraycopy(payload, 0, message, 2, payload.length);
        message[2 + payload.length] = (byte) (cksum(message) & 0xFF);

        return message;
    }

    private byte[] stuffMessage(byte[] message) {
        byte[] stuffed = new byte[1 + (2 * message.length)];
        int cnt;

        // Tack on beginning of string marker
        stuffed[0] = RS232_FLAG;
        int esc_cnt = 1;
        // bytestuff
        for (cnt = 0; cnt < message.length; cnt++) {
            if (message[cnt] == RS232_FLAG) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_FLAG;
            } else if (message[cnt] == RS232_ESCAPE) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else {
                stuffed[cnt + esc_cnt] = message[cnt];
            }
        }

        byte[] retval = new byte[cnt + esc_cnt];
        System.arraycopy(stuffed, 0, retval, 0, (cnt + esc_cnt));
        return retval;
    }

    private int cksum(byte[] commandBytes) {
        int count = 0;

        for (int i = 1; i < commandBytes.length; i++) {
            count += uByte(commandBytes[i]);
        }

        return (byte) (~(count & 0xFF) + (byte) 1);
    }

    private int cksum(byte[] data, int numbytes) {
        int count = 0;

        for (int i = 0; i < numbytes; i++) {
            count += uByte(data[i]);
        }
        return (byte) (~(count & 0xFF) + (byte) 1);
    }

    private int uByte(byte b) {
        return (int) b & 0xFF;
    }

    private int tenths = 0;
    private int seconds = 0;

    private Runnable runnablePeriodic = new Runnable() {
        @Override
        public void run() {
            if (connected) {
                if (network_type == NET_TYPE_AUTOFAILED) {
                    if ((seconds == 0) && (tenths == 0)) {
                        // TODO: request VNA ID at the beginning
//                        sendCommand(request(VNA_MSG_VNA_ID));
                        sendCommand(requestSetPassAllMode(PORT_0));
                    }
                }
                else if ((network_type == NET_TYPE_OBD2_11) || (network_type == NET_TYPE_OBD2_29)) {
                    switch (tenths) {
                        case 0:
                            sendCommand(requestFunctional(PORT_0, PID_SPEED));
                            break;

                        case 1:
                            sendCommand(requestFunctional(PORT_0, PID_RPM));
                            break;
                    }
                }

                tenths++;
                if (tenths >= 10) {
                    tenths = 0;
                    seconds++;
                    if (seconds >= 60) {
                        seconds = 0;

                        sendQuery();
                    }
                }

                // Repeat the same runnable after 1 tenth of a second
                handlerPeriodic.postDelayed(this, 100);
            }
        }
    };

    public void sendQuery() {
        Builder builder = new Builder();

        builder.scheme("http")
                .authority("pipeline.trinium4fuel.com")
                .appendEncodedPath("cgi-bin/wspd_cgi-appsus1.sh/WService=ELD-WSV-LIVE/eld-integrator.w")
                .appendQueryParameter("wfprogname", "elr_create.p")
                .appendQueryParameter("supcode", "luisf")
                .appendQueryParameter("speed", speed + "")
                .appendQueryParameter("rpm", rpm + "")
                .appendQueryParameter("odometer", String.format(Locale.US, "%.1f", odometer))
                .appendQueryParameter("latitude", String.format(Locale.US, "%.7f", latInDegrees))
                .appendQueryParameter("longitude", String.format(Locale.US, "%.7f", lonInDegrees))
                .appendQueryParameter("satellites", noofSatellites + "")
                .build();

        try {
            URL url = new URL(builder.build().toString());

            Log.v(TAG, url.toString());

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            Log.v(TAG, urlConnection.getResponseMessage());

            urlConnection.disconnect();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public TxStruct request(byte id) {
        byte[] payload = new byte[1];

        payload[0] = id;

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    public TxStruct requestSetPassAllMode(byte port) {
        byte[] payload = new byte[6];

        payload[0] = VNA_MSG_PAMODE_SET;
        payload[1] = port;
        payload[2] = 1; // j1708
        payload[3] = 1; // j1587
        payload[4] = 1; // can
        payload[5] = 1; // j1939

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    public TxStruct requestSetOdometer(double odometer) {
        byte[] payload = new byte[5];

        payload[0] = VNA_MSG_ODOMETER;
        payload[1] = (byte) ((((int) (odometer * 10)) >> 24) & 0xFF);
        payload[2] = (byte) ((((int) (odometer * 10)) >> 16) & 0xFF);
        payload[3] = (byte) ((((int) (odometer * 10)) >> 8) & 0xFF);
        payload[4] = (byte) (((int) (odometer * 10)) & 0xFF);

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    public TxStruct requestChirpControl() {
        byte[] payload = new byte[5];

        payload[0] = VNA_MSG_CHIRPCON;
        payload[1] = (byte) 0xFF;
        payload[2] = (byte) 0xFF;
        payload[3] = (byte) 0xFF;
        payload[4] = (byte) 0xFF;

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    public TxStruct requestAutoConnect(byte port) {
        byte[] payload = new byte[2];

        payload[0] = VNA_MSG_ACONN_EXT;
        payload[1] = port;

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    public TxStruct requestAutoConnectResult(byte port) {
        byte[] payload = new byte[3];

        payload[0] = VNA_MSG_REQ;
        payload[1] = VNA_MSG_ACONN_EXT;
        payload[2] = port;

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    public TxStruct requestFunctional(byte port, byte pid) {
        byte[] payload = new byte[8];

        payload[0] = VNA_MSG_TX_I15765;
        payload[1] = port;      // port
        payload[3] = (byte) 0xF1;   // src, external test equipmt = 0xF1
        payload[4] = 6;      // pri

        // tat mapping: 11-physical=118, 29-functional=119, 11-physical=218, 29-functional=219
        if (network_type == NET_TYPE_OBD2_11) {
            payload[2] = 1;      // dst, unused since we are doing a functinoal request (i.e. 0x7DF request)
            payload[5] = 119;    // tat, 11-bit functional request
        } else if (network_type == NET_TYPE_OBD2_29) {
            payload[2] = 0x33;   // dst, iso spec says a functional request has a dst of 0x33
            payload[5] = (byte) 219;    // tat, 29-bit functional request
        } else {
            // not 11-bt or 29-bit OBD2
            return null;
        }

        // 2 bytes of data
        payload[6] = 1;
        payload[7] = pid;

        return new TxStruct(stuffMessage(buildMessage(payload)));
    }

    private void init_j1939() {
        // trimUntil = 0;
        long[] initPGN_AddFilter = {61444, 65262, 65263};

        for (long pgn : initPGN_AddFilter) {
            sendCommand(filterAddDelJ1939((byte) 0, pgn, true));
        }
    }

    public TxStruct filterAddDelJ1939(byte port, long pgnLong, boolean add) {
        byte[] pgn = new byte[3];

        pgn[0] = (byte) ((pgnLong >> 16) & 0xFF);
        pgn[1] = (byte) ((pgnLong >> 8) & 0xFF);
        pgn[2] = (byte) ((pgnLong) & 0xFF);

        byte[] message = new byte[8];
        byte[] stuffed = new byte[17];
        int cnt;

        message[0] = 0;
        message[1] = 6;
        message[2] = (byte) (add ? VNA_MSG_FA_J1939 : VNA_MSG_FD_J1939);
        message[3] = port;
        System.arraycopy(pgn, 0, message, 4, 3);

        message[7] = (byte) cksum(message);


        // Tack on beginning of string marker

        stuffed[0] = RS232_FLAG;


        int esc_cnt = 1;

        // Bytestuff
        for (cnt = 0; cnt < message.length; cnt++) {
            if (message[cnt] == RS232_FLAG) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_FLAG;
            } else if (message[cnt] == RS232_ESCAPE) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else {
                stuffed[cnt + esc_cnt] = message[cnt];
            }
        }
        return new TxStruct(stuffed, cnt + esc_cnt);
    }

    private TxStruct requestPGNJ1939(byte port, long pgnLong) {
        // c0 00 0a 05 00 pp gg nn 00 00 00 ff xx
        //                  PGN
        byte[] pgn = new byte[3];
        byte[] stuffed = new byte[30];

        pgn[0] = (byte) ((pgnLong) & 0xFF);
        pgn[1] = (byte) ((pgnLong >> 8) & 0xFF);
        pgn[2] = (byte) ((pgnLong >> 16) & 0xFF);

        byte[] message = new byte[14];
        int cnt;

        message[0] = 0;
        message[1] = (byte) (message.length - 2);
        message[2] = VNA_MSG_TX_J1939;
        message[3] = port;
        System.arraycopy(new byte[]{(byte) 0x00, (byte) 0xEA, (byte) 0x00}, 0, message, 4, 3);

        message[7] = (byte) 255;    // destination addr
        message[8] = (byte) 252;                // source addr
        message[9] = 6;                // priority

        System.arraycopy(pgn, 0, message, 10, 3);

        message[13] = (byte) cksum(message);

        // Tack on beginning of string marker
        stuffed[0] = RS232_FLAG;
        int esc_cnt = 1;
        // bytestuff
        for (cnt = 0; cnt < message.length; cnt++) {
            if (message[cnt] == RS232_FLAG) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_FLAG;
            } else if (message[cnt] == RS232_ESCAPE) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else {
                stuffed[cnt + esc_cnt] = message[cnt];
            }
        }

        return new TxStruct(stuffed, cnt + esc_cnt);
    }

    class TxStruct {
        private byte[] buf;
        private int len;

        public TxStruct() {
            buf = new byte[10];
            len = 0;
        }

        public TxStruct(int bufSize, int len) {
            buf = new byte[bufSize];
            this.len = len;
        }

        public TxStruct(byte[] buf, int len) {
            this.buf = buf;
            this.len = len;
        }

        public TxStruct(byte[] buf) {
            this.buf = buf;
            this.len = buf.length;
        }

        public void setLen(int length) {
            len = length;
        }

        public int getLen() {
            return len;
        }

        public void setBuf(int pos, byte data) {
            if (pos >= 0 && pos < buf.length) buf[pos] = data;
        }

        public byte[] getBuf() {
            return buf;
        }
    }
}
